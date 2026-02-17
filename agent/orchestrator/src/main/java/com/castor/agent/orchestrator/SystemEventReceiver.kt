package com.castor.agent.orchestrator

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that listens for system-level events and emits them to the
 * [AgentEventBus] so that agents and the [ProactiveEngine] can react.
 *
 * **Lifecycle**: This receiver is registered dynamically in [AgentService.onCreate]
 * and unregistered in [AgentService.onDestroy]. It is NOT declared in the manifest
 * to give the service full control over when system events are monitored.
 *
 * Monitored events:
 * - Battery: low battery, power connected/disconnected
 * - Screen: screen on/off
 * - Bluetooth: ACL device connected/disconnected
 *
 * @param eventBus The event bus to emit system events to.
 * @param scope    A coroutine scope tied to the service lifecycle for launching
 *                 the suspend [AgentEventBus.emit] call.
 */
class SystemEventReceiver(
    private val eventBus: AgentEventBus,
    private val scope: CoroutineScope
) : BroadcastReceiver() {

    companion object {

        /**
         * Build the [IntentFilter] containing all actions this receiver handles.
         * Used by the service when calling [Context.registerReceiver].
         */
        fun buildIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                // Battery
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)

                // Screen
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)

                // Bluetooth
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // BroadcastReceiver callback
    // -------------------------------------------------------------------------------------

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return

        val eventType: SystemEventType? = when (action) {
            // Battery
            Intent.ACTION_BATTERY_LOW -> SystemEventType.BATTERY_LOW
            Intent.ACTION_POWER_CONNECTED -> SystemEventType.CHARGING_STARTED
            Intent.ACTION_POWER_DISCONNECTED -> SystemEventType.CHARGING_STOPPED

            // Screen
            Intent.ACTION_SCREEN_ON -> SystemEventType.SCREEN_ON
            Intent.ACTION_SCREEN_OFF -> SystemEventType.SCREEN_OFF

            // Bluetooth
            BluetoothDevice.ACTION_ACL_CONNECTED -> SystemEventType.BLUETOOTH_CONNECTED
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> SystemEventType.BLUETOOTH_DISCONNECTED

            else -> null
        }

        if (eventType != null) {
            scope.launch {
                eventBus.emit(AgentEvent.SystemEvent(type = eventType))
            }
        }
    }
}

/**
 * Helper extension to register a [SystemEventReceiver] respecting API-level requirements.
 *
 * On Android 13+ (TIRAMISU), exported receivers must specify
 * [Context.RECEIVER_NOT_EXPORTED] to avoid a security exception.
 */
fun Context.registerSystemEventReceiver(receiver: SystemEventReceiver) {
    val filter = SystemEventReceiver.buildIntentFilter()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        registerReceiver(receiver, filter)
    }
}
