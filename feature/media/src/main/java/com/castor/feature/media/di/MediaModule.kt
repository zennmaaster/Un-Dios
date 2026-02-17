package com.castor.feature.media.di

import com.castor.core.common.model.BookNotificationCallback
import com.castor.feature.media.adapter.UnifiedMediaAdapter
import com.castor.feature.media.kindle.BookNotificationCallbackImpl
import com.castor.feature.media.spotify.SpotifyApi
import com.castor.feature.media.spotify.SpotifyAuthManager
import com.castor.feature.media.spotify.SpotifyMediaAdapter
import com.castor.feature.media.youtube.YouTubeApi
import com.castor.feature.media.youtube.YouTubeApiClient
import com.castor.feature.media.youtube.YouTubeAuthManager
import com.castor.feature.media.youtube.YouTubeMediaAdapter
import com.castor.feature.media.audible.AudibleMediaAdapter
import com.castor.feature.media.session.MediaSessionMonitor
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifier for the media-layer [OkHttpClient]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MediaHttpClient

/**
 * Hilt module that wires up all media networking (Spotify, YouTube) and
 * exposes adapter bindings consumed by the rest of the media feature.
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    // -----------------------------------------------------------------
    // JSON
    // -----------------------------------------------------------------

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    // -----------------------------------------------------------------
    // OkHttp
    // -----------------------------------------------------------------

    @Provides
    @Singleton
    @MediaHttpClient
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // -----------------------------------------------------------------
    // Retrofit / Spotify API
    // -----------------------------------------------------------------

    @Provides
    @Singleton
    fun provideSpotifyApi(
        @MediaHttpClient client: OkHttpClient,
        json: Json
    ): SpotifyApi {
        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(SPOTIFY_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(SpotifyApi::class.java)
    }

    // -----------------------------------------------------------------
    // Retrofit / YouTube API
    // -----------------------------------------------------------------

    @Provides
    @Singleton
    fun provideYouTubeApi(
        @MediaHttpClient client: OkHttpClient,
        json: Json
    ): YouTubeApi {
        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(YouTubeApiClient.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(YouTubeApi::class.java)
    }

    // -----------------------------------------------------------------
    // Adapter bindings
    //
    // Each concrete adapter has @Singleton @Inject on its constructor,
    // so Hilt provides the concrete type directly. The @IntoSet methods
    // below widen the type into a Set<UnifiedMediaAdapter> multibinding
    // so the media hub can iterate over all adapters generically.
    // -----------------------------------------------------------------

    @Provides
    @IntoSet
    fun provideSpotifyAdapterIntoSet(
        adapter: SpotifyMediaAdapter
    ): UnifiedMediaAdapter = adapter

    @Provides
    @IntoSet
    fun provideYouTubeAdapterIntoSet(
        adapter: YouTubeMediaAdapter
    ): UnifiedMediaAdapter = adapter

    @Provides
    @IntoSet
    fun provideAudibleAdapterIntoSet(
        adapter: AudibleMediaAdapter
    ): UnifiedMediaAdapter = adapter

    // -----------------------------------------------------------------
    // Book Sync — Kindle/Audible notification callback
    // -----------------------------------------------------------------

    @Provides
    @Singleton
    fun provideBookNotificationCallback(
        impl: BookNotificationCallbackImpl
    ): BookNotificationCallback = impl

    // -----------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------

    private const val SPOTIFY_BASE_URL = "https://api.spotify.com/"
}
