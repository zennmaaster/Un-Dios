package com.castor.app.weather.di

import com.castor.app.weather.WeatherApiService
import com.castor.app.weather.WeatherRepository
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for the weather feature.
 *
 * Provides the [WeatherApiService] and [WeatherRepository] singletons.
 * Both use constructor injection via @Inject, so these @Provides methods
 * are technically redundant — but they make the dependency graph explicit
 * and consistent with other feature modules in the project.
 */
@Module
@InstallIn(SingletonComponent::class)
object WeatherModule {

    @Provides
    @Singleton
    fun provideWeatherApiService(): WeatherApiService {
        return WeatherApiService()
    }

    @Provides
    @Singleton
    fun provideWeatherRepository(
        apiService: WeatherApiService,
        @ApplicationContext context: Context
    ): WeatherRepository {
        return WeatherRepository(apiService, context)
    }
}
