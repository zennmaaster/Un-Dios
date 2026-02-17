package com.castor.agent.orchestrator

import com.castor.core.inference.InferenceEngine
import com.castor.core.inference.ModelManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentOrchestrator @Inject constructor(
    private val engine: InferenceEngine,
    private val modelManager: ModelManager
) {
    private val routerSystemPrompt = """
        You are Castor, a helpful AI assistant running on the user's Android phone.
        You can help with:
        - Messaging: Read and reply to WhatsApp and Teams messages
        - Media: Control Spotify, YouTube, and Audible playback
        - Reminders: Set and manage reminders and calendar events
        - General questions: Answer questions using your knowledge

        Respond concisely and helpfully. If you need to take an action (like sending a message or playing music), describe what you would do.
    """.trimIndent()

    suspend fun processInput(input: String): String {
        return if (engine.isLoaded) {
            engine.generate(
                prompt = input,
                systemPrompt = routerSystemPrompt,
                maxTokens = 256,
                temperature = 0.7f
            )
        } else {
            "Model not loaded. Place a .gguf model file in the models directory and restart Castor."
        }
    }
}
