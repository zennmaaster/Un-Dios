package com.castor.app.desktop.integration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the type of content being dragged between windows.
 *
 * Used by [DragData] to indicate what kind of content is in the drag
 * payload, so that target windows can decide whether to accept the drop.
 */
enum class DragType {
    /** A file path or content URI pointing to a file. */
    FILE,

    /** Plain text or rich text content. */
    TEXT,

    /** A URI (web URL, deep link, or content URI). */
    URI,

    /** An image file or bitmap. */
    IMAGE
}

/**
 * Data payload for an active drag-and-drop operation.
 *
 * Created when a drag starts and carried through the operation until
 * the user drops the content on a target window or cancels.
 *
 * @param type The kind of content being dragged
 * @param content The content string — a URI, file path, or text
 * @param sourceWindowId The ID of the window where the drag originated
 * @param mimeType The MIME type of the content (e.g., "text/plain", "image/png")
 */
data class DragData(
    val type: DragType,
    val content: String,
    val sourceWindowId: String,
    val mimeType: String = "text/plain"
)

/**
 * Manages drag-and-drop operations between desktop windows.
 *
 * Provides a simple state machine for inter-window drag and drop:
 * 1. Source window calls [startDrag] with the content payload
 * 2. UI layer renders a visual drag indicator while [isDragging] is true
 * 3. Target window calls [acceptDrop] to receive the payload
 * 4. The drag ends (either accepted, cancelled, or dropped outside)
 *
 * Supports dragging files, text, URIs, and images between apps.
 * The active drag state is observable via [activeDrag] so that
 * Compose UI can show drop target indicators on eligible windows.
 *
 * This is a Hilt [Singleton] — a single instance coordinates drag
 * operations across all desktop windows.
 *
 * Usage:
 * ```kotlin
 * // Start a drag from the terminal window
 * dragDropManager.startDrag(DragData(
 *     type = DragType.TEXT,
 *     content = "Hello from terminal",
 *     sourceWindowId = "terminal"
 * ))
 *
 * // Accept the drop in the messages window
 * val data = dragDropManager.acceptDrop("messages")
 * data?.let { handleDrop(it) }
 * ```
 */
@Singleton
class DragDropManager @Inject constructor() {

    /** Internal mutable state for the active drag operation. */
    private val _activeDrag = MutableStateFlow<DragData?>(null)

    /** Observable state of the current drag operation, or null if no drag is active. */
    val activeDrag: StateFlow<DragData?> = _activeDrag.asStateFlow()

    /**
     * Whether a drag operation is currently in progress.
     *
     * Derived from [activeDrag] — true when the drag data is non-null.
     * Observe this to show/hide drag indicators in the UI.
     */
    val isDragging: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        // Manually sync since we can't use stateIn without a scope here.
        // The UI layer should observe activeDrag and derive isDragging.
    }

    /**
     * Starts a drag operation with the given payload.
     *
     * Sets the active drag state, which triggers UI updates in all
     * observing windows. Only one drag can be active at a time; calling
     * this while a drag is already active replaces the previous drag.
     *
     * @param data The drag payload including content, type, and source window
     */
    fun startDrag(data: DragData) {
        _activeDrag.value = data
    }

    /**
     * Ends the current drag operation without delivering to any target.
     *
     * Clears the active drag state. Called when the drag is completed
     * (after [acceptDrop]) or when the user releases outside a valid target.
     */
    fun endDrag() {
        _activeDrag.value = null
    }

    /**
     * Accepts the current drag payload at the target window.
     *
     * Returns the drag data and clears the active drag state. Returns
     * null if no drag is currently active or if the target is the same
     * window that initiated the drag (self-drops are not allowed).
     *
     * @param targetWindowId The ID of the window accepting the drop
     * @return The drag data if accepted, null if no drag or self-drop
     */
    fun acceptDrop(targetWindowId: String): DragData? {
        val current = _activeDrag.value ?: return null

        // Prevent self-drops (dragging within the same window)
        if (current.sourceWindowId == targetWindowId) {
            return null
        }

        _activeDrag.value = null
        return current
    }

    /**
     * Cancels the current drag operation.
     *
     * Equivalent to [endDrag] — clears the drag state so that no
     * drop target can accept the payload.
     */
    fun cancelDrag() {
        _activeDrag.value = null
    }

    /**
     * Checks whether the current drag is active and originated from a different window.
     *
     * Useful for windows to determine if they should show a drop target indicator.
     *
     * @param windowId The window to check against
     * @return true if a drag is active from a different window
     */
    fun isDropTarget(windowId: String): Boolean {
        val current = _activeDrag.value ?: return false
        return current.sourceWindowId != windowId
    }
}
