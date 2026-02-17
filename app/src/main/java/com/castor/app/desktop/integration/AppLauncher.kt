package com.castor.app.desktop.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles launching apps and deep links from the desktop environment.
 *
 * Provides utility functions for common app operations in the Un-Dios
 * desktop, including launching apps by package name, opening deep links
 * to specific app screens, and performing app management actions (info,
 * uninstall).
 *
 * Deep link URI schemes used:
 * - Outlook: `ms-outlook://compose?to=...&subject=...`
 * - Teams: `msteams://...`
 * - Chrome: `googlechrome://navigate?url=...` (fallback to ACTION_VIEW)
 * - Spotify: `spotify:playlist:...`
 * - YouTube: `vnd.youtube:...`
 * - WhatsApp: `https://wa.me/...`
 *
 * All launch operations are wrapped in try-catch blocks and return a
 * Boolean indicating success. This prevents crashes when apps are not
 * installed or intents cannot be resolved.
 *
 * This is a Hilt [Singleton] scoped to the application lifecycle.
 *
 * @param context Application context for starting activities and querying packages
 */
@Singleton
class AppLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Launches an app by its package name.
     *
     * Resolves the package name to a launch intent via PackageManager
     * and starts the activity. Returns false if the app is not installed
     * or the intent cannot be resolved.
     *
     * @param packageName The package name of the app to launch
     * @return true if the app was launched successfully, false otherwise
     */
    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launches a deep link URI.
     *
     * Parses the given URI string and opens it via ACTION_VIEW.
     * This is the general-purpose deep link handler — specific app methods
     * below construct the appropriate URIs for their respective apps.
     *
     * @param uri The deep link URI string to open
     * @return true if the deep link was handled, false otherwise
     */
    fun launchDeepLink(uri: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens Microsoft Outlook to compose a new email.
     *
     * Uses the `ms-outlook://compose` URI scheme. Falls back to a
     * standard mailto: intent if Outlook is not installed.
     *
     * @param to Optional recipient email address
     * @param subject Optional email subject line
     * @return true if Outlook (or a mail app) was opened, false otherwise
     */
    fun openOutlookCompose(to: String? = null, subject: String? = null): Boolean {
        // Try Outlook-specific deep link first
        val uriBuilder = StringBuilder("ms-outlook://compose")
        val params = mutableListOf<String>()
        to?.let { params.add("to=$it") }
        subject?.let { params.add("subject=$it") }
        if (params.isNotEmpty()) {
            uriBuilder.append("?${params.joinToString("&")}")
        }

        if (launchDeepLink(uriBuilder.toString())) return true

        // Fallback to standard mailto:
        return try {
            val mailtoUri = buildString {
                append("mailto:")
                to?.let { append(it) }
                subject?.let { append("?subject=$it") }
            }
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailtoUri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens Microsoft Teams, optionally to a specific chat.
     *
     * Uses the `msteams://` URI scheme. If no user ID is provided,
     * opens Teams to the main screen.
     *
     * @param userId Optional Teams user ID to open a direct chat with
     * @return true if Teams was opened, false otherwise
     */
    fun openTeamsChat(userId: String? = null): Boolean {
        val uri = if (userId != null) {
            "msteams://people/chat?users=$userId"
        } else {
            "msteams://"
        }
        return launchDeepLink(uri) || launchApp("com.microsoft.teams")
    }

    /**
     * Opens Microsoft Word, optionally with a specific document.
     *
     * If a document URI is provided, opens it directly in Word.
     * Otherwise, opens Word to its home screen.
     *
     * @param uri Optional document URI to open
     * @return true if Word was opened, false otherwise
     */
    fun openWordDocument(uri: Uri? = null): Boolean {
        if (uri != null) {
            return try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/msword")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.microsoft.office.word")
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                // Fallback without specific package
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/msword")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(fallbackIntent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        return launchApp("com.microsoft.office.word")
    }

    /**
     * Opens Microsoft Excel, optionally with a specific spreadsheet.
     *
     * If a spreadsheet URI is provided, opens it directly in Excel.
     * Otherwise, opens Excel to its home screen.
     *
     * @param uri Optional spreadsheet URI to open
     * @return true if Excel was opened, false otherwise
     */
    fun openExcelSpreadsheet(uri: Uri? = null): Boolean {
        if (uri != null) {
            return try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.ms-excel")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.microsoft.office.excel")
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.ms-excel")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(fallbackIntent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        return launchApp("com.microsoft.office.excel")
    }

    /**
     * Opens Google Chrome to a specific URL.
     *
     * Attempts the Chrome-specific `googlechrome://navigate?url=` scheme
     * first, then falls back to ACTION_VIEW with the Chrome package, and
     * finally falls back to a generic browser intent.
     *
     * @param url The URL to open in Chrome
     * @return true if the URL was opened, false otherwise
     */
    fun openChromeUrl(url: String): Boolean {
        // Try Chrome-specific scheme
        if (launchDeepLink("googlechrome://navigate?url=$url")) return true

        // Fallback: ACTION_VIEW with Chrome package
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.android.chrome")
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            // Final fallback: generic browser
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Opens a Spotify playlist by its ID.
     *
     * Uses the `spotify:playlist:` URI scheme. Falls back to the Spotify
     * web URL if the app is not installed.
     *
     * @param playlistId The Spotify playlist ID
     * @return true if Spotify was opened, false otherwise
     */
    fun openSpotifyPlaylist(playlistId: String): Boolean {
        if (launchDeepLink("spotify:playlist:$playlistId")) return true
        return launchDeepLink("https://open.spotify.com/playlist/$playlistId")
    }

    /**
     * Opens a YouTube video by its ID.
     *
     * Uses the `vnd.youtube:` URI scheme. Falls back to the YouTube
     * web URL if the app is not installed.
     *
     * @param videoId The YouTube video ID
     * @return true if YouTube was opened, false otherwise
     */
    fun openYouTubeVideo(videoId: String): Boolean {
        if (launchDeepLink("vnd.youtube:$videoId")) return true
        return launchDeepLink("https://www.youtube.com/watch?v=$videoId")
    }

    /**
     * Opens a WhatsApp chat with the specified phone number.
     *
     * Uses the WhatsApp Click-to-Chat URL (`https://wa.me/`). The phone
     * number should include the country code without the + prefix.
     *
     * @param phoneNumber The phone number (with country code, no + prefix)
     * @return true if WhatsApp was opened, false otherwise
     */
    fun openWhatsAppChat(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
        return launchDeepLink("https://wa.me/$cleanNumber")
    }

    /**
     * Opens the system App Info settings page for the given package.
     *
     * @param packageName The package name of the app
     */
    fun openAppInfo(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Settings could not be opened
        }
    }

    /**
     * Requests uninstallation of the given app via the system uninstall dialog.
     *
     * Only works for user-installed apps; system apps will show an error.
     *
     * @param packageName The package name of the app to uninstall
     */
    fun uninstallApp(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Uninstall could not be initiated
        }
    }

    /**
     * Checks whether the specified app is installed on the device.
     *
     * @param packageName The package name to check
     * @return true if the app is installed, false otherwise
     */
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Gets the launch intent for the specified app, or null if unavailable.
     *
     * @param packageName The package name to resolve
     * @return The launch intent, or null if the app is not installed or has no launcher activity
     */
    fun getLaunchIntent(packageName: String): Intent? {
        return context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
