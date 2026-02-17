package com.castor.core.security.di

import com.castor.core.security.BiometricAuthManager
import com.castor.core.security.CastorKeyManager
import com.castor.core.security.SecurePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    // CastorKeyManager and SecurePreferences are @Singleton @Inject constructor
    // so Hilt provides them automatically.
    // BiometricAuthManager needs to be created per-activity, not singleton.
}
