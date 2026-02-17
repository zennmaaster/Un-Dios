package com.castor.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.castor.core.data.db.dao.ConversationDao
import com.castor.core.data.db.dao.MediaQueueDao
import com.castor.core.data.db.dao.MessageDao
import com.castor.core.data.db.dao.ReminderDao
import com.castor.core.data.db.entity.ConversationEntity
import com.castor.core.data.db.entity.MediaQueueEntity
import com.castor.core.data.db.entity.MessageEntity
import com.castor.core.data.db.entity.ReminderEntity

@Database(
    entities = [
        MessageEntity::class,
        ReminderEntity::class,
        MediaQueueEntity::class,
        ConversationEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class CastorDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun reminderDao(): ReminderDao
    abstract fun mediaQueueDao(): MediaQueueDao
    abstract fun conversationDao(): ConversationDao
}
