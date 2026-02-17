package com.castor.app.di

import android.content.Context
import com.castor.app.system.NotificationCountHolder
import com.castor.app.system.SystemStatsProvider
import com.castor.core.common.model.NotificationCountCallback
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
     * Provide the [NotificationCountHolder] singleton.
     *
     * Shared between [SystemStatsProvider] (reads the count) and
     * [CastorNotificationListener] (writes the count via the [NotificationCountCallback] binding).
     */
    @Provides
    @Singleton
    fun provideNotificationCountHolder(): NotificationCountHolder {
        return NotificationCountHolder()
    }

    /**
     * Bind [NotificationCountCallback] to [NotificationCountHolder].
     *
     * This allows `:feature:notifications` (which depends on `:core:common` but not `:app`)
     * to inject the callback interface and update notification counts without knowing
     * about the concrete holder implementation.
     */
    @Provides
    @Singleton
    fun provideNotificationCountCallback(
        holder: NotificationCountHolder
    ): NotificationCountCallback {
        return holder
    }

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
        @ApplicationContext context: Context,
        notificationCountHolder: NotificationCountHolder
    ): SystemStatsProvider {
        return SystemStatsProvider(context, notificationCountHolder)
    }
}
