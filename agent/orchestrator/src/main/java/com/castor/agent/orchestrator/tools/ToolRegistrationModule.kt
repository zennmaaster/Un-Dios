package com.castor.agent.orchestrator.tools

import com.castor.agent.orchestrator.AgentHealthMonitor
import com.castor.agent.orchestrator.MediaAgent
import com.castor.agent.orchestrator.MemoryManager
import com.castor.agent.orchestrator.MessagingAgent
import com.castor.agent.orchestrator.ReminderAgent
import com.castor.core.inference.tool.ToolHandler
import com.castor.core.inference.tool.ToolRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet

/**
 * Hilt module that provides all tool handlers as a multibinding set.
 *
 * The tools are registered with [ToolRegistry] via [ToolInitializer],
 * which is injected into the [AgentLoop] constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
object ToolRegistrationModule {

    @Provides
    @ElementsIntoSet
    fun provideMediaTools(
        mediaAgent: MediaAgent
    ): Set<ToolHandler> = setOf(
        PlayMediaTool(mediaAgent),
        PauseMediaTool(mediaAgent),
        SkipTrackTool(mediaAgent),
        PreviousTrackTool(mediaAgent),
        NowPlayingTool(mediaAgent),
        QueueMediaTool(mediaAgent)
    )

    @Provides
    @ElementsIntoSet
    fun provideMessagingTools(
        messagingAgent: MessagingAgent
    ): Set<ToolHandler> = setOf(
        SendMessageTool(messagingAgent),
        SummarizeMessagesTool(messagingAgent),
        SmartReplyTool(messagingAgent)
    )

    @Provides
    @ElementsIntoSet
    fun provideReminderTools(
        reminderAgent: ReminderAgent
    ): Set<ToolHandler> = setOf(
        SetReminderTool(reminderAgent),
        ListRemindersTool(reminderAgent),
        CompleteReminderTool(reminderAgent)
    )

    @Provides
    @ElementsIntoSet
    fun provideSystemTools(
        healthMonitor: AgentHealthMonitor,
        memoryManager: MemoryManager
    ): Set<ToolHandler> = setOf(
        GetTimeTool(),
        GetStatusTool(healthMonitor),
        SaveMemoryTool(memoryManager),
        RecallMemoryTool(memoryManager)
    )
}
