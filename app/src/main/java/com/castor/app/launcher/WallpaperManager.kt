package com.castor.app.launcher

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for accessing and observing the system wallpaper.
 *
 * Provides the current wallpaper as a [Bitmap] via a [StateFlow], enabling
 * composable UIs to reactively display the wallpaper (e.g., with a blur effect
 * behind the terminal or as the home screen background). Automatically listens
 * for wallpaper change broadcasts and refreshes the bitmap.
 *
 * This is injected as a @Singleton so all consumers share the same wallpaper
 * bitmap instance, avoiding redundant decoding of potentially large images.
 *
 * Requires `android.permission.READ_EXTERNAL_STORAGE` on API < 33, or no
 * special permission on API 33+ for the live wallpaper drawable. The drawable
 * returned by WallpaperManager may be null on devices with restricted wallpaper
 * access (e.g., managed/enterprise devices).
 *
 * @param context Application context used for WallpaperManager and broadcast registration
 */
@Singleton
class WallpaperManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _wallpaperBitmap = MutableStateFlow<Bitmap?>(null)

    /**
     * Observable wallpaper bitmap. Emits null when wallpaper cannot be read
     * (permissions, managed device, etc.) and a [Bitmap] when available.
     * Updates automatically when the user changes wallpaper.
     */
    val wallpaperBitmap: StateFlow<Bitmap?> = _wallpaperBitmap.asStateFlow()

    private val wallpaperManager: WallpaperManager? = try {
        WallpaperManager.getInstance(context)
    } catch (_: Exception) {
        null
    }

    /**
     * BroadcastReceiver that listens for ACTION_WALLPAPER_CHANGED and triggers
     * a refresh of the wallpaper bitmap.
     */
    private val wallpaperChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                scope.launch { refreshWallpaper() }
            }
        }
    }

    init {
        // Register for wallpaper change broadcasts
        try {
            val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    wallpaperChangeReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(wallpaperChangeReceiver, filter)
            }
        } catch (_: Exception) {
            // Registration may fail on restricted contexts
        }

        // Load initial wallpaper
        scope.launch { refreshWallpaper() }
    }

    /**
     * Returns the current system wallpaper as a [Bitmap], or null if unavailable.
     *
     * Runs on the IO dispatcher since wallpaper decoding can be expensive for
     * high-resolution images. The bitmap is scaled down to a reasonable size
     * (max 1080px on the longest edge) to conserve memory while still looking
     * good as a blurred background.
     */
    fun getWallpaper(): Bitmap? {
        return try {
            val drawable = wallpaperManager?.drawable ?: return null
            val bitmap = drawable.toBitmapOrNull() ?: return null
            scaleBitmap(bitmap, maxDimension = 1080)
        } catch (_: SecurityException) {
            // READ_EXTERNAL_STORAGE not granted on older APIs
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Refreshes the wallpaper bitmap state. Called on init and whenever the
     * system broadcasts a wallpaper change event.
     */
    private suspend fun refreshWallpaper() {
        val bitmap = withContext(Dispatchers.IO) {
            getWallpaper()
        }
        _wallpaperBitmap.value = bitmap
    }

    /**
     * Scales a bitmap so its longest edge is at most [maxDimension] pixels,
     * preserving aspect ratio. Returns the original bitmap if already within bounds.
     */
    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / maxOf(width, height).toFloat()
        val newWidth = (width * scale).toInt().coerceAtLeast(1)
        val newHeight = (height * scale).toInt().coerceAtLeast(1)

        return try {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * Unregisters the wallpaper change receiver. Should be called when the
     * application is terminating to avoid leaked receivers.
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(wallpaperChangeReceiver)
        } catch (_: Exception) {
            // Receiver may not have been registered
        }
    }
}
