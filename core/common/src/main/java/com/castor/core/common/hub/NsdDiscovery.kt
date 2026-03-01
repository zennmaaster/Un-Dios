package com.castor.core.common.hub

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Zero-configuration service discovery using Android's built-in NSD (Network Service Discovery).
 *
 * Registers the Un-Dios hub as a local network service so that other devices on the
 * same WiFi can discover it automatically without knowing the phone's IP address.
 *
 * The service is advertised as `_undios._tcp` with the name "Un-Dios Hub". Clients
 * (e.g. a laptop running an NSD browser or a custom discovery tool) can scan for
 * this service type and connect to the hub server's IP and port.
 *
 * Usage:
 * ```
 * val discovery = NsdDiscovery(context)
 * discovery.register(port = 8484)
 * // ... later ...
 * discovery.unregister()
 * ```
 *
 * @param context Android context used to obtain the [NsdManager] system service.
 */
class NsdDiscovery(context: Context) {

    companion object {
        private const val TAG = "NsdDiscovery"

        /** NSD service type for Un-Dios hub discovery. */
        const val SERVICE_TYPE = "_undios._tcp."

        /** Human-readable service name broadcast on the network. */
        const val SERVICE_NAME = "Un-Dios Hub"
    }

    // ---------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------

    private val nsdManager: NsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null

    /** Whether the service is currently registered and advertising. */
    @Volatile
    var isRegistered: Boolean = false
        private set

    /** The resolved service name (may differ from [SERVICE_NAME] if a conflict occurred). */
    @Volatile
    var resolvedName: String? = null
        private set

    // ---------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------

    /**
     * Register the Un-Dios hub service on the local network.
     *
     * Android's NSD framework handles mDNS announcements automatically. If a
     * service with the same name already exists, the system appends a numeric
     * suffix (e.g. "Un-Dios Hub (2)").
     *
     * This method is idempotent -- calling it while already registered will
     * unregister first, then re-register with the new port.
     *
     * @param port The TCP port that the hub server is listening on.
     */
    fun register(port: Int) {
        // Unregister any existing registration first
        if (isRegistered) {
            unregister()
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {

            override fun onServiceRegistered(info: NsdServiceInfo) {
                // The system may have changed the service name to resolve a conflict
                resolvedName = info.serviceName
                isRegistered = true
                Log.i(TAG, "Service registered: ${info.serviceName} on port $port")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                isRegistered = false
                resolvedName = null
                Log.e(TAG, "Registration failed: errorCode=$errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                isRegistered = false
                resolvedName = null
                Log.i(TAG, "Service unregistered: ${info.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: errorCode=$errorCode")
            }
        }

        registrationListener = listener

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NSD service", e)
            isRegistered = false
            registrationListener = null
        }
    }

    /**
     * Unregister the service from the local network.
     *
     * After this call, the service will no longer be discoverable by other devices.
     * This method is idempotent -- calling it when not registered has no effect.
     */
    fun unregister() {
        val listener = registrationListener ?: return
        registrationListener = null

        try {
            nsdManager.unregisterService(listener)
        } catch (e: IllegalArgumentException) {
            // Listener was not registered -- safe to ignore
            Log.w(TAG, "Unregister called but listener was not registered", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering NSD service", e)
        }

        isRegistered = false
        resolvedName = null
    }
}
