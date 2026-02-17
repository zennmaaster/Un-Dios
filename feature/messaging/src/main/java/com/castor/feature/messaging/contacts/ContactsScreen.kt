package com.castor.feature.messaging.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.castor.core.ui.theme.TerminalColors
import com.castor.core.ui.theme.TeamsBlue
import com.castor.core.ui.theme.WhatsAppGreen
import kotlinx.coroutines.launch

/**
 * Contacts Hub screen — a terminal-styled contact browser and action launcher.
 *
 * Visual structure (top to bottom):
 * ┌──────────────────────────────────────────────┐
 * │  $ cat /etc/contacts          [search] [back]│  ← TopAppBar
 * ├──────────────────────────────────────────────┤
 * │  $ grep -i "query" /etc/contacts             │  ← Search bar (animated)
 * ├──────────────────────────────────────────────┤
 * │  # favorites                                 │  ← Section header
 * │  [avatar] [avatar] [avatar] ...  (horiz row) │  ← Favorites row
 * ├──────────────────────────────────────────────┤
 * │  # all-contacts (N entries)                  │  ← Section header
 * │  ┌─ A ────────────────────────────────────┐  │
 * │  │ [A] Alice Johnson    +1-555-0101  ● ●  │  │  ← Contact row
 * │  │ [A] Amit Patel       +1-555-0102  ●    │  │
 * │  ├─ B ────────────────────────────────────┤  │
 * │  │ [B] Bob Smith        +1-555-0201       │  │
 * │  └────────────────────────────────────────┘  │
 * ├──────────────────────────────────────────────┤
 * │                              [$ useradd] FAB │  ← Floating action button
 * └──────────────────────────────────────────────┘
 *
 * Tapping a contact opens a bottom sheet with terminal-command actions:
 *   $ call <name>            → ACTION_DIAL intent
 *   $ msg <name> --whatsapp  → WhatsApp deep link
 *   $ msg <name> --teams     → Teams deep link
 *   $ info <name>            → System contact detail view
 *
 * Permission model:
 *   READ_CONTACTS is requested at runtime via the Activity Result API.
 *   If denied, an empty state with a grant-permission prompt is shown.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── State collection ────────────────────────────────────────────────
    val filteredContacts by viewModel.filteredContacts.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val contactCount by viewModel.contactCount.collectAsState()

    // ── Local UI state ──────────────────────────────────────────────────
    var showSearch by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // ── Permission launcher ─────────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    // Request permission and load contacts on first composition
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onPermissionGranted()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    // ── Group contacts by first letter for alphabetical headers ─────────
    val groupedContacts = remember(filteredContacts) {
        filteredContacts.groupBy { contact ->
            val firstChar = contact.name.firstOrNull()?.uppercase() ?: "#"
            if (firstChar.first().isLetter()) firstChar else "#"
        }.toSortedMap()
    }

    Scaffold(
        topBar = {
            ContactsTopBar(
                showSearch = showSearch,
                searchQuery = searchQuery,
                onBack = onBack,
                onToggleSearch = { showSearch = !showSearch },
                onSearchQueryChange = { viewModel.updateSearchQuery(it) }
            )
        },
        floatingActionButton = {
            if (hasPermission) {
                FloatingActionButton(
                    onClick = {
                        val intent = viewModel.buildAddContactIntent()
                        context.startActivity(intent)
                    },
                    containerColor = TerminalColors.Accent,
                    contentColor = TerminalColors.Background,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add contact",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$ useradd",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        },
        containerColor = TerminalColors.Background
    ) { padding ->
        if (!hasPermission && !isLoading) {
            // ── Empty / permission-denied state ─────────────────────────
            EmptyContactsState(
                error = error,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            // ── Main contacts content ───────────────────────────────────
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(TerminalColors.Background)
            ) {
                // ── Loading indicator ───────────────────────────────────
                if (isLoading) {
                    item(key = "loading") {
                        LoadingIndicator()
                    }
                }

                // ── Favorites section ───────────────────────────────────
                if (favorites.isNotEmpty()) {
                    item(key = "favorites_header") {
                        TerminalSectionHeader(
                            title = "favorites",
                            count = favorites.size
                        )
                    }

                    item(key = "favorites_row") {
                        FavoritesRow(
                            favorites = favorites,
                            onContactClick = { selectedContact = it }
                        )
                    }

                    item(key = "favorites_divider") {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // ── All contacts section header ─────────────────────────
                item(key = "all_contacts_header") {
                    TerminalSectionHeader(
                        title = "all-contacts",
                        count = filteredContacts.size,
                        sortMode = sortMode
                    )
                }

                // ── Sort mode selector ──────────────────────────────────
                item(key = "sort_bar") {
                    SortModeBar(
                        currentMode = sortMode,
                        onModeChange = { viewModel.setSortMode(it) }
                    )
                }

                // ── Grouped alphabetical contacts ───────────────────────
                if (filteredContacts.isEmpty() && !isLoading) {
                    item(key = "no_results") {
                        NoResultsState(searchQuery = searchQuery)
                    }
                } else {
                    groupedContacts.forEach { (letter, contacts) ->
                        // Alphabetical sticky header
                        stickyHeader(key = "header_$letter") {
                            AlphabetHeader(letter = letter)
                        }

                        items(
                            items = contacts,
                            key = { "contact_${it.id}" }
                        ) { contact ->
                            ContactRow(
                                contact = contact,
                                onClick = { selectedContact = contact }
                            )
                            HorizontalDivider(
                                color = TerminalColors.Surface.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 60.dp)
                            )
                        }
                    }
                }

                // ── Footer status line ──────────────────────────────────
                item(key = "footer") {
                    FooterStatusLine(
                        totalContacts = contactCount,
                        displayedContacts = filteredContacts.size,
                        searchQuery = searchQuery
                    )
                }
            }
        }
    }

    // ── Contact action bottom sheet ─────────────────────────────────────
    if (selectedContact != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedContact = null },
            sheetState = bottomSheetState,
            containerColor = TerminalColors.Surface,
            contentColor = TerminalColors.Command,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            ContactActionSheet(
                contact = selectedContact!!,
                onDial = { contact ->
                    viewModel.buildDialIntent(contact)?.let { intent ->
                        context.startActivity(intent)
                    }
                    scope.launch {
                        bottomSheetState.hide()
                        selectedContact = null
                    }
                },
                onWhatsApp = { contact ->
                    viewModel.buildWhatsAppIntent(contact)?.let { intent ->
                        safeStartActivity(context, intent)
                    }
                    scope.launch {
                        bottomSheetState.hide()
                        selectedContact = null
                    }
                },
                onTeams = { contact ->
                    viewModel.buildTeamsIntent(contact)?.let { intent ->
                        safeStartActivity(context, intent)
                    }
                    scope.launch {
                        bottomSheetState.hide()
                        selectedContact = null
                    }
                },
                onInfo = { contact ->
                    val intent = viewModel.buildContactInfoIntent(contact)
                    context.startActivity(intent)
                    scope.launch {
                        bottomSheetState.hide()
                        selectedContact = null
                    }
                }
            )
        }
    }
}

// ── Top Bar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsTopBar(
    showSearch: Boolean,
    searchQuery: String,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    Column(modifier = Modifier.background(TerminalColors.Background)) {
        TopAppBar(
            title = {
                Text(
                    text = "$ cat /etc/contacts",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Prompt
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
            actions = {
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        if (showSearch) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (showSearch) "Close search" else "Search contacts",
                        tint = TerminalColors.Command
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TerminalColors.Background
            )
        )

        // Animated search bar
        AnimatedVisibility(
            visible = showSearch,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "$ grep -i \"...\" /etc/contacts",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TerminalColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalColors.Command
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TerminalColors.Accent,
                    unfocusedBorderColor = TerminalColors.Surface,
                    cursorColor = TerminalColors.Cursor,
                    focusedContainerColor = TerminalColors.Surface.copy(alpha = 0.5f),
                    unfocusedContainerColor = TerminalColors.Surface.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

// ── Section Headers ─────────────────────────────────────────────────────────

@Composable
private fun TerminalSectionHeader(
    title: String,
    count: Int,
    sortMode: ContactSortMode? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
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
        Text(
            text = " ($count entries)",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp.copy(alpha = 0.6f)
            )
        )
        if (sortMode != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = sortMode.flag,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalColors.Info.copy(alpha = 0.5f)
                )
            )
        }
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

@Composable
private fun AlphabetHeader(letter: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalColors.Background)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "--- ",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Subtext
                )
            )
            Text(
                text = letter,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Accent
                )
            )
            Text(
                text = " ",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Subtext
                )
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(TerminalColors.Surface.copy(alpha = 0.3f))
            )
        }
    }
}

// ── Favorites Row ───────────────────────────────────────────────────────────

@Composable
private fun FavoritesRow(
    favorites: List<Contact>,
    onContactClick: (Contact) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = favorites,
            key = { "fav_${it.id}" }
        ) { contact ->
            FavoriteContactChip(
                contact = contact,
                onClick = { onContactClick(contact) }
            )
        }
    }
}

@Composable
private fun FavoriteContactChip(
    contact: Contact,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .width(64.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            ContactAvatar(
                contact = contact,
                size = 52
            )
            // Favorite star indicator
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorite",
                tint = TerminalColors.Warning,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(TerminalColors.Background)
                    .padding(1.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = contact.name.split(" ").first(),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TerminalColors.Command
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ── Contact Row ─────────────────────────────────────────────────────────────

@Composable
private fun ContactRow(
    contact: Contact,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(TerminalColors.Background)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Avatar
        ContactAvatar(contact = contact, size = 40)

        Spacer(modifier = Modifier.width(12.dp))

        // Name + phone
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.name,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TerminalColors.Command
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (contact.isFavorite) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorite",
                        tint = TerminalColors.Warning,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            if (contact.phoneNumber != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.phoneNumber,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalColors.Timestamp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Source badges (WhatsApp / Teams dots)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (contact.hasWhatsApp) {
                SourceDot(color = WhatsAppGreen, label = "WhatsApp")
            }
            if (contact.hasTeams) {
                SourceDot(color = TeamsBlue, label = "Teams")
            }
        }
    }
}

@Composable
private fun SourceDot(color: Color, label: String) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Circle,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(8.dp)
        )
    }
}

// ── Contact Avatar ──────────────────────────────────────────────────────────

/** Palette of Catppuccin-inspired avatar background colors. */
private val avatarColors = listOf(
    Color(0xFFF38BA8), // Red
    Color(0xFFFAB387), // Peach
    Color(0xFFF9E2AF), // Yellow
    Color(0xFFA6E3A1), // Green
    Color(0xFF89DCEB), // Teal
    Color(0xFF89B4FA), // Blue
    Color(0xFFCBA6F7), // Mauve
    Color(0xFFF5C2E7), // Pink
)

@Composable
private fun ContactAvatar(
    contact: Contact,
    size: Int
) {
    val bgColor = avatarColors[contact.colorIndex]

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (contact.photoUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(contact.photoUri)
                    .crossfade(true)
                    .build(),
                contentDescription = contact.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape)
            )
        } else {
            Text(
                text = contact.initial,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = (size / 2.5).sp,
                    fontWeight = FontWeight.Bold,
                    color = bgColor
                )
            )
        }
    }
}

// ── Sort Mode Bar ───────────────────────────────────────────────────────────

@Composable
private fun SortModeBar(
    currentMode: ContactSortMode,
    onModeChange: (ContactSortMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ContactSortMode.entries.forEach { mode ->
            val isSelected = mode == currentMode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isSelected) TerminalColors.Accent.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .clickable { onModeChange(mode) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = mode.flag,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) TerminalColors.Accent
                        else TerminalColors.Timestamp
                    )
                )
            }
        }
    }
}

// ── Contact Action Bottom Sheet ─────────────────────────────────────────────

@Composable
private fun ContactActionSheet(
    contact: Contact,
    onDial: (Contact) -> Unit,
    onWhatsApp: (Contact) -> Unit,
    onTeams: (Contact) -> Unit,
    onInfo: (Contact) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // Contact header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            ContactAvatar(contact = contact, size = 48)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = contact.name,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Command
                    )
                )
                if (contact.phoneNumber != null) {
                    Text(
                        text = contact.phoneNumber,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TerminalColors.Timestamp
                        )
                    )
                }
            }
        }

        HorizontalDivider(
            color = TerminalColors.Background.copy(alpha = 0.5f),
            thickness = 1.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Terminal prompt line
        Text(
            text = "castor@contacts:~\$ ",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Prompt.copy(alpha = 0.5f)
            ),
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Action: Call
        if (contact.phoneNumber != null) {
            ActionRow(
                command = "$ call ${contact.name.split(" ").first().lowercase()}",
                description = "Dial ${contact.phoneNumber}",
                icon = Icons.Default.Call,
                accentColor = TerminalColors.Success,
                onClick = { onDial(contact) }
            )
        }

        // Action: WhatsApp
        if (contact.phoneNumber != null) {
            ActionRow(
                command = "$ msg ${contact.name.split(" ").first().lowercase()} --whatsapp",
                description = "Open WhatsApp conversation",
                icon = Icons.Default.Message,
                accentColor = WhatsAppGreen,
                onClick = { onWhatsApp(contact) }
            )
        }

        // Action: Teams
        if (contact.phoneNumber != null) {
            ActionRow(
                command = "$ msg ${contact.name.split(" ").first().lowercase()} --teams",
                description = "Open Teams chat",
                icon = Icons.Default.Message,
                accentColor = TeamsBlue,
                onClick = { onTeams(contact) }
            )
        }

        // Action: Contact info
        ActionRow(
            command = "$ info ${contact.name.split(" ").first().lowercase()}",
            description = "View in system contacts",
            icon = Icons.Default.Info,
            accentColor = TerminalColors.Info,
            onClick = { onInfo(contact) }
        )
    }
}

@Composable
private fun ActionRow(
    command: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = command,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalColors.Prompt
                )
            )
            Text(
                text = description,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Timestamp
                )
            )
        }
    }
}

// ── Loading Indicator ───────────────────────────────────────────────────────

@Composable
private fun LoadingIndicator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        Text(
            text = "$ cat /etc/contacts | sort",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TerminalColors.Prompt
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Loading contacts...",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

// ── Empty / Error States ────────────────────────────────────────────────────

@Composable
private fun EmptyContactsState(
    error: String?,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(TerminalColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = TerminalColors.Timestamp,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$ cat /etc/contacts",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalColors.Prompt
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "cat: /etc/contacts: Permission denied",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Error
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "(empty) -- grant READ_CONTACTS to populate",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalColors.Timestamp
                )
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "stderr: $error",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TerminalColors.Error.copy(alpha = 0.7f)
                    ),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalColors.Accent.copy(alpha = 0.15f))
                    .clickable(onClick = onRequestPermission)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "$ sudo chmod +r /etc/contacts",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Accent
                    )
                )
            }
        }
    }
}

@Composable
private fun NoResultsState(searchQuery: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        Text(
            text = "$ grep -i \"$searchQuery\" /etc/contacts",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TerminalColors.Prompt
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "0 matches found.",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Timestamp
            )
        )
    }
}

// ── Footer Status Line ──────────────────────────────────────────────────────

@Composable
private fun FooterStatusLine(
    totalContacts: Int,
    displayedContacts: Int,
    searchQuery: String
) {
    val statusText = if (searchQuery.isBlank()) {
        "-- $totalContacts contacts loaded from /etc/contacts --"
    } else {
        "-- $displayedContacts/$totalContacts contacts matching \"$searchQuery\" --"
    }
    Text(
        text = statusText,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = TerminalColors.Subtext
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 16.dp)
    )
}

// ── Utility ─────────────────────────────────────────────────────────────────

/**
 * Safely start an activity, catching ActivityNotFoundException
 * when the target app (WhatsApp, Teams) is not installed.
 */
private fun safeStartActivity(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        // Target app not installed — fall back to opening the URL in a browser
        val fallbackIntent = Intent(Intent.ACTION_VIEW, intent.data)
        try {
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
            // No handler available — silently fail
        }
    }
}
