package com.castor.core.inference.llama

/**
 * Callback interface for streaming token generation from native llama.cpp.
 * Called from JNI for each generated token chunk.
 */
interface LlamaStreamCallback {
    fun onToken(token: String)
}
