#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <cmath>
#include <unistd.h>

#include "common.h"
#include "chat.h"
#include "llama.h"
#include "sampling.h"

#define TAG "UnDios-LLM"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGw(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGd(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

// -------------------------------------------------------------------------
// Global state
// -------------------------------------------------------------------------
static llama_model   *g_model   = nullptr;
static llama_context *g_context = nullptr;
static llama_batch    g_batch;
static common_chat_templates_ptr g_chat_templates;
static common_sampler *g_sampler = nullptr;

static int g_context_size = 4096;
static int g_batch_size   = 512;

// Chat state
static std::vector<common_chat_msg> g_chat_msgs;
static llama_pos g_system_pos  = 0;
static llama_pos g_current_pos = 0;

// Generation state
static llama_pos   g_stop_pos = 0;
static std::string g_cached_chars;
static std::ostringstream g_assistant_ss;

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------
static void reset_chat_state(bool clear_kv = true) {
    g_chat_msgs.clear();
    g_system_pos  = 0;
    g_current_pos = 0;
    if (clear_kv && g_context)
        llama_memory_clear(llama_get_memory(g_context), false);
}

static void reset_gen_state() {
    g_stop_pos = 0;
    g_cached_chars.clear();
    g_assistant_ss.str("");
}

static void shift_context() {
    int n_discard = (g_current_pos - g_system_pos) / 2;
    LOGi("Shifting context: discarding %d tokens", n_discard);
    llama_memory_seq_rm(llama_get_memory(g_context), 0, g_system_pos, g_system_pos + n_discard);
    llama_memory_seq_add(llama_get_memory(g_context), 0, g_system_pos + n_discard, g_current_pos, -n_discard);
    g_current_pos -= n_discard;
}

static int decode_batched(
    llama_context *ctx, llama_batch &batch,
    const llama_tokens &tokens, llama_pos start,
    bool logit_last = false
) {
    for (int i = 0; i < (int)tokens.size(); i += g_batch_size) {
        int cur = std::min((int)tokens.size() - i, g_batch_size);
        common_batch_clear(batch);

        if (start + i + cur >= g_context_size - 4) {
            shift_context();
        }

        for (int j = 0; j < cur; j++) {
            bool want_logit = logit_last && (i + j == (int)tokens.size() - 1);
            common_batch_add(batch, tokens[i + j], start + i + j, {0}, want_logit);
        }

        if (llama_decode(ctx, batch) != 0) {
            LOGe("llama_decode failed");
            return 1;
        }
    }
    return 0;
}

static bool is_valid_utf8(const char *s) {
    if (!s) return true;
    const unsigned char *b = (const unsigned char *)s;
    while (*b) {
        int n;
        if      ((*b & 0x80) == 0x00) n = 1;
        else if ((*b & 0xE0) == 0xC0) n = 2;
        else if ((*b & 0xF0) == 0xE0) n = 3;
        else if ((*b & 0xF8) == 0xF0) n = 4;
        else return false;
        b++;
        for (int i = 1; i < n; i++) {
            if ((*b & 0xC0) != 0x80) return false;
            b++;
        }
    }
    return true;
}

// -------------------------------------------------------------------------
// JNI: Package com.castor.core.inference.llama.LlamaCppEngine
// -------------------------------------------------------------------------
extern "C" {

// --- nativeInit(nativeLibDir: String) ---
JNIEXPORT void JNICALL
Java_com_castor_core_inference_llama_LlamaCppEngine_nativeInit(
    JNIEnv *env, jobject, jstring jLibDir
) {
    const char *libDir = env->GetStringUTFChars(jLibDir, nullptr);
    LOGi("Loading backends from %s", libDir);
    ggml_backend_load_all_from_path(libDir);
    env->ReleaseStringUTFChars(jLibDir, libDir);
    llama_backend_init();
    LOGi("Backend initialized");
}

// --- nativeLoadModel(path, contextSize, threads, gpuLayers, useMmap, flashAttention): Long ---
JNIEXPORT jlong JNICALL
Java_com_castor_core_inference_llama_LlamaCppEngine_nativeLoadModel(
    JNIEnv *env, jobject,
    jstring jpath, jint contextSize, jint threads,
    jint gpuLayers, jboolean useMmap, jboolean flashAttention
) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    LOGi("Loading model: %s (ctx=%d, threads=%d, gpu=%d)", path, contextSize, threads, gpuLayers);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = gpuLayers;
    mparams.use_mmap = useMmap;

    llama_model *model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);

    if (!model) {
        LOGe("Failed to load model");
        return 0;
    }

    g_model = model;
    g_context_size = contextSize;
    g_batch_size = 512;

    // Create context
    int n_threads = std::max(2, std::min(threads, (int)sysconf(_SC_NPROCESSORS_ONLN) - 2));
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = contextSize;
    cparams.n_batch   = g_batch_size;
    cparams.n_ubatch  = g_batch_size;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;
    cparams.flash_attn_type = flashAttention ? LLAMA_FLASH_ATTN_TYPE_ENABLED : LLAMA_FLASH_ATTN_TYPE_DISABLED;

    g_context = llama_init_from_model(model, cparams);
    if (!g_context) {
        LOGe("Failed to create context");
        llama_model_free(model);
        g_model = nullptr;
        return 0;
    }

    g_batch = llama_batch_init(g_batch_size, 0, 1);
    g_chat_templates = common_chat_templates_init(model, "");

    // Default sampler
    common_params_sampling sparams;
    sparams.temp = 0.7f;
    g_sampler = common_sampler_init(model, sparams);

    reset_chat_state();
    reset_gen_state();

    LOGi("Model loaded successfully");
    return (jlong)(intptr_t)model;
}

// --- nativeFreeModel(handle: Long) ---
JNIEXPORT void JNICALL
Java_com_castor_core_inference_llama_LlamaCppEngine_nativeFreeModel(
    JNIEnv *, jobject, jlong handle
) {
    reset_chat_state(false);
    reset_gen_state();

    if (g_sampler) { common_sampler_free(g_sampler); g_sampler = nullptr; }
    g_chat_templates.reset();
    llama_batch_free(g_batch);
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model)   { llama_model_free(g_model); g_model = nullptr; }

    LOGi("Model unloaded");
}

// --- nativeGenerate(handle, prompt, maxTokens, temperature, topP, topK, repeatPenalty): String ---
JNIEXPORT jstring JNICALL
Java_com_castor_core_inference_llama_LlamaCppEngine_nativeGenerate(
    JNIEnv *env, jobject,
    jlong handle, jstring jprompt, jint maxTokens,
    jfloat temperature, jfloat topP, jint topK, jfloat repeatPenalty
) {
    if (!g_model || !g_context) {
        return env->NewStringUTF("[Error: Model not loaded]");
    }

    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt_str(prompt);
    env->ReleaseStringUTFChars(jprompt, prompt);

    // Reset state for new generation
    reset_chat_state();
    reset_gen_state();

    // Reconfigure sampler with requested params
    if (g_sampler) common_sampler_free(g_sampler);
    common_params_sampling sparams;
    sparams.temp           = temperature;
    sparams.top_p          = topP;
    sparams.top_k          = topK;
    sparams.penalty_repeat = repeatPenalty;
    g_sampler = common_sampler_init(g_model, sparams);

    // Tokenize the full prompt
    bool has_tmpl = common_chat_templates_was_explicit(g_chat_templates.get());
    auto tokens = common_tokenize(g_context, prompt_str, has_tmpl, has_tmpl);

    // Truncate if too long
    int max_prompt = g_context_size - maxTokens - 4;
    if ((int)tokens.size() > max_prompt) {
        tokens.resize(max_prompt);
        LOGw("Prompt truncated to %d tokens", max_prompt);
    }

    // Decode prompt
    if (decode_batched(g_context, g_batch, tokens, 0, true) != 0) {
        return env->NewStringUTF("[Error: Failed to process prompt]");
    }
    g_current_pos = (int)tokens.size();

    // Generate tokens
    std::ostringstream result;
    for (int i = 0; i < maxTokens; i++) {
        if (g_current_pos >= g_context_size - 4) shift_context();

        llama_token id = common_sampler_sample(g_sampler, g_context, -1);
        common_sampler_accept(g_sampler, id, true);

        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), id)) break;

        std::string piece = common_token_to_piece(g_context, id);
        result << piece;

        common_batch_clear(g_batch);
        common_batch_add(g_batch, id, g_current_pos, {0}, true);
        if (llama_decode(g_context, g_batch) != 0) {
            LOGe("Decode failed during generation at token %d", i);
            break;
        }
        g_current_pos++;
    }

    std::string output = result.str();
    LOGi("Generated %d chars", (int)output.size());

    // Validate UTF-8 before passing to JNI (truncate incomplete trailing bytes)
    if (!is_valid_utf8(output.c_str())) {
        // Trim trailing incomplete UTF-8 sequence
        while (!output.empty() && (output.back() & 0xC0) == 0x80) {
            output.pop_back();
        }
        if (!output.empty() && (output.back() & 0x80) != 0) {
            output.pop_back(); // Remove the incomplete lead byte
        }
    }

    return env->NewStringUTF(output.c_str());
}

// --- nativeGenerateStream(handle, prompt, maxTokens, temp, topP, topK, repeatPenalty, callback) ---
JNIEXPORT void JNICALL
Java_com_castor_core_inference_llama_LlamaCppEngine_nativeGenerateStream(
    JNIEnv *env, jobject,
    jlong handle, jstring jprompt, jint maxTokens,
    jfloat temperature, jfloat topP, jint topK, jfloat repeatPenalty,
    jobject callback
) {
    if (!g_model || !g_context) return;

    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt_str(prompt);
    env->ReleaseStringUTFChars(jprompt, prompt);

    // Reset and configure
    reset_chat_state();
    reset_gen_state();

    if (g_sampler) common_sampler_free(g_sampler);
    common_params_sampling sparams;
    sparams.temp           = temperature;
    sparams.top_p          = topP;
    sparams.top_k          = topK;
    sparams.penalty_repeat = repeatPenalty;
    g_sampler = common_sampler_init(g_model, sparams);

    bool has_tmpl = common_chat_templates_was_explicit(g_chat_templates.get());
    auto tokens = common_tokenize(g_context, prompt_str, has_tmpl, has_tmpl);

    int max_prompt = g_context_size - maxTokens - 4;
    if ((int)tokens.size() > max_prompt) tokens.resize(max_prompt);

    if (decode_batched(g_context, g_batch, tokens, 0, true) != 0) return;
    g_current_pos = (int)tokens.size();

    // Get callback method
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    if (!onToken) {
        LOGe("Could not find onToken callback method");
        return;
    }

    std::string cached;
    for (int i = 0; i < maxTokens; i++) {
        if (g_current_pos >= g_context_size - 4) shift_context();

        llama_token id = common_sampler_sample(g_sampler, g_context, -1);
        common_sampler_accept(g_sampler, id, true);

        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), id)) break;

        cached += common_token_to_piece(g_context, id);

        if (is_valid_utf8(cached.c_str())) {
            jstring jtoken = env->NewStringUTF(cached.c_str());
            env->CallVoidMethod(callback, onToken, jtoken);
            env->DeleteLocalRef(jtoken);
            cached.clear();

            // Check if the Java callback threw an exception
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                LOGw("Java callback threw exception, stopping generation");
                break;
            }
        }

        common_batch_clear(g_batch);
        common_batch_add(g_batch, id, g_current_pos, {0}, true);
        if (llama_decode(g_context, g_batch) != 0) break;
        g_current_pos++;
    }
}

// --- nativeTokenize(handle, text): IntArray ---
JNIEXPORT jintArray JNICALL
Java_com_castor_core_inference_llama_LlamaCppEngine_nativeTokenize(
    JNIEnv *env, jobject, jlong handle, jstring jtext
) {
    if (!g_context) {
        return env->NewIntArray(0);
    }

    const char *text = env->GetStringUTFChars(jtext, nullptr);
    auto tokens = common_tokenize(g_context, std::string(text), false, false);
    env->ReleaseStringUTFChars(jtext, text);

    jintArray result = env->NewIntArray((int)tokens.size());
    if (result) {
        std::vector<jint> jtokens(tokens.begin(), tokens.end());
        env->SetIntArrayRegion(result, 0, (int)jtokens.size(), jtokens.data());
    }
    return result;
}

// --- nativeShutdown() ---
JNIEXPORT void JNICALL
Java_com_castor_core_inference_llama_LlamaCppEngine_nativeShutdown(
    JNIEnv *, jobject
) {
    llama_backend_free();
    LOGi("Backend shut down");
}

} // extern "C"
