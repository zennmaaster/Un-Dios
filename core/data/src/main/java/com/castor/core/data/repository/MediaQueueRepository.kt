package com.castor.core.data.repository

import com.castor.core.common.model.MediaSource
import com.castor.core.common.model.MediaType
import com.castor.core.common.model.UnifiedMediaItem
import com.castor.core.data.db.dao.MediaQueueDao
import com.castor.core.data.db.entity.MediaQueueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository managing the unified cross-source media queue.
 *
 * Provides an observable queue of [UnifiedMediaItem]s backed by Room through
 * [MediaQueueDao]. All position bookkeeping (insert-at, shift, reorder) is
 * handled here so that callers never need to think about raw queue indices.
 *
 * Sources can be any [MediaSource] — Spotify, YouTube, Audible — and the queue
 * treats them uniformly. The item at position 0 is always the "currently playing"
 * item by convention.
 */
@Singleton
class MediaQueueRepository @Inject constructor(
    private val mediaQueueDao: MediaQueueDao
) {

    // -------------------------------------------------------------------------------------
    // Observable queue
    // -------------------------------------------------------------------------------------

    /**
     * Returns a [Flow] of the full queue ordered by position, mapped to domain models.
     * Emits a new list every time the underlying table changes.
     */
    fun getQueue(): Flow<List<UnifiedMediaItem>> =
        mediaQueueDao.getQueue().map { entities -> entities.map { it.toDomain() } }

    /**
     * Returns a [Flow] of the current queue size (number of items).
     */
    fun getQueueSize(): Flow<Int> = mediaQueueDao.getQueueSize()

    // -------------------------------------------------------------------------------------
    // Current item
    // -------------------------------------------------------------------------------------

    /**
     * Returns the item at the lowest queue position (the "now playing" item), or null
     * if the queue is empty.
     */
    suspend fun getCurrentItem(): UnifiedMediaItem? =
        mediaQueueDao.getCurrentItem()?.toDomain()

    /**
     * Returns a specific queue item by its ID, or null if not found.
     */
    suspend fun getItemById(itemId: String): UnifiedMediaItem? =
        mediaQueueDao.getItemById(itemId)?.toDomain()

    // -------------------------------------------------------------------------------------
    // Queue management — add
    // -------------------------------------------------------------------------------------

    /**
     * Appends an item to the end of the queue.
     */
    suspend fun addToQueue(item: UnifiedMediaItem) {
        val nextPosition = mediaQueueDao.getNextPosition()
        mediaQueueDao.insertItem(item.toEntity(nextPosition))
    }

    /**
     * Inserts an item at position 1 (immediately after the currently-playing item)
     * so it becomes the "play next" track. All items at position >= 1 are shifted up.
     */
    suspend fun addToQueueNext(item: UnifiedMediaItem) {
        // If the queue is empty, just insert at position 0.
        val currentItem = mediaQueueDao.getCurrentItem()
        if (currentItem == null) {
            mediaQueueDao.insertItem(item.toEntity(0))
            return
        }

        // Shift everything at position >= 1 up by one.
        mediaQueueDao.shiftPositionsUp(1)
        mediaQueueDao.insertItem(item.toEntity(1))
    }

    // -------------------------------------------------------------------------------------
    // Queue management — remove
    // -------------------------------------------------------------------------------------

    /**
     * Removes a single item from the queue and shifts all subsequent items down
     * to close the gap.
     */
    suspend fun removeFromQueue(itemId: String) {
        val entity = mediaQueueDao.getItemById(itemId) ?: return
        val position = entity.queuePosition
        mediaQueueDao.removeItem(itemId)
        mediaQueueDao.shiftPositionsDown(position)
    }

    /**
     * Clears the entire queue.
     */
    suspend fun clearQueue() {
        mediaQueueDao.clearQueue()
    }

    // -------------------------------------------------------------------------------------
    // Queue management — advance
    // -------------------------------------------------------------------------------------

    /**
     * Advances the queue by removing the current (position-0) item and shifting
     * every remaining item down by one. After this call the previous position-1
     * item becomes the new position-0 ("now playing") item.
     */
    suspend fun advanceQueue() {
        val current = mediaQueueDao.getCurrentItem() ?: return
        mediaQueueDao.removeItem(current.id)
        mediaQueueDao.shiftPositionsDown(current.queuePosition)
    }

    // -------------------------------------------------------------------------------------
    // Reorder
    // -------------------------------------------------------------------------------------

    /**
     * Moves an item from [fromPosition] to [toPosition], shifting intermediate
     * items as needed to maintain a contiguous position sequence.
     *
     * This is the operation backing drag-to-reorder in the queue UI.
     */
    suspend fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return

        // Snapshot the queue so we can perform the reorder atomically.
        val queue = mediaQueueDao.getCurrentQueue()
        if (queue.isEmpty()) return

        // Validate bounds.
        val maxPos = queue.maxOf { it.queuePosition }
        if (fromPosition < 0 || fromPosition > maxPos) return
        if (toPosition < 0 || toPosition > maxPos) return

        val movingItem = queue.firstOrNull { it.queuePosition == fromPosition } ?: return

        if (fromPosition < toPosition) {
            // Moving down: shift items in (from+1..to) up by -1, then place item at toPosition.
            queue.filter { it.queuePosition in (fromPosition + 1)..toPosition }
                .forEach { entity ->
                    mediaQueueDao.updatePosition(entity.id, entity.queuePosition - 1)
                }
        } else {
            // Moving up: shift items in (to..from-1) down by +1, then place item at toPosition.
            queue.filter { it.queuePosition in toPosition until fromPosition }
                .forEach { entity ->
                    mediaQueueDao.updatePosition(entity.id, entity.queuePosition + 1)
                }
        }

        mediaQueueDao.updatePosition(movingItem.id, toPosition)
    }

    // -------------------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------------------

    private fun MediaQueueEntity.toDomain(): UnifiedMediaItem = UnifiedMediaItem(
        id = id,
        source = try {
            MediaSource.valueOf(source)
        } catch (_: IllegalArgumentException) {
            MediaSource.SPOTIFY // Fallback — should never happen with well-formed data
        },
        sourceUri = sourceUri,
        title = title,
        artist = artist,
        albumArtUrl = albumArtUrl,
        durationMs = durationMs,
        mediaType = try {
            MediaType.valueOf(mediaType)
        } catch (_: IllegalArgumentException) {
            MediaType.MUSIC
        }
    )

    private fun UnifiedMediaItem.toEntity(position: Int): MediaQueueEntity = MediaQueueEntity(
        id = id,
        source = source.name,
        sourceUri = sourceUri,
        title = title,
        artist = artist,
        albumArtUrl = albumArtUrl,
        durationMs = durationMs,
        mediaType = mediaType.name,
        queuePosition = position
    )
}
