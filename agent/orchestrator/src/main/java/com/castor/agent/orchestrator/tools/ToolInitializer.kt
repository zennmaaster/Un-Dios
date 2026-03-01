package com.castor.agent.orchestrator.tools

import android.util.Log
import com.castor.core.inference.tool.ToolHandler
import com.castor.core.inference.tool.ToolRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers all tool handlers with the [ToolRegistry] on first access.
 *
 * Hilt injects the multibinding `Set<ToolHandler>` (populated by
 * [ToolRegistrationModule]) and the singleton [ToolRegistry]. On first
 * call to [ensureRegistered], all tools are registered exactly once.
 */
@Singleton
class ToolInitializer @Inject constructor(
    private val registry: ToolRegistry,
    private val tools: Set<@JvmSuppressWildcards ToolHandler>
) {

    companion object {
        private const val TAG = "ToolInitializer"
    }

    @Volatile
    private var initialized = false

    /**
     * Register all tools if not yet done. Safe to call multiple times.
     */
    fun ensureRegistered() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            tools.forEach { handler ->
                registry.register(handler)
            }
            initialized = true
            Log.d(TAG, "Registered ${tools.size} tools with ToolRegistry")
        }
    }
}
