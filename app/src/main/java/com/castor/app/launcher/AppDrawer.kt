package com.castor.app.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.core.ui.theme.TerminalColors

/**
 * Full-screen GNOME Activities-style app drawer overlay.
 *
 * Displays all installed launchable applications in a grid, with a terminal-styled
 * search bar at the top, category filter chips, a "Recent" row for recently-used
 * apps, and a "Frequent" row for most-launched apps. The overlay slides up from
 * the bottom with a dark semi-transparent backdrop, reminiscent of GNOME's
 * Activities overview or Ubuntu's app grid.
 *
 * Features:
 * - Responsive grid: 4 columns on phone, 6 columns on tablet (>600dp width)
 * - Terminal-style search: `$ find /apps -name "..."` with fuzzy matching
 * - Category filter tabs: All | Social | Work | Media | Games | Utils | System
 * - Recent apps section: `$ history | tail -8`
 * - Frequent apps section: `$ sort -rn ~/.app_usage | head -4`
 * - Categorized section headers as directory paths: `$ ls ~/apps/<category>/`
 * - Long-press context menu: App Info, Uninstall, Add to Dock
 * - Smooth slide-up/slide-down animation
 * - Fuzzy search with matched character highlighting
 *
 * @param isVisible Whether the drawer is currently shown (drives animation)
 * @param onDismiss Callback to close the drawer
 * @param viewModel The [AppDrawerViewModel] providing app data
 */
@Composable
fun AppDrawer(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    viewModel: AppDrawerViewModel = hiltViewModel()
) {
    val filteredApps by viewModel.filteredApps.collectAsState()
    val recentApps by viewModel.recentApps.collectAsState()
    val sessionRecentApps by viewModel.sessionRecentApps.collectAsState()
    val frequentApps by viewModel.frequentApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val categoryCounts by viewModel.categoryCounts.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    // Merge session-recent with usage-stats-recent, preferring session data
    val effectiveRecentApps = remember(sessionRecentApps, recentApps) {
        if (sessionRecentApps.isNotEmpty()) {
            val sessionPkgs = sessionRecentApps.map { it.packageName }.toSet()
            val combined = sessionRecentApps +
                recentApps.filter { it.packageName !in sessionPkgs }
            combined.take(8)
        } else {
            recentApps
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(250)) +
            slideInVertically(
                animationSpec = tween(350),
                initialOffsetY = { it / 3 }
            ),
        exit = fadeOut(animationSpec = tween(200)) +
            slideOutVertically(
                animationSpec = tween(300),
                targetOffsetY = { it / 3 }
            )
    ) {
        AppDrawerContent(
            apps = filteredApps,
            recentApps = effectiveRecentApps,
            frequentApps = frequentApps,
            searchQuery = searchQuery,
            searchResults = searchResults,
            selectedCategory = selectedCategory,
            categoryCounts = categoryCounts,
            totalAppCount = installedApps.size,
            onSearchChange = { viewModel.searchQuery.value = it },
            onCategorySelect = { viewModel.selectCategory(it) },
            onAppClick = { appInfo ->
                viewModel.launchApp(appInfo)
                onDismiss()
            },
            onAppLongClickInfo = { viewModel.openAppInfo(it) },
            onAppLongClickUninstall = { viewModel.uninstallApp(it) },
            onAppDockPin = { appInfo ->
                if (viewModel.isAppPinned(appInfo.packageName)) {
                    viewModel.unpinAppFromDock(appInfo.packageName)
                } else {
                    viewModel.pinAppToDock(appInfo.packageName)
                }
            },
            isAppPinned = { viewModel.isAppPinned(it.packageName) },
            onBack = onDismiss,
            isLoading = isLoading
        )
    }
}

/**
 * The actual content layout of the app drawer. Separated from the animated
 * visibility wrapper for clarity.
 */
@Composable
private fun AppDrawerContent(
    apps: List<AppInfo>,
    recentApps: List<AppInfo>,
    frequentApps: List<AppInfo>,
    searchQuery: String,
    searchResults: List<SearchResult>,
    selectedCategory: AppCategory?,
    categoryCounts: Map<AppCategory, Int>,
    totalAppCount: Int,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (AppCategory?) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClickInfo: (AppInfo) -> Unit,
    onAppLongClickUninstall: (AppInfo) -> Unit,
    onAppDockPin: (AppInfo) -> Unit,
    isAppPinned: (AppInfo) -> Boolean,
    onBack: () -> Unit,
    isLoading: Boolean
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val columnCount = if (screenWidthDp >= 600) 6 else 4

    // Group apps by category for categorized view
    val categorizedApps = remember(apps, searchQuery) {
        if (searchQuery.isNotBlank()) {
            // During search, don't group by category
            emptyMap()
        } else {
            apps.groupBy { it.category }
                .toSortedMap(compareBy { it.ordinal })
        }
    }

    val showRecent = searchQuery.isBlank() && recentApps.isNotEmpty() && selectedCategory == null
    val showFrequent = searchQuery.isBlank() && frequentApps.isNotEmpty() && selectedCategory == null
    val isSearching = searchQuery.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ---- Top bar: back button + search ----
            AppDrawerTopBar(
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                onBack = onBack,
                totalAppCount = totalAppCount
            )

            // ---- Category filter chips ----
            CategoryFilterRow(
                selectedCategory = selectedCategory,
                categoryCounts = categoryCounts,
                totalAppCount = totalAppCount,
                onCategorySelect = onCategorySelect
            )

            if (isLoading) {
                // Loading state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = TerminalColors.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "$ ls /usr/share/applications ...",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                    }
                }
            } else if (apps.isEmpty() && searchQuery.isNotBlank()) {
                // No results
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$ find /apps -name \"*$searchQuery*\"",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalColors.Prompt
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$ find: no matches found",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalColors.Error
                            )
                        )
                    }
                }
            } else {
                // App grid
                val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = navBarPadding.calculateBottomPadding() + 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    // ---- Recent apps section (horizontal row) ----
                    if (showRecent) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            DrawerSectionHeader(title = "$ history | tail -8")
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            RecentAppsRow(
                                recentApps = recentApps,
                                onAppClick = onAppClick,
                                onAppLongClickInfo = onAppLongClickInfo,
                                onAppLongClickUninstall = onAppLongClickUninstall,
                                onAppDockPin = onAppDockPin,
                                isAppPinned = isAppPinned
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // ---- Frequent apps section ----
                    if (showFrequent) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            DrawerSectionHeader(title = "$ sort -rn ~/.app_usage | head -4")
                        }

                        items(
                            items = frequentApps,
                            key = { "freq_${it.packageName}" }
                        ) { app ->
                            AppGridItem(
                                appInfo = app,
                                onClick = { onAppClick(app) },
                                onInfoClick = { onAppLongClickInfo(app) },
                                onUninstallClick = { onAppLongClickUninstall(app) },
                                onDockPin = { onAppDockPin(app) },
                                isPinned = isAppPinned(app),
                                showCategoryBadge = true
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // ---- Search results with highlighting ----
                    if (isSearching) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            DrawerSectionHeader(
                                title = "$ find /apps -name \"*$searchQuery*\"  # ${apps.size} packages"
                            )
                        }

                        items(
                            items = searchResults,
                            key = { "search_${it.appInfo.packageName}" }
                        ) { result ->
                            AppGridItemWithHighlight(
                                searchResult = result,
                                onClick = { onAppClick(result.appInfo) },
                                onInfoClick = { onAppLongClickInfo(result.appInfo) },
                                onUninstallClick = { onAppLongClickUninstall(result.appInfo) },
                                onDockPin = { onAppDockPin(result.appInfo) },
                                isPinned = isAppPinned(result.appInfo)
                            )
                        }
                    } else if (selectedCategory != null) {
                        // ---- Single category view ----
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            DrawerSectionHeader(
                                title = "$ ls ~/apps/${selectedCategory.terminalLabel}  # ${apps.size} packages"
                            )
                        }

                        items(
                            items = apps,
                            key = { "cat_${it.packageName}" }
                        ) { app ->
                            AppGridItem(
                                appInfo = app,
                                onClick = { onAppClick(app) },
                                onInfoClick = { onAppLongClickInfo(app) },
                                onUninstallClick = { onAppLongClickUninstall(app) },
                                onDockPin = { onAppDockPin(app) },
                                isPinned = isAppPinned(app),
                                showCategoryBadge = false
                            )
                        }
                    } else {
                        // ---- All apps grouped by category ----
                        categorizedApps.forEach { (category, appsInCategory) ->
                            if (appsInCategory.isNotEmpty()) {
                                // Category section header
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    DrawerSectionHeader(
                                        title = "$ ls ~/apps/${category.terminalLabel}  # ${appsInCategory.size} packages"
                                    )
                                }

                                items(
                                    items = appsInCategory,
                                    key = { "all_${it.packageName}" }
                                ) { app ->
                                    AppGridItem(
                                        appInfo = app,
                                        onClick = { onAppClick(app) },
                                        onInfoClick = { onAppLongClickInfo(app) },
                                        onUninstallClick = { onAppLongClickUninstall(app) },
                                        onDockPin = { onAppDockPin(app) },
                                        isPinned = isAppPinned(app),
                                        showCategoryBadge = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Top bar with search
// =============================================================================

/**
 * Top bar for the app drawer containing a back/close button and a
 * terminal-styled search field that resembles `$ find /apps -name "..."`.
 */
@Composable
private fun AppDrawerTopBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    totalAppCount: Int
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Close / back row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close app drawer",
                    tint = TerminalColors.Command
                )
            }

            Text(
                text = "activities",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            // App count indicator
            Text(
                text = "# $totalAppCount packages",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search bar styled like a terminal find command
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TerminalColors.Surface)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Prompt
            Text(
                text = "$ find /apps -name \"",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalColors.Prompt
                )
            )

            // Input field
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalColors.Command
                ),
                cursorBrush = SolidColor(TerminalColors.Cursor),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "*",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = TerminalColors.Timestamp
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Text(
                text = "\"",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalColors.Prompt
                )
            )

            // Clear button
            if (searchQuery.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { onSearchChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = TerminalColors.Timestamp,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TerminalColors.Timestamp,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// =============================================================================
// Category filter row
// =============================================================================

/**
 * Horizontally scrollable row of category filter chips.
 * Each chip shows the category name and app count. "All" is the default.
 */
@Composable
private fun CategoryFilterRow(
    selectedCategory: AppCategory?,
    categoryCounts: Map<AppCategory, Int>,
    totalAppCount: Int,
    onCategorySelect: (AppCategory?) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // "All" chip
        CategoryChip(
            label = "all",
            count = totalAppCount,
            isSelected = selectedCategory == null,
            onClick = { onCategorySelect(null) }
        )

        // Category chips (only show categories that have apps)
        for (category in AppCategory.entries) {
            val count = categoryCounts[category] ?: 0
            if (count > 0) {
                CategoryChip(
                    label = category.tabLabel,
                    count = count,
                    isSelected = selectedCategory == category,
                    onClick = { onCategorySelect(category) }
                )
            }
        }
    }
}

/**
 * A single category filter chip styled as a terminal path segment.
 * Selected chip gets the accent color; unselected chips are dimmer.
 */
@Composable
private fun CategoryChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        TerminalColors.Accent.copy(alpha = 0.15f)
    } else {
        TerminalColors.Surface
    }
    val textColor = if (isSelected) {
        TerminalColors.Accent
    } else {
        TerminalColors.Timestamp
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$label ($count)",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        )
    }
}

// =============================================================================
// Recent apps horizontal row
// =============================================================================

/**
 * Horizontally scrollable row of recently used apps, shown at the top of the drawer.
 * Each item is a compact icon + label.
 */
@Composable
private fun RecentAppsRow(
    recentApps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClickInfo: (AppInfo) -> Unit,
    onAppLongClickUninstall: (AppInfo) -> Unit,
    onAppDockPin: (AppInfo) -> Unit,
    isAppPinned: (AppInfo) -> Boolean
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(
            items = recentApps,
            key = { "recent_row_${it.packageName}" }
        ) { app ->
            RecentAppItem(
                appInfo = app,
                onClick = { onAppClick(app) },
                onInfoClick = { onAppLongClickInfo(app) },
                onUninstallClick = { onAppLongClickUninstall(app) },
                onDockPin = { onAppDockPin(app) },
                isPinned = isAppPinned(app)
            )
        }
    }
}

/**
 * A compact app item for the recent apps horizontal row.
 * Shows icon + truncated name in a narrow column with long-press context menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentAppItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onDockPin: () -> Unit,
    isPinned: Boolean
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                )
                .padding(vertical = 6.dp)
        ) {
            // App icon
            if (appInfo.icon != null) {
                val bitmap = remember(appInfo.packageName) {
                    try {
                        appInfo.icon.toBitmap(96, 96).asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = appInfo.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    AppIconFallback(size = 40)
                }
            } else {
                AppIconFallback(size = 40)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = appInfo.label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Subtext
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Context menu
        AppContextMenu(
            expanded = showContextMenu,
            onDismiss = { showContextMenu = false },
            appInfo = appInfo,
            onInfoClick = {
                showContextMenu = false
                onInfoClick()
            },
            onUninstallClick = {
                showContextMenu = false
                onUninstallClick()
            },
            onDockPin = {
                showContextMenu = false
                onDockPin()
            },
            isPinned = isPinned
        )
    }
}

// =============================================================================
// Section headers
// =============================================================================

/**
 * Terminal-style section header for major drawer sections.
 * Styled as a terminal command with a horizontal rule, matching the terminal aesthetic.
 */
@Composable
private fun DrawerSectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Prompt
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
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
// App grid item
// =============================================================================

/**
 * A single app item in the drawer grid: icon (48dp) + label (monospace, 10sp).
 * Supports long-press for a context menu with App Info, Uninstall, and Add to Dock options.
 * Optionally displays a small category badge beneath the app name.
 *
 * @param appInfo The app to display
 * @param onClick Called when the app is tapped (launches the app)
 * @param onInfoClick Called when "App Info" is selected from the context menu
 * @param onUninstallClick Called when "Uninstall" is selected from the context menu
 * @param showCategoryBadge Whether to show the category badge below the app name
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onDockPin: () -> Unit = {},
    isPinned: Boolean = false,
    showCategoryBadge: Boolean = false
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                )
                .padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            // App icon
            AppIconDisplay(appInfo = appInfo)

            Spacer(modifier = Modifier.height(6.dp))

            // App label
            Text(
                text = appInfo.label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    color = TerminalColors.Command
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Category badge
            if (showCategoryBadge) {
                Spacer(modifier = Modifier.height(2.dp))
                CategoryBadge(category = appInfo.category)
            }
        }

        // Long-press context menu
        AppContextMenu(
            expanded = showContextMenu,
            onDismiss = { showContextMenu = false },
            appInfo = appInfo,
            onInfoClick = {
                showContextMenu = false
                onInfoClick()
            },
            onUninstallClick = {
                showContextMenu = false
                onUninstallClick()
            },
            onDockPin = {
                showContextMenu = false
                onDockPin()
            },
            isPinned = isPinned
        )
    }
}

/**
 * App grid item variant for search results with matched character highlighting.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItemWithHighlight(
    searchResult: SearchResult,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onDockPin: () -> Unit = {},
    isPinned: Boolean = false
) {
    val appInfo = searchResult.appInfo
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                )
                .padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            // App icon
            AppIconDisplay(appInfo = appInfo)

            Spacer(modifier = Modifier.height(6.dp))

            // App label with highlighted matched characters
            if (searchResult.matchedIndices.isNotEmpty()) {
                HighlightedText(
                    text = appInfo.label,
                    matchedIndices = searchResult.matchedIndices,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = appInfo.label,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = TerminalColors.Command
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Category badge
            Spacer(modifier = Modifier.height(2.dp))
            CategoryBadge(category = appInfo.category)
        }

        // Long-press context menu
        AppContextMenu(
            expanded = showContextMenu,
            onDismiss = { showContextMenu = false },
            appInfo = appInfo,
            onInfoClick = {
                showContextMenu = false
                onInfoClick()
            },
            onUninstallClick = {
                showContextMenu = false
                onUninstallClick()
            },
            onDockPin = {
                showContextMenu = false
                onDockPin()
            },
            isPinned = isPinned
        )
    }
}

// =============================================================================
// Shared composables
// =============================================================================

/**
 * Displays an app icon or a fallback icon if loading fails.
 */
@Composable
private fun AppIconDisplay(appInfo: AppInfo) {
    if (appInfo.icon != null) {
        val bitmap = remember(appInfo.packageName) {
            try {
                appInfo.icon.toBitmap(128, 128).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = appInfo.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            AppIconFallback()
        }
    } else {
        AppIconFallback()
    }
}

/**
 * Text with specific character indices highlighted in the accent color.
 * Used for fuzzy search result display.
 */
@Composable
private fun HighlightedText(
    text: String,
    matchedIndices: List<Int>,
    modifier: Modifier = Modifier
) {
    val matchedSet = matchedIndices.toSet()
    val annotatedString = buildAnnotatedString {
        text.forEachIndexed { index, char ->
            if (index in matchedSet) {
                withStyle(
                    SpanStyle(
                        color = TerminalColors.Accent,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                ) {
                    append(char)
                }
            } else {
                withStyle(
                    SpanStyle(
                        color = TerminalColors.Command,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                ) {
                    append(char)
                }
            }
        }
    }

    Text(
        text = annotatedString,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/**
 * Small category badge displayed beneath app names.
 * Shows the category's terminal label in a compact chip.
 */
@Composable
private fun CategoryBadge(category: AppCategory) {
    val badgeColor = when (category) {
        AppCategory.SOCIAL -> TerminalColors.Info
        AppCategory.WORK -> TerminalColors.Warning
        AppCategory.MEDIA -> TerminalColors.Accent
        AppCategory.GAMES -> TerminalColors.Success
        AppCategory.UTILITIES -> TerminalColors.Prompt
        AppCategory.SYSTEM -> TerminalColors.Timestamp
        AppCategory.OTHER -> TerminalColors.Subtext
    }

    Text(
        text = category.tabLabel,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            fontWeight = FontWeight.Normal,
            color = badgeColor.copy(alpha = 0.7f)
        ),
        maxLines = 1,
        textAlign = TextAlign.Center
    )
}

/**
 * Fallback icon displayed when an app's drawable cannot be loaded or converted.
 *
 * @param size Icon size in dp. Defaults to 48.
 */
@Composable
private fun AppIconFallback(size: Int = 48) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TerminalColors.Surface)
    ) {
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size((size * 0.58f).dp)
        )
    }
}

/**
 * Shared context menu for app items. Shows App Info, Uninstall (for non-system
 * apps), and Pin to Dock options.
 */
@Composable
private fun AppContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    appInfo: AppInfo,
    onInfoClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onDockPin: () -> Unit = {},
    isPinned: Boolean = false
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 0.dp),
        modifier = Modifier.background(TerminalColors.Surface)
    ) {
        // App Info
        DropdownMenuItem(
            text = {
                ContextMenuItemContent(
                    icon = Icons.Default.Info,
                    label = "$ app-info"
                )
            },
            onClick = onInfoClick
        )

        // Uninstall (only for non-system apps)
        if (!appInfo.isSystemApp) {
            DropdownMenuItem(
                text = {
                    ContextMenuItemContent(
                        icon = Icons.Default.Delete,
                        label = "$ apt remove",
                        color = TerminalColors.Error
                    )
                },
                onClick = onUninstallClick
            )
        }

        // Pin / Unpin from Dock
        DropdownMenuItem(
            text = {
                ContextMenuItemContent(
                    icon = Icons.Default.PushPin,
                    label = if (isPinned) "$ unpin-from-dock" else "$ pin-to-dock",
                    color = if (isPinned) TerminalColors.Warning else TerminalColors.Command
                )
            },
            onClick = onDockPin
        )
    }
}

/**
 * Row layout for context menu items: icon + terminal-style command label.
 */
@Composable
private fun ContextMenuItemContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color = TerminalColors.Command
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = color
            )
        )
    }
}
