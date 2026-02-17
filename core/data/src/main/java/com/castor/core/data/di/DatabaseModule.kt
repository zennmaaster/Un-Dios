package com.castor.core.data.di

import android.content.Context
import androidx.room.Room
import com.castor.core.data.db.CastorDatabase
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
import com.castor.core.security.CastorKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: CastorKeyManager
    ): CastorDatabase {
        val passphrase = keyManager.getDatabasePassphrase()
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            CastorDatabase::class.java,
            "castor.db"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideMessageDao(db: CastorDatabase): MessageDao = db.messageDao()
    @Provides fun provideReminderDao(db: CastorDatabase): ReminderDao = db.reminderDao()
    @Provides fun provideMediaQueueDao(db: CastorDatabase): MediaQueueDao = db.mediaQueueDao()
    @Provides fun provideConversationDao(db: CastorDatabase): ConversationDao = db.conversationDao()
    @Provides fun provideWatchHistoryDao(db: CastorDatabase): WatchHistoryDao = db.watchHistoryDao()
    @Provides fun provideTasteProfileDao(db: CastorDatabase): TasteProfileDao = db.tasteProfileDao()
    @Provides fun provideRecommendationDao(db: CastorDatabase): RecommendationDao = db.recommendationDao()
    @Provides fun provideBookSyncDao(db: CastorDatabase): BookSyncDao = db.bookSyncDao()
    @Provides fun provideNoteDao(db: CastorDatabase): NoteDao = db.noteDao()
    @Provides fun provideNotificationDao(db: CastorDatabase): NotificationDao = db.notificationDao()
}
