package com.castor.app.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.castor.core.ui.theme.TerminalColors

/**
 * Custom AppWidgetHost for the Un-Dios launcher.
 *
 * Extends Android's [AppWidgetHost] to provide widget hosting capabilities
 * on the home screen. Each AppWidgetHost must have a unique hostId within the
 * application; we use the constant [UNDIOS_WIDGET_HOST_ID].
 *
 * In the current implementation this is a minimal wrapper. Future phases may
 * add custom host views with the terminal-style frame, drag-to-reposition,
 * and resize handles.
 *
 * @param context Application context
 * @param hostId Unique identifier for this widget host instance
 */
class UnDiosWidgetHost(
    context: Context,
    hostId: Int = UNDIOS_WIDGET_HOST_ID
) : AppWidgetHost(context, hostId) {

    companion object {
        /** Unique host ID for the Un-Dios launcher widget host. */
        const val UNDIOS_WIDGET_HOST_ID = 1024

        /**
         * Request code used when launching the widget picker via
         * AppWidgetManager.ACTION_APPWIDGET_PICK.
         */
        const val REQUEST_PICK_WIDGET = 1025

        /**
         * Request code used when a widget requires configuration
         * (e.g., the user needs to select which calendar to display).
         */
        const val REQUEST_CONFIGURE_WIDGET = 1026
    }

    /**
     * Creates a custom host view for the widget. Override point for future
     * terminal-framed widget views.
     */
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return super.onCreateView(context, appWidgetId, appWidget)
    }
}

// =============================================================================
// Composable widget area
// =============================================================================

/**
 * A composable area for displaying hosted Android AppWidgets on the home screen.
 *
 * When widgets are configured, they are displayed in a horizontal scrollable row.
 * When no widgets are present, shows a terminal-styled placeholder with an "Add widget"
 * action. Each widget is wrapped in a terminal-style bordered container.
 *
 * @param widgetHost The [UnDiosWidgetHost] instance managing widget lifecycle
 * @param widgetIds List of currently hosted widget IDs
 * @param onAddWidget Called when the user taps the "add widget" button
 * @param onRemoveWidget Called when a widget should be removed
 * @param modifier Modifier for the root composable
 */
@Composable
fun WidgetArea(
    widgetHost: UnDiosWidgetHost,
    widgetIds: List<Int>,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }

    // Start listening for widget updates when this composable is active
    DisposableEffect(widgetHost) {
        try {
            widgetHost.startListening()
        } catch (_: Exception) {
            // Widget host may already be listening
        }
        onDispose {
            try {
                widgetHost.stopListening()
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(
                text = "# ",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )
            Text(
                text = "widgets",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Timestamp
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(TerminalColors.Surface)
            )
        }

        if (widgetIds.isEmpty()) {
            // Empty state: no widgets configured
            WidgetEmptyState(onAddWidget = onAddWidget)
        } else {
            // Horizontal scroll of hosted widgets
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(widgetIds) { widgetId ->
                    HostedWidget(
                        widgetHost = widgetHost,
                        appWidgetManager = appWidgetManager,
                        widgetId = widgetId
                    )
                }

                // "Add widget" button at the end
                item {
                    AddWidgetButton(onClick = onAddWidget)
                }
            }
        }
    }
}

/**
 * Renders a single hosted Android AppWidget inside a terminal-styled container.
 * Uses [AndroidView] to embed the native AppWidgetHostView in the Compose hierarchy.
 *
 * @param widgetHost The widget host managing this widget
 * @param appWidgetManager System AppWidgetManager for provider info
 * @param widgetId The allocated ID of the widget to display
 */
@Composable
private fun HostedWidget(
    widgetHost: UnDiosWidgetHost,
    appWidgetManager: AppWidgetManager,
    widgetId: Int
) {
    val context = LocalContext.current
    val providerInfo = remember(widgetId) {
        appWidgetManager.getAppWidgetInfo(widgetId)
    }

    Box(
        modifier = Modifier
            .size(width = 200.dp, height = 160.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = TerminalColors.Surface,
                shape = RoundedCornerShape(8.dp)
            )
            .background(TerminalColors.Background.copy(alpha = 0.7f))
    ) {
        if (providerInfo != null) {
            AndroidView(
                factory = { ctx ->
                    try {
                        widgetHost.createView(ctx, widgetId, providerInfo).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                    } catch (_: Exception) {
                        // Fallback: empty FrameLayout if widget creation fails
                        FrameLayout(ctx)
                    }
                },
                modifier = Modifier
                    .matchParentSize()
                    .padding(4.dp)
            )
        } else {
            // Widget provider not found (widget may have been uninstalled)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.matchParentSize()
            ) {
                Text(
                    text = "widget unavailable\n(provider removed)",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Error,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

/**
 * Empty state placeholder shown when no widgets have been added.
 * Displays a terminal-style prompt encouraging the user to add a widget.
 */
@Composable
private fun WidgetEmptyState(onAddWidget: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = TerminalColors.Selection,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onAddWidget)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = null,
                tint = TerminalColors.Subtext,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$ widget --add",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Prompt
                )
            )
            Text(
                text = "# tap to add an Android widget",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Subtext
                )
            )
        }
    }
}

/**
 * Small "add widget" button displayed at the end of the widget row.
 */
@Composable
private fun AddWidgetButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 80.dp, height = 160.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = TerminalColors.Selection,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add widget",
                tint = TerminalColors.Accent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "+add",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Accent
                )
            )
        }
    }
}
