package com.castor.app.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.castor.core.common.hub.HubServer
import com.castor.core.common.hub.NsdDiscovery
import com.castor.core.ui.theme.TerminalColors
import kotlinx.coroutines.flow.StateFlow

private val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, color = Color(0xFFCDD6F4))

@Composable
fun HubControlPanel(
    isRunning: StateFlow<Boolean>,
    connectedClients: StateFlow<Int>,
    ipAddress: String?,
    port: Int = HubServer.DEFAULT_PORT,
    onStartStop: (start: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val running by isRunning.collectAsState()
    val clients by connectedClients.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface.copy(alpha = 0.5f))
            .border(1.dp, if (running) TerminalColors.Success.copy(alpha = 0.3f) else TerminalColors.Subtext.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Hub, null, tint = TerminalColors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Hub Control Panel", style = MonoStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TerminalColors.Accent))
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (running) TerminalColors.Success.copy(alpha = 0.12f) else TerminalColors.Subtext.copy(alpha = 0.08f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(if (running) "ACTIVE" else "STOPPED", style = MonoStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (running) TerminalColors.Success else TerminalColors.Subtext))
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = TerminalColors.Subtext.copy(alpha = 0.15f))
        Spacer(Modifier.height(12.dp))

        // Status dot
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (running) TerminalColors.Success else TerminalColors.Error))
            Spacer(Modifier.width(8.dp))
            Text(if (running) "Server is running" else "Server is stopped", style = MonoStyle.copy(fontSize = 12.sp, color = if (running) TerminalColors.Success else TerminalColors.Error))
        }
        Spacer(Modifier.height(14.dp))

        // Connection info
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(TerminalColors.Background.copy(alpha = 0.6f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoRow(Icons.Default.Wifi, "IP Address", ipAddress ?: "Not connected", if (ipAddress != null && running) TerminalColors.Command else TerminalColors.Subtext)
            InfoRow(Icons.Default.Computer, "Port", port.toString(), if (running) TerminalColors.Command else TerminalColors.Subtext)
        }
        Spacer(Modifier.height(10.dp))

        // Clients
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(TerminalColors.Background.copy(alpha = 0.6f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.People, null, tint = TerminalColors.Timestamp, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("Connected Clients", style = MonoStyle.copy(fontSize = 11.sp, color = TerminalColors.Timestamp))
            }
            Text(if (running) clients.toString() else "--", style = MonoStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (clients > 0) TerminalColors.Info else TerminalColors.Subtext))
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = TerminalColors.Subtext.copy(alpha = 0.15f))
        Spacer(Modifier.height(12.dp))

        // URL display
        if (running && ipAddress != null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(TerminalColors.Accent.copy(alpha = 0.06f)).border(1.dp, TerminalColors.Accent.copy(alpha = 0.2f), RoundedCornerShape(6.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Open in browser:", style = MonoStyle.copy(fontSize = 10.sp, color = TerminalColors.Timestamp))
                Spacer(Modifier.height(6.dp))
                Text("http://$ipAddress:$port", style = MonoStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TerminalColors.Accent, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text("NSD Service: ${NsdDiscovery.SERVICE_TYPE}", style = MonoStyle.copy(fontSize = 9.sp, color = TerminalColors.Timestamp))
            }
            Spacer(Modifier.height(14.dp))
        }

        // Start/Stop button
        Button(onClick = { onStartStop(!running) }, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (running) TerminalColors.Error.copy(alpha = 0.15f) else TerminalColors.Success.copy(alpha = 0.15f), contentColor = if (running) TerminalColors.Error else TerminalColors.Success)) {
            Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (running) "Stop Hub Server" else "Start Hub Server", style = MonoStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (running) TerminalColors.Error else TerminalColors.Success))
        }
        Spacer(Modifier.height(8.dp))
        Text("All traffic stays on your local network. No data is sent to the internet.", style = MonoStyle.copy(fontSize = 9.sp, color = TerminalColors.Timestamp, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TerminalColors.Timestamp, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MonoStyle.copy(fontSize = 11.sp, color = TerminalColors.Timestamp))
        }
        Text(value, style = MonoStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = valueColor))
    }
}
