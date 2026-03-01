package com.castor.app.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.castor.core.ui.theme.TerminalColors
import kotlinx.coroutines.launch

/**
 * Full-screen first-launch onboarding wizard for the Un-Dios launcher.
 *
 * Guides the user through a series of permission grants and system access
 * requests required for full launcher functionality. Each step is a full-screen
 * page within a [HorizontalPager] with swipe and button navigation.
 *
 * Steps:
 * 0. Welcome
 * 1. Set as default launcher
 * 2. Notification access
 * 3. Accessibility service
 * 4. Storage access
 * 5. Battery optimization
 * 6. Calendar access
 * 7. Feature walkthrough
 * 8. Setup complete summary
 * All text uses [FontFamily.Monospace] and colors from [TerminalColors] to
 * maintain the Catppuccin Mocha terminal aesthetic.
 *
 * @param onComplete Callback invoked when the user finishes onboarding and
 *   taps the launch button. The caller should navigate to the home screen.
 * @param viewModel The [OnboardingViewModel] managing state and permission checks.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val permissionStatuses by viewModel.permissionStatuses.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Re-check permissions whenever the screen resumes (e.g., returning from settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = currentStep,
        pageCount = { viewModel.totalSteps }
    )

    // Sync pager with ViewModel state
    DisposableEffect(currentStep) {
        coroutineScope.launch {
            if (pagerState.currentPage != currentStep) {
                pagerState.animateScrollToPage(currentStep)
            }
        }
        onDispose { }
    }

    // Sync ViewModel with pager swipes
    DisposableEffect(pagerState.currentPage) {
        viewModel.goToStep(pagerState.currentPage)
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Terminal-style progress bar at top
        OnboardingProgressBar(
            currentStep = pagerState.currentPage,
            totalSteps = viewModel.totalSteps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )

        // Main pager content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> WelcomeStep(
                    onBegin = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(1)
                        }
                    }
                )
                1 -> DefaultLauncherStep(
                    isGranted = permissionStatuses.isDefaultLauncher,
                    onAction = {
                        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                        context.startActivity(intent)
                    },
                    onSkip = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(2)
                        }
                    },
                    onNext = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(2)
                        }
                    }
                )
                2 -> NotificationAccessStep(
                    isGranted = permissionStatuses.isNotificationAccessGranted,
                    onAction = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    },
                    onSkip = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(3)
                        }
                    },
                    onNext = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(3)
                        }
                    }
                )
                3 -> AccessibilityStep(
                    isGranted = permissionStatuses.isAccessibilityServiceEnabled,
                    onAction = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    onSkip = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(4)
                        }
                    },
                    onNext = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(4)
                        }
                    }
                )
                4 -> StorageAccessStep(
                    isGranted = permissionStatuses.isStorageGranted,
                    onSkip = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(5)
                        }
                    },
                    onNext = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(5)
                        }
                    },
                    onPermissionResult = {
                        viewModel.refreshPermissionStatuses()
                    }
                )
                5 -> BatteryOptimizationStep(
                    isGranted = permissionStatuses.isBatteryOptimizationDisabled,
                    manufacturer = viewModel.getManufacturer(),
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    onSkip = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(6)
                        }
                    },
                    onNext = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(6)
                        }
                    }
                )
                6 -> CalendarAccessStep(
                    isGranted = permissionStatuses.isCalendarGranted,
                    onSkip = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(7)
                        }
                    },
                    onNext = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(7)
                        }
                    },
                    onPermissionResult = {
                        viewModel.refreshPermissionStatuses()
                    }
                )
                7 -> FeatureWalkthroughStep(
                    onContinue = {
                        coroutineScope.launch {
                            viewModel.nextStep()
                            pagerState.animateScrollToPage(8)
                        }
                    }
                )
                8 -> SetupCompleteStep(
                    permissionStatuses = permissionStatuses,
                    onLaunch = {
                        viewModel.completeOnboarding()
                        onComplete()
                    }
                )
            }
        }
    }
}

// =============================================================================
// Progress Bar
// =============================================================================

/**
 * Terminal-style progress bar rendered as `[████████░░░░░░░░]` with step count.
 *
 * Animates smoothly between steps.
 *
 * @param currentStep The zero-based index of the current step.
 * @param totalSteps The total number of steps in the wizard.
 * @param modifier Modifier for layout customization.
 */
@Composable
private fun OnboardingProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = (currentStep + 1).toFloat() / totalSteps.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progress"
    )

    Column(modifier = modifier) {
        // Step counter
        Text(
            text = "step ${currentStep + 1}/$totalSteps",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalColors.Timestamp
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Progress bar track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TerminalColors.Surface)
        ) {
            // Filled portion
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(TerminalColors.Accent)
            )
        }
    }
}

// =============================================================================
// Step 0 — Welcome
// =============================================================================

/**
 * Welcome screen introducing the Un-Dios launcher.
 *
 * Displays the brand name, tagline, and a brief description of the platform.
 * The begin button initiates the onboarding wizard.
 *
 * @param onBegin Callback when the user taps the begin setup button.
 */
@Composable
private fun WelcomeStep(onBegin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Brand name
        Text(
            text = "un-dios",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Accent,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tagline
        Text(
            text = "$ echo 'your phone, your rules'",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = TerminalColors.Prompt,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Description
        Text(
            text = "Open convergence platform for Android.\nPrivacy-first, local-first, fully on-device.",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TerminalColors.Output,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        // Begin button
        TerminalButton(
            text = "$ begin-setup",
            onClick = onBegin,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(48.dp))
    }
}

// =============================================================================
// Step 1 — Default Launcher
// =============================================================================

/**
 * Step guiding the user to set Un-Dios as the default home launcher.
 *
 * @param isGranted Whether Un-Dios is already the default launcher.
 * @param onAction Callback to open the home settings screen.
 * @param onSkip Callback when the user skips this step.
 * @param onNext Callback to advance to the next step.
 */
@Composable
private fun DefaultLauncherStep(
    isGranted: Boolean,
    onAction: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    PermissionStepLayout(
        icon = Icons.Default.Dashboard,
        title = "# default-launcher",
        description = "Set Un-Dios as your home screen replacement. " +
            "You can always change this back in Android Settings.",
        isGranted = isGranted,
        grantedText = "Un-Dios is your default launcher",
        buttonText = "$ set-default-home",
        onAction = onAction,
        onSkip = onSkip,
        onNext = onNext,
        warningText = null,
        noteText = null,
        showSkip = true
    )
}

// =============================================================================
// Step 2 — Notification Access
// =============================================================================

/**
 * Step guiding the user to grant notification listener access.
 *
 * Required for the unified messaging inbox and smart replies.
 *
 * @param isGranted Whether notification access is already granted.
 * @param onAction Callback to open notification listener settings.
 * @param onSkip Callback when the user skips this step.
 * @param onNext Callback to advance to the next step.
 */
@Composable
private fun NotificationAccessStep(
    isGranted: Boolean,
    onAction: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    PermissionStepLayout(
        icon = Icons.Default.Notifications,
        title = "# notification-access",
        description = "Allow Un-Dios to read notifications from WhatsApp, " +
            "Teams, and other apps. This powers the unified messaging inbox " +
            "and smart replies.",
        isGranted = isGranted,
        grantedText = "Notification access granted",
        buttonText = "$ grant-access",
        onAction = onAction,
        onSkip = onSkip,
        onNext = onNext,
        warningText = "Required for messaging agent",
        noteText = null,
        showSkip = true
    )
}

// =============================================================================
// Step 3 — Accessibility Service
// =============================================================================

/**
 * Step guiding the user to enable the accessibility service for Kindle/Audible tracking.
 *
 * This is optional and only needed for book sync features.
 *
 * @param isGranted Whether the accessibility service is already enabled.
 * @param onAction Callback to open accessibility settings.
 * @param onSkip Callback when the user skips this step.
 * @param onNext Callback to advance to the next step.
 */
@Composable
private fun AccessibilityStep(
    isGranted: Boolean,
    onAction: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    PermissionStepLayout(
        icon = Icons.Default.Accessibility,
        title = "# accessibility-service",
        description = "Enable the Un-Dios accessibility service for Kindle " +
            "and Audible reading position tracking. This ONLY monitors " +
            "reading apps \u2014 no other data is collected.",
        isGranted = isGranted,
        grantedText = "Accessibility service enabled",
        buttonText = "$ enable-service",
        onAction = onAction,
        onSkip = onSkip,
        onNext = onNext,
        warningText = null,
        noteText = "Optional \u2014 only needed for book sync",
        showSkip = true
    )
}

// =============================================================================
// Step 4 — Storage Access
// =============================================================================

/**
 * Step guiding the user to grant storage access for the file manager.
 *
 * On Android 11+, opens the MANAGE_ALL_FILES settings. On Android 10,
 * requests READ_EXTERNAL_STORAGE via runtime permission.
 *
 * @param isGranted Whether storage access is already granted.
 * @param onSkip Callback when the user skips this step.
 * @param onNext Callback to advance to the next step.
 * @param onPermissionResult Callback when a runtime permission result is received.
 */
@Composable
private fun StorageAccessStep(
    isGranted: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onPermissionResult: () -> Unit
) {
    val context = LocalContext.current

    // Launcher for Android 11+ MANAGE_ALL_FILES_ACCESS settings
    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onPermissionResult()
    }

    // Launcher for legacy runtime permission (API 29)
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onPermissionResult()
    }

    val onAction: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            manageStorageLauncher.launch(intent)
        } else {
            legacyPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    PermissionStepLayout(
        icon = Icons.Default.Folder,
        title = "# storage-access",
        description = "Grant storage access for the file manager to browse " +
            "and manage your files.",
        isGranted = isGranted,
        grantedText = "Storage access granted",
        buttonText = "$ grant-storage",
        onAction = onAction,
        onSkip = onSkip,
        onNext = onNext,
        warningText = null,
        noteText = null,
        showSkip = true
    )
}

// =============================================================================
// Step 5 — Battery Optimization
// =============================================================================

/**
 * Step guiding the user to disable battery optimization for background agents.
 *
 * Includes manufacturer-specific tips for Samsung, Xiaomi, OnePlus, Huawei,
 * and other OEMs that aggressively kill background processes.
 *
 * @param isGranted Whether battery optimization is already disabled.
 * @param manufacturer The lowercase device manufacturer name.
 * @param onAction Callback to open battery optimization settings.
 * @param onSkip Callback when the user skips this step.
 * @param onNext Callback to advance to the next step.
 */
@Composable
private fun BatteryOptimizationStep(
    isGranted: Boolean,
    manufacturer: String,
    onAction: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    val oemTip = getManufacturerBatteryTip(manufacturer)

    PermissionStepLayout(
        icon = Icons.Default.BatteryFull,
        title = "# battery-optimization",
        description = "Disable battery optimization for Un-Dios so " +
            "background agents keep running.",
        isGranted = isGranted,
        grantedText = "Battery optimization disabled",
        buttonText = "$ disable-battery-opt",
        onAction = onAction,
        onSkip = onSkip,
        onNext = onNext,
        warningText = null,
        noteText = oemTip,
        showSkip = true
    )
}

/**
 * Returns a manufacturer-specific tip for disabling aggressive battery management.
 *
 * @param manufacturer The lowercase device manufacturer name from [Build.MANUFACTURER].
 * @return A human-readable tip string for the user's device.
 */
private fun getManufacturerBatteryTip(manufacturer: String): String {
    return when {
        manufacturer.contains("samsung") ->
            "Samsung: Also disable 'Put app to sleep' in Device Care > Battery"
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
            "Xiaomi: Enable 'Autostart' and set battery saver to 'No restrictions'"
        manufacturer.contains("oneplus") ->
            "OnePlus: Disable 'Battery optimization' AND 'Adaptive battery'"
        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            "Huawei: Lock the app in recent apps and enable 'App launch > Manage manually'"
        manufacturer.contains("oppo") ->
            "OPPO: Enable 'Autostart' in App Management and disable battery optimization"
        manufacturer.contains("vivo") ->
            "Vivo: Enable 'Autostart' and set 'Background power consumption' to unrestricted"
        manufacturer.contains("realme") ->
            "Realme: Enable 'Autostart' in App Management and disable battery optimization"
        else ->
            "Check dontkillmyapp.com for your device"
    }
}

// =============================================================================
// Step 6 — Calendar Access
// =============================================================================

/**
 * Step guiding the user to grant calendar read/write access for the reminders agent.
 *
 * Uses standard runtime permission requests for [READ_CALENDAR] and [WRITE_CALENDAR].
 *
 * @param isGranted Whether calendar permissions are already granted.
 * @param onSkip Callback when the user skips this step.
 * @param onNext Callback to advance to the next step.
 * @param onPermissionResult Callback when the runtime permission result is received.
 */
@Composable
private fun CalendarAccessStep(
    isGranted: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onPermissionResult: () -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onPermissionResult()
    }

    val onAction: () -> Unit = {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.READ_CALENDAR,
                android.Manifest.permission.WRITE_CALENDAR
            )
        )
    }

    PermissionStepLayout(
        icon = Icons.Default.CalendarMonth,
        title = "# calendar-access",
        description = "Grant calendar access for the reminders agent to " +
            "read and create events.",
        isGranted = isGranted,
        grantedText = "Calendar access granted",
        buttonText = "$ grant-calendar",
        onAction = onAction,
        onSkip = onSkip,
        onNext = onNext,
        warningText = null,
        noteText = null,
        showSkip = true
    )
}

// =============================================================================
// Step 8 — Setup Complete
// =============================================================================

/**
 * Final onboarding step shown after the feature walkthrough, summarizing granted and skipped permissions.
 *
 * Displays a green checkmark or yellow warning for each permission category.
 * The launch button marks onboarding as complete and navigates to the home screen.
 *
 * @param permissionStatuses Current status of all permission checks.
 * @param onLaunch Callback when the user taps the launch button.
 */
@Composable
private fun SetupCompleteStep(
    permissionStatuses: PermissionStatuses,
    onLaunch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Large checkmark
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Setup complete",
            tint = TerminalColors.Success,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = "# setup-complete",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Permission summary
        PermissionSummaryRow(
            label = "Default launcher",
            isGranted = permissionStatuses.isDefaultLauncher
        )
        PermissionSummaryRow(
            label = "Notification access",
            isGranted = permissionStatuses.isNotificationAccessGranted
        )
        PermissionSummaryRow(
            label = "Accessibility service",
            isGranted = permissionStatuses.isAccessibilityServiceEnabled
        )
        PermissionSummaryRow(
            label = "Storage access",
            isGranted = permissionStatuses.isStorageGranted
        )
        PermissionSummaryRow(
            label = "Battery optimization",
            isGranted = permissionStatuses.isBatteryOptimizationDisabled
        )
        PermissionSummaryRow(
            label = "Calendar access",
            isGranted = permissionStatuses.isCalendarGranted
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Hint
        Text(
            text = "You can reopen the step-by-step walkthrough anytime in Settings.",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TerminalColors.Timestamp,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Launch button
        TerminalButton(
            text = "$ open-home",
            onClick = onLaunch,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * A single row in the setup complete summary showing permission status.
 *
 * @param label Human-readable permission name.
 * @param isGranted Whether the permission is currently granted.
 */
@Composable
private fun PermissionSummaryRow(
    label: String,
    isGranted: Boolean
) {
    val iconColor by animateColorAsState(
        targetValue = if (isGranted) TerminalColors.Success else TerminalColors.Warning,
        animationSpec = tween(300),
        label = "statusColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = if (isGranted) "Granted" else "Not granted",
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = if (isGranted) TerminalColors.Command else TerminalColors.Timestamp
            )
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (isGranted) "granted" else "skipped",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = iconColor
            )
        )
    }
}

// =============================================================================
// Shared Step Layout
// =============================================================================

/**
 * Reusable layout for permission-granting steps (Steps 1-6).
 *
 * Provides a consistent structure: icon, title, description, status indicator,
 * action button, optional warning/note text, and a skip option.
 *
 * @param icon The Material icon displayed at the top of the step.
 * @param title The step title (displayed in terminal heading style).
 * @param description The step description explaining why the permission is needed.
 * @param isGranted Whether the permission is already granted.
 * @param grantedText The text shown when the permission is already granted.
 * @param buttonText The action button label in terminal command style.
 * @param onAction Callback when the user taps the action button.
 * @param onSkip Callback when the user taps the skip option.
 * @param onNext Callback to advance to the next step (used when already granted).
 * @param warningText Optional warning text shown below the description.
 * @param noteText Optional note text shown below the action button.
 * @param showSkip Whether to show the skip option.
 */
@Composable
private fun PermissionStepLayout(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    grantedText: String,
    buttonText: String,
    onAction: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    warningText: String?,
    noteText: String?,
    showSkip: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Icon
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = TerminalColors.Accent,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = title,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Command,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Description
        Text(
            text = description,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = TerminalColors.Output,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        )

        // Warning text
        if (warningText != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = TerminalColors.Warning,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = warningText,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalColors.Warning
                    )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Status indicator
        AnimatedVisibility(
            visible = isGranted,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Granted",
                    tint = TerminalColors.Success,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = grantedText,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = TerminalColors.Success
                    )
                )
            }
        }

        // Action button or Next button
        if (isGranted) {
            TerminalButton(
                text = "$ next",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            TerminalButton(
                text = buttonText,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Note text
        if (noteText != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = noteText,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalColors.Timestamp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Skip option
        if (showSkip && !isGranted) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "skip >>",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalColors.Subtext
                ),
                modifier = Modifier
                    .clickable(onClick = onSkip)
                    .padding(vertical = 8.dp, horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

// =============================================================================
// Shared UI Components
// =============================================================================

/**
 * A styled button matching the terminal aesthetic of the Un-Dios launcher.
 *
 * Renders as a filled button with the accent color background and dark text,
 * using monospace font to appear as a terminal command prompt.
 *
 * @param text The button label (should be formatted as a terminal command, e.g., "$ do-thing").
 * @param onClick Callback when the button is tapped.
 * @param modifier Modifier for layout customization.
 */
@Composable
private fun TerminalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TerminalColors.Accent,
            contentColor = TerminalColors.Background
        )
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalColors.Background
            )
        )
    }
}
