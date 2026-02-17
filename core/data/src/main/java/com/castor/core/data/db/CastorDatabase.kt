package com.castor.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.castor.core.data.db.dao.BookSyncDao
import com.castor.core.data.db.dao.ConversationDao
import com.castor.core.data.db.dao.MediaQueueDao
import com.castor.core.data.db.dao.MessageDao
import com.castor.core.data.db.dao.NoteDao
import com.castor.core.data.db.dao.NotificationDao
import com.castor.core.data.db.dao.RecommendationDao
import com.castor.core.data.db.dao.ReminderDao
import com.castor.core.data.db.dao.TasteProfileDao
import com.castor.core.data.db.dao.WatchHistoryDao
import com.castor.core.data.db.entity.BookSyncEntity
import com.castor.core.data.db.entity.ConversationEntity
import com.castor.core.data.db.entity.MediaQueueEntity
import com.castor.core.data.db.entity.MessageEntity
import com.castor.core.data.db.entity.NoteEntity
import com.castor.core.data.db.entity.NotificationEntity
import com.castor.core.data.db.entity.RecommendationEntity
import com.castor.core.data.db.entity.ReminderEntity
import com.castor.core.data.db.entity.TasteProfileEntity
import com.castor.core.data.db.entity.WatchHistoryEntity

@Database(
    entities = [
        MessageEntity::class,
        ReminderEntity::class,
        MediaQueueEntity::class,
        ConversationEntity::class,
        WatchHistoryEntity::class,
        TasteProfileEntity::class,
        RecommendationEntity::class,
        BookSyncEntity::class,
        NoteEntity::class,
        NotificationEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class CastorDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun reminderDao(): ReminderDao
    abstract fun mediaQueueDao(): MediaQueueDao
    abstract fun conversationDao(): ConversationDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun tasteProfileDao(): TasteProfileDao
    abstract fun recommendationDao(): RecommendationDao
    abstract fun bookSyncDao(): BookSyncDao
    abstract fun noteDao(): NoteDao
    abstract fun notificationDao(): NotificationDao
}
