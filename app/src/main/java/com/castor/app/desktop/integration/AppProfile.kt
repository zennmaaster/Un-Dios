package com.castor.app.desktop.integration

/**
 * Represents a work/personal/custom app profile for the desktop environment.
 *
 * Profiles allow users to organize their apps into distinct workspaces,
 * each with its own set of visible apps, dock shortcuts, and optional
 * wallpaper — similar to Android Work Profiles or GNOME Activities.
 *
 * When a profile is active, only its apps appear in the dock and app drawer
 * filter, providing a focused workspace free from distractions.
 *
 * @param id Unique identifier for the profile (e.g., "work", "personal")
 * @param name Human-readable display name (e.g., "Work", "Personal", "Gaming")
 * @param icon Material icon name reference used in the profile switcher UI
 * @param apps Package names of apps visible in this profile
 * @param wallpaper Optional wallpaper resource name for this profile
 * @param dockApps Package names of apps pinned to the dock when this profile is active
 * @param isActive Whether this profile is currently the active workspace
 */
data class AppProfile(
    val id: String,
    val name: String,
    val icon: String,
    val apps: List<String>,
    val wallpaper: String? = null,
    val dockApps: List<String>,
    val isActive: Boolean = false
)

/**
 * Default work profile pre-loaded with common productivity applications.
 *
 * Includes Microsoft 365 suite, Google Workspace, Slack, and Chrome.
 * Users can customize this profile by adding or removing apps.
 */
val DEFAULT_WORK_PROFILE = AppProfile(
    id = "work",
    name = "Work",
    icon = "work",
    apps = listOf(
        "com.microsoft.teams",
        "com.microsoft.office.outlook",
        "com.microsoft.office.word",
        "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint",
        "com.google.android.apps.docs",
        "com.slack",
        "com.google.android.calendar",
        "com.android.chrome",
        "com.google.android.gm"
    ),
    dockApps = listOf(
        "com.microsoft.teams",
        "com.microsoft.office.outlook",
        "com.android.chrome"
    )
)

/**
 * Default personal profile pre-loaded with common entertainment and social apps.
 *
 * Includes messaging, social media, music, video, and reading apps.
 * Users can customize this profile by adding or removing apps.
 */
val DEFAULT_PERSONAL_PROFILE = AppProfile(
    id = "personal",
    name = "Personal",
    icon = "person",
    apps = listOf(
        "com.whatsapp",
        "com.instagram.android",
        "com.twitter.android",
        "com.spotify.music",
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "com.amazon.kindle",
        "com.google.android.apps.photos",
        "com.android.chrome"
    ),
    dockApps = listOf(
        "com.whatsapp",
        "com.spotify.music",
        "com.google.android.youtube"
    )
)
