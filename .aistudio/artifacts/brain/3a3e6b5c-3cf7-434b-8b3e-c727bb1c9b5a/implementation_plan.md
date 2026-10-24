# JEE Study OS — 80-Day Backlog & Digital Wellbeing Command Center

Upgrading JEE Study OS into a modern, minimalist native Android productivity system that manages student backlogs (**23 October 2026 to 10 January 2027**), protects restorative sleep, delivers smart notifications, tracks screen time via `UsageStatsManager`, and provides competitive mastery progression.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences were explicitly confirmed in Turn 1:
>
> 1. **Focus Guard & App Restriction**:
>    - **Confirmed Choice**: Digital Wellbeing Integration with voluntary `UsageStatsManager` permission prompt and transparent, non-coercive focus overlays.
> 2. **Screen-Time & Digital Wellbeing Depth**:
>    - **Confirmed Choice**: On-device `UsageStatsManager` tracking providing daily total screen time, top 5 distracting app charts, and local privacy controls.
> 3. **Competitive Mode & Level Progression**:
>    - **Confirmed Choice**: Private Mastery & Level Progression featuring daily missions, XP, streaks, level badges, and personal bests (100% offline & private).
> 4. **Smart Daily Notifications**:
>    - **Confirmed Choice**: Android Notification Channels (`JEE_DAILY_TARGETS`, `JEE_MOCK_TESTS`, `JEE_REVISION`) delivering scheduled target reminders, quiet hours, and upcoming Sunday test alerts.

---

## 1. Overview & Core Concept

**JEE Study OS** is designed as a serious digital command center for JEE Main and JEE Advanced 2027 candidates. It treats student preparation as a high-performance athletic sprint—balancing rigorous academic progress with health protection.

- **Source of Truth Backlog**: Integrates the exact Physics, Chemistry, and Mathematics chapter names and task counts from `JEE_80_Day_Backlog_Tracker.xlsx` without inventing unassessed data (e.g. Organic Chemistry placeholder counts remain marked as "Not assessed").
- **Smart Pacing**: Enforces the 4-lecture/day target as an aspirational benchmark while hard-protecting a **7.5-hour sleep window** (e.g. 11:00 PM – 6:30 AM). Unfinished work is automatically rebalanced realistically over remaining available days.
- **On-Device Privacy**: All study logs, test scores, mistake notebook entries, and screen-time statistics remain 100% on-device in a local Room/SQLite database.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Dashboard & Mission HUD ("Today")**:
   - Displays 80-day countdown to 10 Jan 2027, overall backlog completion %, and daily 4-lecture / 25-problem benchmark rings.
   - Shows personalized daily lecture recommendation (e.g. *Physics — Laws of Motion — Lecture 3*).
   - Features quick-action cards for active Pomodoro sessions, next task, and weak-topic alerts.

2. **Backlog Tracker ("Backlog")**:
   - Subject filter tabs (*All, Physics, Chemistry, Mathematics*) and search bar with "Weak Topics Only" filter.
   - Interactive chapter cards with granular subtask counters for Lectures, Notes, DIBY, Class Problems, DPP, PYQ, and Worksheets.
   - Clear "Not assessed" badges for unassessed topics.

3. **Focus & Pacing Engine ("Focus")**:
   - Configurable Pomodoro stopwatch (50m study focus block, 10m break).
   - Distraction-free Focus Guard overlay showing active sprint time and current task.
   - Quick one-tap return to study after rest breaks.

4. **Digital Wellbeing & Insights ("Insights")**:
   - Voluntary `UsageStatsManager` permission setup explaining why access is requested.
   - Displays total screen time, foreground app usage trends, and distracting app breakdown.
   - Diagnostic weak-topic alerts derived from Sunday test analysis and mistake notebook entries.
   - Competitive Mastery HUD: Daily missions, XP rewards, streak counters, and level progression badges.

5. **Sunday Mock Test Series & Mistake Notebook**:
   - 12 Sunday Mock Test tracking slots (25 Oct 2026 – 10 Jan 2027) with score breakdown (*Physics, Chemistry, Maths, Negatives*).
   - Mistake Notebook recording specific error classifications (*Concept Gap, Calculation Slip, Time Pressure, Misread Question, Formula Forgot, Trap Option*), concepts missed, and actionable lessons learned.

6. **Settings & Data Management ("Settings")**:
   - Notification preferences (quiet hours, daily reminder times).
   - Sleep window boundary settings.
   - Theme options (Dark Academic default, Clean Light, Dimmer mode).
   - Workbook import/export (JSON, CSV, and one-tap 80-day blueprint loader) with full backup and data wipe controls.

### Visual Identity & Design Tokens

- **Aesthetic Direction**: Premium Dark Academic productivity command center. Minimalist, purposeful, crisp, and high-contrast.
- **Color Tokens**:
  - `Background`: Deep Navy (`#0B1220`)
  - `Surface Cards`: Slate (`#172033`)
  - `Elevated Surface`: Slate Card Elevated (`#1E293B`)
  - `Outline / Border`: Slate Border (`#334155`)
  - `Primary Accent`: Electric Blue (`#4F8CFF`)
  - `Completed / Success`: Soft Green (`#34D399`)
  - `Warnings / Weak Topics`: Warm Amber (`#FBBF24`)
  - `Errors / Negatives`: Coral Red (`#F87171`)
  - `Primary Text`: High-Contrast White (`#F8FAFC`)
  - `Secondary Text`: Slate Grey (`#94A3B8`)
  - `Muted Text`: Muted Blue-Grey (`#64748B`)
- **Typography Hierarchy**:
  - `Display Large`: 32sp, Bold, -0.5sp letter spacing.
  - `Title Large`: 20sp, SemiBold.
  - `Title Medium`: 16sp, Medium.
  - `Body Large`: 15sp, Normal.
  - `Body Medium`: 13sp, Normal.
  - `Label Large`: 13sp, SemiBold.

---

## 3. Key Product Decisions & Trade-Offs

1. **Digital Wellbeing & App Blocking (Android Platform Capabilities)**:
   - *Decision*: Use standard `UsageStatsManager` for screen-time analytics and in-app Focus Guard overlay instead of forcing an `AccessibilityService`.
   - *Rationale*: Google Play developer policies strictly prohibit `AccessibilityService` for non-accessibility app blocking unless explicitly intended for disabled users. Combining `UsageStatsManager` with a clean focus guard overlay gives 100% policy compliance, zero security risks, and voluntary permission handling.

2. **Smart Notification Architecture**:
   - *Decision*: Use Android `AlarmManager` and `NotificationChannel` with exact/inexact scheduling suited for Android 13+ (`POST_NOTIFICATIONS` runtime permission).
   - *Rationale*: Ensures study target reminders and Sunday test alerts arrive reliably without battery drain or excessive spam.

3. **Zero-Fabrication Data Policy**:
   - *Decision*: Unassessed topics (e.g. Organic Chemistry placeholders or future Sunday tests) are explicitly tagged `NOT_ASSESSED`.
   - *Rationale*: Prevents false confidence or distorted analytics.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            JETPACK COMPOSE UI LAYER                         │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────────┐ │
│ │ DashboardScreen│ │ TrackerScreen │ │ FocusScreen   │ │ InsightsScreen    │ │
│ └───────┬───────┘ └───────┬───────┘ └───────┬───────┘ └─────────┬─────────┘ │
│         │                 │                 │                   │           │
│ ┌───────┴─────────────────┴─────────────────┴───────────────────┴─────────┐ │
│ │                             STUDY VIEW MODEL                            │ │
│ │ (StateFlow, Timer Logic, XP/Streak Engine, UsageStats Integration)     │ │
│ └────────────────────────────────────┬────────────────────────────────────┘ │
└──────────────────────────────────────┼──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                              STUDY REPOSITORY                               │
│  ┌───────────────────────┐ ┌────────────────────────┐ ┌───────────────────┐  │
│  │ ChapterDao            │ │ DailyScheduleDao       │ │ TestRecordDao     │  │
│  ├───────────────────────┤ ├────────────────────────┤ ├───────────────────┤  │
│  │ MistakeEntryDao       │ │ RevisionItemDao        │ │ UserSettingsDao   │  │
│  └───────────┬───────────┘ └───────────┬────────────┘ └─────────┬─────────┘  │
└──────────────┼─────────────────────────┼────────────────────────┼───────────┘
               │                         │                        │
┌──────────────▼─────────────────────────▼────────────────────────▼───────────┐
│                        ROOM LOCAL DATABASE (SQLite)                         │
│                   "jee_study_os_db" (Schema Version 2)                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Data Entities & Schema

1. `ChapterEntity`:
   - `id`: Long (PK)
   - `subject`: String ("Physics", "Chemistry", "Mathematics")
   - `chapterName`: String
   - `orderIndex`: Int
   - `totalLectures`: Int? (null = Not assessed)
   - `completedLectures`: Int
   - `notesDone`: Boolean
   - `dibyTotal`: Int?, `dibyDone`: Int?
   - `dppTotal`: Int?, `dppDone`: Int?
   - `pyqTotal`: Int?, `pyqDone`: Int?
   - `worksheetsTotal`: Int?, `worksheetsDone`: Int?
   - `status`: String ("NOT_ASSESSED", "NOT_STARTED", "IN_PROGRESS", "COMPLETED", "WEAK")
   - `isWeak`: Boolean

2. `DailyScheduleEntity`:
   - `date`: String (YYYY-MM-DD, 2026-10-23 to 2027-01-10)
   - `dayNumber`: Int (1 to 80)
   - `targetLectures`: Int (default 4)
   - `completedLectures`: Int
   - `targetProblems`: Int (default 25)
   - `completedProblems`: Int
   - `studyMinutes`: Int, `breakMinutes`: Int
   - `sleepHours`: Float (default 7.5f)
   - `isSunday`: Boolean, `isCompleted`: Boolean

3. `TestRecordEntity`:
   - `id`: Long (PK)
   - `testName`: String, `testDate`: String
   - `physicsMarks`: Int?, `chemistryMarks`: Int?, `mathMarks`: Int?
   - `negativeMarks`: Int
   - `weakTopics`: String, `analysisNotes`: String
   - `isAssessed`: Boolean

4. `MistakeEntryEntity`:
   - `id`: Long (PK)
   - `subject`: String, `chapterName`: String, `testName`: String
   - `questionSnippet`: String, `errorType`: String
   - `conceptMissed`: String, `correctTakeaway`: String, `lessonLearned`: String
   - `isResolved`: Boolean

5. `UserSettingsEntity`:
   - `id`: Int = 1
   - `dailyLectureTarget`: Int = 4
   - `sleepStartTime`: String = "23:00", `sleepEndTime`: String = "06:30"
   - `pomodoroWorkMinutes`: Int = 50, `pomodoroBreakMinutes`: Int = 10
   - `isDimmerActive`: Boolean = false
   - `notificationsEnabled`: Boolean = true
   - `xpPoints`: Int = 0, `currentStreakDays`: Int = 0

---

## Implementation Milestones & Roadmap

### Phase 1: Core Foundation & Data Strategy
- [x] Room Database & Schema v2 with DAOs and Repositories.
- [x] DateUtils API 24+ helper for 80-day timeline generation.
- [x] Standard JEE 80-Day Blueprint loader & JSON/CSV importer.
- [x] Core Navigation Scaffold with TopCommandBar and M3 BottomNavBar.

### Phase 2: Pacing, Timer & Digital Wellbeing
- [x] Pomodoro Study & Break Stopwatch (50m study / 10m break).
- [x] Rest & Sleep Protection Screen with 7.5h sleep boundary configuration.
- [x] UsageStatsManager integration manager for screen-time tracking and distracting app usage charts.
- [x] Distraction-free Focus Guard overlay screen.

### Phase 3: Diagnostics, Revision & Competitive Mastery
- [x] Sunday Mock Test score recorder with negative mark tracking and automatic weak-topic detection.
- [x] Mistake Notebook with specific error types, concepts missed, and actionable lessons learned.
- [x] Spaced Revision Queue (1, 3, 7, 21 day repetition triggers).
- [x] Competitive Mode HUD: XP reward engine (+50 XP per lecture watched, +100 XP per test analyzed, +200 XP per mistake resolved), streak counter, and level progression badges.

### Phase 4: Smart Notifications & AI Integration
- [x] Notification channel helper (`JEE_DAILY_TARGETS`, `JEE_MOCK_TESTS`) with quiet hours protection.
- [x] Gemini AI Academic Coach with `gemini-3.8-flash` and Google Search Grounding tool integration.
- [x] Full unit test suite and compilation verification.
