package com.castor.core.inference.prompt

/**
 * Supported prompt template formats for on-device LLM inference.
 *
 * Each format corresponds to how a particular model family expects
 * system prompts, user messages, and assistant responses to be structured.
 * Using the wrong format will produce garbage output or cause the model
 * to ignore instructions.
 */
enum class PromptFormat {
    /** ChatML format used by Qwen2.5, Yi, and other ChatML-trained models. */
    CHATML,

    /** Microsoft Phi-3 format with <|system|>, <|user|>, <|assistant|> tags. */
    PHI3,

    /** Meta Llama 3 format with <|begin_of_text|> and role headers. */
    LLAMA3,

    /** Google Gemma format with <start_of_turn> markers. */
    GEMMA,

    /** Generic Alpaca/Stanford instruction format (### Instruction / ### Response). */
    ALPACA
}

/**
 * Model family identifier for model-specific optimizations and defaults.
 *
 * Used by [ModelManager] to select appropriate inference parameters,
 * prompt format, and context length for each model.
 */
enum class ModelFamily(
    val displayName: String,
    val defaultPromptFormat: PromptFormat,
    val defaultContextLength: Int
) {
    QWEN25("Qwen 2.5", PromptFormat.CHATML, 32768),
    PHI3("Phi-3", PromptFormat.PHI3, 4096),
    LLAMA3("Llama 3", PromptFormat.LLAMA3, 8192),
    GEMMA("Gemma", PromptFormat.GEMMA, 8192),
    GENERIC("Generic", PromptFormat.ALPACA, 4096)
}
