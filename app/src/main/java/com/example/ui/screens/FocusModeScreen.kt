package com.example.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.focus.FocusGuardManager
import com.example.data.focus.InstalledAppInfo
import com.example.data.focus.InstalledAppsRepository
import com.example.data.local.entities.FocusSessionEntity
import com.example.data.wellbeing.DigitalWellbeingManager
import com.example.ui.components.GlowGlassCard
import com.example.ui.theme.AmberGlowPrimary
import com.example.ui.theme.CopperGlowAccent
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.GoldGlowHighlight
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.TabularNumeralStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import com.example.util.SystemBarHelper
import kotlinx.coroutines.delay
import java.util.Locale

import com.example.data.local.entities.ChapterEntity

private enum class RegainTimerStyle(val label: String) {
    TIMER("Timer"),
    STOPWATCH("Stopwatch"),
    POMODORO("Pomodoro")
}

private val FocusEmerald = Color(0xFF10B981)
private val FocusMint = Color(0xFF34D399)
private val FocusDarkSurface = Color(0xFF111827)
private val FocusDarkBorder = Color(0xFF1F2937)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusModeScreen(
    activeSession: FocusSessionEntity?,
    setupDurationMinutes: Int,
    setupExtraSeconds: Int = 0,
    setupPermittedBreaks: Int,
    setupBreakDurationMinutes: Int,
    setupTaskName: String,
    selectedPackages: Set<String>,
    todayStudyMinutes: Int = 62,
    chapters: List<ChapterEntity> = emptyList(),
    isInPipMode: Boolean = false,
    onEnterPipMode: () -> Unit = {},
    onSetSetupDuration: (Int) -> Unit,
    onSetCustomDuration: (Int, Int) -> Unit = { m, _ -> onSetSetupDuration(m) },
    onSetSetupPermittedBreaks: (Int) -> Unit,
    onSetSetupBreakDuration: (Int) -> Unit,
    onSetSetupTaskName: (String) -> Unit,
    onToggleSelectedPackage: (String) -> Unit,
    onStartFocusSession: () -> Unit,
    onTakeBreak: () -> Unit,
    onEndBreak: () -> Unit,
    onPauseSession: () -> Unit,
    onResumeSession: () -> Unit,
    onCancelSession: () -> Unit,
    onCompleteSession: () -> Unit,
    onExitFocusMode: () -> Unit,
    onOpenDigitalWellbeing: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activityWindow = (context as? Activity)?.window
    val lifecycleOwner = LocalLifecycleOwner.current

    var showEndConfirmDialog by remember { mutableStateOf(false) }
    var optInImmersiveFullscreen by remember { mutableStateOf(false) }

    // Live verified permission & service states (re-verified automatically on ON_RESUME)
    var isUsageAccessGranted by remember {
        mutableStateOf(DigitalWellbeingManager.hasUsageStatsPermission(context))
    }
    var isAccessibilityGuardEnabled by remember {
        mutableStateOf(FocusGuardManager.isAccessibilityServiceEnabled(context))
    }
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    // Load real launchable applications from PackageManager
    LaunchedEffect(Unit) {
        isLoadingApps = true
        installedApps = InstalledAppsRepository.getLaunchableApps(context)
        isLoadingApps = false
    }

    // Re-check permissions whenever screen resumes after returning from Android Settings
    DisposableEffect(lifecycleOwner, activityWindow) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isUsageAccessGranted = DigitalWellbeingManager.hasUsageStatsPermission(context)
                isAccessibilityGuardEnabled = FocusGuardManager.isAccessibilityServiceEnabled(context)
                if (!optInImmersiveFullscreen) {
                    SystemBarHelper.ensureNormalSystemBars(activityWindow)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            SystemBarHelper.ensureNormalSystemBars(activityWindow)
        }
    }

    val isActive = activeSession != null &&
            (activeSession.sessionStatus == "RUNNING" ||
             activeSession.sessionStatus == "BREAK" ||
             activeSession.sessionStatus == "PAUSED")

    LaunchedEffect(isActive, activeSession?.sessionStatus, optInImmersiveFullscreen) {
        val shouldUseImmersive = isActive &&
                activeSession?.sessionStatus == "RUNNING" &&
                optInImmersiveFullscreen
        SystemBarHelper.setOptInImmersiveMode(activityWindow, shouldUseImmersive)
    }

    BackHandler {
        if (isActive) {
            showEndConfirmDialog = true
        } else {
            SystemBarHelper.ensureNormalSystemBars(activityWindow)
            onExitFocusMode()
        }
    }

    // Picture-in-Picture (PiP) Compact Floating Timer View (Priority 3)
    if (isInPipMode && isActive && activeSession != null) {
        PipFloatingTimerView(
            session = activeSession,
            onCompleteSession = onCompleteSession,
            onEndBreak = onEndBreak
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("focus_mode_screen")
    ) {
        if (!isActive) {
            FocusSetupView(
                durationMinutes = setupDurationMinutes,
                extraSeconds = setupExtraSeconds,
                permittedBreaks = setupPermittedBreaks,
                breakDurationMinutes = setupBreakDurationMinutes,
                taskName = setupTaskName,
                selectedPackages = selectedPackages,
                todayStudyMinutes = todayStudyMinutes,
                chapters = chapters,
                installedApps = installedApps,
                isLoadingApps = isLoadingApps,
                isUsageAccessGranted = isUsageAccessGranted,
                isAccessibilityGuardEnabled = isAccessibilityGuardEnabled,
                onRefreshPermissions = {
                    isUsageAccessGranted = DigitalWellbeingManager.hasUsageStatsPermission(context)
                    isAccessibilityGuardEnabled = FocusGuardManager.isAccessibilityServiceEnabled(context)
                },
                onOpenUsageAccessSettings = {
                    try {
                        context.startActivity(DigitalWellbeingManager.createUsageAccessSettingsIntent())
                    } catch (_: Exception) {}
                },
                onOpenAccessibilitySettings = {
                    try {
                        context.startActivity(FocusGuardManager.createAccessibilitySettingsIntent())
                    } catch (_: Exception) {}
                },
                onSetDuration = onSetSetupDuration,
                onSetCustomDuration = onSetCustomDuration,
                onSetPermittedBreaks = onSetSetupPermittedBreaks,
                onSetBreakDuration = onSetSetupBreakDuration,
                onSetTaskName = onSetSetupTaskName,
                onTogglePackage = onToggleSelectedPackage,
                onStartSession = onStartFocusSession,
                onOpenDigitalWellbeing = onOpenDigitalWellbeing,
                onExit = {
                    SystemBarHelper.ensureNormalSystemBars(activityWindow)
                    onExitFocusMode()
                }
            )
        } else {
            ActiveFocusLockView(
                session = activeSession!!,
                isAccessibilityGuardEnabled = isAccessibilityGuardEnabled,
                onOpenAccessibilitySettings = {
                    try {
                        context.startActivity(FocusGuardManager.createAccessibilitySettingsIntent())
                    } catch (_: Exception) {}
                },
                optInImmersiveFullscreen = optInImmersiveFullscreen,
                onToggleImmersiveFullscreen = { enabled ->
                    optInImmersiveFullscreen = enabled
                },
                onEnterPipMode = onEnterPipMode,
                onTakeBreak = onTakeBreak,
                onEndBreak = onEndBreak,
                onPauseSession = {
                    optInImmersiveFullscreen = false
                    SystemBarHelper.ensureNormalSystemBars(activityWindow)
                    onPauseSession()
                },
                onResumeSession = onResumeSession,
                onCancelSession = { showEndConfirmDialog = true },
                onEmergencyExitNow = {
                    optInImmersiveFullscreen = false
                    SystemBarHelper.ensureNormalSystemBars(activityWindow)
                    FocusGuardManager.lastInterceptedPackage = null
                    onCancelSession()
                    onExitFocusMode()
                },
                onCompleteSession = {
                    optInImmersiveFullscreen = false
                    SystemBarHelper.ensureNormalSystemBars(activityWindow)
                    onCompleteSession()
                },
                onExitFocusMode = { showEndConfirmDialog = true }
            )
        }

        if (showEndConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showEndConfirmDialog = false },
                title = { Text("Stop Focus Guard Session?", color = TextPrimary) },
                text = {
                    Text(
                        "Stopping the session will immediately disable Focus Guard app restrictions and return to the dashboard.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showEndConfirmDialog = false
                            optInImmersiveFullscreen = false
                            SystemBarHelper.ensureNormalSystemBars(activityWindow)
                            FocusGuardManager.lastInterceptedPackage = null
                            onCancelSession()
                            onExitFocusMode()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                    ) {
                        Text("Stop Session Now", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEndConfirmDialog = false }) {
                        Text("Keep Studying", color = AmberGlowPrimary)
                    }
                },
                containerColor = SlateCard,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary
            )
        }
    }
}

@Composable
private fun PipFloatingTimerView(
    session: FocusSessionEntity,
    onCompleteSession: () -> Unit,
    onEndBreak: () -> Unit
) {
    val context = LocalContext.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(session) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            // Supplementary foreground package check when Usage Access is granted while in PiP mode
            if (session.sessionStatus == "RUNNING") {
                val fgPkg = DigitalWellbeingManager.getCurrentForegroundPackage(context)
                if (!fgPkg.isNullOrBlank() &&
                    fgPkg != context.packageName &&
                    FocusGuardManager.shouldBlockPackage(session, fgPkg, nowMillis)
                ) {
                    FocusGuardManager.lastInterceptedPackage = fgPkg
                    try {
                        val redirectIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                            addFlags(
                                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            )
                            putExtra(FocusGuardManager.EXTRA_INTERCEPTED_PACKAGE, fgPkg)
                        }
                        context.startActivity(redirectIntent)
                    } catch (_: Exception) {}
                }
            }
            delay(1000L)
        }
    }

    val remainingStudySeconds = FocusGuardManager.getRemainingStudySeconds(session, nowMillis)
    val remainingBreakSeconds = FocusGuardManager.getRemainingBreakSeconds(session, nowMillis)
    val isStudyExpired = FocusGuardManager.isSessionExpired(session, nowMillis)
    val isBreakExpired = FocusGuardManager.isBreakExpired(session, nowMillis)

    LaunchedEffect(isStudyExpired, session.sessionStatus) {
        if (isStudyExpired && session.sessionStatus == "RUNNING") {
            onCompleteSession()
        }
    }

    LaunchedEffect(isBreakExpired, session.sessionStatus) {
        if (isBreakExpired && session.sessionStatus == "BREAK") {
            onEndBreak()
        }
    }

    val displaySec = if (session.sessionStatus == "BREAK") remainingBreakSeconds else remainingStudySeconds
    val formattedTime = String.format(Locale.US, "%02d:%02d", displaySec / 60, displaySec % 60)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .padding(8.dp)
            .testTag("pip_floating_timer_view"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (session.sessionStatus == "BREAK") "BREAK" else "FOCUS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (session.sessionStatus == "BREAK") SoftGreen else AmberGlowPrimary
            )
            Text(
                text = formattedTime,
                style = TabularNumeralStyle,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = session.activeTaskName,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusSetupView(
    durationMinutes: Int,
    extraSeconds: Int = 0,
    permittedBreaks: Int,
    breakDurationMinutes: Int,
    taskName: String,
    selectedPackages: Set<String>,
    todayStudyMinutes: Int,
    chapters: List<ChapterEntity> = emptyList(),
    installedApps: List<InstalledAppInfo>,
    isLoadingApps: Boolean,
    isUsageAccessGranted: Boolean,
    isAccessibilityGuardEnabled: Boolean,
    onRefreshPermissions: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSetDuration: (Int) -> Unit,
    onSetCustomDuration: (Int, Int) -> Unit,
    onSetPermittedBreaks: (Int) -> Unit,
    onSetBreakDuration: (Int) -> Unit,
    onSetTaskName: (String) -> Unit,
    onTogglePackage: (String) -> Unit,
    onStartSession: () -> Unit,
    onOpenDigitalWellbeing: () -> Unit = {},
    onExit: () -> Unit
) {
    val scrollState = rememberScrollState()
    var appSearchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }

    var showInstalledAppPickerModal by remember { mutableStateOf(false) }
    var showPermissionPromptModal by remember { mutableStateOf(false) }
    var pendingStartSessionAfterPrompt by remember { mutableStateOf(false) }
    var showDurationPickerDialog by remember { mutableStateOf(false) }
    var showBreakPickerDialog by remember { mutableStateOf(false) }
    var showObjectiveEditor by remember { mutableStateOf(false) }

    val allPermissionsGranted = isUsageAccessGranted && isAccessibilityGuardEnabled

    LaunchedEffect(allPermissionsGranted, isAccessibilityGuardEnabled) {
        if (isAccessibilityGuardEnabled && showPermissionPromptModal && pendingStartSessionAfterPrompt) {
            showPermissionPromptModal = false
            pendingStartSessionAfterPrompt = false
            onStartSession()
        }
    }

    val filteredApps = remember(installedApps, appSearchQuery, showSystemApps, selectedPackages) {
        InstalledAppsRepository.filterApps(
            apps = installedApps,
            searchQuery = appSearchQuery,
            includeSystemApps = showSystemApps,
            selectedPackages = selectedPackages
        )
    }

    val standardPresets = listOf(25, 45, 60, 90)
    val isCustomDuration = extraSeconds > 0 || !standardPresets.contains(durationMinutes)
    val formattedTimerDisplay = String.format(Locale.US, "%02d:%02d", durationMinutes, extraSeconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Clean Top Header matching Screenshot 7 ("Focus" on left, settings/exit on right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Focus",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(FocusDarkSurface)
                        .border(1.dp, FocusDarkBorder, RoundedCornerShape(14.dp))
                        .clickable { onOpenDigitalWellbeing() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("focus_open_wellbeing_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Digital Wellbeing",
                            tint = FocusMint,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Wellbeing",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FocusMint
                        )
                    }
                }

                IconButton(
                    onClick = { showBreakPickerDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(FocusDarkSurface)
                        .border(1.dp, FocusDarkBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Break Allowance Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onExit,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(FocusDarkSurface)
                        .border(1.dp, FocusDarkBorder, CircleShape)
                        .testTag("exit_focus_setup_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit", tint = TextSecondary)
                }
            }
        }

        // 2. Glowing Emerald/Mint Circular Timer Ring (Matches Screenshot 7)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable { showDurationPickerDialog = true }
                .testTag("regain_focus_goal_ring"),
            contentAlignment = Alignment.Center
        ) {
            // Subtle radial emerald glow behind the ring
            Box(
                modifier = Modifier
                    .size(248.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                FocusEmerald.copy(alpha = 0.16f),
                                FocusEmerald.copy(alpha = 0.04f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(214.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = 3.5.dp.toPx()
                        drawCircle(
                            color = Color(0xFF1E293B),
                            style = Stroke(width = strokePx)
                        )
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(FocusMint, FocusEmerald, FocusMint)
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formattedTimerDisplay,
                            style = TabularNumeralStyle,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "READY TO FOCUS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.0.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // 3. Preset Duration Pills Row: [25m] [45m] [60m] [90m] [Custom] (Matches Screenshot 7)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            standardPresets.forEach { mins ->
                val isSelected = !isCustomDuration && durationMinutes == mins
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) FocusEmerald.copy(alpha = 0.18f) else FocusDarkSurface
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) FocusEmerald else FocusDarkBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSetDuration(mins) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${mins}m",
                        style = TabularNumeralStyle,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) FocusMint else TextSecondary
                    )
                }
            }

            // Custom (Minutes & Seconds) Pill
            Box(
                modifier = Modifier
                    .weight(1.15f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isCustomDuration) FocusEmerald.copy(alpha = 0.18f) else FocusDarkSurface
                    )
                    .border(
                        width = 1.dp,
                        color = if (isCustomDuration) FocusEmerald else FocusDarkBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { showDurationPickerDialog = true }
                    .padding(vertical = 11.dp)
                    .testTag("focus_custom_timer_pill"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isCustomDuration) formattedTimerDisplay else "Custom",
                    style = TabularNumeralStyle,
                    fontSize = 13.sp,
                    fontWeight = if (isCustomDuration) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCustomDuration) FocusMint else TextSecondary
                )
            }
        }

        // 4. STUDY OBJECTIVE Card + Blocked Apps Card + Ultra Focus / Break Settings + Start Focus Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("regain_setup_sheet_card"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // STUDY OBJECTIVE Card (Matches Screenshot 7)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showObjectiveEditor = !showObjectiveEditor },
                colors = CardDefaults.cardColors(containerColor = FocusDarkSurface),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, FocusDarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "STUDY OBJECTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.3.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = taskName.ifBlank { "Physics · Laws of Motion" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Edit Study Objective",
                            tint = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = taskName,
                        onValueChange = onSetTaskName,
                        placeholder = { Text("Physics · Laws of Motion", color = TextMuted, fontSize = 13.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("focus_task_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FocusEmerald,
                            unfocusedBorderColor = FocusDarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (showObjectiveEditor && chapters.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            chapters.take(6).forEach { ch ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SlateCardElevated)
                                        .border(1.dp, FocusDarkBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            onSetTaskName("${ch.subject} · ${ch.chapterName}")
                                            showObjectiveEditor = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${ch.subject}: ${ch.chapterName}",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Blocked apps Card (Matches Screenshot 7 & opens Blocked Apps Modal matching Screenshot 4)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showInstalledAppPickerModal = true }
                    .testTag("regain_blocked_apps_card"),
                colors = CardDefaults.cardColors(containerColor = FocusDarkSurface),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, FocusDarkBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Blocked apps",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isAccessibilityGuardEnabled) FocusEmerald.copy(alpha = 0.16f)
                                            else WarmAmber.copy(alpha = 0.16f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isAccessibilityGuardEnabled) "Guard Active" else "Permission Needed",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAccessibilityGuardEnabled) FocusMint else WarmAmber
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Manage selected apps • ${selectedPackages.size} selected",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(FocusEmerald.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${selectedPackages.size} apps",
                                    style = TabularNumeralStyle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FocusMint
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Manage Blocked Apps",
                                tint = FocusMint
                            )
                        }
                    }

                    if (!isAccessibilityGuardEnabled || !isUsageAccessGranted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateCardElevated.copy(alpha = 0.75f))
                                .clickable {
                                    onRefreshPermissions()
                                    pendingStartSessionAfterPrompt = false
                                    showPermissionPromptModal = true
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("focus_configure_guard_permissions_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = WarmAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (!isAccessibilityGuardEnabled) {
                                        "Enable Focus Guard Service to block apps during timer"
                                    } else {
                                        "Grant Usage Access for Digital Wellbeing screen-time stats"
                                    },
                                    fontSize = 11.sp,
                                    color = WarmAmber,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "Enable →",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FocusMint
                            )
                        }
                    }
                }
            }

            // Ultra Focus / Break Allowance Row Card (Preserves regain_ultra_focus_card testTag)
            val isUltraFocus = permittedBreaks == 0
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetPermittedBreaks(if (isUltraFocus) 2 else 0) }
                    .testTag("regain_ultra_focus_card"),
                colors = CardDefaults.cardColors(containerColor = FocusDarkSurface),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isUltraFocus) FocusEmerald.copy(alpha = 0.6f) else FocusDarkBorder
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ultra focus mode",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isUltraFocus) {
                                "Strict 0-break session active"
                            } else {
                                "$permittedBreaks break${if (permittedBreaks > 1) "s" else ""} (${breakDurationMinutes}m) allowed • Tap for strict mode"
                            },
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = isUltraFocus,
                        onCheckedChange = { strict -> onSetPermittedBreaks(if (strict) 0 else 2) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepNavyBg,
                            checkedTrackColor = FocusEmerald
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Full-Width Vibrant Emerald "Start Focus" Button (Prompts for Guard Permission if missing)
            Button(
                onClick = {
                    onRefreshPermissions()
                    if (!isAccessibilityGuardEnabled || !isUsageAccessGranted) {
                        pendingStartSessionAfterPrompt = true
                        showPermissionPromptModal = true
                    } else {
                        onStartSession()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = FocusEmerald),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("start_focus_session_button")
            ) {
                Text(
                    text = "Start Focus",
                    color = Color(0xFF042F2E),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }
        }
    }

    // Modal 1: Blocked Apps Picker Dialog (Matches Screenshot 4: Search apps + Suggested Apps + Other Apps)
    if (showInstalledAppPickerModal) {
        val socialAndVideoKeywords = remember {
            listOf("instagram", "youtube", "music", "twitter", "facebook", "whatsapp", "snapchat", "reddit", "pubg", "game")
        }
        val suggestedApps = remember(filteredApps) {
            filteredApps.filter { app ->
                socialAndVideoKeywords.any { kw ->
                    app.packageName.contains(kw, ignoreCase = true) || app.appName.contains(kw, ignoreCase = true)
                }
            }.ifEmpty { filteredApps.take(4) }
        }
        val otherApps = remember(filteredApps, suggestedApps) {
            val suggestedSet = suggestedApps.map { it.packageName }.toSet()
            filteredApps.filterNot { suggestedSet.contains(it.packageName) }
        }

        AlertDialog(
            onDismissRequest = { showInstalledAppPickerModal = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Blocked Apps",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${selectedPackages.size} selected for focus guard",
                            fontSize = 11.sp,
                            color = FocusMint
                        )
                    }
                    TextButton(onClick = { showInstalledAppPickerModal = false }) {
                        Text("Save", color = FocusMint, fontWeight = FontWeight.Bold)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("installed_apps_picker_dialog")
                ) {
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        placeholder = { Text("Search apps", fontSize = 13.sp, color = TextMuted) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (appSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { appSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_installed_apps_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FocusEmerald,
                            unfocusedBorderColor = FocusDarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Include system apps",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Switch(
                            checked = showSystemApps,
                            onCheckedChange = { showSystemApps = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepNavyBg,
                                checkedTrackColor = FocusEmerald
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (isLoadingApps) {
                        Text(
                            text = "Loading installed applications...",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SlateCardElevated)
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (appSearchQuery.isNotBlank()) {
                                    "No apps match \"$appSearchQuery\"."
                                } else {
                                    "No user apps found. Toggle 'Include system apps' above to view all packages."
                                },
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 310.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (suggestedApps.isNotEmpty()) {
                                Text(
                                    text = "Suggested Apps",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                                suggestedApps.forEach { app ->
                                    InstalledAppRow(
                                        app = app,
                                        isSelected = selectedPackages.contains(app.packageName),
                                        onToggle = { onTogglePackage(app.packageName) }
                                    )
                                }
                            }

                            if (otherApps.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Other Apps",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                                otherApps.forEach { app ->
                                    InstalledAppRow(
                                        app = app,
                                        isSelected = selectedPackages.contains(app.packageName),
                                        onToggle = { onTogglePackage(app.packageName) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInstalledAppPickerModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = FocusEmerald),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save (${selectedPackages.size})", color = Color(0xFF042F2E), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = FocusDarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Modal 2: On-Demand Permission Setup Dialog (Shown ONLY when starting a feature that needs missing permissions)
    if (showPermissionPromptModal && !allPermissionsGranted) {
        AlertDialog(
            onDismissRequest = {
                showPermissionPromptModal = false
                pendingStartSessionAfterPrompt = false
            },
            title = {
                Text(
                    text = "Permissions Needed for App Guard",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("focus_permission_setup_dialog"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Why each permission is needed:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberGlowPrimary
                    )
                    Text(
                        text = "• Focus Guard Accessibility Service: Detects when a blocked app opens during an active Focus Session and returns you to your study timer. Never reads screen content or keystrokes.\n" +
                                "• Usage Access (Optional): Used only for Digital Wellbeing screen-time analytics.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    PermissionVerificationRow(
                        title = "Focus Guard Service (App Blocking)",
                        statusText = if (isAccessibilityGuardEnabled) "Active" else "Required for App Blocking",
                        isGranted = isAccessibilityGuardEnabled,
                        buttonLabel = if (isAccessibilityGuardEnabled) "Enabled" else "Open Settings",
                        onAction = onOpenAccessibilitySettings,
                        testTag = "focus_accessibility_setup_button"
                    )

                    PermissionVerificationRow(
                        title = "Usage Access (Screen-Time Stats)",
                        statusText = if (isUsageAccessGranted) "Granted" else "Optional Analytics",
                        isGranted = isUsageAccessGranted,
                        buttonLabel = if (isUsageAccessGranted) "Granted" else "Grant Access",
                        onAction = onOpenUsageAccessSettings,
                        testTag = "focus_usage_access_button"
                    )
                }
            },
            confirmButton = {
                if (pendingStartSessionAfterPrompt) {
                    Button(
                        onClick = {
                            showPermissionPromptModal = false
                            pendingStartSessionAfterPrompt = false
                            onStartSession()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGlowPrimary),
                        modifier = Modifier.testTag("start_timer_without_guard_button")
                    ) {
                        Text("Start Timer Only", color = DeepNavyBg, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            onRefreshPermissions()
                            showPermissionPromptModal = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGlowPrimary)
                    ) {
                        Text("Done", color = DeepNavyBg, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionPromptModal = false
                        pendingStartSessionAfterPrompt = false
                    }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SlateCard
        )
    }

    if (showDurationPickerDialog) {
        var customMin by remember(showDurationPickerDialog) { mutableStateOf(durationMinutes) }
        var customSec by remember(showDurationPickerDialog) { mutableStateOf(extraSeconds) }
        var minText by remember(showDurationPickerDialog) { mutableStateOf(durationMinutes.toString()) }
        var secText by remember(showDurationPickerDialog) { mutableStateOf(extraSeconds.toString()) }

        AlertDialog(
            onDismissRequest = { showDurationPickerDialog = false },
            title = {
                Text(
                    text = "Custom Focus Timer (Minutes & Seconds)",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Choose any custom minutes and seconds for your Focus session:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    // Minutes Picker
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("MINUTES (0 - 180)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FocusMint)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = minText,
                                onValueChange = { str ->
                                    val digits = str.filter { it.isDigit() }.take(3)
                                    minText = digits
                                    customMin = (digits.toIntOrNull() ?: 0).coerceIn(0, 180)
                                },
                                label = { Text("Min") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("focus_custom_minutes_input")
                            )
                            listOf(-5, -1, +1, +5).forEach { delta ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SlateCardElevated)
                                        .border(1.dp, FocusDarkBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            customMin = (customMin + delta).coerceIn(0, 180)
                                            minText = customMin.toString()
                                        }
                                        .padding(horizontal = 9.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = if (delta > 0) "+${delta}m" else "${delta}m",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Seconds Picker
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("SECONDS (0 - 59)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FocusMint)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = secText,
                                onValueChange = { str ->
                                    val digits = str.filter { it.isDigit() }.take(2)
                                    secText = digits
                                    customSec = (digits.toIntOrNull() ?: 0).coerceIn(0, 59)
                                },
                                label = { Text("Sec") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("focus_custom_seconds_input")
                            )
                            listOf(-15, -5, +5, +15).forEach { delta ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SlateCardElevated)
                                        .border(1.dp, FocusDarkBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            customSec = (customSec + delta).coerceIn(0, 59)
                                            secText = customSec.toString()
                                        }
                                        .padding(horizontal = 9.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = if (delta > 0) "+${delta}s" else "${delta}s",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateCardElevated)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Custom Timer: ${String.format(Locale.US, "%02d:%02d", customMin, customSec)}",
                            style = TabularNumeralStyle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FocusMint
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalMin = (minText.toIntOrNull() ?: customMin).coerceIn(0, 180)
                        val finalSec = (secText.toIntOrNull() ?: customSec).coerceIn(0, 59)
                        onSetCustomDuration(finalMin, finalSec)
                        showDurationPickerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FocusEmerald),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("apply_focus_custom_duration_button")
                ) {
                    Text("Set Duration", color = Color(0xFF042F2E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDurationPickerDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = FocusDarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showBreakPickerDialog) {
        AlertDialog(
            onDismissRequest = { showBreakPickerDialog = false },
            title = { Text("Select Break Allowance", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 1, 2, 3).forEach { count ->
                        val selected = permittedBreaks == count
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AmberGlowPrimary else SlateCardElevated)
                                .clickable {
                                    onSetPermittedBreaks(count)
                                    showBreakPickerDialog = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = if (count == 0) "Strict (0)" else "$count breaks",
                                color = if (selected) DeepNavyBg else TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBreakPickerDialog = false }) {
                    Text("Done", color = AmberGlowPrimary)
                }
            },
            containerColor = SlateCard
        )
    }
}

@Composable
private fun PermissionVerificationRow(
    title: String,
    statusText: String,
    isGranted: Boolean,
    buttonLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SlateCardElevated)
            .border(1.dp, if (isGranted) SoftGreen.copy(alpha = 0.4f) else SlateCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) SoftGreen else WarmAmber,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isGranted) SoftGreen else WarmAmber
                )
            }
        }

        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) SlateCard else AmberGlowPrimary
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .height(34.dp)
                .testTag(testTag)
        ) {
            Text(
                text = buttonLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGranted) TextSecondary else DeepNavyBg
            )
        }
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledAppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val iconBitmap: ImageBitmap? = remember(app.packageName, app.icon) {
        drawableToImageBitmap(app.icon)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) AmberGlowPrimary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("installed_app_row_${app.packageName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateCard),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = AmberGlowPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = app.appName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName + if (app.isSystemApp) " • System" else "",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = AmberGlowPrimary,
                uncheckedColor = TextMuted,
                checkmarkColor = DeepNavyBg
            )
        )
    }
}

private fun drawableToImageBitmap(drawable: Drawable?): ImageBitmap? {
    if (drawable == null) return null
    return try {
        val width = drawable.intrinsicWidth.coerceIn(1, 96)
        val height = drawable.intrinsicHeight.coerceIn(1, 96)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun ActiveFocusLockView(
    session: FocusSessionEntity,
    isAccessibilityGuardEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit = {},
    optInImmersiveFullscreen: Boolean,
    onToggleImmersiveFullscreen: (Boolean) -> Unit,
    onEnterPipMode: () -> Unit,
    onTakeBreak: () -> Unit,
    onEndBreak: () -> Unit,
    onPauseSession: () -> Unit,
    onResumeSession: () -> Unit,
    onCancelSession: () -> Unit,
    onEmergencyExitNow: () -> Unit,
    onCompleteSession: () -> Unit,
    onExitFocusMode: () -> Unit
) {
    val context = LocalContext.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val interceptedPkg = FocusGuardManager.lastInterceptedPackage

    LaunchedEffect(session) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            if (session.sessionStatus == "RUNNING") {
                val fgPkg = DigitalWellbeingManager.getCurrentForegroundPackage(context)
                if (!fgPkg.isNullOrBlank() &&
                    fgPkg != context.packageName &&
                    FocusGuardManager.shouldBlockPackage(session, fgPkg, nowMillis)
                ) {
                    FocusGuardManager.lastInterceptedPackage = fgPkg
                    try {
                        val redirectIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                            addFlags(
                                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            )
                            putExtra(FocusGuardManager.EXTRA_INTERCEPTED_PACKAGE, fgPkg)
                        }
                        context.startActivity(redirectIntent)
                    } catch (_: Exception) {}
                }
            }
            delay(1000L)
        }
    }

    val remainingStudySeconds = FocusGuardManager.getRemainingStudySeconds(session, nowMillis)
    val remainingBreakSeconds = FocusGuardManager.getRemainingBreakSeconds(session, nowMillis)
    val isStudyExpired = FocusGuardManager.isSessionExpired(session, nowMillis)
    val isBreakExpired = FocusGuardManager.isBreakExpired(session, nowMillis)

    LaunchedEffect(isStudyExpired, session.sessionStatus) {
        if (isStudyExpired && session.sessionStatus == "RUNNING") {
            onCompleteSession()
        }
    }

    LaunchedEffect(isBreakExpired, session.sessionStatus) {
        if (isBreakExpired && session.sessionStatus == "BREAK") {
            onEndBreak()
        }
    }

    val studyMin = remainingStudySeconds / 60
    val studySec = remainingStudySeconds % 60
    val formattedStudyTime = String.format(Locale.US, "%02d:%02d", studyMin, studySec)

    val breakMin = remainingBreakSeconds / 60
    val breakSec = remainingBreakSeconds % 60
    val formattedBreakTime = String.format(Locale.US, "%02d:%02d", breakMin, breakSec)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Status, PiP Floating Timer Action & Fullscreen Toggle
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (session.sessionStatus) {
                                "BREAK" -> SoftGreen.copy(alpha = 0.2f)
                                "PAUSED" -> WarmAmber.copy(alpha = 0.2f)
                                else -> AmberGlowPrimary.copy(alpha = 0.2f)
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val badgeLabel = when (session.sessionStatus) {
                        "BREAK" -> "AUTHORIZED BREAK"
                        "PAUSED" -> "SESSION PAUSED"
                        else -> if (isAccessibilityGuardEnabled) "FOCUS GUARD ACTIVE" else "FOCUS TIMER ACTIVE"
                    }
                    Text(
                        text = badgeLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (session.sessionStatus) {
                            "BREAK" -> SoftGreen
                            "PAUSED" -> WarmAmber
                            else -> AmberGlowPrimary
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEnterPipMode,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("enter_pip_timer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Floating PiP Timer",
                            tint = AmberGlowPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Float Timer (PiP)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    IconButton(
                        onClick = onExitFocusMode,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SlateCard)
                            .testTag("exit_active_focus_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Stop Focus", tint = TextSecondary)
                    }
                }
            }

            // Opt-in Full Immersive Toggle (Hides Navigation Bar in addition to Status Bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateCard)
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = null,
                        tint = AmberGlowPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Hide Navigation Bar (Deep Fullscreen)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Swipe edge to reveal system bars",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
                Switch(
                    checked = optInImmersiveFullscreen,
                    onCheckedChange = onToggleImmersiveFullscreen,
                    modifier = Modifier.testTag("immersive_fullscreen_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DeepNavyBg,
                        checkedTrackColor = AmberGlowPrimary
                    )
                )
            }

            if (!isAccessibilityGuardEnabled && session.sessionStatus == "RUNNING") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccessibilitySettings() },
                    colors = CardDefaults.cardColors(containerColor = WarmAmber.copy(alpha = 0.14f)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarmAmber.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Blocking Service is OFF",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarmAmber
                            )
                            Text(
                                text = "Tap here to enable JEE Study OS in Accessibility Settings so blocked apps are restricted.",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enable →",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FocusMint
                        )
                    }
                }
            }

            if (!interceptedPkg.isNullOrBlank() && session.sessionStatus == "RUNNING") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CoralRed.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CoralRed.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Restricted App Redirected: ${interceptedPkg.substringAfterLast('.')}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CoralRed
                            )
                            Text(
                                text = "Stay focused on ${session.activeTaskName}, or tap Stop below if you need immediate access.",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Center Glow Countdown View with Tabular Numerals
        GlowGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            glowColor = if (session.sessionStatus == "BREAK") SoftGreen else CopperGlowAccent,
            glowAlpha = 0.30f,
            glowAlignment = Alignment.Center,
            cornerRadius = 26.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp, horizontal = 20.dp)
            ) {
                Text(
                    text = session.activeTaskName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (session.sessionStatus == "BREAK") {
                    Text(
                        text = formattedBreakTime,
                        style = TabularNumeralStyle,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = SoftGreen
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Break active — restricted apps temporarily unlocked.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                } else {
                    Text(
                        text = formattedStudyTime,
                        style = TabularNumeralStyle,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Remaining Focus Duration • ${session.blockedPackageList.size} app(s) in guard list",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SlateCardElevated)
                        .border(1.dp, SlateCardBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = GoldGlowHighlight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Breaks Remaining: ${session.breaksRemaining} / ${session.permittedBreaks}",
                        style = TabularNumeralStyle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        // Controls (Start/Resume, Pause, Break, Stop & Emergency Exit)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (session.sessionStatus == "BREAK") {
                Button(
                    onClick = onEndBreak,
                    colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("end_break_early_button")
                ) {
                    Text("RESUME STUDY FOCUS EARLY", color = DeepNavyBg, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val canTakeBreak = session.breaksRemaining > 0 && session.sessionStatus == "RUNNING"
                    Button(
                        onClick = onTakeBreak,
                        enabled = canTakeBreak,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarmAmber,
                            disabledContainerColor = SlateCardElevated
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("take_break_button")
                    ) {
                        Text(
                            text = if (session.breaksRemaining > 0) "Take Break (${session.breakDurationMinutes}m)" else "No Breaks Left",
                            color = if (canTakeBreak) DeepNavyBg else TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = {
                            if (session.sessionStatus == "PAUSED") onResumeSession() else onPauseSession()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pause_resume_focus_button")
                    ) {
                        Icon(
                            imageVector = if (session.sessionStatus == "PAUSED") Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = AmberGlowPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (session.sessionStatus == "PAUSED") "Resume" else "Pause Guard",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCancelSession,
                    colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .border(1.dp, CoralRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .testTag("stop_focus_session_button")
                ) {
                    Text("Stop Session", color = CoralRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onEmergencyExitNow,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("emergency_exit_focus_button")
                ) {
                    Text("Emergency Exit Now", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
