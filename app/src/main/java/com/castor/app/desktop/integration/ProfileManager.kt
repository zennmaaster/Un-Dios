package com.castor.app.desktop.integration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages work/personal/custom app profiles in the desktop environment.
 *
 * The ProfileManager is the single source of truth for which profile is active
 * and what apps belong to each profile. When the user switches profiles, the
 * dock and app drawer observe the [activeProfile] state and filter their
 * displayed apps accordingly.
 *
 * Initialized with [DEFAULT_WORK_PROFILE] and [DEFAULT_PERSONAL_PROFILE].
 * The work profile is active by default.
 *
 * This is a Hilt [Singleton] — a single instance manages profiles across
 * the entire desktop session.
 *
 * Usage:
 * ```kotlin
 * // Observe the active profile
 * val activeProfile by profileManager.activeProfile.collectAsState()
 *
 * // Switch to personal profile
 * profileManager.switchProfile("personal")
 *
 * // Add an app to the work profile
 * profileManager.addAppToProfile("work", "com.notion.android")
 * ```
 */
@Singleton
class ProfileManager @Inject constructor() {

    /** Internal mutable list of all available profiles. */
    private val _profiles = MutableStateFlow(
        listOf(
            DEFAULT_WORK_PROFILE.copy(isActive = true),
            DEFAULT_PERSONAL_PROFILE
        )
    )

    /** Observable list of all available profiles. */
    val profiles: StateFlow<List<AppProfile>> = _profiles.asStateFlow()

    /** Internal mutable active profile state. */
    private val _activeProfile = MutableStateFlow(DEFAULT_WORK_PROFILE.copy(isActive = true))

    /** The currently active profile. Dock and app drawer observe this. */
    val activeProfile: StateFlow<AppProfile> = _activeProfile.asStateFlow()

    /**
     * Switches to the profile with the given [id].
     *
     * Marks the target profile as active and deactivates all others.
     * If no profile matches the given ID, the operation is a no-op.
     *
     * @param id The unique identifier of the profile to activate
     */
    fun switchProfile(id: String) {
        _profiles.update { currentProfiles ->
            val targetExists = currentProfiles.any { it.id == id }
            if (!targetExists) return@update currentProfiles

            currentProfiles.map { profile ->
                profile.copy(isActive = profile.id == id)
            }
        }

        val newActive = _profiles.value.find { it.id == id }
        if (newActive != null) {
            _activeProfile.value = newActive
        }
    }

    /**
     * Creates a new profile and adds it to the profile list.
     *
     * The new profile is added in an inactive state. If a profile with
     * the same ID already exists, the operation is a no-op.
     *
     * @param profile The new profile to create
     */
    fun createProfile(profile: AppProfile) {
        _profiles.update { currentProfiles ->
            if (currentProfiles.any { it.id == profile.id }) {
                return@update currentProfiles
            }
            currentProfiles + profile.copy(isActive = false)
        }
    }

    /**
     * Deletes the profile with the given [id].
     *
     * If the deleted profile was active, the first remaining profile
     * is automatically activated. Cannot delete the last remaining profile.
     *
     * @param id The unique identifier of the profile to delete
     */
    fun deleteProfile(id: String) {
        _profiles.update { currentProfiles ->
            // Prevent deleting the last profile
            if (currentProfiles.size <= 1) return@update currentProfiles

            val deletedWasActive = currentProfiles.find { it.id == id }?.isActive == true
            val remaining = currentProfiles.filter { it.id != id }

            if (deletedWasActive && remaining.isNotEmpty()) {
                // Activate the first remaining profile
                val activated = remaining.mapIndexed { index, profile ->
                    profile.copy(isActive = index == 0)
                }
                _activeProfile.value = activated.first()
                activated
            } else {
                remaining
            }
        }
    }

    /**
     * Adds an app to the specified profile by package name.
     *
     * If the app is already in the profile's app list, the operation is a no-op.
     *
     * @param profileId The profile to add the app to
     * @param packageName The package name of the app to add
     */
    fun addAppToProfile(profileId: String, packageName: String) {
        _profiles.update { currentProfiles ->
            currentProfiles.map { profile ->
                if (profile.id == profileId && packageName !in profile.apps) {
                    val updated = profile.copy(apps = profile.apps + packageName)
                    if (profile.isActive) {
                        _activeProfile.value = updated
                    }
                    updated
                } else {
                    profile
                }
            }
        }
    }

    /**
     * Removes an app from the specified profile by package name.
     *
     * Also removes the app from the profile's dock apps if present.
     *
     * @param profileId The profile to remove the app from
     * @param packageName The package name of the app to remove
     */
    fun removeAppFromProfile(profileId: String, packageName: String) {
        _profiles.update { currentProfiles ->
            currentProfiles.map { profile ->
                if (profile.id == profileId) {
                    val updated = profile.copy(
                        apps = profile.apps.filter { it != packageName },
                        dockApps = profile.dockApps.filter { it != packageName }
                    )
                    if (profile.isActive) {
                        _activeProfile.value = updated
                    }
                    updated
                } else {
                    profile
                }
            }
        }
    }

    /**
     * Adds an app to the dock shortcuts of the specified profile.
     *
     * The app must already be in the profile's app list. If it's already
     * a dock app, the operation is a no-op.
     *
     * @param profileId The profile to modify
     * @param packageName The package name of the app to pin to the dock
     */
    fun addToDock(profileId: String, packageName: String) {
        _profiles.update { currentProfiles ->
            currentProfiles.map { profile ->
                if (profile.id == profileId &&
                    packageName in profile.apps &&
                    packageName !in profile.dockApps
                ) {
                    val updated = profile.copy(dockApps = profile.dockApps + packageName)
                    if (profile.isActive) {
                        _activeProfile.value = updated
                    }
                    updated
                } else {
                    profile
                }
            }
        }
    }

    /**
     * Removes an app from the dock shortcuts of the specified profile.
     *
     * @param profileId The profile to modify
     * @param packageName The package name of the app to unpin from the dock
     */
    fun removeFromDock(profileId: String, packageName: String) {
        _profiles.update { currentProfiles ->
            currentProfiles.map { profile ->
                if (profile.id == profileId) {
                    val updated = profile.copy(
                        dockApps = profile.dockApps.filter { it != packageName }
                    )
                    if (profile.isActive) {
                        _activeProfile.value = updated
                    }
                    updated
                } else {
                    profile
                }
            }
        }
    }
}
