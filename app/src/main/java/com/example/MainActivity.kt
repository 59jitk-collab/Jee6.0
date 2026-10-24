package com.example

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.focus.FocusGuardManager
import com.example.data.local.entities.UserSettingsEntity
import com.example.ui.components.BottomNavBar
import com.example.ui.components.ImportExportDialog
import com.example.ui.screens.AiMentorScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DigitalWellbeingScreen
import com.example.ui.screens.FocusModeScreen
import com.example.ui.screens.MistakeNotebookScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.RestSleepScreen
import com.example.ui.screens.RevisionScreen
import com.example.ui.screens.SchedulerScreen
import com.example.ui.screens.StudyAnalyticsScreen
import com.example.ui.screens.TestSeriesScreen
import com.example.ui.screens.TrackerScreen
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.JeeStudyOSTheme
import com.example.util.DateUtils
import com.example.util.StudyNotificationManager
import com.example.util.SystemBarHelper
import com.example.viewmodel.AppScreen
import com.example.viewmodel.StudyViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: StudyViewModel by viewModels()
    private val isInPipState = mutableStateOf(false)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                StudyNotificationManager.scheduleDailyStudyReminder(this, hourOfDay = 8, minute = 0)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        SystemBarHelper.ensureNormalSystemBars(window)
        StudyNotificationManager.ensureChannelsCreated(this)
        requestStandardNotificationPermissionIfNeeded()
        handleIncomingIntent(intent)

        setContent {
            val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
            val inPip by remember { isInPipState }
            var isDimmer by remember(userSettings) { mutableStateOf(userSettings?.isDimmerActive ?: false) }

            JeeStudyOSTheme(dimmerMode = isDimmer) {
                JeeStudyOSApp(
                    viewModel = viewModel,
                    isDimmer = isDimmer,
                    isInPipMode = inPip,
                    onEnterPipMode = { enterFocusPipMode() },
                    onToggleDimmer = {
                        isDimmer = !isDimmer
                        userSettings?.let { current ->
                            viewModel.updateSettings(current.copy(isDimmerActive = isDimmer))
                        }
                    }
                )
            }
        }
    }

    private fun requestStandardNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!SystemBarHelper.isImmersiveModeActive) {
            SystemBarHelper.ensureNormalSystemBars(window)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val session = viewModel.activeFocusSession.value
        val isActiveSession = session != null &&
                (session.sessionStatus == "RUNNING" || session.sessionStatus == "BREAK")
        if (isActiveSession && viewModel.currentScreen.value == AppScreen.FOCUS_MODE) {
            enterFocusPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipState.value = isInPictureInPictureMode
    }

    private fun enterFocusPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
                // Safe fallback on devices/ROMs where PiP is disabled
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val interceptedPkg = intent?.getStringExtra(FocusGuardManager.EXTRA_INTERCEPTED_PACKAGE)
        if (!interceptedPkg.isNullOrBlank()) {
            FocusGuardManager.lastInterceptedPackage = interceptedPkg
            viewModel.navigateTo(AppScreen.FOCUS_MODE)
            return
        }
        val targetScreenName = intent?.getStringExtra(StudyNotificationManager.EXTRA_TARGET_SCREEN)
        if (!targetScreenName.isNullOrBlank()) {
            runCatching { AppScreen.valueOf(targetScreenName) }.getOrNull()?.let { screen ->
                viewModel.navigateTo(screen)
            }
        }
    }
}

@Composable
fun JeeStudyOSApp(
    viewModel: StudyViewModel,
    isDimmer: Boolean,
    isInPipMode: Boolean = false,
    onEnterPipMode: () -> Unit = {},
    onToggleDimmer: () -> Unit
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val allChapters by viewModel.allChapters.collectAsStateWithLifecycle()
    val allSchedules by viewModel.allSchedules.collectAsStateWithLifecycle()
    val allTests by viewModel.allTests.collectAsStateWithLifecycle()
    val allMistakes by viewModel.allMistakes.collectAsStateWithLifecycle()
    val revisions by viewModel.pendingRevisions.collectAsStateWithLifecycle()
    val categorizedRevisions by viewModel.categorizedRevisions.collectAsStateWithLifecycle()
    val progressionStatus by viewModel.progressionStatus.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()

    val selectedSubject by viewModel.selectedSubject.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showOnlyWeak by viewModel.showOnlyWeak.collectAsStateWithLifecycle()

    val timerSeconds by viewModel.timerSecondsLeft.collectAsStateWithLifecycle()
    val isTimerRunning by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val timerMode by viewModel.timerMode.collectAsStateWithLifecycle()

    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAiGenerating by viewModel.isAiGenerating.collectAsStateWithLifecycle()
    val selectedMentorChapterId by viewModel.selectedMentorChapterId.collectAsStateWithLifecycle()
    val userApprovedExternalAiSend by viewModel.userApprovedExternalAiSend.collectAsStateWithLifecycle()
    val aiBackendHealthState by viewModel.aiBackendHealthState.collectAsStateWithLifecycle()
    val aiBackendHealthDetail by viewModel.aiBackendHealthDetail.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()

    val weeklyReport by viewModel.weeklyReport.collectAsStateWithLifecycle()
    val wellbeingStats by viewModel.wellbeingStats.collectAsStateWithLifecycle()

    val workbookConfig by viewModel.workbookConfig.collectAsStateWithLifecycle()
    val pendingOutboxCount by viewModel.pendingOutboxCount.collectAsStateWithLifecycle()
    val unresolvedConflicts by viewModel.unresolvedConflicts.collectAsStateWithLifecycle()
    val isSyncInProgress by viewModel.isSyncInProgress.collectAsStateWithLifecycle()
    val connectedGoogleEmail by viewModel.connectedGoogleEmail.collectAsStateWithLifecycle()
    val pendingAuthConsentIntent by viewModel.pendingAuthConsentIntent.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showImportExportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(statusMsg) {
        statusMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.DIGITAL_WELLBEING) {
            viewModel.refreshWellbeingStats()
        }
    }

    // Hardware and gesture Back button handling to navigate back to More or Dashboard
    if (currentScreen != AppScreen.DASHBOARD && currentScreen != AppScreen.FOCUS_MODE) {
        BackHandler {
            if (currentScreen == AppScreen.ANALYTICS ||
                currentScreen == AppScreen.DIGITAL_WELLBEING ||
                currentScreen == AppScreen.REVISION
            ) {
                viewModel.navigateTo(AppScreen.MORE)
            } else {
                viewModel.navigateTo(AppScreen.DASHBOARD)
            }
        }
    }

    if (currentScreen == AppScreen.FOCUS_MODE) {
        val activeSession by viewModel.activeFocusSession.collectAsStateWithLifecycle()
        val setupDurationMinutes by viewModel.focusSetupDurationMinutes.collectAsStateWithLifecycle()
        val setupExtraSeconds by viewModel.focusSetupExtraSeconds.collectAsStateWithLifecycle()
        val setupPermittedBreaks by viewModel.focusSetupPermittedBreaks.collectAsStateWithLifecycle()
        val setupBreakDurationMinutes by viewModel.focusSetupBreakDurationMinutes.collectAsStateWithLifecycle()
        val setupTaskName by viewModel.focusSetupTaskName.collectAsStateWithLifecycle()
        val selectedPackages by viewModel.focusSelectedPackages.collectAsStateWithLifecycle()
        val todayStr = remember { DateUtils.todayDateString() }
        val missionStartDate = allSchedules.firstOrNull()?.date ?: "2026-10-23"
        val todayStudyMinutes = remember(allSchedules, todayStr, missionStartDate) {
            DateUtils.resolveScheduleForDate(allSchedules, todayStr, missionStartDate)?.studyMinutes ?: 0
        }

        FocusModeScreen(
            activeSession = activeSession,
            setupDurationMinutes = setupDurationMinutes,
            setupExtraSeconds = setupExtraSeconds,
            setupPermittedBreaks = setupPermittedBreaks,
            setupBreakDurationMinutes = setupBreakDurationMinutes,
            setupTaskName = setupTaskName,
            selectedPackages = selectedPackages,
            todayStudyMinutes = todayStudyMinutes,
            chapters = allChapters,
            isInPipMode = isInPipMode,
            onEnterPipMode = onEnterPipMode,
            onSetSetupDuration = { viewModel.setFocusSetupDuration(it) },
            onSetCustomDuration = { min, sec -> viewModel.setFocusSetupCustomDuration(min, sec) },
            onSetSetupPermittedBreaks = { viewModel.setFocusSetupPermittedBreaks(it) },
            onSetSetupBreakDuration = { viewModel.setFocusSetupBreakDuration(it) },
            onSetSetupTaskName = { viewModel.setFocusSetupTaskName(it) },
            onToggleSelectedPackage = { viewModel.toggleFocusSelectedPackage(it) },
            onStartFocusSession = { viewModel.startFocusLockSession() },
            onTakeBreak = { viewModel.takeFocusBreak() },
            onEndBreak = { viewModel.endFocusBreak() },
            onPauseSession = { viewModel.pauseFocusLockSession() },
            onResumeSession = { viewModel.resumeFocusLockSession() },
            onCancelSession = { viewModel.cancelFocusLockSession() },
            onCompleteSession = { viewModel.completeFocusLockSession() },
            onExitFocusMode = { viewModel.navigateTo(AppScreen.DASHBOARD) },
            onOpenDigitalWellbeing = { viewModel.navigateTo(AppScreen.DIGITAL_WELLBEING) }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                BottomNavBar(
                    currentScreen = currentScreen,
                    onSelectScreen = { viewModel.navigateTo(it) }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepNavyBg)
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    AppScreen.DASHBOARD -> DashboardScreen(
                        chapters = allChapters,
                        schedules = allSchedules,
                        settings = userSettings,
                        progressionStatus = progressionStatus,
                        categorizedRevisions = categorizedRevisions,
                        isTimerRunning = isTimerRunning,
                        timerSeconds = timerSeconds,
                        timerMode = timerMode,
                        onToggleTimer = { viewModel.toggleTimer() },
                        onNavigate = { viewModel.navigateTo(it) },
                        onOpenImportExport = { showImportExportDialog = true },
                        onExecutePlanTask = { sched, task ->
                            viewModel.executePlanTaskAction(sched, task)
                        }
                    )

                    AppScreen.TRACKER -> TrackerScreen(
                        chapters = allChapters,
                        selectedSubject = selectedSubject,
                        searchQuery = searchQuery,
                        showOnlyWeak = showOnlyWeak,
                        onSelectSubject = { viewModel.setSubject(it) },
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onToggleShowOnlyWeak = { viewModel.toggleShowOnlyWeak() },
                        onLectureChange = { ch, delta -> viewModel.updateLecture(ch, delta) },
                        onNotesToggle = { ch -> viewModel.toggleNotes(ch) },
                        onDppChange = { ch, delta -> viewModel.updateDpp(ch, delta) },
                        onPyqChange = { ch, delta -> viewModel.updatePyq(ch, delta) },
                        onDibyChange = { ch, delta -> viewModel.updateDiby(ch, delta) },
                        onClassProbChange = { ch, delta -> viewModel.updateClassProblems(ch, delta) },
                        onWorksheetChange = { ch, delta -> viewModel.updateWorksheet(ch, delta) },
                        onToggleWeak = { ch -> viewModel.toggleChapterWeak(ch) },
                        onMasteryChange = { ch, level -> viewModel.updateChapterMastery(ch, level) },
                        onMarkStudied = { ch -> viewModel.markChapterStudied(ch) },
                        onSaveChapter = { ch -> viewModel.saveChapter(ch) },
                        onDeleteChapter = { id -> viewModel.deleteChapter(id) },
                        onOpenImportExport = { showImportExportDialog = true },
                        onLoadDefaultTemplate = { viewModel.seedStandardTemplate() }
                    )

                    AppScreen.SCHEDULER -> SchedulerScreen(
                        schedules = allSchedules,
                        missionStartDate = allSchedules.firstOrNull()?.date ?: "2026-10-23",
                        onUpdateLecture = { s, delta -> viewModel.updateScheduleLecture(s, delta) },
                        onUpdateProblems = { s, delta -> viewModel.updateScheduleProblems(s, delta) },
                        onToggleNotes = { s -> viewModel.toggleScheduleNotes(s) },
                        onUpdateAssignedChapters = { s, csv -> viewModel.updateScheduleAssignedChapters(s, csv) },
                        onExecutePlanTask = { s, task -> viewModel.executePlanTaskAction(s, task) },
                        onRebalanceRemaining = { fromDay -> viewModel.rebalanceRemainingDays(fromDay) },
                        onNavigateToTests = { viewModel.navigateTo(AppScreen.TESTS) }
                    )

                    AppScreen.TESTS -> TestSeriesScreen(
                        tests = allTests,
                        onSaveTest = { t -> viewModel.saveTestRecord(t) },
                        onDeleteTest = { id -> viewModel.deleteTest(id) },
                        onSaveMistake = { m -> viewModel.saveMistake(m) },
                        chapters = allChapters,
                        onAutoEvaluateTest = { t -> viewModel.autoEvaluateSundayTest(t) },
                        onAutoEvaluateAllTests = { viewModel.autoEvaluateAllSundayTests() },
                        onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
                    )

                    AppScreen.MISTAKES -> MistakeNotebookScreen(
                        mistakes = allMistakes,
                        revisions = revisions,
                        onSaveMistake = { m -> viewModel.saveMistake(m) },
                        onDeleteMistake = { id -> viewModel.deleteMistake(id) },
                        onToggleMistakeResolved = { m -> viewModel.toggleMistakeResolved(m) },
                        onCompleteRevision = { rev -> viewModel.completeRevision(rev) },
                        onNavigate = { viewModel.navigateTo(it) }
                    )

                    AppScreen.REST_SLEEP -> RestSleepScreen(
                        settings = userSettings,
                        isTimerRunning = isTimerRunning,
                        timerSeconds = timerSeconds,
                        timerMode = timerMode,
                        onToggleTimer = { viewModel.toggleTimer() },
                        onResetTimer = { viewModel.resetTimer() },
                        onSetTimerMode = { viewModel.setTimerMode(it) },
                        onSetCustomTimerDuration = { min, sec -> viewModel.setCustomTimerDuration(min, sec) },
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onNavigate = { viewModel.navigateTo(it) }
                    )

                    AppScreen.AI_MENTOR -> AiMentorScreen(
                        messages = chatMessages,
                        isGenerating = isAiGenerating,
                        chapters = allChapters,
                        selectedChapterId = selectedMentorChapterId,
                        isRemoteBackendConfigured = viewModel.isAiRemoteBackendConfigured,
                        userApprovedExternalSend = userApprovedExternalAiSend,
                        aiBackendHealthState = aiBackendHealthState,
                        aiBackendHealthDetail = aiBackendHealthDetail,
                        onVerifyBackendHealth = { viewModel.refreshAiBackendHealth() },
                        onSelectChapterContext = { viewModel.setSelectedMentorChapter(it) },
                        onSetExternalSendConsent = { viewModel.setUserApprovedExternalAiSend(it) },
                        onSetCustomApiKey = { viewModel.setCustomGeminiApiKey(it) },
                        onSendMessage = { viewModel.sendChatMessage(it) },
                        onRetryMessage = { viewModel.retryLastAiMessage() },
                        onClearChat = { viewModel.clearAiChat() },
                        onNavigate = { viewModel.navigateTo(it) }
                    )

                    AppScreen.MORE -> {
                        val isFirebaseSignedIn by viewModel.isFirebaseSignedIn.collectAsStateWithLifecycle()
                        val firebaseUserEmail by viewModel.firebaseUserEmail.collectAsStateWithLifecycle()
                        val firebaseUserDisplayName by viewModel.firebaseUserDisplayName.collectAsStateWithLifecycle()
                        val isCloudSyncing by viewModel.isCloudSyncing.collectAsStateWithLifecycle()
                        val lastCloudSyncStatus by viewModel.lastCloudSyncStatus.collectAsStateWithLifecycle()

                        MoreScreen(
                            settings = userSettings,
                            progressionStatus = progressionStatus,
                            isFirebaseSignedIn = isFirebaseSignedIn,
                            firebaseUserEmail = firebaseUserEmail,
                            firebaseUserDisplayName = firebaseUserDisplayName,
                            isCloudSyncing = isCloudSyncing,
                            lastCloudSyncStatus = lastCloudSyncStatus,
                            onSignInFirebaseGoogle = { ctx -> viewModel.signInWithFirebaseGoogle(ctx) },
                            onSyncFirebaseCloud = { viewModel.syncWithFirebaseCloud() },
                            onSignOutFirebase = { viewModel.signOutFromFirebase() },
                            onNavigate = { viewModel.navigateTo(it) },
                            onOpenImportExport = { showImportExportDialog = true },
                            onUpdateSettings = { viewModel.updateSettings(it) }
                        )
                    }

                    AppScreen.ANALYTICS -> StudyAnalyticsScreen(
                        report = weeklyReport,
                        onBack = { viewModel.navigateTo(AppScreen.MORE) }
                    )

                    AppScreen.DIGITAL_WELLBEING -> DigitalWellbeingScreen(
                        stats = wellbeingStats,
                        onRefresh = { viewModel.refreshWellbeingStats() },
                        onBack = { viewModel.navigateTo(AppScreen.MORE) },
                        onOpenFocusGuard = { viewModel.navigateTo(AppScreen.FOCUS_MODE) }
                    )

                    AppScreen.REVISION -> RevisionScreen(
                        categorized = categorizedRevisions,
                        userSettings = userSettings ?: UserSettingsEntity(),
                        onBack = { viewModel.navigateTo(AppScreen.MORE) },
                        onCompleteRevision = { viewModel.completeRevision(it) },
                        onSnoozeRevision = { item, days -> viewModel.snoozeRevision(item, days) },
                        onRescheduleRevision = { item, date -> viewModel.rescheduleRevision(item, date) },
                        onUpdateIntervals = { csv, notifEnabled ->
                            val base = userSettings ?: UserSettingsEntity()
                            viewModel.updateSettings(
                                base.copy(
                                    revisionIntervalsCsv = csv,
                                    revisionNotificationsEnabled = notifEnabled
                                )
                            )
                        },
                        onNavigateToTracker = { viewModel.navigateTo(AppScreen.TRACKER) }
                    )

                    AppScreen.FOCUS_MODE -> {}
                }
            }
        }
    }

    if (showImportExportDialog) {
        ImportExportDialog(
            chapters = allChapters,
            onImportText = { content -> viewModel.importData(content) },
            onImportXlsxBytes = { bytes -> viewModel.importXlsxWorkbook(bytes) },
            onSyncScheduleFromBacklog = { viewModel.syncScheduleFromBacklog() },
            onLoadTemplate = { viewModel.seedStandardTemplate() },
            onClearAll = { viewModel.clearAllData() },
            onDismiss = { showImportExportDialog = false },
            workbookConfig = workbookConfig,
            pendingOutboxCount = pendingOutboxCount,
            unresolvedConflicts = unresolvedConflicts,
            isSyncInProgress = isSyncInProgress,
            connectedGoogleEmail = connectedGoogleEmail,
            pendingAuthConsentIntent = pendingAuthConsentIntent,
            onRequestGoogleAuth = { viewModel.requestGoogleSheetsAuth() },
            onGoogleAuthConsentResult = { intent -> viewModel.onGoogleAuthConsentResult(intent) },
            onClearPendingAuthConsent = { viewModel.clearPendingAuthConsent() },
            onLinkSpreadsheet = { sheetIdOrUrl, xlsxBytes, xlsxName, convertOnDrive, newTitle ->
                viewModel.linkGoogleSpreadsheet(
                    spreadsheetIdOrUrl = sheetIdOrUrl,
                    xlsxBytesToImport = xlsxBytes,
                    xlsxFileName = xlsxName,
                    convertXlsxOnDrive = convertOnDrive,
                    createNewSpreadsheetTitle = newTitle
                )
            },
            onSyncNow = { viewModel.syncWithGoogleSheetsNow() },
            onRefreshFromSheet = { viewModel.refreshFromGoogleSheetNow() },
            onResolveConflict = { conflictId, choice, mergedJson ->
                viewModel.resolveSyncConflict(conflictId, choice, mergedJson)
            },
            onDisconnectGoogleSheet = { viewModel.disconnectGoogleSheet(clearOutbox = false) }
        )
    }
}
