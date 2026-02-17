package com.castor.feature.notifications.center

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.ui.theme.TerminalColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =====================================================================================
// NotificationCenterScreen -- $ journalctl --user
// =====================================================================================

/**
 * Full notification management screen styled as `$ journalctl --user` output.
 *
 * Features:
 * - Terminal-aesthetic header with unread badge count
 * - Category filter tabs rendered as command-line flags (`--filter=social`)
 * - Pinned notifications section at the top (always visible)
 * - Time-grouped sections: today, yesterday, this-week, older
 * - Swipeable notification cards with snooze (right) and dismiss (left)
 * - Priority indicators: high priority = red left border, normal = no border, low = dimmed
 * - Tap to expand full content, long-press for action sheet
 * - Bulk action bar for multi-select operations
 * - Empty state mimicking journalctl output with no entries
 *
 * @param onBack Navigation callback to return to the previous screen.
 * @param viewModel Hilt-injected [NotificationCenterViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    viewModel: NotificationCenterViewModel = hiltViewModel()
) {
    val filteredNotifications by viewModel.filteredNotifications.collectAsState()
    val pinnedNotifications by viewModel.pinnedNotifications.collectAsState()
    val groupedNotifications by viewModel.groupedNotifications.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedKeys by viewModel.selectedKeys.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val notificationCount by viewModel.notificationCount.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val expandedId by viewModel.expandedNotificationId.collectAsState()

    // Bottom sheet state for long-press actions
    var actionSheetNotification by remember { mutableStateOf<NotificationEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // Snooze picker state
    var snoozePickerNotification by remember { mutableStateOf<NotificationEntry?>(null) }
    var showBulkSnoozePicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ==================================================================
            // Top bar: # notification-center
            // ==================================================================
            NotificationCenterTopBar(
                notificationCount = notificationCount,
                unreadCount = unreadCount,
                onBack = onBack,
                onClearAll = { viewModel.clearAll() }
            )

            // ==================================================================
            // Filter tabs: --filter=all | --filter=social | ...
            // ==================================================================
            FilterTabRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            HorizontalDivider(
                color = TerminalColors.Surface,
                thickness = 1.dp
            )

            // ==================================================================
            // Notification list or empty state
            // ==================================================================
            if (filteredNotifications.isEmpty()) {
                EmptyState(selectedFilter = selectedFilter)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = if (isSelectionMode) 80.dp else 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // ---- Pinned section ----
                    if (pinnedNotifications.isNotEmpty()) {
                        item(key = "pinned_header") {
                            SectionHeader(
                                label = "-- pinned --",
                                color = TerminalColors.Accent,
                                count = pinnedNotifications.size
                            )
                        }

                        items(
                            items = pinnedNotifications,
                            key = { "pinned_${it.id}" }
                        ) { notification ->
                            SwipeableNotificationCard(
                                notification = notification,
                                isSelected = notification.id in selectedKeys,
                                isSelectionMode = isSelectionMode,
                                isExpanded = expandedId == notification.id,
                                appMetadata = viewModel.getAppMetadata(notification.packageName),
                                onDismiss = { viewModel.dismissNotification(notification.id) },
                                onSnooze = { snoozePickerNotification = notification },
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(notification.id)
                                    } else {
                                        viewModel.markAsRead(notification.id)
                                        viewModel.toggleExpanded(notification.id)
                                    }
                                },
                                onLongPress = {
                                    if (!isSelectionMode) {
                                        actionSheetNotification = notification
                                    } else {
                                        viewModel.toggleSelection(notification.id)
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }

                        item(key = "pinned_divider") {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // ---- Time-grouped sections ----
                    groupedNotifications.forEach { (timeGroup, notifications) ->
                        item(key = "header_${timeGroup.name}") {
                            SectionHeader(
                                label = timeGroup.header,
                                color = TerminalColors.Timestamp,
                                count = notifications.size
                            )
                        }

                        items(
                            items = notifications,
                            key = { it.id }
                        ) { notification ->
                            SwipeableNotificationCard(
                                notification = notification,
                                isSelected = notification.id in selectedKeys,
                                isSelectionMode = isSelectionMode,
                                isExpanded = expandedId == notification.id,
                                appMetadata = viewModel.getAppMetadata(notification.packageName),
                                onDismiss = { viewModel.dismissNotification(notification.id) },
                                onSnooze = { snoozePickerNotification = notification },
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(notification.id)
                                    } else {
                                        viewModel.markAsRead(notification.id)
                                        viewModel.toggleExpanded(notification.id)
                                    }
                                },
                                onLongPress = {
                                    if (!isSelectionMode) {
                                        actionSheetNotification = notification
                                    } else {
                                        viewModel.toggleSelection(notification.id)
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }

        // ==================================================================
        // Bulk actions bar (bottom)
        // ==================================================================
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BulkActionsBar(
                selectedCount = selectedKeys.size,
                onDismissSelected = { viewModel.dismissSelected() },
                onMarkRead = { viewModel.markSelectedAsRead() },
                onSnooze = { showBulkSnoozePicker = true },
                onSelectAll = { viewModel.selectAll() },
                onClearSelection = { viewModel.clearSelection() },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }
    }

    // ==================================================================
    // Long-press action sheet
    // ==================================================================
    actionSheetNotification?.let { notif ->
        val context = LocalContext.current
        ModalBottomSheet(
            onDismissRequest = { actionSheetNotification = null },
            sheetState = sheetState,
            containerColor = TerminalColors.Surface,
            contentColor = TerminalColors.Command
        ) {
            ActionSheetContent(
                notification = notif,
                onSnooze = {
                    actionSheetNotification = null
                    snoozePickerNotification = notif
                },
                onPin = {
                    viewModel.pinNotification(notif.id)
                    coroutineScope.launch {
                        sheetState.hide()
                        actionSheetNotification = null
                    }
                },
                onSetPriority = { priority ->
                    viewModel.setPriority(notif.id, priority)
                    coroutineScope.launch {
                        sheetState.hide()
                        actionSheetNotification = null
                    }
                },
                onMuteApp = {
                    viewModel.muteApp(notif.packageName)
                    coroutineScope.launch {
                        sheetState.hide()
                        actionSheetNotification = null
                    }
                },
                onOpen = {
                    val launchIntent = context.packageManager
                        .getLaunchIntentForPackage(notif.packageName)
                    launchIntent?.let { context.startActivity(it) }
                    coroutineScope.launch {
                        sheetState.hide()
                        actionSheetNotification = null
                    }
                },
                onSelect = {
                    viewModel.startSelection(notif.id)
                    coroutineScope.launch {
                        sheetState.hide()
                        actionSheetNotification = null
                    }
                },
                onDismiss = {
                    coroutineScope.launch {
                        sheetState.hide()
                        actionSheetNotification = null
                    }
                }
            )
        }
    }

    // ==================================================================
    // Snooze duration picker (single notification)
    // ==================================================================
    snoozePickerNotification?.let { notif ->
        SnoozeDurationPicker(
            onDurationSelected = { duration ->
                viewModel.snoozeNotification(notif.id, duration)
                snoozePickerNotification = null
            },
            onDismiss = { snoozePickerNotification = null }
        )
    }

    // ==================================================================
    // Snooze duration picker (bulk)
    // ==================================================================
    if (showBulkSnoozePicker) {
        SnoozeDurationPicker(
            onDurationSelected = { duration ->
                viewModel.snoozeSelected(duration)
                showBulkSnoozePicker = false
            },
            onDismiss = { showBulkSnoozePicker = false }
        )
    }
}

// =====================================================================================
// Top bar
// =====================================================================================

@Composable
private fun NotificationCenterTopBar(
    notificationCount: Int,
    unreadCount: Int,
    onBack: () -> Unit,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.StatusBar)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TerminalColors.Timestamp,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Title: # notification-center
        Text(
            text = "# ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )
        Text(
            text = "notification-center",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Unread badge
        if (unreadCount > 0) {
            Badge(
                containerColor = TerminalColors.BadgeRed,
                contentColor = TerminalColors.Background,
                modifier = Modifier.size(20.dp)
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.width(6.dp))
        }

        // Entry count
        Text(
            text = "($notificationCount entries)",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        // Clear all button
        if (notificationCount > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(TerminalColors.Error.copy(alpha = 0.15f))
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$ clear",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TerminalColors.Error
                    )
                )
            }
        }
    }
}

// =====================================================================================
// Filter tab row
// =====================================================================================

@Composable
private fun FilterTabRow(
    selectedFilter: NotificationFilter,
    onFilterSelected: (NotificationFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.StatusBar)
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(NotificationFilter.entries.toList()) { filter ->
            FilterTab(
                filter = filter,
                isSelected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) }
            )
        }
    }
}

@Composable
private fun FilterTab(
    filter: NotificationFilter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) TerminalColors.Accent.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(200),
        label = "filterBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) TerminalColors.Accent else TerminalColors.Timestamp,
        animationSpec = tween(200),
        label = "filterText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) TerminalColors.Accent.copy(alpha = 0.5f) else TerminalColors.Surface,
        animationSpec = tween(200),
        label = "filterBorder"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = filter.flag,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        )
    }
}

// =====================================================================================
// Section header (time group / pinned)
// =====================================================================================

@Composable
private fun SectionHeader(
    label: String,
    color: Color,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(color.copy(alpha = 0.3f))
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "[$count]",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = color.copy(alpha = 0.6f)
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(color.copy(alpha = 0.3f))
        )
    }
}

// =====================================================================================
// Swipeable notification card
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableNotificationCard(
    notification: NotificationEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isExpanded: Boolean,
    appMetadata: AppMetadata,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDismiss()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSnooze()
                    false // Don't confirm -- the snooze picker handles it
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            SwipeBackground(dismissState.targetValue)
        },
        enableDismissFromStartToEnd = !isSelectionMode,
        enableDismissFromEndToStart = !isSelectionMode,
        content = {
            NotificationCard(
                notification = notification,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                isExpanded = isExpanded,
                appMetadata = appMetadata,
                onClick = onClick,
                onLongPress = onLongPress
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(targetValue: SwipeToDismissBoxValue) {
    val color by animateColorAsState(
        targetValue = when (targetValue) {
            SwipeToDismissBoxValue.StartToEnd -> TerminalColors.Info.copy(alpha = 0.2f)
            SwipeToDismissBoxValue.EndToStart -> TerminalColors.Error.copy(alpha = 0.2f)
            SwipeToDismissBoxValue.Settled -> Color.Transparent
        },
        label = "swipeBg"
    )

    val icon = when (targetValue) {
        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Snooze
        SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
        SwipeToDismissBoxValue.Settled -> null
    }

    val iconTint = when (targetValue) {
        SwipeToDismissBoxValue.StartToEnd -> TerminalColors.Info
        SwipeToDismissBoxValue.EndToStart -> TerminalColors.Error
        SwipeToDismissBoxValue.Settled -> Color.Transparent
    }

    val alignment = when (targetValue) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        else -> Alignment.CenterEnd
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        icon?.let {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (targetValue == SwipeToDismissBoxValue.StartToEnd) "$ snooze" else "$ rm",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = iconTint
                    )
                )
            }
        }
    }
}

// =====================================================================================
// Notification card
// =====================================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationCard(
    notification: NotificationEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isExpanded: Boolean,
    appMetadata: AppMetadata,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val selectionBorderColor by animateColorAsState(
        targetValue = if (isSelected) TerminalColors.Accent else Color.Transparent,
        animationSpec = tween(150),
        label = "cardBorder"
    )

    // Priority-based left border color
    val priorityBorderColor = when (notification.priority) {
        NotificationPriority.HIGH -> TerminalColors.Error
        NotificationPriority.NORMAL -> Color.Transparent
        NotificationPriority.LOW -> Color.Transparent
    }

    // Dim low-priority and read notifications
    val contentAlpha = when {
        notification.priority == NotificationPriority.LOW -> 0.55f
        notification.isRead -> 0.7f
        else -> 1f
    }

    val backgroundColor = if (notification.isRead) {
        TerminalColors.Surface.copy(alpha = 0.5f)
    } else {
        TerminalColors.Surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            // Draw priority left border
            .then(
                if (priorityBorderColor != Color.Transparent) {
                    Modifier.drawBehind {
                        drawLine(
                            color = priorityBorderColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 4.dp.toPx()
                        )
                    }
                } else {
                    Modifier
                }
            )
            // Selection border
            .then(
                if (isSelected) {
                    Modifier.border(1.dp, selectionBorderColor, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(12.dp)
            .animateContentSize(animationSpec = tween(200)),
        verticalAlignment = Alignment.Top
    ) {
        // Selection checkbox or app icon
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) TerminalColors.Accent.copy(alpha = 0.2f)
                        else TerminalColors.Background
                    )
                    .border(
                        1.dp,
                        if (isSelected) TerminalColors.Accent else TerminalColors.Timestamp,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = TerminalColors.Accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            AppIconView(
                appIcon = appMetadata.appIcon,
                size = 24
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Row 1: journalctl-style log line header
            // Format: <timestamp> <appname> <category> <priority>
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timestamp (journalctl format)
                Text(
                    text = formatJournalTimestamp(notification.timestamp),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Timestamp.copy(alpha = contentAlpha)
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                // App name
                Text(
                    text = notification.appName,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = TerminalColors.Info.copy(alpha = contentAlpha)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Category badge
                CategoryBadge(category = notification.category)

                Spacer(modifier = Modifier.weight(1f))

                // Pin indicator
                if (notification.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = TerminalColors.Accent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Priority dot
                PriorityDot(priority = notification.priority)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Title (Command color, bold) -- severity-styled
            if (notification.title.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Severity label prefix like journalctl
                    val severityLabel = when (notification.priority) {
                        NotificationPriority.HIGH -> "ERR"
                        NotificationPriority.NORMAL -> "INFO"
                        NotificationPriority.LOW -> "DEBUG"
                    }
                    val severityColor = when (notification.priority) {
                        NotificationPriority.HIGH -> TerminalColors.Error
                        NotificationPriority.NORMAL -> TerminalColors.Command
                        NotificationPriority.LOW -> TerminalColors.Subtext
                    }

                    Text(
                        text = "$severityLabel: ",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = severityColor.copy(alpha = contentAlpha)
                        )
                    )

                    Text(
                        text = notification.title,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Command.copy(alpha = contentAlpha)
                        ),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
            }

            // Row 3: Content preview / full content
            if (notification.content.isNotBlank()) {
                Text(
                    text = notification.content,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Output.copy(alpha = contentAlpha),
                        lineHeight = 16.sp
                    ),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis
                )
            }

            // Expanded: show full metadata
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(
                        color = TerminalColors.Selection,
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Package name
                    MetadataLine(
                        label = "pkg",
                        value = notification.packageName
                    )

                    // Category
                    MetadataLine(
                        label = "category",
                        value = notification.category.displayName
                    )

                    // Priority
                    MetadataLine(
                        label = "priority",
                        value = notification.priority.displayName
                    )

                    // Full timestamp
                    MetadataLine(
                        label = "posted",
                        value = formatFullTimestamp(notification.timestamp)
                    )

                    // Relative time
                    MetadataLine(
                        label = "age",
                        value = formatRelativeTime(notification.timestamp)
                    )

                    // Snoozed indicator
                    if (notification.isSnoozed) {
                        MetadataLine(
                            label = "snoozed-until",
                            value = formatFullTimestamp(notification.snoozeUntil)
                        )
                    }
                }
            }

            // Snoozed indicator (collapsed view)
            if (notification.isSnoozed && !isExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Snooze,
                        contentDescription = null,
                        tint = TerminalColors.Info,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "snoozed until ${formatRelativeTime(notification.snoozeUntil)}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = TerminalColors.Info
                        )
                    )
                }
            }
        }
    }
}

// =====================================================================================
// Sub-components
// =====================================================================================

@Composable
private fun MetadataLine(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label=",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Timestamp
            )
        )
        Text(
            text = value,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Prompt
            )
        )
    }
}

@Composable
private fun AppIconView(
    appIcon: Drawable?,
    size: Int
) {
    if (appIcon != null) {
        val bitmap = remember(appIcon) {
            appIcon.toBitmap(width = size * 2, height = size * 2).asImageBitmap()
        }
        Image(
            bitmap = bitmap,
            contentDescription = "App icon",
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(TerminalColors.Selection),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = TerminalColors.Timestamp,
                modifier = Modifier.size((size * 0.6f).dp)
            )
        }
    }
}

@Composable
private fun PriorityDot(priority: NotificationPriority) {
    val color = when (priority) {
        NotificationPriority.HIGH -> TerminalColors.Error
        NotificationPriority.NORMAL -> TerminalColors.Warning
        NotificationPriority.LOW -> TerminalColors.Success
    }

    Icon(
        imageVector = Icons.Default.Circle,
        contentDescription = "Priority: ${priority.name.lowercase()}",
        tint = color,
        modifier = Modifier.size(8.dp)
    )
}

@Composable
private fun CategoryBadge(category: NotificationCategory) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(category.color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = category.displayName,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = category.color
            )
        )
    }
}

// =====================================================================================
// Action sheet (long-press)
// =====================================================================================

@Composable
private fun ActionSheetContent(
    notification: NotificationEntry,
    onSnooze: () -> Unit,
    onPin: () -> Unit,
    onSetPriority: (NotificationPriority) -> Unit,
    onMuteApp: () -> Unit,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        // Header: notification title
        Text(
            text = notification.title.ifBlank { notification.appName },
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${notification.appName} | ${notification.category.displayName} | ${notification.priority.displayName}",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(color = TerminalColors.Selection, thickness = 1.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // $ snooze --duration=?
        ActionSheetItem(
            command = "$ snooze --duration=?",
            description = "Snooze this notification",
            icon = Icons.Default.Snooze,
            color = TerminalColors.Info,
            onClick = onSnooze
        )

        // $ pin / $ unpin
        ActionSheetItem(
            command = if (notification.isPinned) "$ unpin" else "$ pin",
            description = if (notification.isPinned) "Unpin from top" else "Pin to top of list",
            icon = Icons.Default.PushPin,
            color = TerminalColors.Accent,
            onClick = onPin
        )

        // $ priority --set=high
        ActionSheetItem(
            command = "$ priority --set=high",
            description = "Set priority to high",
            icon = Icons.Default.PriorityHigh,
            color = TerminalColors.Error,
            onClick = { onSetPriority(NotificationPriority.HIGH) }
        )

        // $ priority --set=low
        ActionSheetItem(
            command = "$ priority --set=low",
            description = "Set priority to low",
            icon = Icons.Default.Circle,
            color = TerminalColors.Success,
            onClick = { onSetPriority(NotificationPriority.LOW) }
        )

        // $ mute --app=<name>
        ActionSheetItem(
            command = "$ mute --app=${notification.appName.lowercase().replace(" ", "-")}",
            description = "Mute all from ${notification.appName}",
            icon = Icons.Default.NotificationsOff,
            color = TerminalColors.Warning,
            onClick = onMuteApp
        )

        // $ open
        ActionSheetItem(
            command = "$ open --app=${notification.appName.lowercase().replace(" ", "-")}",
            description = "Open ${notification.appName}",
            icon = Icons.Default.Launch,
            color = TerminalColors.Prompt,
            onClick = onOpen
        )

        // $ select
        ActionSheetItem(
            command = "$ select",
            description = "Start multi-select",
            icon = Icons.Default.CheckCircle,
            color = TerminalColors.Accent,
            onClick = onSelect
        )

        Spacer(modifier = Modifier.height(4.dp))

        HorizontalDivider(color = TerminalColors.Selection, thickness = 1.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // $ dismiss
        ActionSheetItem(
            command = "$ dismiss",
            description = "Remove this notification",
            icon = Icons.Default.Close,
            color = TerminalColors.Error.copy(alpha = 0.7f),
            onClick = onDismiss
        )
    }
}

@Composable
private fun ActionSheetItem(
    command: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = command,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = color
                )
            )
            Text(
                text = description,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }
}

// =====================================================================================
// Snooze duration picker
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnoozeDurationPicker(
    onDurationSelected: (SnoozeDuration) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TerminalColors.Surface,
        contentColor = TerminalColors.Command
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "$ snooze --duration=",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Info
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Select snooze duration:",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Timestamp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            SnoozeDuration.entries.forEach { duration ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onDurationSelected(duration) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = TerminalColors.Info,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "--duration=${duration.label}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TerminalColors.Command
                        )
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = when (duration) {
                            SnoozeDuration.FIFTEEN_MIN -> "15 minutes"
                            SnoozeDuration.THIRTY_MIN -> "30 minutes"
                            SnoozeDuration.ONE_HOUR -> "1 hour"
                            SnoozeDuration.TWO_HOURS -> "2 hours"
                            SnoozeDuration.TOMORROW -> "Tomorrow 9 AM"
                        },
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                }
            }
        }
    }
}

// =====================================================================================
// Bulk actions bar
// =====================================================================================

@Composable
private fun BulkActionsBar(
    selectedCount: Int,
    onDismissSelected: () -> Unit,
    onMarkRead: () -> Unit,
    onSnooze: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(TerminalColors.StatusBar)
    ) {
        HorizontalDivider(color = TerminalColors.Accent.copy(alpha = 0.3f), thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Selection count
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "[$selectedCount selected]",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Select all
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onSelectAll)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = "Select all",
                        tint = TerminalColors.Timestamp,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Clear selection
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onClearSelection)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear selection",
                        tint = TerminalColors.Timestamp,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // $ rm -rf selected
                BulkActionButton(
                    label = "$ rm -rf",
                    color = TerminalColors.Error,
                    onClick = onDismissSelected
                )

                // $ mark-read
                BulkActionButton(
                    label = "$ mark-read",
                    color = TerminalColors.Success,
                    onClick = onMarkRead
                )

                // $ snooze --bulk
                BulkActionButton(
                    label = "$ snooze",
                    color = TerminalColors.Info,
                    onClick = onSnooze
                )
            }
        }
    }
}

@Composable
private fun BulkActionButton(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        )
    }
}

// =====================================================================================
// Empty state
// =====================================================================================

@Composable
private fun EmptyState(selectedFilter: NotificationFilter) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Dimmed notification icon
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = null,
                tint = TerminalColors.Subtext,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // journalctl output
            Text(
                text = "$ journalctl --user ${selectedFilter.flag}",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "-- No entries --",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Timestamp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (selectedFilter == NotificationFilter.ALL) {
                    "All caught up. No notifications to display."
                } else {
                    "No ${selectedFilter.name.lowercase()} notifications.\nTry --filter=all to see everything."
                },
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Subtext,
                    lineHeight = 18.sp
                )
            )
        }
    }
}

// =====================================================================================
// Utilities
// =====================================================================================

/**
 * Formats a timestamp in journalctl style: "Feb 17 14:32:05"
 */
private fun formatJournalTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.US)
    return sdf.format(Date(timestamp))
}

/**
 * Formats a timestamp in full ISO-like format for expanded metadata view.
 */
private fun formatFullTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return sdf.format(Date(timestamp))
}

/**
 * Formats a timestamp into a human-readable relative time string.
 *
 * Examples: "just now", "2m ago", "1h ago", "3d ago"
 */
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp

    if (diffMs < 0) {
        // Future timestamp (e.g., snooze-until time)
        val futureMs = -diffMs
        return when {
            futureMs < 60_000 -> "in <1m"
            futureMs < 3_600_000 -> "in ${futureMs / 60_000}m"
            futureMs < 86_400_000 -> "in ${futureMs / 3_600_000}h"
            else -> "in ${futureMs / 86_400_000}d"
        }
    }

    return when {
        diffMs < 60_000 -> "just now"
        diffMs < 3_600_000 -> "${diffMs / 60_000}m ago"
        diffMs < 86_400_000 -> "${diffMs / 3_600_000}h ago"
        diffMs < 604_800_000 -> "${diffMs / 86_400_000}d ago"
        else -> "${diffMs / 604_800_000}w ago"
    }
}
