package com.castor.app.quicksettings

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Quick Settings panel.
 *
 * Provides state and toggle functions for:
 * - WiFi
 * - Bluetooth
 * - Flashlight
 * - Auto-rotate
 * - Do Not Disturb
 * - Brightness
 * - Media volume
 *
 * Registers broadcast receivers to keep state in sync with system changes.
 */
@HiltViewModel
class QuickSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothAdapter = try {
        BluetoothAdapter.getDefaultAdapter()
    } catch (e: Exception) {
        null
    }
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val notificationManager = NotificationManagerCompat.from(context)

    // State flows
    private val _wifiEnabled = MutableStateFlow(false)
    val wifiEnabled: StateFlow<Boolean> = _wifiEnabled.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(false)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    private val _flashlightOn = MutableStateFlow(false)
    val flashlightOn: StateFlow<Boolean> = _flashlightOn.asStateFlow()

    private val _autoRotateEnabled = MutableStateFlow(false)
    val autoRotateEnabled: StateFlow<Boolean> = _autoRotateEnabled.asStateFlow()

    private val _brightnessLevel = MutableStateFlow(0.5f)
    val brightnessLevel: StateFlow<Float> = _brightnessLevel.asStateFlow()

    private val _mediaVolume = MutableStateFlow(0.5f)
    val mediaVolume: StateFlow<Float> = _mediaVolume.asStateFlow()

    private val _dndEnabled = MutableStateFlow(false)
    val dndEnabled: StateFlow<Boolean> = _dndEnabled.asStateFlow()

    private val _batteryPercent = MutableStateFlow(0)
    val batteryPercent: StateFlow<Int> = _batteryPercent.asStateFlow()

    private val _networkName = MutableStateFlow("Not connected")
    val networkName: StateFlow<String> = _networkName.asStateFlow()

    // Camera ID for flashlight
    private var cameraId: String? = null

    // Broadcast receivers
    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshWifiState()
        }
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshBluetoothState()
        }
    }

    init {
        // Get camera ID for flashlight
        try {
            cameraId = cameraManager?.cameraIdList?.firstOrNull()
        } catch (e: Exception) {
            // No camera available
        }

        // Register broadcast receivers
        registerReceivers()

        // Initial state refresh
        refreshAllStates()
    }

    private fun registerReceivers() {
        try {
            // WiFi state receiver
            val wifiFilter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
            context.registerReceiver(wifiStateReceiver, wifiFilter)

            // Bluetooth state receiver
            val btFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.registerReceiver(bluetoothStateReceiver, btFilter)
        } catch (e: Exception) {
            // Registration failed
        }
    }

    private fun unregisterReceivers() {
        try {
            context.unregisterReceiver(wifiStateReceiver)
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    override fun onCleared() {
        super.onCleared()
        unregisterReceivers()

        // Turn off flashlight if it's on
        if (_flashlightOn.value) {
            toggleFlashlight()
        }
    }

    private fun refreshAllStates() {
        viewModelScope.launch {
            refreshWifiState()
            refreshBluetoothState()
            refreshAutoRotateState()
            refreshBrightnessState()
            refreshVolumeState()
            refreshDndState()
            refreshBatteryState()
            refreshNetworkName()
        }
    }

    private fun refreshWifiState() {
        _wifiEnabled.value = wifiManager?.isWifiEnabled ?: false
        refreshNetworkName()
    }

    private fun refreshBluetoothState() {
        _bluetoothEnabled.value = bluetoothAdapter?.isEnabled ?: false
    }

    private fun refreshAutoRotateState() {
        try {
            val rotationEnabled = Settings.System.getInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            ) == 1
            _autoRotateEnabled.value = rotationEnabled
        } catch (e: Exception) {
            _autoRotateEnabled.value = false
        }
    }

    private fun refreshBrightnessState() {
        try {
            val brightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            // Normalize from 0-255 to 0f-1f
            _brightnessLevel.value = brightness / 255f
        } catch (e: Exception) {
            _brightnessLevel.value = 0.5f
        }
    }

    private fun refreshVolumeState() {
        try {
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7
            _mediaVolume.value = if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0.5f
        } catch (e: Exception) {
            _mediaVolume.value = 0.5f
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun refreshDndState() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val filter = notificationManager.currentInterruptionFilter
                _dndEnabled.value = filter != NotificationManagerCompat.INTERRUPTION_FILTER_ALL
            }
        } catch (e: Exception) {
            _dndEnabled.value = false
        }
    }

    private fun refreshBatteryState() {
        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            _batteryPercent.value = (level * 100 / scale.coerceAtLeast(1))
        } catch (e: Exception) {
            _batteryPercent.value = 0
        }
    }

    private fun refreshNetworkName() {
        try {
            if (_wifiEnabled.value) {
                val wifiInfo = wifiManager?.connectionInfo
                val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "Not connected"
                _networkName.value = if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                    ssid
                } else {
                    "Not connected"
                }
            } else {
                _networkName.value = "WiFi off"
            }
        } catch (e: Exception) {
            _networkName.value = "Not connected"
        }
    }

    // Toggle functions
    fun toggleWifi() {
        // On Android 10+, we can't toggle WiFi directly. Open settings panel instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(panelIntent)
            } catch (e: Exception) {
                // Fallback to WiFi settings
                try {
                    val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(settingsIntent)
                } catch (e2: Exception) {
                    // Can't open settings
                }
            }
        } else {
            // On Android 9 and below, we can toggle directly (deprecated but still works)
            @Suppress("DEPRECATION")
            wifiManager?.isWifiEnabled = !_wifiEnabled.value
        }
    }

    fun toggleBluetooth() {
        // Check if we have permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                // No permission, open Bluetooth settings
                try {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Can't open settings
                }
                return
            }
        }

        try {
            if (_bluetoothEnabled.value) {
                bluetoothAdapter?.disable()
            } else {
                bluetoothAdapter?.enable()
            }
        } catch (e: Exception) {
            // Toggle failed, open settings
            try {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e2: Exception) {
                // Can't open settings
            }
        }
    }

    fun toggleFlashlight() {
        try {
            val cameraIdToUse = cameraId ?: return
            val newState = !_flashlightOn.value
            cameraManager?.setTorchMode(cameraIdToUse, newState)
            _flashlightOn.value = newState
        } catch (e: Exception) {
            // Flashlight toggle failed
            _flashlightOn.value = false
        }
    }

    fun toggleAutoRotate() {
        try {
            val newState = if (_autoRotateEnabled.value) 0 else 1
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                newState
            )
            _autoRotateEnabled.value = newState == 1
        } catch (e: Exception) {
            // Can't change rotation setting, open settings
            try {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e2: Exception) {
                // Can't open settings
            }
        }
    }

    fun toggleDnd() {
        // Opening DND settings requires WRITE_SETTINGS permission
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            // Can't open settings
        }
    }

    fun setBrightness(level: Float) {
        try {
            // Clamp to 0-1 range
            val clampedLevel = level.coerceIn(0f, 1f)
            _brightnessLevel.value = clampedLevel

            // Convert to 0-255 range
            val brightnessValue = (clampedLevel * 255).toInt()

            // Check if we have WRITE_SETTINGS permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.System.canWrite(context)) {
                    // Request permission
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            }

            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightnessValue
            )
        } catch (e: Exception) {
            // Can't change brightness
        }
    }

    fun setVolume(level: Float) {
        try {
            val clampedLevel = level.coerceIn(0f, 1f)
            _mediaVolume.value = clampedLevel

            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            val volumeValue = (clampedLevel * maxVolume).toInt()

            audioManager?.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                volumeValue,
                0 // No flags (don't show UI)
            )
        } catch (e: Exception) {
            // Can't change volume
        }
    }
}
