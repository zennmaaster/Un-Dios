package com.castor.app.hub

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.common.hub.HubServer
import com.castor.core.ui.theme.TerminalColors
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Screen that hosts the [HubControlPanel] and manages the [HubService] lifecycle.
 *
 * Accessible via the "hub" navigation route. Provides start/stop controls
 * for the phone-as-hub server and displays connection information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf(getLocalIpAddress(context)) }

    // Refresh IP periodically
    LaunchedEffect(Unit) {
        while (true) {
            ipAddress = getLocalIpAddress(context)
            kotlinx.coroutines.delay(5000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "hub-server",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TerminalColors.Accent
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TerminalColors.Command
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TerminalColors.Background
                )
            )
        },
        containerColor = TerminalColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            HubControlPanel(
                isRunning = kotlinx.coroutines.flow.MutableStateFlow(isRunning),
                connectedClients = kotlinx.coroutines.flow.MutableStateFlow(0),
                ipAddress = ipAddress,
                port = HubServer.DEFAULT_PORT,
                onStartStop = { start ->
                    isRunning = start
                    if (start) {
                        context.startForegroundService(
                            Intent(context, HubService::class.java)
                        )
                    } else {
                        context.stopService(
                            Intent(context, HubService::class.java)
                        )
                    }
                }
            )
        }
    }
}

@Suppress("deprecation")
private fun getLocalIpAddress(context: Context): String? {
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
        if (ipInt != 0) {
            val ip = String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff
            )
            if (ip != "0.0.0.0") return ip
        }
    } catch (_: Exception) {}
    try {
        for (ni in NetworkInterface.getNetworkInterfaces().asSequence()) {
            if (ni.isLoopback || !ni.isUp) continue
            for (addr in ni.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
            }
        }
    } catch (_: Exception) {}
    return null
}
