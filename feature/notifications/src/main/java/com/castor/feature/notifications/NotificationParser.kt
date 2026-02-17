package com.castor.feature.notifications

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.castor.core.common.model.MessageSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of parsing a notification from WhatsApp or Teams.
 *
 * @param sender The display name of the person who sent the message.
 * @param content The message body. May contain placeholders like "[Photo]" or "[Voice message]".
 * @param groupName The group or channel name, null for direct messages.
 * @param isActionable True when the notification carries a RemoteInput that can be used to reply.
 * @param conversationId An opaque identifier built from sender + group that can be used to thread
 *   messages into conversations.
 * @param groupKey The notification group key from the system for grouping related notifications.
 * @param conversationTitle The conversation title for messaging-style notifications (e.g., group chat name).
 * @param actionCount The number of actions available on the notification.
 * @param hasReplyAction Whether this notification has a direct reply action with RemoteInput.
 * @param thumbnailUri URI string of the notification's large icon / thumbnail, if present.
 */
data class ParsedNotification(
    val sender: String,
    val content: String,
    val groupName: String?,
    val isActionable: Boolean,
    val conversationId: String?,
    val groupKey: String? = null,
    val conversationTitle: String? = null,
    val actionCount: Int = 0,
    val hasReplyAction: Boolean = false,
    val thumbnailUri: String? = null
)

/**
 * Robust notification parser that handles the complex and varying notification formats produced by
 * WhatsApp and Microsoft Teams.
 *
 * Notification formats handled:
 *
 * **WhatsApp**
 * - DM: title = "Sender", text = "message"
 * - Group: title = "Sender @ Group" **or** "Group: Sender", text = "message"
 * - Group summary: title = "Group (N messages)", text = multiline summary
 * - Media messages: text contains emoji placeholders (camera, mic, etc.)
 * - Calls / ongoing / progress notifications are skipped.
 *
 * **Teams**
 * - DM: title = "Sender", text = "message"
 * - Channel: title = "Channel in Team", text = "Sender: message"
 * - Meeting notifications (title contains "Meeting") are skipped.
 * - Calls / ongoing notifications are skipped.
 */
@Singleton
class NotificationParser @Inject constructor() {

    companion object {
        // WhatsApp notification tags and categories used for filtering.
        private const val WHATSAPP_MSG_TAG = "msg"
        private const val WHATSAPP_SUMMARY_TAG = "summary"

        // WhatsApp media placeholder patterns.
        private val MEDIA_PLACEHOLDERS = listOf(
            "\uD83D\uDCF7" to "[Photo]",       // camera emoji
            "\uD83C\uDFA5" to "[Video]",        // video camera emoji
            "\uD83C\uDFA4" to "[Voice message]", // microphone emoji
            "\uD83D\uDCCE" to "[Document]",     // paperclip emoji
            "\uD83D\uDCCD" to "[Location]",     // pin emoji
            "\uD83D\uDC64" to "[Contact]",      // silhouette emoji
            "\uD83D\uDCF9" to "[Video call]",   // video camera emoji variant
        )

        // Pattern that matches WhatsApp group summary titles like "Family (5 messages)".
        private val GROUP_SUMMARY_PATTERN = Regex("""^(.+)\s*\(\d+\s*messages?\)$""")

        // Pattern that matches "Sender @ Group" (WhatsApp group notification variant).
        private val WHATSAPP_AT_GROUP_PATTERN = Regex("""^(.+?)\s*@\s*(.+)$""")

        // Teams meeting keywords found in notification titles.
        private val TEAMS_MEETING_KEYWORDS = listOf("Meeting", "meeting", "Call", "call")
    }

    /**
     * Attempts to parse a [StatusBarNotification] into a [ParsedNotification].
     *
     * Returns `null` when the notification should be ignored (system notifications, calls,
     * ongoing/progress, meetings, summary groups, etc.).
     */
    fun parse(sbn: StatusBarNotification): ParsedNotification? {
        val source = MessageSource.fromPackageName(sbn.packageName) ?: return null
        val notification = sbn.notification

        // --- Skip non-message notifications ---
        if (shouldSkip(notification, sbn)) return null

        val extras = notification.extras

        // Extract raw title and text from the notification extras.
        val title = extractTitle(extras) ?: return null
        val rawText = extractText(extras) ?: return null

        // Normalise media placeholders in the body.
        val text = normalizeMediaPlaceholders(rawText)

        // Check whether this notification has a reply action.
        val isActionable = hasReplyAction(notification)

        // Extract enriched metadata for grouping and digest features.
        val groupKey = sbn.groupKey
        val conversationTitle = extractConversationTitle(extras)
        val actionCount = notification.actions?.size ?: 0
        val replyAction = detectReplyAction(notification)
        val thumbnailUri = extractThumbnailUri(notification)

        val base = when (source) {
            MessageSource.WHATSAPP -> parseWhatsApp(title, text, isActionable, sbn)
            MessageSource.TEAMS -> parseTeams(title, text, isActionable, sbn)
        } ?: return null

        // Enrich the parsed result with the new metadata fields.
        return base.copy(
            groupKey = groupKey,
            conversationTitle = conversationTitle,
            actionCount = actionCount,
            hasReplyAction = replyAction,
            thumbnailUri = thumbnailUri
        )
    }

    // -------------------------------------------------------------------------------------
    // WhatsApp parsing
    // -------------------------------------------------------------------------------------

    private fun parseWhatsApp(
        title: String,
        text: String,
        isActionable: Boolean,
        sbn: StatusBarNotification
    ): ParsedNotification? {

        // WhatsApp group summary notifications (e.g. "Family (5 messages)").
        GROUP_SUMMARY_PATTERN.matchEntire(title)?.let { match ->
            val groupName = match.groupValues[1].trim()
            // Summary notifications are informational; the individual message notifications carry
            // the actual content. Skip if the text is just a count or empty.
            return ParsedNotification(
                sender = groupName,
                content = text,
                groupName = groupName,
                isActionable = isActionable,
                conversationId = buildConversationId(groupName, groupName)
            )
        }

        // "Sender @ Group" variant.
        WHATSAPP_AT_GROUP_PATTERN.matchEntire(title)?.let { match ->
            val sender = match.groupValues[1].trim()
            val groupName = match.groupValues[2].trim()
            return ParsedNotification(
                sender = sender,
                content = text,
                groupName = groupName,
                isActionable = isActionable,
                conversationId = buildConversationId(sender, groupName)
            )
        }

        // "Group: Sender" variant (WhatsApp in some locales / versions).
        if (title.contains(":")) {
            val parts = title.split(":", limit = 2)
            val firstPart = parts[0].trim()
            val secondPart = parts[1].trim()
            // Heuristic: the longer name is usually the group name.
            // However the canonical format is "Group: Sender" where firstPart = group.
            return ParsedNotification(
                sender = secondPart,
                content = text,
                groupName = firstPart,
                isActionable = isActionable,
                conversationId = buildConversationId(secondPart, firstPart)
            )
        }

        // Plain DM — title is sender name.
        return ParsedNotification(
            sender = title,
            content = text,
            groupName = null,
            isActionable = isActionable,
            conversationId = buildConversationId(title, null)
        )
    }

    // -------------------------------------------------------------------------------------
    // Teams parsing
    // -------------------------------------------------------------------------------------

    private fun parseTeams(
        title: String,
        text: String,
        isActionable: Boolean,
        sbn: StatusBarNotification
    ): ParsedNotification? {

        // Skip meeting notifications.
        if (TEAMS_MEETING_KEYWORDS.any { title.contains(it) }) return null

        // Channel message: title = "Channel in Team", text = "Sender: message".
        if (title.contains(" in ")) {
            val channelName = title.trim()
            // Extract sender from text body.
            val (sender, content) = extractTeamsChannelSenderAndContent(text)
            return ParsedNotification(
                sender = sender,
                content = content,
                groupName = channelName,
                isActionable = isActionable,
                conversationId = buildConversationId(sender, channelName)
            )
        }

        // DM — title is the sender name.
        return ParsedNotification(
            sender = title,
            content = text,
            groupName = null,
            isActionable = isActionable,
            conversationId = buildConversationId(title, null)
        )
    }

    /**
     * Teams channel messages embed the sender in the text body as "Sender: actual message".
     */
    private fun extractTeamsChannelSenderAndContent(text: String): Pair<String, String> {
        val colonIndex = text.indexOf(':')
        return if (colonIndex > 0 && colonIndex < text.length - 1) {
            val sender = text.substring(0, colonIndex).trim()
            val content = text.substring(colonIndex + 1).trim()
            sender to content
        } else {
            "Unknown" to text
        }
    }

    // -------------------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------------------

    /**
     * Determines if a notification should be skipped entirely.
     */
    private fun shouldSkip(notification: Notification, sbn: StatusBarNotification): Boolean {
        // Skip ongoing notifications (calls, active voice/video).
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return true

        // Skip progress notifications (file transfers, etc.).
        val extras = notification.extras
        if (extras.containsKey(Notification.EXTRA_PROGRESS)) return true

        // Skip group summary headers — we process the individual children instead.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return true

        // Skip notifications from the system category that don't carry a message.
        val category = notification.category
        if (category == Notification.CATEGORY_CALL ||
            category == Notification.CATEGORY_TRANSPORT ||
            category == Notification.CATEGORY_SERVICE ||
            category == Notification.CATEGORY_SYSTEM ||
            category == Notification.CATEGORY_PROGRESS
        ) return true

        return false
    }

    /**
     * Extracts the notification title from extras, preferring EXTRA_TITLE.
     */
    private fun extractTitle(extras: Bundle): String? {
        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.takeIf { it.isNotBlank() }
    }

    /**
     * Extracts the notification text from extras, trying multiple fields in priority order.
     */
    private fun extractText(extras: Bundle): String? {
        // Prefer EXTRA_BIG_TEXT (expanded notification) because it contains the full message.
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        if (bigText != null) return bigText

        // Fall back to EXTRA_TEXT.
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        if (text != null) return text

        // Last resort: EXTRA_SUMMARY_TEXT (some summary notifications).
        return extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.takeIf { it.isNotBlank() }
    }

    /**
     * Replaces common emoji media placeholders with human-readable text.
     */
    private fun normalizeMediaPlaceholders(text: String): String {
        var result = text
        for ((emoji, replacement) in MEDIA_PLACEHOLDERS) {
            result = result.replace(emoji, replacement)
        }
        return result.trim()
    }

    /**
     * Checks whether a [Notification] has at least one action with a [android.app.RemoteInput]
     * (i.e., a reply action).
     */
    private fun hasReplyAction(notification: Notification): Boolean {
        return notification.actions?.any { action ->
            action.remoteInputs?.isNotEmpty() == true
        } == true
    }

    /**
     * Detects whether any action on the notification carries a [android.app.RemoteInput],
     * indicating a direct inline reply capability.
     */
    private fun detectReplyAction(notification: Notification): Boolean {
        return notification.actions?.any { action ->
            action.remoteInputs?.isNotEmpty() == true
        } == true
    }

    /**
     * Extracts the conversation title from notification extras.
     *
     * Messaging-style notifications (e.g., group chats) often carry
     * [Notification.EXTRA_CONVERSATION_TITLE] to identify the conversation.
     */
    private fun extractConversationTitle(extras: Bundle): String? {
        return extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Extracts a thumbnail URI from the notification's large icon.
     *
     * Returns the URI string if the large icon is a URI-based [Icon], or `null` otherwise.
     * Falls back to checking the deprecated [Notification.EXTRA_LARGE_ICON] bitmap extra.
     */
    private fun extractThumbnailUri(notification: Notification): String? {
        // Try the modern Icon API (API 23+). Icon.getUri() is available for URI-type icons.
        notification.getLargeIcon()?.let { icon ->
            try {
                return icon.uri?.toString()
            } catch (_: Exception) {
                // Icon is not URI-based (bitmap, resource, etc.) -- fall through.
            }
        }
        return null
    }

    /**
     * Builds a stable conversation identifier from the sender and optional group name.
     * Used to thread messages belonging to the same conversation.
     */
    private fun buildConversationId(sender: String, groupName: String?): String {
        return if (groupName != null) {
            "conv:${groupName.lowercase().trim()}"
        } else {
            "conv:dm:${sender.lowercase().trim()}"
        }
    }
}
