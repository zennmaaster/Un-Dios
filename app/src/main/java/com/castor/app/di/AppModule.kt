package com.castor.app.di

import android.content.Context
import com.castor.app.system.SystemStatsProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing app-level singleton dependencies.
 *
 * Installed in [SingletonComponent] so these instances live for the entire
 * application lifecycle and are shared across all activities and fragments.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provide the [SystemStatsProvider] singleton.
     *
     * This is provided explicitly via @Provides rather than constructor injection
     * so that the app module owns its lifecycle and it can be easily swapped
     * or mocked in tests.
     */
    @Provides
    @Singleton
    fun provideSystemStatsProvider(
        @ApplicationContext context: Context
    ): SystemStatsProvider {
        return SystemStatsProvider(context)
    }
}
