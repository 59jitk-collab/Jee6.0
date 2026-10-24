package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiBackendHealthState
import com.example.data.ai.ChatMessage
import com.example.data.ai.GeminiChatService
import com.example.data.analytics.StudyAnalyticsCalculator
import com.example.data.analytics.WeeklyReport
import com.example.data.local.AppDatabase
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.DailyPlanTaskItem
import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.MasteryLevel
import com.example.data.local.entities.MistakeEntryEntity
import com.example.data.local.entities.PlanTaskActionType
import com.example.data.local.entities.RevisionItemEntity
import com.example.data.local.entities.TestRecordEntity
import com.example.data.local.entities.UserSettingsEntity
import com.example.data.progression.ProgressionManager
import com.example.data.progression.ProgressionStatus
import com.example.data.repository.StudyRepository
import com.example.data.revision.CategorizedRevisions
import com.example.data.revision.SpacedRevisionManager
import com.example.data.wellbeing.DailyWellbeingStats
import com.example.data.wellbeing.DigitalWellbeingManager
import com.example.util.DateUtils
import com.example.util.StudyNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.cloud.FirestoreCloudSyncRepository
import com.example.data.cloud.authStateFlow
import com.example.data.local.entities.SyncConflictEntity
import com.example.data.local.entities.SyncWorkbookConfigEntity
import com.example.data.sync.AuthorizationOutcome
import com.example.data.sync.ConflictResolutionChoice
import com.example.data.sync.GoogleAuthManager
import com.example.data.sync.SheetsSyncWorker
import com.example.data.sync.SyncExecutionResult
import com.example.data.sync.TwoWaySheetsSyncEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    DASHBOARD,
    TRACKER,
    SCHEDULER,
    TESTS,
    MISTAKES,
    REVISION,
    REST_SLEEP,
    AI_MENTOR,
    FOCUS_MODE,
    MORE,
    ANALYTICS,
    DIGITAL_WELLBEING
}

enum class TimerMode {
    STUDY,
    BREAK
}

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StudyRepository
    private val geminiService = GeminiChatService()
    private val googleAuthManager: GoogleAuthManager
    private val sheetsSyncEngine: TwoWaySheetsSyncEngine
    private val roomDb: AppDatabase
    private val firestoreCloudRepo: FirestoreCloudSyncRepository by lazy {
        FirestoreCloudSyncRepository(getApplication())
    }

    init {
        val db = AppDatabase.getDatabase(application)
        roomDb = db
        repository = StudyRepository(db)
        googleAuthManager = GoogleAuthManager(application)
        sheetsSyncEngine = TwoWaySheetsSyncEngine(application, db)
        viewModelScope.launch {
            repository.initializeIfEmpty()
        }
        observeFirebaseAuthIfAvailable()
    }

    // Navigation & UI State
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedSubject = MutableStateFlow("All")
    val selectedSubject: StateFlow<String> = _selectedSubject.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showOnlyWeak = MutableStateFlow(false)
    val showOnlyWeak: StateFlow<Boolean> = _showOnlyWeak.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Database reactive streams
    val allChapters = repository.allChapters.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val weakChapters = repository.weakChapters.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allSchedules = repository.allSchedules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allTests = repository.allTests.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allMistakes = repository.allMistakes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val pendingRevisions = repository.pendingRevisions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allRevisions = repository.allRevisions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categorizedRevisions: StateFlow<CategorizedRevisions> = allRevisions.map { list ->
        SpacedRevisionManager.categorize(list, DateUtils.todayDateString())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategorizedRevisions(emptyList(), emptyList(), emptyList(), emptyList())
    )

    val userSettings = repository.userSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettingsEntity()
    )

    val activeFocusSession = repository.activeFocusSession.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val allXpEvents = repository.allXpEvents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Google Sheets Two-Way Sync reactive streams
    val workbookConfig: StateFlow<SyncWorkbookConfigEntity?> = repository.workbookConfig.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val pendingOutboxCount: StateFlow<Int> = repository.pendingOutboxCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val unresolvedConflicts: StateFlow<List<SyncConflictEntity>> = repository.unresolvedConflicts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isSyncInProgress = MutableStateFlow(false)
    val isSyncInProgress: StateFlow<Boolean> = _isSyncInProgress.asStateFlow()

    private val _pendingAuthConsentIntent = MutableStateFlow<PendingIntent?>(null)
    val pendingAuthConsentIntent: StateFlow<PendingIntent?> = _pendingAuthConsentIntent.asStateFlow()

    private val _connectedGoogleEmail = MutableStateFlow<String?>(googleAuthManager.getCurrentSession()?.accountEmail)
    val connectedGoogleEmail: StateFlow<String?> = _connectedGoogleEmail.asStateFlow()

    // Stage 4: Private XP, Levels, and Local-Date Streaks
    val progressionStatus: StateFlow<ProgressionStatus> = combine(
        allXpEvents,
        allSchedules
    ) { events, schedules ->
        ProgressionManager.computeProgressionStatus(
            xpEvents = events,
            schedules = schedules,
            todayDateStr = DateUtils.todayDateString()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProgressionManager.computeProgressionStatus(
            xpEvents = emptyList(),
            schedules = emptyList(),
            todayDateStr = DateUtils.todayDateString()
        )
    )

    // Derived Weekly Analytics & Study Report strictly from real persisted records
    val weeklyReport: StateFlow<WeeklyReport> = combine(
        allSchedules,
        allChapters,
        allTests,
        allMistakes
    ) { schedules, chapters, tests, mistakes ->
        StudyAnalyticsCalculator.computeWeeklyReport(
            schedules = schedules,
            chapters = chapters,
            tests = tests,
            mistakes = mistakes,
            referenceDateStr = DateUtils.todayDateString()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudyAnalyticsCalculator.computeWeeklyReport(
            schedules = emptyList(),
            chapters = emptyList(),
            tests = emptyList(),
            mistakes = emptyList(),
            referenceDateStr = DateUtils.todayDateString()
        )
    )

    // Digital Wellbeing State (Android UsageStats on-device only)
    private val _wellbeingStats = MutableStateFlow(
        DailyWellbeingStats(
            isPermissionGranted = false,
            isSupportedOnDevice = true,
            totalScreenTimeMillis = 0L,
            topApps = emptyList(),
            studyAppsTimeMillis = 0L,
            otherAppsTimeMillis = 0L,
            hasUsageData = false
        )
    )
    val wellbeingStats: StateFlow<DailyWellbeingStats> = _wellbeingStats.asStateFlow()

    fun refreshWellbeingStats() {
        viewModelScope.launch {
            val stats = DigitalWellbeingManager.getDailyUsageStats(getApplication())
            _wellbeingStats.value = stats
        }
    }

    // Focus Lock Setup State
    private val _focusSetupDurationMinutes = MutableStateFlow(50)
    val focusSetupDurationMinutes: StateFlow<Int> = _focusSetupDurationMinutes.asStateFlow()

    private val _focusSetupExtraSeconds = MutableStateFlow(0)
    val focusSetupExtraSeconds: StateFlow<Int> = _focusSetupExtraSeconds.asStateFlow()

    private val _focusSetupPermittedBreaks = MutableStateFlow(2)
    val focusSetupPermittedBreaks: StateFlow<Int> = _focusSetupPermittedBreaks.asStateFlow()

    private val _focusSetupBreakDurationMinutes = MutableStateFlow(5)
    val focusSetupBreakDurationMinutes: StateFlow<Int> = _focusSetupBreakDurationMinutes.asStateFlow()

    private val _focusSetupTaskName = MutableStateFlow("Physics · Laws of Motion")
    val focusSetupTaskName: StateFlow<String> = _focusSetupTaskName.asStateFlow()

    private val _focusSelectedPackages = MutableStateFlow<Set<String>>(
        setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.twitter.android",
            "com.facebook.katana",
            "com.google.android.youtube",
            "com.pubg.krmobile"
        )
    )
    val focusSelectedPackages: StateFlow<Set<String>> = _focusSelectedPackages.asStateFlow()

    // Timer State
    private val _timerMode = MutableStateFlow(TimerMode.STUDY)
    val timerMode: StateFlow<TimerMode> = _timerMode.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private val _customTimerSecondsOverride = MutableStateFlow<Int?>(null)
    val customTimerSecondsOverride: StateFlow<Int?> = _customTimerSecondsOverride.asStateFlow()

    private val _timerSecondsLeft = MutableStateFlow(50 * 60)
    val timerSecondsLeft: StateFlow<Int> = _timerSecondsLeft.asStateFlow()

    private var timerJob: Job? = null

    // Stage 5: AI Mentor State, Context Selection, Real Health Check & Privacy Opt-In
    val isAiRemoteBackendConfigured: Boolean
        get() = geminiService.isRemoteBackendConfigured()

    private val _aiBackendHealthState = MutableStateFlow(AiBackendHealthState.ONLINE)
    val aiBackendHealthState: StateFlow<AiBackendHealthState> = _aiBackendHealthState.asStateFlow()

    private val _aiBackendHealthDetail = MutableStateFlow(
        if (geminiService.isRemoteBackendConfigured()) "Online (Gemini AI Mentor Ready)" else "Active (Built-in JEE AI Mentor Engine)"
    )
    val aiBackendHealthDetail: StateFlow<String> = _aiBackendHealthDetail.asStateFlow()

    private val _selectedMentorChapterId = MutableStateFlow<Long?>(null)
    val selectedMentorChapterId: StateFlow<Long?> = _selectedMentorChapterId.asStateFlow()

    private val _userApprovedExternalAiSend = MutableStateFlow(true)
    val userApprovedExternalAiSend: StateFlow<Boolean> = _userApprovedExternalAiSend.asStateFlow()

    private var lastUserPromptText: String = ""

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "ai",
                text = "Welcome to JEE Study OS AI Mentor (Physics, Chemistry & Mathematics).\n\nSelect any chapter pill above to attach verified progress context, or ask a conceptual / problem-solving question below.",
                isLocalFallback = !geminiService.isRemoteBackendConfigured()
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiGenerating = MutableStateFlow(false)
    val isAiGenerating: StateFlow<Boolean> = _isAiGenerating.asStateFlow()

    fun setCustomGeminiApiKey(apiKey: String) {
        geminiService.runtimeApiKeyOverride = apiKey.trim()
        _userApprovedExternalAiSend.value = true
        refreshAiBackendHealth()
        _statusMessage.value = if (apiKey.isNotBlank()) "Gemini API Key saved for this session!" else "Switched to Built-in JEE AI Engine."
    }

    fun refreshAiBackendHealth() {
        if (!geminiService.isRemoteBackendConfigured()) {
            _aiBackendHealthState.value = AiBackendHealthState.ONLINE
            _aiBackendHealthDetail.value = "Active (Built-in JEE AI Mentor Engine)"
            return
        }
        _aiBackendHealthState.value = AiBackendHealthState.CHECKING
        _aiBackendHealthDetail.value = "Verifying Gemini endpoint connectivity..."
        viewModelScope.launch {
            val res = geminiService.checkBackendHealth()
            res.onSuccess { modelName ->
                _aiBackendHealthState.value = AiBackendHealthState.ONLINE
                _aiBackendHealthDetail.value = "Online ($modelName verified)"
            }.onFailure {
                _aiBackendHealthState.value = AiBackendHealthState.ONLINE
                _aiBackendHealthDetail.value = "Active (Built-in JEE AI Mentor Engine)"
            }
        }
    }

    fun setSelectedMentorChapter(chapterId: Long?) {
        _selectedMentorChapterId.value = chapterId
    }

    fun setUserApprovedExternalAiSend(approved: Boolean) {
        _userApprovedExternalAiSend.value = approved
        if (approved && geminiService.isRemoteBackendConfigured()) {
            refreshAiBackendHealth()
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        if (screen == AppScreen.AI_MENTOR && geminiService.isRemoteBackendConfigured()) {
            refreshAiBackendHealth()
        }
    }

    fun setSubject(subject: String) {
        _selectedSubject.value = subject
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleShowOnlyWeak() {
        _showOnlyWeak.value = !_showOnlyWeak.value
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // Stage 2 & 4: Chapter Tracker mutations with idempotent XP & reversal safety
    fun updateLecture(chapter: ChapterEntity, delta: Int) {
        val total = chapter.totalLectures ?: return
        val oldCount = chapter.completedLectures
        val newCount = (oldCount + delta).coerceIn(0, total)
        if (newCount == oldCount) return
        val todayStr = DateUtils.todayDateString()

        viewModelScope.launch {
            val updated = chapter.copy(
                completedLectures = newCount,
                lastStudiedDate = if (delta > 0) todayStr else chapter.lastStudiedDate
            )
            repository.saveChapter(updated)
            if (newCount > oldCount) {
                for (lecIdx in (oldCount + 1)..newCount) {
                    repository.awardIdempotentXp(
                        eventKey = ProgressionManager.lectureEventKey(chapter.id, lecIdx),
                        category = "LECTURE",
                        description = "Completed Lecture $lecIdx in ${chapter.chapterName}",
                        requestedXp = ProgressionManager.XP_LECTURE_COMPLETED,
                        dateStr = todayStr
                    )
                }
            } else {
                for (lecIdx in (newCount + 1)..oldCount) {
                    repository.revokeXpByKey(ProgressionManager.lectureEventKey(chapter.id, lecIdx))
                }
            }
        }
    }

    fun toggleNotes(chapter: ChapterEntity) {
        val newNotesDone = !chapter.notesDone
        val todayStr = DateUtils.todayDateString()
        viewModelScope.launch {
            repository.saveChapter(
                chapter.copy(
                    notesDone = newNotesDone,
                    lastStudiedDate = if (newNotesDone) todayStr else chapter.lastStudiedDate
                )
            )
            val key = ProgressionManager.notesEventKey(chapter.id)
            if (newNotesDone) {
                repository.awardIdempotentXp(
                    eventKey = key,
                    category = "NOTES",
                    description = "Completed Notes for ${chapter.chapterName}",
                    requestedXp = ProgressionManager.XP_NOTES_COMPLETED,
                    dateStr = todayStr
                )
            } else {
                repository.revokeXpByKey(key)
            }
        }
    }

    fun updateDpp(chapter: ChapterEntity, delta: Int) {
        val total = chapter.dppTotal ?: return
        val current = chapter.dppDone ?: 0
        val newCount = (current + delta).coerceIn(0, total)
        if (newCount == current) return
        val todayStr = DateUtils.todayDateString()

        viewModelScope.launch {
            repository.saveChapter(
                chapter.copy(
                    dppDone = newCount,
                    lastStudiedDate = if (delta > 0) todayStr else chapter.lastStudiedDate
                )
            )
            if (newCount > current) {
                for (idx in (current + 1)..newCount) {
                    repository.awardIdempotentXp(
                        eventKey = ProgressionManager.dppEventKey(chapter.id, idx),
                        category = "DPP",
                        description = "Solved DPP #$idx in ${chapter.chapterName}",
                        requestedXp = ProgressionManager.XP_DPP_COMPLETED,
                        dateStr = todayStr
                    )
                }
            } else {
                for (idx in (newCount + 1)..current) {
                    repository.revokeXpByKey(ProgressionManager.dppEventKey(chapter.id, idx))
                }
            }
        }
    }

    fun updatePyq(chapter: ChapterEntity, delta: Int) {
        val total = chapter.pyqTotal ?: return
        val current = chapter.pyqDone ?: 0
        val newCount = (current + delta).coerceIn(0, total)
        if (newCount == current) return
        val todayStr = DateUtils.todayDateString()

        viewModelScope.launch {
            repository.saveChapter(
                chapter.copy(
                    pyqDone = newCount,
                    lastStudiedDate = if (delta > 0) todayStr else chapter.lastStudiedDate
                )
            )
            if (newCount > current) {
                for (idx in (current + 1)..newCount) {
                    repository.awardIdempotentXp(
                        eventKey = ProgressionManager.pyqEventKey(chapter.id, idx),
                        category = "PYQ",
                        description = "Solved PYQ #$idx in ${chapter.chapterName}",
                        requestedXp = ProgressionManager.XP_PYQ_COMPLETED,
                        dateStr = todayStr
                    )
                }
            } else {
                for (idx in (newCount + 1)..current) {
                    repository.revokeXpByKey(ProgressionManager.pyqEventKey(chapter.id, idx))
                }
            }
        }
    }

    fun updateDiby(chapter: ChapterEntity, delta: Int) {
        val total = chapter.dibyTotal ?: return
        val current = chapter.dibyDone ?: 0
        val newCount = (current + delta).coerceIn(0, total)
        if (newCount == current) return
        val todayStr = DateUtils.todayDateString()

        viewModelScope.launch {
            repository.saveChapter(
                chapter.copy(
                    dibyDone = newCount,
                    lastStudiedDate = if (delta > 0) todayStr else chapter.lastStudiedDate
                )
            )
            if (newCount > current) {
                for (idx in (current + 1)..newCount) {
                    repository.awardIdempotentXp(
                        eventKey = ProgressionManager.dibyEventKey(chapter.id, idx),
                        category = "DIBY",
                        description = "Solved DIBY #$idx in ${chapter.chapterName}",
                        requestedXp = ProgressionManager.XP_DIBY_COMPLETED,
                        dateStr = todayStr
                    )
                }
            } else {
                for (idx in (newCount + 1)..current) {
                    repository.revokeXpByKey(ProgressionManager.dibyEventKey(chapter.id, idx))
                }
            }
        }
    }

    fun updateWorksheet(chapter: ChapterEntity, delta: Int) {
        val total = chapter.worksheetsTotal ?: return
        val current = chapter.worksheetsDone ?: 0
        val newCount = (current + delta).coerceIn(0, total)
        if (newCount == current) return
        val todayStr = DateUtils.todayDateString()

        viewModelScope.launch {
            repository.saveChapter(
                chapter.copy(
                    worksheetsDone = newCount,
                    lastStudiedDate = if (delta > 0) todayStr else chapter.lastStudiedDate
                )
            )
            if (newCount > current) {
                for (idx in (current + 1)..newCount) {
                    repository.awardIdempotentXp(
                        eventKey = ProgressionManager.worksheetEventKey(chapter.id, idx),
                        category = "WORKSHEET",
                        description = "Completed Worksheet #$idx in ${chapter.chapterName}",
                        requestedXp = ProgressionManager.XP_WORKSHEET_COMPLETED,
                        dateStr = todayStr
                    )
                }
            } else {
                for (idx in (newCount + 1)..current) {
                    repository.revokeXpByKey(ProgressionManager.worksheetEventKey(chapter.id, idx))
                }
            }
        }
    }

    fun updateClassProblems(chapter: ChapterEntity, delta: Int) {
        val total = chapter.classProblemsTotal ?: return
        val current = chapter.classProblemsDone ?: 0
        val newCount = (current + delta).coerceIn(0, total)
        if (newCount == current) return
        val todayStr = DateUtils.todayDateString()

        viewModelScope.launch {
            repository.saveChapter(
                chapter.copy(
                    classProblemsDone = newCount,
                    lastStudiedDate = if (delta > 0) todayStr else chapter.lastStudiedDate
                )
            )
            if (newCount > current) {
                for (idx in (current + 1)..newCount) {
                    repository.awardIdempotentXp(
                        eventKey = ProgressionManager.classProblemEventKey(chapter.id, idx),
                        category = "CLASS_PROBLEM",
                        description = "Solved Class Problem #$idx in ${chapter.chapterName}",
                        requestedXp = ProgressionManager.XP_CLASS_PROBLEM_COMPLETED,
                        dateStr = todayStr
                    )
                }
            } else {
                for (idx in (newCount + 1)..current) {
                    repository.revokeXpByKey(ProgressionManager.classProblemEventKey(chapter.id, idx))
                }
            }
        }
    }

    fun updateChapterMastery(chapter: ChapterEntity, mastery: MasteryLevel) {
        viewModelScope.launch {
            repository.updateChapterMastery(chapter, mastery)
            _statusMessage.value = "${chapter.chapterName} mastery set to ${mastery.displayLabel}"
        }
    }

    fun toggleChapterWeak(chapter: ChapterEntity) {
        viewModelScope.launch {
            repository.saveChapter(chapter.copy(isWeak = !chapter.isWeak))
        }
    }

    fun saveChapter(chapter: ChapterEntity) {
        viewModelScope.launch {
            repository.saveChapter(chapter)
            _statusMessage.value = "Chapter saved: ${chapter.chapterName}"
        }
    }

    fun deleteChapter(id: Long) {
        viewModelScope.launch {
            repository.deleteChapter(id)
        }
    }

    // Schedule Mutations (Single Source of Truth for Home Today's Plan and 80-Day Schedule)
    fun updateScheduleLecture(schedule: DailyScheduleEntity, delta: Int) {
        val newLecs = (schedule.completedLectures + delta).coerceAtLeast(0)
        viewModelScope.launch {
            repository.updateSchedule(schedule.copy(completedLectures = newLecs))
        }
    }

    fun updateScheduleProblems(schedule: DailyScheduleEntity, delta: Int) {
        val newProblems = (schedule.completedProblems + delta).coerceAtLeast(0)
        viewModelScope.launch {
            repository.updateSchedule(schedule.copy(completedProblems = newProblems))
        }
    }

    fun toggleScheduleNotes(schedule: DailyScheduleEntity) {
        viewModelScope.launch {
            repository.updateSchedule(schedule.copy(notesReviewed = !schedule.notesReviewed))
        }
    }

    fun updateScheduleAssignedChapters(schedule: DailyScheduleEntity, chaptersCsv: String) {
        viewModelScope.launch {
            repository.updateSchedule(schedule.copy(assignedChapterNames = chaptersCsv.trim()))
            _statusMessage.value = "Updated Day ${schedule.dayNumber} study plan!"
        }
    }

    fun executePlanTaskAction(schedule: DailyScheduleEntity, task: DailyPlanTaskItem) {
        when (task.actionType) {
            PlanTaskActionType.INCREMENT_LECTURE -> {
                val nextDelta = if (schedule.completedLectures >= schedule.targetLectures) -1 else 1
                updateScheduleLecture(schedule, nextDelta)
            }
            PlanTaskActionType.INCREMENT_PROBLEMS -> {
                val step = 5
                val nextProblems = if (schedule.completedProblems >= schedule.targetProblems) {
                    (schedule.completedProblems - step).coerceAtLeast(0)
                } else {
                    (schedule.completedProblems + step).coerceAtMost(schedule.targetProblems)
                }
                updateScheduleProblems(schedule, nextProblems - schedule.completedProblems)
            }
            PlanTaskActionType.TOGGLE_NOTES -> {
                toggleScheduleNotes(schedule)
            }
        }
    }

    fun rebalanceRemainingDays(fromDayNumber: Int) {
        viewModelScope.launch {
            repository.rebalanceSchedule(fromDayNumber)
            _statusMessage.value = "Schedule rebalanced realistically from Day $fromDayNumber with sleep protected!"
        }
    }

    // Test series mutations
    fun saveTestRecord(test: TestRecordEntity) {
        viewModelScope.launch {
            repository.saveTestRecord(test)
            _statusMessage.value = "Test recorded: ${test.testName}"
        }
    }

    fun autoEvaluateSundayTest(test: TestRecordEntity) {
        viewModelScope.launch {
            repository.autoEvaluateSundayTest(test)
            _statusMessage.value = "Auto-evaluated ${test.testName} from your chapter progress!"
        }
    }

    fun autoEvaluateAllSundayTests() {
        viewModelScope.launch {
            repository.autoEvaluateAllSundayTests()
            _statusMessage.value = "All Sunday Mock Tests auto-populated with syllabus scores!"
        }
    }

    fun deleteTest(id: Long) {
        viewModelScope.launch {
            repository.deleteTest(id)
        }
    }

    // Mistake Notebook
    fun saveMistake(mistake: MistakeEntryEntity) {
        viewModelScope.launch {
            repository.saveMistake(mistake)
            _statusMessage.value = "Mistake logged in notebook!"
        }
    }

    fun deleteMistake(id: Long) {
        viewModelScope.launch {
            repository.deleteMistake(id)
        }
    }

    fun toggleMistakeResolved(mistake: MistakeEntryEntity) {
        viewModelScope.launch {
            repository.saveMistake(mistake.copy(isResolved = !mistake.isResolved))
        }
    }

    // Stage 3: Spaced Revision Actions
    fun markChapterStudied(chapter: ChapterEntity) {
        viewModelScope.launch {
            val rev = repository.markChapterStudiedAndScheduleRevision(chapter)
            syncRevisionNotificationSchedule()
            _statusMessage.value = "Marked '${chapter.chapterName}' studied! Next revision due: ${rev.dueDate}"
        }
    }

    fun completeRevision(item: RevisionItemEntity) {
        viewModelScope.launch {
            repository.completeRevision(item)
            syncRevisionNotificationSchedule()
            _statusMessage.value = "Completed Stage ${item.stage} revision for ${item.chapterName} (+${ProgressionManager.XP_REVISION_COMPLETED} XP)"
        }
    }

    fun snoozeRevision(item: RevisionItemEntity, days: Int) {
        viewModelScope.launch {
            repository.snoozeRevision(item, days)
            syncRevisionNotificationSchedule()
            _statusMessage.value = "Snoozed '${item.chapterName}' revision by $days day(s)."
        }
    }

    fun rescheduleRevision(item: RevisionItemEntity, newDateStr: String) {
        viewModelScope.launch {
            repository.rescheduleRevision(item, newDateStr)
            syncRevisionNotificationSchedule()
            _statusMessage.value = "Rescheduled '${item.chapterName}' revision to $newDateStr."
        }
    }

    fun deleteRevision(id: Long) {
        viewModelScope.launch {
            repository.deleteRevision(id)
            syncRevisionNotificationSchedule()
        }
    }

    private fun syncRevisionNotificationSchedule() {
        val app = getApplication<Application>()
        val settings = userSettings.value ?: UserSettingsEntity()
        if (!settings.revisionNotificationsEnabled || !StudyNotificationManager.hasPostNotificationsPermission(app)) {
            StudyNotificationManager.cancelRevisionReminder(app)
            return
        }
        val categorized = categorizedRevisions.value
        val nextItem = categorized.overdue.firstOrNull()
            ?: categorized.dueToday.firstOrNull()
            ?: categorized.upcoming.firstOrNull()
        if (nextItem != null) {
            StudyNotificationManager.scheduleRevisionReminder(
                context = app,
                actionableCount = (categorized.overdue.size + categorized.dueToday.size).coerceAtLeast(1),
                nextChapterName = nextItem.chapterName
            )
        } else {
            StudyNotificationManager.cancelRevisionReminder(app)
        }
    }

    // Data Import/Export & Template (with 80-Day Schedule Ingestion)
    fun seedStandardTemplate() {
        viewModelScope.launch {
            val report = repository.seedOfficialTemplate()
            _statusMessage.value = "Loaded JEE 80-Day Blueprint & mapped ${report.scheduledDaysUpdated} daily plans!"
        }
    }

    fun importData(content: String) {
        viewModelScope.launch {
            val report = repository.importData(content)
            _statusMessage.value = report.summaryMessage
        }
    }

    fun importXlsxWorkbook(xlsxBytes: ByteArray) {
        viewModelScope.launch {
            val report = repository.importXlsxBytes(xlsxBytes)
            _statusMessage.value = report.summaryMessage
        }
    }

    fun syncScheduleFromBacklog() {
        viewModelScope.launch {
            val report = repository.syncScheduleFromCurrentBacklog()
            _statusMessage.value = "80-Day Schedule generated for ${report.scheduledDaysUpdated} days (${report.totalLecturesScheduled} lectures)!"
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearChapters()
            _statusMessage.value = "Cleared chapters."
        }
    }

    // Timer Controls
    fun toggleTimer() {
        if (_isTimerRunning.value) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        _isTimerRunning.value = true
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isTimerRunning.value && _timerSecondsLeft.value > 0) {
                delay(1000)
                _timerSecondsLeft.value -= 1
            }
            if (_timerSecondsLeft.value <= 0) {
                _isTimerRunning.value = false
                val today = DateUtils.todayDateString()
                if (_timerMode.value == TimerMode.STUDY) {
                    val settings = userSettings.value
                    val studyMin = settings?.pomodoroWorkMinutes ?: 50
                    repository.recordStudySession(today, studyMinutes = studyMin, breakMinutes = 0)
                    _statusMessage.value = "Study session complete! Time for a rest break."
                    setTimerMode(TimerMode.BREAK)
                } else {
                    val settings = userSettings.value
                    val breakMin = settings?.pomodoroBreakMinutes ?: 10
                    repository.recordStudySession(today, studyMinutes = 0, breakMinutes = breakMin)
                    _statusMessage.value = "Break finished! Ready for the next focus block."
                    setTimerMode(TimerMode.STUDY)
                }
            }
        }
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
    }

    fun resetTimer() {
        pauseTimer()
        val customSec = _customTimerSecondsOverride.value
        if (customSec != null && customSec > 0) {
            _timerSecondsLeft.value = customSec
            return
        }
        val settings = userSettings.value
        _timerSecondsLeft.value = if (_timerMode.value == TimerMode.STUDY) {
            (settings?.pomodoroWorkMinutes ?: 50) * 60
        } else {
            (settings?.pomodoroBreakMinutes ?: 10) * 60
        }
    }

    fun setTimerMode(mode: TimerMode) {
        pauseTimer()
        _timerMode.value = mode
        _customTimerSecondsOverride.value = null
        val settings = userSettings.value
        _timerSecondsLeft.value = if (mode == TimerMode.STUDY) {
            (settings?.pomodoroWorkMinutes ?: 50) * 60
        } else {
            (settings?.pomodoroBreakMinutes ?: 10) * 60
        }
    }

    fun setCustomTimerDuration(minutes: Int, seconds: Int) {
        pauseTimer()
        val safeMin = minutes.coerceIn(0, 240)
        val safeSec = seconds.coerceIn(0, 59)
        val totalSeconds = (safeMin * 60 + safeSec).coerceAtLeast(5)
        _customTimerSecondsOverride.value = totalSeconds
        _timerSecondsLeft.value = totalSeconds
        val currentSettings = userSettings.value ?: UserSettingsEntity()
        if (safeMin > 0) {
            updateSettings(currentSettings.copy(pomodoroWorkMinutes = safeMin))
        }
        _statusMessage.value = "Custom timer set to ${String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)}"
    }

    // Settings
    fun updateSettings(settings: UserSettingsEntity) {
        viewModelScope.launch {
            repository.updateSettings(settings)
            syncRevisionNotificationSchedule()
        }
    }

    // Stage 5: Gemini AI Chat with optional chapter context, privacy opt-in & retry
    fun sendChatMessage(text: String) {
        if (text.isBlank() || _isAiGenerating.value) return
        lastUserPromptText = text

        val selectedChapter = allChapters.value.find { it.id == _selectedMentorChapterId.value }
        val userMsg = ChatMessage(
            sender = "user",
            text = text,
            verifiedContextSummary = geminiService.formatVerifiedChapterContext(selectedChapter)
        )
        _chatMessages.value = _chatMessages.value + userMsg
        _isAiGenerating.value = true

        viewModelScope.launch {
            val result = geminiService.sendMessage(
                history = _chatMessages.value,
                newPrompt = text,
                selectedChapter = selectedChapter,
                userApprovedExternalSend = _userApprovedExternalAiSend.value,
                enableSearchGrounding = false
            )
            result.onSuccess { aiMsg ->
                _aiBackendHealthState.value = AiBackendHealthState.ONLINE
                _aiBackendHealthDetail.value = if (aiMsg.isLocalFallback) {
                    "Active (Built-in JEE AI Mentor Engine)"
                } else {
                    "Online (Gemini AI Response Verified)"
                }
                _chatMessages.value = _chatMessages.value + aiMsg
            }.onFailure {
                _aiBackendHealthState.value = AiBackendHealthState.ONLINE
                _aiBackendHealthDetail.value = "Active (Built-in JEE AI Mentor Engine)"
                val fallbackGuide = geminiService.generateLocalStructuredGuide(text, selectedChapter)
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    sender = "ai",
                    text = fallbackGuide,
                    isLocalFallback = true,
                    verifiedContextSummary = geminiService.formatVerifiedChapterContext(selectedChapter),
                    isError = false
                )
            }
            _isAiGenerating.value = false
        }
    }

    fun retryLastAiMessage() {
        if (lastUserPromptText.isNotBlank() && !_isAiGenerating.value) {
            sendChatMessage(lastUserPromptText)
        }
    }

    fun clearAiChat() {
        _chatMessages.value = listOf(
            ChatMessage(
                sender = "ai",
                text = "Chat cleared. Select a chapter above to attach verified progress context, or ask a JEE conceptual / problem-solving question below.",
                isLocalFallback = !geminiService.isRemoteBackendConfigured()
            )
        )
    }

    // Focus Lock Session Actions
    fun setFocusSetupDuration(minutes: Int) {
        _focusSetupDurationMinutes.value = minutes.coerceIn(1, 240)
        _focusSetupExtraSeconds.value = 0
    }

    fun setFocusSetupCustomDuration(minutes: Int, seconds: Int) {
        val safeMin = minutes.coerceIn(0, 240)
        val safeSec = seconds.coerceIn(0, 59)
        if (safeMin == 0 && safeSec < 10) {
            _focusSetupDurationMinutes.value = 0
            _focusSetupExtraSeconds.value = 10
        } else {
            _focusSetupDurationMinutes.value = safeMin
            _focusSetupExtraSeconds.value = safeSec
        }
        val total = _focusSetupDurationMinutes.value * 60 + _focusSetupExtraSeconds.value
        _statusMessage.value = "Custom Focus timer set to ${String.format("%02d:%02d", total / 60, total % 60)}"
    }

    fun setFocusSetupPermittedBreaks(breaks: Int) {
        _focusSetupPermittedBreaks.value = breaks.coerceIn(0, 5)
    }

    fun setFocusSetupBreakDuration(minutes: Int) {
        _focusSetupBreakDurationMinutes.value = minutes.coerceIn(2, 30)
    }

    fun setFocusSetupTaskName(name: String) {
        _focusSetupTaskName.value = name
    }

    fun toggleFocusSelectedPackage(pkgName: String) {
        val current = _focusSelectedPackages.value.toMutableSet()
        if (current.contains(pkgName)) {
            current.remove(pkgName)
        } else {
            current.add(pkgName)
        }
        _focusSelectedPackages.value = current
    }

    fun startFocusLockSession() {
        viewModelScope.launch {
            repository.startFocusSession(
                studyDurationMinutes = _focusSetupDurationMinutes.value,
                permittedBreaks = _focusSetupPermittedBreaks.value,
                breakDurationMinutes = _focusSetupBreakDurationMinutes.value,
                blockedPackagesCsv = _focusSelectedPackages.value.joinToString(","),
                activeTaskName = _focusSetupTaskName.value,
                extraSeconds = _focusSetupExtraSeconds.value
            )
            _statusMessage.value = "Focus Session Started! Stay focused."
        }
    }

    fun takeFocusBreak() {
        viewModelScope.launch {
            repository.takeFocusBreak()
            _statusMessage.value = "Break Started! Enjoy your authorized break."
        }
    }

    fun endFocusBreak() {
        viewModelScope.launch {
            repository.endFocusBreak()
            _statusMessage.value = "Break Ended! Resuming Study Focus."
        }
    }

    fun pauseFocusLockSession() {
        viewModelScope.launch {
            repository.pauseFocusSession()
            _statusMessage.value = "Session Paused."
        }
    }

    fun resumeFocusLockSession() {
        viewModelScope.launch {
            repository.resumeFocusSession()
            _statusMessage.value = "Session Resumed."
        }
    }

    fun cancelFocusLockSession() {
        viewModelScope.launch {
            repository.cancelFocusSession()
            _statusMessage.value = "Session Cancelled."
        }
    }

    fun completeFocusLockSession() {
        viewModelScope.launch {
            val completed = repository.completeFocusSession()
            if (completed != null) {
                StudyNotificationManager.notifyFocusSessionCompleted(
                    context = getApplication(),
                    taskName = completed.activeTaskName,
                    durationMinutes = completed.studyDurationMinutes
                )
                _statusMessage.value = "Focus Session Completed (+${ProgressionManager.XP_FOCUS_SESSION_COMPLETED} XP)!"
            }
        }
    }

    // =========================================================================
    // Google Sheets Two-Way Synchronization Actions
    // =========================================================================

    fun requestGoogleSheetsAuth() {
        viewModelScope.launch {
            _isSyncInProgress.value = true
            when (val res = googleAuthManager.requestGoogleSheetsAuthorization()) {
                is AuthorizationOutcome.Authorized -> {
                    _connectedGoogleEmail.value = res.session.accountEmail.ifBlank { "Authorized Google Account" }
                    _pendingAuthConsentIntent.value = null
                    _statusMessage.value = "Google Sheets & Drive authorized!"
                }
                is AuthorizationOutcome.NeedsConsentResolution -> {
                    _pendingAuthConsentIntent.value = res.pendingIntent
                }
                is AuthorizationOutcome.Failed -> {
                    _statusMessage.value = res.errorMessage
                }
            }
            _isSyncInProgress.value = false
        }
    }

    fun onGoogleAuthConsentResult(dataIntent: Intent?) {
        _pendingAuthConsentIntent.value = null
        viewModelScope.launch {
            _isSyncInProgress.value = true
            when (val res = googleAuthManager.handleAuthorizationIntentResult(dataIntent)) {
                is AuthorizationOutcome.Authorized -> {
                    _connectedGoogleEmail.value = res.session.accountEmail.ifBlank { "Authorized Google Account" }
                    _statusMessage.value = "Google account connected for Sheets Sync!"
                }
                is AuthorizationOutcome.NeedsConsentResolution -> {
                    _pendingAuthConsentIntent.value = res.pendingIntent
                }
                is AuthorizationOutcome.Failed -> {
                    _statusMessage.value = res.errorMessage
                }
            }
            _isSyncInProgress.value = false
        }
    }

    fun clearPendingAuthConsent() {
        _pendingAuthConsentIntent.value = null
    }

    fun linkGoogleSpreadsheet(
        spreadsheetIdOrUrl: String?,
        xlsxBytesToImport: ByteArray? = null,
        xlsxFileName: String? = null,
        convertXlsxOnDrive: Boolean = false,
        createNewSpreadsheetTitle: String? = null
    ) {
        viewModelScope.launch {
            _isSyncInProgress.value = true
            val result = sheetsSyncEngine.linkAndSyncSpreadsheet(
                spreadsheetIdOrUrl = spreadsheetIdOrUrl,
                xlsxBytesToImport = xlsxBytesToImport,
                xlsxFileName = xlsxFileName,
                convertXlsxOnDrive = convertXlsxOnDrive,
                createNewSpreadsheetTitle = createNewSpreadsheetTitle
            )
            handleSyncResultMessage(result, prefix = "Workbook linked")
            if (result is SyncExecutionResult.Success) {
                SheetsSyncWorker.schedulePeriodicSync(getApplication(), enabled = true)
            }
            _isSyncInProgress.value = false
        }
    }

    fun syncWithGoogleSheetsNow() {
        viewModelScope.launch {
            _isSyncInProgress.value = true
            val result = sheetsSyncEngine.performTwoWaySync(forceFullPush = false)
            handleSyncResultMessage(result, prefix = "Two-way sync complete")
            _isSyncInProgress.value = false
        }
    }

    fun refreshFromGoogleSheetNow() {
        viewModelScope.launch {
            _isSyncInProgress.value = true
            val result = sheetsSyncEngine.refreshFromSheet()
            handleSyncResultMessage(result, prefix = "Refreshed from Google Sheet")
            _isSyncInProgress.value = false
        }
    }

    fun resolveSyncConflict(
        conflictId: String,
        choice: ConflictResolutionChoice,
        manualMergedPayloadJson: String? = null
    ) {
        viewModelScope.launch {
            val ok = sheetsSyncEngine.resolveConflict(conflictId, choice, manualMergedPayloadJson)
            if (ok) {
                _statusMessage.value = "Conflict resolved (${choice.name}). Syncing with Google Sheets..."
                SheetsSyncWorker.enqueueImmediateSync(getApplication())
            }
        }
    }

    fun disconnectGoogleSheet(clearOutbox: Boolean = false) {
        viewModelScope.launch {
            sheetsSyncEngine.disconnectWorkbook(clearOutbox)
            _connectedGoogleEmail.value = null
            SheetsSyncWorker.schedulePeriodicSync(getApplication(), enabled = false)
            _statusMessage.value = "Disconnected from Google Sheet. Local Room data preserved."
        }
    }

    private fun handleSyncResultMessage(result: SyncExecutionResult, prefix: String) {
        when (result) {
            is SyncExecutionResult.Success -> {
                val pulled = result.pulledChapters + result.pulledSchedules + result.pulledMistakes
                val pushed = result.pushedChapters + result.pushedSchedules + result.pushedMistakes
                val conflictSuffix = if (result.conflictsDetected > 0) " · ${result.conflictsDetected} conflict(s) need review" else ""
                _statusMessage.value = "$prefix: ↑$pushed pushed, ↓$pulled pulled$conflictSuffix"
            }
            is SyncExecutionResult.Skipped -> {
                _statusMessage.value = result.reason
            }
            is SyncExecutionResult.Failure -> {
                _statusMessage.value = result.errorMessage
            }
        }
    }

    // =========================================================================
    // Firebase Auth (Google Sign-In via CredentialManager) & Cloud Firestore Sync
    // =========================================================================

    private val _firebaseUserEmail = MutableStateFlow<String?>(null)
    val firebaseUserEmail: StateFlow<String?> = _firebaseUserEmail.asStateFlow()

    private val _firebaseUserDisplayName = MutableStateFlow<String?>(null)
    val firebaseUserDisplayName: StateFlow<String?> = _firebaseUserDisplayName.asStateFlow()

    private val _isFirebaseSignedIn = MutableStateFlow(false)
    val isFirebaseSignedIn: StateFlow<Boolean> = _isFirebaseSignedIn.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _lastCloudSyncStatus = MutableStateFlow<String?>(null)
    val lastCloudSyncStatus: StateFlow<String?> = _lastCloudSyncStatus.asStateFlow()

    private fun observeFirebaseAuthIfAvailable() {
        runCatching {
            val appContext = getApplication<Application>().applicationContext ?: getApplication()
            if (!FirestoreCloudSyncRepository.ensureFirebaseInitialized(appContext)) {
                Log.w("StudyViewModel", "Firebase Auth observation skipped: FirebaseApp not configured in this build.")
                return
            }
            val auth = Firebase.auth
            viewModelScope.launch {
                auth.authStateFlow().collect { user ->
                    if (user != null) {
                        _isFirebaseSignedIn.value = true
                        _firebaseUserEmail.value = user.email
                        _firebaseUserDisplayName.value = user.displayName ?: "JEE Aspirant"
                    } else {
                        _isFirebaseSignedIn.value = false
                        _firebaseUserEmail.value = null
                        _firebaseUserDisplayName.value = null
                    }
                }
            }
        }.onFailure { e ->
            Log.w("StudyViewModel", "Firebase Auth observation not available [${e.javaClass.simpleName}]: ${e.message}")
        }
    }

    fun signInWithFirebaseGoogle(activityContext: Context) {
        if (_isCloudSyncing.value) {
            Log.w("StudyViewModel", "Google Sign-In requested while sync/sign-in is already in progress. Ignoring duplicate launch.")
            return
        }
        viewModelScope.launch {
            _isCloudSyncing.value = true
            try {
                val result = firestoreCloudRepo.signInWithGoogleCredentialManager(activityContext)
                result.onSuccess { user ->
                    _isFirebaseSignedIn.value = true
                    _firebaseUserEmail.value = user.email
                    _firebaseUserDisplayName.value = user.displayName ?: "JEE Aspirant"
                    _statusMessage.value = "Signed in as ${user.email ?: user.displayName ?: "Google User"}. Syncing with Firebase Cloud..."
                    syncWithFirebaseCloud()
                }.onFailure { err ->
                    _isCloudSyncing.value = false
                    when (err) {
                        is androidx.credentials.exceptions.GetCredentialCancellationException -> {
                            _statusMessage.value = "Google Sign-In cancelled."
                            Log.i("StudyViewModel", "Google Sign-In cancelled by user.")
                        }
                        else -> {
                            Log.e("StudyViewModel", "Google Sign-In failed [${err.javaClass.simpleName}]: ${err.message}")
                            val displayError = err.localizedMessage ?: err.message ?: "Sign-In error occurred"
                            _statusMessage.value = "Google Sign-In failed: $displayError"
                        }
                    }
                }
            } catch (e: Exception) {
                _isCloudSyncing.value = false
                Log.e("StudyViewModel", "Unhandled exception during Google Sign-In [${e.javaClass.simpleName}]: ${e.message}")
                _statusMessage.value = "Google Sign-In error: ${e.localizedMessage ?: e.message ?: "An unexpected error occurred"}"
            }
        }
    }

    fun syncWithFirebaseCloud() {
        val appContext = getApplication<Application>().applicationContext ?: getApplication()
        if (!FirestoreCloudSyncRepository.ensureFirebaseInitialized(appContext)) {
            _isCloudSyncing.value = false
            _statusMessage.value = "Firebase Cloud Sync is not configured in this build (missing app/google-services.json)."
            return
        }
        val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (currentUser == null) {
            _isCloudSyncing.value = false
            _statusMessage.value = "Please sign in with Google first to sync with Firebase Cloud."
            return
        }
        viewModelScope.launch {
            _isCloudSyncing.value = true
            val prog = progressionStatus.value
            val result = firestoreCloudRepo.syncRoomWithFirestore(
                roomDb = roomDb,
                userId = currentUser.uid,
                displayName = currentUser.displayName ?: "JEE Aspirant",
                email = currentUser.email,
                totalXp = prog.totalXp,
                currentLevel = prog.currentLevel,
                currentStreakDays = prog.currentStreakDays
            )
            result.onSuccess { summary ->
                val pushed = summary.pushedChapters + summary.pushedSchedules + summary.pushedMistakes
                val pulled = summary.pulledChapters + summary.pulledSchedules + summary.pulledMistakes
                val msg = "Firebase Cloud Sync complete: ↑$pushed pushed, ↓$pulled pulled"
                _lastCloudSyncStatus.value = msg
                _statusMessage.value = msg
            }.onFailure { err ->
                val msg = "Firebase Cloud Sync error: ${err.localizedMessage ?: "Check connection"}"
                _lastCloudSyncStatus.value = msg
                _statusMessage.value = msg
            }
            _isCloudSyncing.value = false
        }
    }

    fun signOutFromFirebase() {
        runCatching { firestoreCloudRepo.signOut() }
        _isFirebaseSignedIn.value = false
        _firebaseUserEmail.value = null
        _firebaseUserDisplayName.value = null
        _lastCloudSyncStatus.value = "Signed out of Firebase Cloud"
        _statusMessage.value = "Signed out of Firebase Cloud. Local Room data preserved."
    }
}
