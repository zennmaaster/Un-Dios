package com.castor.app.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.castor.core.ui.theme.TerminalColors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom AppWidgetHost for the Un-Dios launcher.
 *
 * Extends Android's [AppWidgetHost] to provide widget hosting capabilities
 * on the home screen. Each AppWidgetHost must have a unique hostId within the
 * application; we use the constant [UNDIOS_WIDGET_HOST_ID].
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
// Widget Manager (Hilt singleton for widget persistence and lifecycle)
// =============================================================================

/**
 * Application-scoped manager for Android widget hosting.
 *
 * Handles the full widget lifecycle: allocation of widget IDs, persistence
 * of active widget IDs in DataStore, launching the widget picker and
 * configuration activities, and removal of widgets.
 *
 * The manager owns the [UnDiosWidgetHost] instance and provides it to the
 * composable UI layer. Widget IDs are stored as a comma-separated string
 * in the launcher DataStore so they survive app restarts.
 *
 * Usage:
 * ```kotlin
 * val widgetIds by widgetManager.widgetIds.collectAsState()
 * widgetManager.addWidget(widgetId)
 * widgetManager.removeWidget(widgetId)
 * ```
 */
@Singleton
class WidgetManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.launcherDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /** DataStore key for persisted widget IDs. */
        val WIDGET_IDS_KEY = stringPreferencesKey("widgets.active_ids")

        /** Delimiter for the comma-separated widget ID string. */
        private const val DELIMITER = ","
    }

    /** The widget host instance shared across the app. */
    val widgetHost: UnDiosWidgetHost = UnDiosWidgetHost(context)

    /** System AppWidgetManager instance. */
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    /** Currently active widget IDs (source of truth from DataStore). */
    private val _widgetIds = MutableStateFlow<List<Int>>(emptyList())
    val widgetIds: StateFlow<List<Int>> = _widgetIds.asStateFlow()

    init {
        scope.launch {
            loadWidgetIds()
        }

        // Continuously observe DataStore changes.
        scope.launch {
            dataStore.data.collect { prefs ->
                val raw = prefs[WIDGET_IDS_KEY] ?: ""
                _widgetIds.value = parseWidgetIds(raw)
            }
        }
    }

    // =========================================================================
    // Public actions
    // =========================================================================

    /**
     * Allocate a new widget ID from the host. This should be called before
     * launching the widget picker so we have an ID to bind the widget to.
     *
     * @return A new unique widget ID
     */
    fun allocateWidgetId(): Int {
        return widgetHost.allocateAppWidgetId()
    }

    /**
     * Create an intent to launch the system widget picker.
     *
     * The caller should start this intent with [Activity.startActivityForResult]
     * or via an ActivityResultLauncher. The picker will return the selected
     * widget's provider info bound to the allocated [widgetId].
     *
     * @param widgetId The pre-allocated widget ID to bind the selection to
     * @return Intent for the widget picker activity
     */
    fun createPickerIntent(widgetId: Int): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
    }

    /**
     * Create an intent to launch a widget's configuration activity.
     *
     * Some widgets require configuration (e.g., selecting which account,
     * list, or calendar to display). This intent launches that configuration
     * flow. Not all widgets have a configure activity -- check
     * [AppWidgetProviderInfo.configure] first.
     *
     * @param widgetId The widget ID to configure
     * @param providerInfo The provider info for the widget
     * @return Intent for the configuration activity, or null if none is needed
     */
    fun createConfigureIntent(widgetId: Int, providerInfo: AppWidgetProviderInfo): Intent? {
        val configureComponent = providerInfo.configure ?: return null
        return Intent().apply {
            component = configureComponent
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
    }

    /**
     * Add a widget ID to the persisted list after successful binding.
     *
     * Call this after the widget picker and/or configuration activity
     * return [Activity.RESULT_OK].
     *
     * @param widgetId The widget ID to persist
     */
    fun addWidget(widgetId: Int) {
        val current = _widgetIds.value.toMutableList()
        if (current.contains(widgetId)) return
        current.add(widgetId)
        persistWidgetIds(current)
    }

    /**
     * Remove a widget and deallocate its ID from the host.
     *
     * Also deletes the widget ID from the host so the system knows
     * we are no longer interested in updates for it.
     *
     * @param widgetId The widget ID to remove
     */
    fun removeWidget(widgetId: Int) {
        val current = _widgetIds.value.toMutableList()
        if (!current.remove(widgetId)) return
        try {
            widgetHost.deleteAppWidgetId(widgetId)
        } catch (_: Exception) {
            // Ignore -- widget may already be deleted
        }
        persistWidgetIds(current)
    }

    /**
     * Cancel a widget ID allocation (e.g., when the user cancels the picker).
     *
     * @param widgetId The allocated widget ID to release
     */
    fun cancelWidgetId(widgetId: Int) {
        try {
            widgetHost.deleteAppWidgetId(widgetId)
        } catch (_: Exception) {
            // Ignore
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Load persisted widget IDs from DataStore on startup.
     */
    private suspend fun loadWidgetIds() {
        val prefs = dataStore.data.first()
        val raw = prefs[WIDGET_IDS_KEY] ?: ""
        _widgetIds.value = parseWidgetIds(raw)
    }

    /**
     * Persist the widget IDs list to DataStore.
     */
    private fun persistWidgetIds(ids: List<Int>) {
        _widgetIds.value = ids
        scope.launch {
            dataStore.edit { prefs ->
                prefs[WIDGET_IDS_KEY] = ids.joinToString(DELIMITER)
            }
        }
    }

    /**
     * Parse a comma-separated string of widget IDs into a list of Ints.
     */
    private fun parseWidgetIds(raw: String): List<Int> {
        if (raw.isBlank()) return emptyList()
        return raw.split(DELIMITER).mapNotNull { it.trim().toIntOrNull() }
    }
}

// =============================================================================
// Composable widget area
// =============================================================================

/**
 * A composable area for displaying hosted Android AppWidgets on the home screen.
 *
 * When widgets are configured, they are displayed in a horizontal scrollable row.
 * When no widgets are present, shows a terminal-styled placeholder with an
 * `$ apt install --widget` add button. Each widget is wrapped in a terminal-style
 * bordered container and supports long-press to remove.
 *
 * The widget picker flow uses ActivityResultLauncher to handle the system widget
 * picker and optional widget configuration activity. The flow is:
 * 1. User taps "add widget" -> allocate widget ID -> launch picker
 * 2. Picker returns -> check if widget needs configuration -> launch configure
 * 3. Configure returns (or skipped) -> add widget ID to persistence
 *
 * @param widgetManager The [WidgetManager] singleton managing widget lifecycle
 * @param modifier Modifier for the root composable
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetArea(
    widgetManager: WidgetManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val widgetHost = widgetManager.widgetHost
    val appWidgetManager = widgetManager.appWidgetManager

    // Track the pending widget ID during the picker/configure flow
    var pendingWidgetId by remember { mutableIntStateOf(-1) }

    // Track which widget the user wants to remove (for confirmation dialog)
    var widgetToRemove by remember { mutableStateOf<Int?>(null) }

    // Activity result launcher for widget configuration
    val configureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingWidgetId != -1) {
            widgetManager.addWidget(pendingWidgetId)
        } else if (pendingWidgetId != -1) {
            // User cancelled configuration -- release the widget ID
            widgetManager.cancelWidgetId(pendingWidgetId)
        }
        pendingWidgetId = -1
    }

    // Activity result launcher for widget picker
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val widgetId = result.data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, -1
            ) ?: pendingWidgetId

            if (widgetId != -1) {
                // Check if the selected widget needs configuration
                val providerInfo = appWidgetManager.getAppWidgetInfo(widgetId)
                val configureIntent = providerInfo?.let {
                    widgetManager.createConfigureIntent(widgetId, it)
                }

                if (configureIntent != null) {
                    // Widget needs configuration -- launch configure activity
                    pendingWidgetId = widgetId
                    configureLauncher.launch(configureIntent)
                } else {
                    // No configuration needed -- add directly
                    widgetManager.addWidget(widgetId)
                    pendingWidgetId = -1
                }
            }
        } else {
            // User cancelled the picker -- release the widget ID
            if (pendingWidgetId != -1) {
                widgetManager.cancelWidgetId(pendingWidgetId)
                pendingWidgetId = -1
            }
        }
    }

    // Callback to initiate the add-widget flow
    val onAddWidget: () -> Unit = {
        val newWidgetId = widgetManager.allocateWidgetId()
        pendingWidgetId = newWidgetId
        val pickerIntent = widgetManager.createPickerIntent(newWidgetId)
        pickerLauncher.launch(pickerIntent)
    }

    // Collect the current widget IDs from the manager
    val widgetIds = widgetManager.widgetIds

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

    // Removal confirmation dialog
    if (widgetToRemove != null) {
        AlertDialog(
            onDismissRequest = { widgetToRemove = null },
            containerColor = TerminalColors.Surface,
            title = {
                Text(
                    text = "$ widget --remove",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Warning
                    )
                )
            },
            text = {
                Text(
                    text = "Remove this widget from the home screen?",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Command
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    widgetToRemove?.let { widgetManager.removeWidget(it) }
                    widgetToRemove = null
                }) {
                    Text(
                        text = "$ rm -f",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TerminalColors.Error
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { widgetToRemove = null }) {
                    Text(
                        text = "cancel",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                }
            }
        )
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

        val currentIds by widgetIds.collectAsState()

        if (currentIds.isEmpty()) {
            // Empty state: no widgets configured
            WidgetEmptyState(onAddWidget = onAddWidget)
        } else {
            // Horizontal scroll of hosted widgets
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(currentIds) { widgetId ->
                    HostedWidget(
                        widgetHost = widgetHost,
                        appWidgetManager = appWidgetManager,
                        widgetId = widgetId,
                        onLongPress = { widgetToRemove = widgetId }
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
 * Long-press triggers widget removal.
 *
 * @param widgetHost The widget host managing this widget
 * @param appWidgetManager System AppWidgetManager for provider info
 * @param widgetId The allocated ID of the widget to display
 * @param onLongPress Called when the user long-presses the widget (for removal)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostedWidget(
    widgetHost: UnDiosWidgetHost,
    appWidgetManager: AppWidgetManager,
    widgetId: Int,
    onLongPress: () -> Unit
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
            .combinedClickable(
                onClick = { /* Widget handles its own clicks */ },
                onLongClick = onLongPress
            )
    ) {
        if (providerInfo != null) {
            // Widget provider label in the top-left corner
            Text(
                text = providerInfo.loadLabel(context.packageManager) ?: "widget",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = TerminalColors.Timestamp
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            )

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
                    .padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 4.dp)
            )

            // Remove hint icon in top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Surface.copy(alpha = 0.6f))
                    .clickable(onClick = onLongPress),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove widget",
                    tint = TerminalColors.Timestamp,
                    modifier = Modifier.size(10.dp)
                )
            }
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
 * Displays a terminal-style prompt: `$ apt install --widget`
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
                text = "$ apt install --widget",
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
