package com.castor.app.desktop.packagemanager

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.castor.app.desktop.integration.AppLauncher
import com.castor.core.ui.theme.TerminalColors

/**
 * GNOME Software-style package manager screen for the desktop environment.
 *
 * A full-screen composable designed to run inside a desktop window, providing
 * an apt/dpkg-style interface for browsing, searching, and managing installed
 * applications. The UI follows the terminal aesthetic with monospace fonts,
 * dark surfaces, and `$ command`-style labels.
 *
 * Layout (top to bottom):
 * 1. Header with title and tab controls (Categories / Installed)
 * 2. Search bar styled as `$ apt search ...`
 * 3. Category grid (when in Categories tab) or app list (when in Installed tab)
 * 4. Sort controls
 * 5. App rows with icon, name, package name, version, size, and quick actions
 *
 * Quick actions per app:
 * - `$ open` — Launch the app
 * - `$ info` — Open system App Info settings
 * - `$ rm` — Uninstall the app
 *
 * @param modifier Modifier for the screen container
 * @param appLauncher The [AppLauncher] for launching apps and opening app info
 * @param viewModel The [PackageManagerViewModel] providing app data
 */
@Composable
fun PackageManagerScreen(
    modifier: Modifier = Modifier,
    appLauncher: AppLauncher,
    viewModel: PackageManagerViewModel = hiltViewModel()
) {
    val filteredApps by viewModel.filteredApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val currentSort by viewModel.sortOrder.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var activeTab by remember { mutableStateOf(PackageManagerTab.CATEGORIES) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .padding(12.dp)
    ) {
        // ---- Header ----
        PackageManagerHeader(
            activeTab = activeTab,
            onTabChange = { tab ->
                activeTab = tab
                if (tab == PackageManagerTab.CATEGORIES) {
                    viewModel.filterByCategory(null)
                    viewModel.searchApps("")
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ---- Search bar ----
        PackageManagerSearchBar(
            query = searchQuery,
            onQueryChange = { viewModel.searchApps(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isLoading -> {
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
                            text = "$ dpkg --list ...",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                    }
                }
            }

            activeTab == PackageManagerTab.CATEGORIES && selectedCategory == null && searchQuery.isBlank() -> {
                // Categories grid
                CategoryGrid(
                    categories = viewModel.categories,
                    categoryCounts = viewModel.getCategoryCounts(),
                    onCategoryClick = { categoryId ->
                        viewModel.filterByCategory(categoryId)
                        activeTab = PackageManagerTab.INSTALLED
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            else -> {
                // Sort controls
                SortControls(
                    currentSort = currentSort,
                    selectedCategory = selectedCategory,
                    categoryName = viewModel.categories.find { it.id == selectedCategory }?.name,
                    appCount = filteredApps.size,
                    onSortChange = { viewModel.sortBy(it) },
                    onClearCategory = { viewModel.filterByCategory(null) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // App list
                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "apt: no packages found",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalColors.Error
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        items(
                            items = filteredApps,
                            key = { it.packageName }
                        ) { app ->
                            AppRow(
                                app = app,
                                onOpen = { appLauncher.launchApp(app.packageName) },
                                onInfo = { appLauncher.openAppInfo(app.packageName) },
                                onUninstall = { appLauncher.uninstallApp(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab options for the package manager screen.
 */
private enum class PackageManagerTab {
    CATEGORIES,
    INSTALLED
}

/**
 * Header bar with the package manager title and tab buttons.
 */
@Composable
private fun PackageManagerHeader(
    activeTab: PackageManagerTab,
    onTabChange: (PackageManagerTab) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "# package-manager",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        // Tab buttons
        TabButton(
            label = "categories",
            isActive = activeTab == PackageManagerTab.CATEGORIES,
            onClick = { onTabChange(PackageManagerTab.CATEGORIES) }
        )

        Spacer(modifier = Modifier.width(8.dp))

        TabButton(
            label = "installed",
            isActive = activeTab == PackageManagerTab.INSTALLED,
            onClick = { onTabChange(PackageManagerTab.INSTALLED) }
        )
    }
}

/**
 * A small terminal-styled tab button.
 */
@Composable
private fun TabButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isActive) TerminalColors.Selection else TerminalColors.Surface
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) TerminalColors.Accent else TerminalColors.Timestamp
            )
        )
    }
}

/**
 * Search bar styled as a terminal apt search command.
 */
@Composable
private fun PackageManagerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = "$ apt search ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Prompt
            )
        )

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Command
            ),
            cursorBrush = SolidColor(TerminalColors.Cursor),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "package-name",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalColors.Timestamp
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = TerminalColors.Timestamp,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TerminalColors.Timestamp,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Grid of category cards for browsing apps by type.
 */
@Composable
private fun CategoryGrid(
    categories: List<AppCategory>,
    categoryCounts: Map<String, Int>,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(
            items = categories,
            key = { it.id }
        ) { category ->
            CategoryCard(
                category = category,
                appCount = categoryCounts[category.id] ?: 0,
                onClick = { onCategoryClick(category.id) }
            )
        }
    }
}

/**
 * A single category card in the category grid.
 *
 * Shows the category icon, name, and app count in a terminal-styled card.
 */
@Composable
private fun CategoryCard(
    category: AppCategory,
    appCount: Int,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalColors.Surface)
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 8.dp)
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = category.name,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = category.name.lowercase(),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command
            )
        )

        Text(
            text = "$appCount pkgs",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

/**
 * Sort controls row showing the active category filter, app count, and sort dropdown.
 */
@Composable
private fun SortControls(
    currentSort: SortOrder,
    selectedCategory: String?,
    categoryName: String?,
    appCount: Int,
    onSortChange: (SortOrder) -> Unit,
    onClearCategory: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Category filter badge
            if (selectedCategory != null && categoryName != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TerminalColors.Selection)
                        .clickable { onClearCategory() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${categoryName.lowercase()} x",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalColors.Accent
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = "$appCount packages",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }

        // Sort dropdown
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { showSortMenu = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "sort: ${currentSort.name.lowercase()}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Timestamp
                    )
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Sort options",
                    tint = TerminalColors.Timestamp,
                    modifier = Modifier.size(14.dp)
                )
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                modifier = Modifier.background(TerminalColors.Surface)
            ) {
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "$ sort --by ${order.name.lowercase()}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (order == currentSort) {
                                        TerminalColors.Accent
                                    } else {
                                        TerminalColors.Command
                                    }
                                )
                            )
                        },
                        onClick = {
                            onSortChange(order)
                            showSortMenu = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * A single app row in the installed apps list.
 *
 * Shows the app icon, name, package name, version, size, and quick action
 * buttons styled as terminal commands.
 */
@Composable
private fun AppRow(
    app: InstalledApp,
    onOpen: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Surface)
            .clickable { onOpen() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // App icon
        AppIconSmall(icon = app.icon, packageName = app.packageName)

        Spacer(modifier = Modifier.width(10.dp))

        // App info text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Command
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = app.packageName,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalColors.Timestamp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row {
                Text(
                    text = "v${app.versionName}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalColors.Subtext
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatSize(app.appSize),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TerminalColors.Subtext
                    )
                )
            }
        }

        // Quick actions
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            QuickActionButton(label = "open", color = TerminalColors.Success, onClick = onOpen)
            QuickActionButton(label = "info", color = TerminalColors.Info, onClick = onInfo)
            if (!app.isSystemApp) {
                QuickActionButton(label = "rm", color = TerminalColors.Error, onClick = onUninstall)
            }
        }
    }
}

/**
 * Small app icon (32dp) used in app rows. Loads the drawable as a bitmap.
 */
@Composable
private fun AppIconSmall(icon: Drawable?, packageName: String) {
    if (icon != null) {
        val bitmap = remember(packageName) {
            try {
                icon.toBitmap(96, 96).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            AppIconFallbackSmall()
        }
    } else {
        AppIconFallbackSmall()
    }
}

/**
 * Fallback icon for apps whose icon cannot be loaded.
 */
@Composable
private fun AppIconFallbackSmall() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalColors.Selection)
    ) {
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = null,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * A small terminal-styled quick action button (e.g., "$ open", "$ rm").
 */
@Composable
private fun QuickActionButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(TerminalColors.Background)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = "$ $label",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        )
    }
}

/**
 * Formats a file size in bytes to a human-readable string (KB, MB, GB).
 */
private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}
