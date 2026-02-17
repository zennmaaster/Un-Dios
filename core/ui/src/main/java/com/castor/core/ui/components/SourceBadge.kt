package com.castor.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.castor.core.common.model.MessageSource
import com.castor.core.ui.theme.TeamsBlue
import com.castor.core.ui.theme.WhatsAppGreen

@Composable
fun SourceBadge(source: MessageSource, modifier: Modifier = Modifier) {
    val (label, color) = when (source) {
        MessageSource.WHATSAPP -> "WhatsApp" to WhatsAppGreen
        MessageSource.TEAMS -> "Teams" to TeamsBlue
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
