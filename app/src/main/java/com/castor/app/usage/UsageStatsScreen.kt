package com.castor.app.usage

import android.graphics.drawable.Drawable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.castor.core.ui.theme.TerminalColors

/**
 * Full-screen App Usage Stats / Screen Time dashboard.
 *
 * Styled as `$ htop --user-apps` — an information-dense, monospace,
 * terminal-themed screen time viewer built entirely on Android's
 * [UsageStatsManager] APIs. All data stays on-device.
 *
 * Layout (scrollable):
 * 1. Top bar with back arrow, "# screen-time" title, period selector
 * 2. Summary card — total time, change %, pickups, avg session
 * 3. Weekly bar chart — 7 vertical bars with terminal block chars
 * 4. Category breakdown — `du -sh` style category rows
 * 5. Top apps list — `ps aux --sort=-time` style per-app rows
 * 6. Permission gate — shown instead of content if access not granted
 *
 * @param onBack Navigation callback to pop back to the previous screen
 */
@Composable
fun UsageStatsScreen(
    onBack: () -> Unit,
    viewModel: UsageStatsViewModel = hiltViewModel()
) {
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val summary by viewModel.usageSummary.collectAsState()
    val topApps by viewModel.topApps.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val dailyUsage by viewModel.dailyUsage.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Refresh when returning from Settings (permission might have changed)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
    ) {
        // ==================================================================
        // Top bar
        // ==================================================================
        UsageTopBar(onBack = onBack)

        // ==================================================================
        // Period selector
        // ==================================================================
        PeriodSelector(
            selected = selectedPeriod,
            onSelect = viewModel::selectPeriod
        )

        // ==================================================================
        // Content
        // ==================================================================
        if (!hasPermission) {
            PermissionCard(
                onRequestPermission = viewModel::requestUsageStatsPermission
            )
        } else if (isLoading && topApps.isEmpty()) {
            LoadingIndicator()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // ---- Command header ----
                item {
                    TerminalCommandHeader(period = selectedPeriod)
                }

                // ---- Summary card ----
                item {
                    SummaryCard(summary = summary)
                }

                // ---- Weekly bar chart (shown for all periods) ----
                if (dailyUsage.isNotEmpty()) {
                    item {
                        SectionLabel(title = "weekly-usage")
                    }
                    item {
                        WeeklyBarChart(dailyData = dailyUsage)
                    }
                }

                // ---- Category breakdown ----
                if (categoryBreakdown.isNotEmpty()) {
                    item {
                        SectionLabel(title = "by-category")
                    }
                    item {
                        CategoryBreakdownSection(categories = categoryBreakdown)
                    }
                }

                // ---- Top apps ----
                if (topApps.isNotEmpty()) {
                    item {
                        SectionLabel(title = "top-processes")
                    }

                    // Table header
                    item {
                        TopAppsHeader()
                    }

                    // App rows
                    itemsIndexed(topApps) { index, app ->
                        TopAppRow(
                            rank = index + 1,
                            app = app,
                            maxTime = topApps.first().usageTimeMs
                        )
                    }
                }

                // ---- EOF ----
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "# EOF — all data on-device, privacy-first",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalColors.Subtext
                        )
                    )
                }
            }
        }
    }
}

// =============================================================================
// Top bar
// =============================================================================

@Composable
private fun UsageTopBar(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.StatusBar)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TerminalColors.Command
            )
        }

        Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "# screen-time",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )
    }
}

// =============================================================================
// Period selector tabs
// =============================================================================

@Composable
private fun PeriodSelector(
    selected: UsagePeriod,
    onSelect: (UsagePeriod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.StatusBar)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UsagePeriod.entries.forEach { period ->
            val isSelected = period == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isSelected) TerminalColors.Selection
                        else Color.Transparent
                    )
                    .clickable { onSelect(period) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = period.label,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) TerminalColors.Accent else TerminalColors.Timestamp
                    )
                )
            }
        }
    }
}

// =============================================================================
// Terminal command header
// =============================================================================

@Composable
private fun TerminalCommandHeader(period: UsagePeriod) {
    val flag = when (period) {
        UsagePeriod.TODAY -> "--today"
        UsagePeriod.THIS_WEEK -> "--week"
        UsagePeriod.THIS_MONTH -> "--month"
    }
    Column {
        Text(
            text = "$ htop --user-apps $flag",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Prompt
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "# On-device usage statistics — no data leaves this device",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Subtext
            )
        )
    }
}

// =============================================================================
// Summary card
// =============================================================================

@Composable
private fun SummaryCard(summary: UsageSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .padding(16.dp)
    ) {
        // ---- Total screen time (large) ----
        Text(
            text = formatDuration(summary.totalScreenTimeMs),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ---- Change from previous period ----
        if (summary.changePercent > 0) {
            val arrow = if (summary.isIncrease) "\u2191" else "\u2193"
            val changeColor = if (summary.isIncrease) TerminalColors.Warning else TerminalColors.Success
            val periodLabel = "from previous period"
            Text(
                text = "$arrow ${summary.changePercent}% $periodLabel",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = changeColor
                )
            )
        } else {
            Text(
                text = "~ same as previous period",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Pickups + Average session ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Pickups
            Column {
                Text(
                    text = "PICKUPS",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Timestamp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = TerminalColors.Info,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${summary.pickupCount} pickups",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Info
                        )
                    )
                }
            }

            // Average session
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "AVG SESSION",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Timestamp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "~${formatDurationShort(summary.avgSessionMs)} per session",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Command
                    )
                )
            }
        }
    }
}

// =============================================================================
// Weekly bar chart
// =============================================================================

@Composable
private fun WeeklyBarChart(dailyData: List<DailyUsage>) {
    val maxTimeMs = dailyData.maxOfOrNull { it.totalTimeMs } ?: 1L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .padding(16.dp)
    ) {
        // Chart area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            dailyData.forEach { day ->
                DayBar(
                    day = day,
                    maxTimeMs = maxTimeMs,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DayBar(
    day: DailyUsage,
    maxTimeMs: Long,
    modifier: Modifier = Modifier
) {
    val fraction = if (maxTimeMs > 0) {
        (day.totalTimeMs.toFloat() / maxTimeMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "barAnim_${day.dayOfWeek}"
    )

    val barColor = when {
        day.isToday -> TerminalColors.Accent
        fraction < 0.33f -> TerminalColors.Success
        fraction < 0.66f -> TerminalColors.Warning
        else -> TerminalColors.Error
    }

    val hours = day.totalTimeMs / 3_600_000f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = modifier.fillMaxHeight()
    ) {
        // Hours label above the bar
        if (day.totalTimeMs > 0) {
            Text(
                text = if (hours >= 1f) "%.1fh".format(hours) else "${(day.totalTimeMs / 60_000)}m",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = if (day.isToday) TerminalColors.Accent else TerminalColors.Timestamp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Bar
        val barHeight = (animatedFraction * 100).dp.coerceAtLeast(2.dp)
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(barColor.copy(alpha = if (day.isToday) 1f else 0.7f))
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Day label
        Text(
            text = day.dayOfWeek,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (day.isToday) TerminalColors.Accent else TerminalColors.Timestamp
            )
        )
    }
}

// =============================================================================
// Category breakdown
// =============================================================================

@Composable
private fun CategoryBreakdownSection(categories: List<CategoryUsage>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header styled like `du -sh` output
        Text(
            text = "$ du -sh --by-category",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Prompt
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        categories.forEach { category ->
            CategoryRow(category = category)
        }
    }
}

@Composable
private fun CategoryRow(category: CategoryUsage) {
    val barWidth = category.percentage.coerceIn(0f, 1f)

    val animatedWidth by animateFloatAsState(
        targetValue = barWidth,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "catBar_${category.category.name}"
    )

    val categoryColor = when (category.category) {
        UsageCategory.SOCIAL -> TerminalColors.Info
        UsageCategory.ENTERTAINMENT -> TerminalColors.Error
        UsageCategory.PRODUCTIVITY -> TerminalColors.Success
        UsageCategory.TOOLS -> TerminalColors.Warning
        UsageCategory.GAMES -> TerminalColors.Accent
        UsageCategory.OTHER -> TerminalColors.Timestamp
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time
        Text(
            text = formatDurationCompact(category.totalTimeMs),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            ),
            modifier = Modifier.width(64.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Progress bar (terminal-style filled blocks)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(TerminalColors.Selection)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedWidth)
                    .clip(RoundedCornerShape(2.dp))
                    .background(categoryColor.copy(alpha = 0.8f))
            )

            // Block character overlay for terminal feel
            Text(
                text = buildTerminalBar(animatedWidth, 10),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = categoryColor
                ),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Category name
        Text(
            text = category.category.label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = categoryColor
            ),
            modifier = Modifier.width(80.dp)
        )
    }
}

// =============================================================================
// Top apps section
// =============================================================================

@Composable
private fun TopAppsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .background(TerminalColors.Selection)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "PID",
            style = headerStyle(),
            modifier = Modifier.width(32.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "APP",
            style = headerStyle(),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "TIME",
            style = headerStyle(),
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.End
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "CPU%",
            style = headerStyle(),
            modifier = Modifier.width(72.dp)
        )
    }
}

@Composable
private fun TopAppRow(
    rank: Int,
    app: AppUsage,
    maxTime: Long
) {
    val fraction = if (maxTime > 0) {
        (app.usageTimeMs.toFloat() / maxTime).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = "appBar_${app.packageName}"
    )

    var showLimitDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (rank % 2 == 0) TerminalColors.Surface.copy(alpha = 0.5f)
                else TerminalColors.Surface.copy(alpha = 0.3f)
            )
            .clickable { showLimitDialog = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // PID (rank number)
        Text(
            text = rank.toString(),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            ),
            modifier = Modifier.width(32.dp)
        )

        // App icon
        AppIconImage(
            icon = app.icon,
            contentDescription = app.appName,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Spacer(modifier = Modifier.width(8.dp))

        // App name
        Text(
            text = app.appName,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Command
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Time
        Text(
            text = formatDurationCompact(app.usageTimeMs),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            ),
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.End
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Usage bar
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(TerminalColors.Selection)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(getUsageBarColor(fraction))
            )
        }
    }

    // ---- Time limit dialog ----
    if (showLimitDialog) {
        TimeLimitDialog(
            appName = app.appName,
            currentUsage = app.usageTimeMs,
            onDismiss = { showLimitDialog = false },
            onSetLimit = { /* TODO: Persist limit to DataStore */ showLimitDialog = false }
        )
    }
}

// =============================================================================
// App icon composable
// =============================================================================

@Composable
private fun AppIconImage(
    icon: Drawable?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    if (icon != null) {
        val bitmap = remember(icon) {
            icon.toBitmap(width = 64, height = 64).asImageBitmap()
        }
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier
        )
    } else {
        // Fallback: colored circle with first letter
        Box(
            modifier = modifier.background(TerminalColors.Accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contentDescription.firstOrNull()?.uppercase() ?: "?",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Background
                )
            )
        }
    }
}

// =============================================================================
// Time limit dialog
// =============================================================================

@Composable
private fun TimeLimitDialog(
    appName: String,
    currentUsage: Long,
    onDismiss: () -> Unit,
    onSetLimit: (Long) -> Unit
) {
    var selectedHours by remember { mutableIntStateOf(1) }
    var selectedMinutes by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TerminalColors.Surface,
        titleContentColor = TerminalColors.Command,
        textContentColor = TerminalColors.Command,
        title = {
            Text(
                text = "$ set-limit --app \"$appName\"",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )
        },
        text = {
            Column {
                Text(
                    text = "# Current usage: ${formatDuration(currentUsage)}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Subtext
                    )
                )
                Text(
                    text = "# Set a daily time limit for this app",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalColors.Subtext
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Hours / Minutes selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "HOURS",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                if (selectedHours > 0) selectedHours--
                            }) {
                                Text(
                                    text = "-",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 18.sp,
                                        color = TerminalColors.Accent
                                    )
                                )
                            }
                            Text(
                                text = selectedHours.toString().padStart(2, '0'),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TerminalColors.Accent
                                ),
                                modifier = Modifier.width(40.dp),
                                textAlign = TextAlign.Center
                            )
                            TextButton(onClick = {
                                if (selectedHours < 12) selectedHours++
                            }) {
                                Text(
                                    text = "+",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 18.sp,
                                        color = TerminalColors.Accent
                                    )
                                )
                            }
                        }
                    }

                    Text(
                        text = ":",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalColors.Timestamp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Minutes
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "MINUTES",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                selectedMinutes = when {
                                    selectedMinutes >= 15 -> selectedMinutes - 15
                                    else -> 45
                                }
                            }) {
                                Text(
                                    text = "-",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 18.sp,
                                        color = TerminalColors.Accent
                                    )
                                )
                            }
                            Text(
                                text = selectedMinutes.toString().padStart(2, '0'),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TerminalColors.Accent
                                ),
                                modifier = Modifier.width(40.dp),
                                textAlign = TextAlign.Center
                            )
                            TextButton(onClick = {
                                selectedMinutes = when {
                                    selectedMinutes < 45 -> selectedMinutes + 15
                                    else -> 0
                                }
                            }) {
                                Text(
                                    text = "+",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 18.sp,
                                        color = TerminalColors.Accent
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Preview
                val limitMs = (selectedHours * 3_600_000L) + (selectedMinutes * 60_000L)
                if (limitMs > 0) {
                    Text(
                        text = "limit = ${formatDuration(limitMs)}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TerminalColors.Prompt
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val limitMs = (selectedHours * 3_600_000L) + (selectedMinutes * 60_000L)
                if (limitMs > 0) onSetLimit(limitMs)
            }) {
                Text(
                    text = "SET LIMIT",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CANCEL",
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

// =============================================================================
// Permission card
// =============================================================================

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TerminalColors.Surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = TerminalColors.Warning,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "$ usage-stats --check-permission",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "ERROR: USAGE_ACCESS not granted",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Error
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "# Un-Dios needs Usage Access permission to show\n" +
                    "# your screen time statistics. All data stays\n" +
                    "# on-device and is never transmitted anywhere.",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Subtext
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TerminalColors.Accent)
                    .clickable(onClick = onRequestPermission)
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "$ grant-permission",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Background
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "# Opens system Settings > Usage Access",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Subtext
                )
            )
        }
    }
}

// =============================================================================
// Loading indicator
// =============================================================================

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = TerminalColors.Accent,
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Loading usage stats...",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }
}

// =============================================================================
// Section label
// =============================================================================

@Composable
private fun SectionLabel(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
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
            text = title,
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
}

// =============================================================================
// Utility functions
// =============================================================================

/**
 * Formats milliseconds to "Xh Ym" display (e.g., "4h 23m").
 * For zero, returns "0m".
 */
private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/**
 * Shorter format: "Xh Ym" or just "Ym" for durations under an hour.
 */
private fun formatDurationShort(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes} min"
    }
}

/**
 * Compact format for table cells: "1h 23m" or "45m".
 */
private fun formatDurationCompact(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h %02dm".format(minutes)
        else -> "${minutes}m"
    }
}

/**
 * Builds a terminal-style bar using block characters.
 * Filled portion uses full blocks, empty portion uses light shade.
 */
private fun buildTerminalBar(fraction: Float, totalBlocks: Int): String {
    val filled = (fraction * totalBlocks).toInt().coerceIn(0, totalBlocks)
    val empty = totalBlocks - filled
    return "\u2588".repeat(filled) + "\u2591".repeat(empty)
}

/**
 * Returns a color for the usage bar based on relative usage fraction.
 */
private fun getUsageBarColor(fraction: Float): Color = when {
    fraction > 0.7f -> TerminalColors.Error
    fraction > 0.4f -> TerminalColors.Warning
    else -> TerminalColors.Success
}

/**
 * Header text style for the top apps table.
 */
@Composable
private fun headerStyle() = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    color = TerminalColors.Timestamp
)
