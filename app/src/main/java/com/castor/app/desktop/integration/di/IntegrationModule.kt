package com.castor.app.desktop.integration.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt dependency injection module for the desktop integration layer.
 *
 * All integration components ([ProfileManager], [AppLauncher],
 * [KeyboardShortcutRegistry], [DragDropManager]) use `@Singleton`
 * with `@Inject constructor()` and are auto-discovered by Hilt.
 *
 * This module is installed in [SingletonComponent] and exists as a
 * placeholder for any future `@Provides` or `@Binds` bindings that
 * may be needed as the integration layer evolves (e.g., providing
 * interface implementations, configuring profile persistence, etc.).
 */
@Module
@InstallIn(SingletonComponent::class)
object IntegrationModule
