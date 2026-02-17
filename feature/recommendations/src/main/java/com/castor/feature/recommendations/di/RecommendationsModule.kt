package com.castor.feature.recommendations.di

import com.castor.core.common.model.MediaNotificationCallback
import com.castor.core.data.db.dao.RecommendationDao
import com.castor.core.data.db.dao.TasteProfileDao
import com.castor.core.data.db.dao.WatchHistoryDao
import com.castor.core.data.repository.WatchHistoryRepository
import com.castor.core.inference.InferenceEngine
import com.castor.feature.recommendations.engine.ContentCatalogMatcher
import com.castor.feature.recommendations.engine.RecommendationEngine
import com.castor.feature.recommendations.engine.TasteProfileEngine
import com.castor.feature.recommendations.tracking.MediaNotificationCallbackImpl
import com.castor.feature.recommendations.tracking.MediaWatchTracker
import com.castor.feature.recommendations.tracking.NotificationMediaExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for the recommendations feature.
 *
 * Provides singletons for the tracking pipeline, taste profile engine,
 * recommendation engine, and catalog matcher. All DAOs are already
 * provided by [com.castor.core.data.di.DatabaseModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object RecommendationsModule {

    @Provides
    @Singleton
    fun provideNotificationMediaExtractor(): NotificationMediaExtractor =
        NotificationMediaExtractor()

    @Provides
    @Singleton
    fun provideMediaWatchTracker(
        extractor: NotificationMediaExtractor,
        watchHistoryRepository: WatchHistoryRepository
    ): MediaWatchTracker = MediaWatchTracker(extractor, watchHistoryRepository)

    @Provides
    @Singleton
    fun provideMediaNotificationCallback(
        mediaWatchTracker: MediaWatchTracker
    ): MediaNotificationCallback = MediaNotificationCallbackImpl(mediaWatchTracker)

    @Provides
    @Singleton
    fun provideTasteProfileEngine(
        watchHistoryDao: WatchHistoryDao,
        tasteProfileDao: TasteProfileDao
    ): TasteProfileEngine = TasteProfileEngine(watchHistoryDao, tasteProfileDao)

    @Provides
    @Singleton
    fun provideContentCatalogMatcher(): ContentCatalogMatcher = ContentCatalogMatcher()

    @Provides
    @Singleton
    fun provideRecommendationEngine(
        inferenceEngine: InferenceEngine,
        tasteProfileEngine: TasteProfileEngine,
        catalogMatcher: ContentCatalogMatcher,
        recommendationDao: RecommendationDao
    ): RecommendationEngine = RecommendationEngine(
        inferenceEngine, tasteProfileEngine, catalogMatcher, recommendationDao
    )
}
