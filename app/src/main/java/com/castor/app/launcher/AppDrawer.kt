package com.castor.app.launcher

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * search bar at the top and a "Recent" row for frequently/recently used apps.
 * The overlay slides up from the bottom with a dark semi-transparent backdrop,
 * reminiscent of GNOME's Activities overview or Ubuntu's app grid.
 *
 * Features:
 * - Responsive grid: 4 columns on phone, 6 columns on tablet (>600dp width)
 * - Terminal-style search: `$ find /apps -name "..."`
 * - Recent apps section (populated from UsageStatsManager)
 * - Alphabetical section headers for easy browsing
 * - Long-press context menu: App Info, Uninstall, Add to Dock
 * - Smooth slide-up/slide-down animation
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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

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
            recentApps = recentApps,
            searchQuery = searchQuery,
            onSearchChange = { viewModel.searchQuery.value = it },
            onAppClick = { appInfo ->
                viewModel.launchApp(appInfo)
                onDismiss()
            },
            onAppLongClickInfo = { viewModel.openAppInfo(it) },
            onAppLongClickUninstall = { viewModel.uninstallApp(it) },
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
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClickInfo: (AppInfo) -> Unit,
    onAppLongClickUninstall: (AppInfo) -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val columnCount = if (screenWidthDp >= 600) 6 else 4

    // Group apps by first letter for section headers
    val groupedApps = remember(apps) {
        apps.groupBy { app ->
            val firstChar = app.label.firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar.isLetter()) firstChar.toString() else "#"
        }.toSortedMap()
    }

    val showRecent = searchQuery.isBlank() && recentApps.isNotEmpty()

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
                onBack = onBack
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
                            text = "$ find /apps -name \"$searchQuery\"",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalColors.Prompt
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "find: no matches found",
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
                    // ---- Recent apps section ----
                    if (showRecent) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            DrawerSectionHeader(title = "recent")
                        }

                        items(
                            items = recentApps,
                            key = { "recent_${it.packageName}" }
                        ) { app ->
                            AppGridItem(
                                appInfo = app,
                                onClick = { onAppClick(app) },
                                onInfoClick = { onAppLongClickInfo(app) },
                                onUninstallClick = { onAppLongClickUninstall(app) }
                            )
                        }

                        // Spacer between sections
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // ---- All apps with alphabetical section headers ----
                    if (searchQuery.isBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            DrawerSectionHeader(title = "all applications")
                        }
                    }

                    groupedApps.forEach { (letter, appsInSection) ->
                        // Section header
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AlphabetSectionHeader(letter = letter)
                        }

                        items(
                            items = appsInSection,
                            key = { "all_${it.packageName}" }
                        ) { app ->
                            AppGridItem(
                                appInfo = app,
                                onClick = { onAppClick(app) },
                                onInfoClick = { onAppLongClickInfo(app) },
                                onUninstallClick = { onAppLongClickUninstall(app) }
                            )
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
    onBack: () -> Unit
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
                text = "# installed",
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
// Section headers
// =============================================================================

/**
 * Terminal-style section header for major drawer sections (e.g. "recent", "all applications").
 * Styled as `# section_name` with a horizontal rule, matching the HomeScreen pattern.
 */
@Composable
private fun DrawerSectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
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

/**
 * Small alphabetical section header shown before each letter group in the grid.
 * Styled as `-- A --` in a subtle monospace divider.
 */
@Composable
private fun AlphabetSectionHeader(letter: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(TerminalColors.Selection)
        )
        Text(
            text = " $letter ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Subtext
            )
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(TerminalColors.Selection)
        )
    }
}

// =============================================================================
// App grid item
// =============================================================================

/**
 * A single app item in the drawer grid: icon (48dp) + label (monospace, 10sp).
 * Supports long-press for a context menu with App Info, Uninstall, and Add to Dock options.
 *
 * @param appInfo The app to display
 * @param onClick Called when the app is tapped (launches the app)
 * @param onInfoClick Called when "App Info" is selected from the context menu
 * @param onUninstallClick Called when "Uninstall" is selected from the context menu
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    onUninstallClick: () -> Unit
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
        }

        // Long-press context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = DpOffset(0.dp, 0.dp),
            modifier = Modifier
                .background(TerminalColors.Surface)
        ) {
            // App Info
            DropdownMenuItem(
                text = {
                    ContextMenuItemContent(
                        icon = Icons.Default.Info,
                        label = "$ app-info"
                    )
                },
                onClick = {
                    showContextMenu = false
                    onInfoClick()
                }
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
                    onClick = {
                        showContextMenu = false
                        onUninstallClick()
                    }
                )
            }

            // Add to Dock
            DropdownMenuItem(
                text = {
                    ContextMenuItemContent(
                        icon = Icons.Default.PushPin,
                        label = "$ pin-to-dock"
                    )
                },
                onClick = {
                    showContextMenu = false
                    // TODO: Implement dock pinning in future phase
                }
            )
        }
    }
}

/**
 * Fallback icon displayed when an app's drawable cannot be loaded or converted.
 */
@Composable
private fun AppIconFallback() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TerminalColors.Surface)
    ) {
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(28.dp)
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
