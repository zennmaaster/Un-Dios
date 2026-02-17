package com.castor.feature.reminders.di

import com.castor.feature.reminders.google.GoogleCalendarApi
import com.castor.feature.reminders.google.GoogleTasksApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifier for the Google APIs OkHttp client. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GoogleApiClient

/** Qualifier for the Google APIs Retrofit instance. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GoogleApiRetrofit

/**
 * Hilt module providing Google Calendar and Tasks API dependencies.
 *
 * Provides:
 * - Configured [OkHttpClient] with logging interceptor
 * - [Json] instance configured for Google API responses
 * - [Retrofit] instance pointed at `https://www.googleapis.com/`
 * - [GoogleCalendarApi] and [GoogleTasksApi] implementations
 */
@Module
@InstallIn(SingletonComponent::class)
object RemindersModule {

    private const val GOOGLE_API_BASE_URL = "https://www.googleapis.com/"

    /**
     * Provides a lenient [Json] configuration for Google API responses.
     *
     * Google APIs may include unknown fields or use non-standard JSON conventions,
     * so we enable `ignoreUnknownKeys` and `isLenient` to handle these cases.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * Provides an [OkHttpClient] configured for Google API communication.
     *
     * Includes:
     * - HTTP logging interceptor (BODY level for debug builds)
     * - 30-second timeouts for connect, read, and write
     */
    @Provides
    @Singleton
    @GoogleApiClient
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Provides a [Retrofit] instance for Google APIs.
     *
     * Uses kotlinx.serialization for JSON conversion with the configured [Json] instance.
     */
    @Provides
    @Singleton
    @GoogleApiRetrofit
    fun provideRetrofit(
        @GoogleApiClient okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(GOOGLE_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    /**
     * Provides the [GoogleCalendarApi] Retrofit service implementation.
     */
    @Provides
    @Singleton
    fun provideGoogleCalendarApi(
        @GoogleApiRetrofit retrofit: Retrofit
    ): GoogleCalendarApi {
        return retrofit.create(GoogleCalendarApi::class.java)
    }

    /**
     * Provides the [GoogleTasksApi] Retrofit service implementation.
     */
    @Provides
    @Singleton
    fun provideGoogleTasksApi(
        @GoogleApiRetrofit retrofit: Retrofit
    ): GoogleTasksApi {
        return retrofit.create(GoogleTasksApi::class.java)
    }
}
