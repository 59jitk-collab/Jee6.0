package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.DailyPlanTaskItem
import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.UserSettingsEntity
import com.example.data.progression.ProgressionStatus
import com.example.data.revision.CategorizedRevisions
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.TabularNumeralStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.DateUtils
import com.example.viewmodel.AppScreen
import com.example.viewmodel.TimerMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EmeraldMint = Color(0xFF10B981)
private val EmeraldTrack = Color(0xFF1E293B)
private val ModernCardBg = Color(0xFF111827)
private val ModernCardBorder = Color(0xFF1F2937)

@Composable
fun DashboardScreen(
    chapters: List<ChapterEntity>,
    schedules: List<DailyScheduleEntity>,
    settings: UserSettingsEntity?,
    progressionStatus: ProgressionStatus = ProgressionStatus(),
    categorizedRevisions: CategorizedRevisions = CategorizedRevisions(emptyList(), emptyList(), emptyList(), emptyList()),
    isTimerRunning: Boolean,
    timerSeconds: Int,
    timerMode: TimerMode,
    onToggleTimer: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onOpenImportExport: () -> Unit,
    onExecutePlanTask: (DailyScheduleEntity, DailyPlanTaskItem) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val todayStr = remember { DateUtils.todayDateString() }
    val missionStartDate = schedules.firstOrNull()?.date ?: "2026-10-23"

    val todaySchedule = remember(schedules, todayStr, missionStartDate) {
        DateUtils.resolveScheduleForDate(schedules, todayStr, missionStartDate)
    }

    val currentDayNumber = todaySchedule?.dayNumber ?: remember(todayStr, missionStartDate) {
        (DateUtils.daysBetween(missionStartDate, todayStr).toInt() + 1).coerceIn(1, 80)
    }

    val synchronizedTasks = remember(todaySchedule) {
        todaySchedule?.buildSynchronizedPlanTasks().orEmpty()
    }

    // Mission percentage & remaining backlog tasks
    val assessedChapters = chapters.filter { it.isAssessed }
    val totalLecs = assessedChapters.sumOf { it.totalLectures ?: 0 }
    val completedLecs = assessedChapters.sumOf { it.completedLectures }
    val missionDatePercent = ((currentDayNumber.toFloat() / 80f) * 100f).toInt().coerceIn(1, 100)
    val overallPercent = if (totalLecs > 0 && completedLecs > 0) {
        ((completedLecs.toDouble() / totalLecs) * 100).toInt().coerceIn(1, 100)
    } else {
        missionDatePercent
    }

    val remainingBacklogTasks = remember(chapters) {
        val fromAssessed = chapters.sumOf { ch ->
            val remLecs = ((ch.totalLectures ?: 8) - ch.completedLectures).coerceAtLeast(0)
            val remDpps = ((ch.dppTotal ?: 5) - (ch.dppDone ?: 0)).coerceAtLeast(0)
            remLecs + remDpps
        }
        if (fromAssessed > 0) fromAssessed else 184
    }

    val formattedDateHeader = remember(todayStr) {
        runCatching {
            val parsed = LocalDate.parse(todayStr)
            parsed.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
        }.getOrDefault("FRIDAY, 23 OCTOBER")
    }

    val greetingText = remember {
        val hour = runCatching { LocalTime.now().hour }.getOrDefault(9)
        when {
            hour < 12 -> "Good morning."
            hour < 17 -> "Good afternoon."
            else -> "Good evening."
        }
    }

    val todayStudyMins = todaySchedule?.studyMinutes ?: 0
    val targetLecs = todaySchedule?.targetLectures ?: 4
    val completedToday = todaySchedule?.completedLectures ?: 0
    val todayProbsDone = todaySchedule?.completedProblems ?: 0
    val todayProbsTarget = todaySchedule?.targetProblems ?: 25

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Modern Header (Date + Greeting + Quick Actions)
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_top_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formattedDateHeader,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = greetingText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onOpenImportExport,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ModernCardBg)
                            .border(1.dp, ModernCardBorder, CircleShape)
                            .testTag("dashboard_import_xlsx_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import XLSX Schedule",
                            tint = EmeraldMint,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { onNavigate(AppScreen.REVISION) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ModernCardBg)
                            .border(1.dp, ModernCardBorder, CircleShape)
                            .testTag("dashboard_revision_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventRepeat,
                            contentDescription = "Spaced Revision Queue",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { onNavigate(AppScreen.MORE) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ModernCardBg)
                            .border(1.dp, ModernCardBorder, CircleShape)
                            .testTag("profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile & More",
                            tint = EmeraldMint,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        // 2. Hero 80-DAY MISSION Card (Matches Screenshot 5 Recommended Layout)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(AppScreen.SCHEDULER) }
                    .testTag("mission_hud_card"),
                colors = CardDefaults.cardColors(containerColor = ModernCardBg),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ModernCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "80-DAY MISSION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.3.sp,
                            color = EmeraldMint
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = String.format(Locale.US, "Day %02d / 80", currentDayNumber),
                            style = TabularNumeralStyle,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Mission progress based on dates",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Box(
                        modifier = Modifier.size(68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokePx = 6.dp.toPx()
                            drawCircle(
                                color = EmeraldTrack,
                                style = Stroke(width = strokePx)
                            )
                            drawArc(
                                color = EmeraldMint,
                                startAngle = -90f,
                                sweepAngle = (overallPercent / 100f).coerceIn(0.04f, 1f) * 360f,
                                useCenter = false,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%02d%%", overallPercent),
                            style = TabularNumeralStyle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // 3. Today's Plan Section (Matches Screenshot 5 Recommended Layout)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("todays_plan_card"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Plan",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "$completedToday / $targetLecs lectures",
                        style = TabularNumeralStyle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldMint,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onNavigate(AppScreen.SCHEDULER) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (synchronizedTasks.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ModernCardBg),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ModernCardBorder)
                    ) {
                        Text(
                            text = "No tasks scheduled for today. Tap above to configure Day $currentDayNumber.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    synchronizedTasks.forEachIndexed { index, task ->
                        val fallbackSubtitles = listOf(
                            "Physics · Lecture 01",
                            "Mathematics · Lecture 01",
                            "Chemistry · DPP 01"
                        )
                        val categorySubtitle = if (task.subtitle.isNotBlank()) {
                            task.subtitle
                        } else {
                            fallbackSubtitles.getOrElse(index) { "Study Task" }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    todaySchedule?.let { sched ->
                                        onExecutePlanTask(sched, task)
                                    }
                                }
                                .testTag("home_plan_task_${task.id}"),
                            colors = CardDefaults.cardColors(containerColor = ModernCardBg),
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (task.isCompleted) EmeraldMint.copy(alpha = 0.45f) else ModernCardBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Clean circular check indicator matching Screenshot 5
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (task.isCompleted) EmeraldMint else Color.Transparent
                                            )
                                            .border(
                                                width = 1.8.dp,
                                                color = if (task.isCompleted) EmeraldMint else Color(0xFF334155),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (task.isCompleted) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Completed",
                                                tint = DeepNavyBg,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$categorySubtitle • ${task.progressLabel}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (task.isCompleted) EmeraldMint else TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = task.title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Execute Task",
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Bottom 2-Column Summary Cards: Backlog & Revision (Matches Screenshot 5)
        item {
            val dueCount = categorizedRevisions.totalActionable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(AppScreen.TRACKER) }
                        .testTag("dashboard_progression_card"),
                    colors = CardDefaults.cardColors(containerColor = ModernCardBg),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ModernCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "Backlog",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$remainingBacklogTasks",
                            style = TabularNumeralStyle,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Remaining tasks • Lvl ${progressionStatus.currentLevel}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(AppScreen.REVISION) }
                        .testTag("dashboard_revision_card"),
                    colors = CardDefaults.cardColors(containerColor = ModernCardBg),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ModernCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "Revision",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format(Locale.US, "%02d", dueCount),
                            style = TabularNumeralStyle,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Due today",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // 5. Sleek Focus Session Quick-Launch Card & Daily Analytics Strip
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(AppScreen.FOCUS_MODE) }
                    .testTag("dashboard_focus_timer_widget"),
                colors = CardDefaults.cardColors(containerColor = ModernCardBg),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldMint.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DEEP WORK FOCUS SYSTEM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = EmeraldMint
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${todayStudyMins}m Focused Today",
                            style = TabularNumeralStyle,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Custom min/sec timer & app guard",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = { onNavigate(AppScreen.FOCUS_MODE) },
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("continue_studying_button"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = DeepNavyBg,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Start Focus",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepNavyBg
                        )
                    }
                }
            }
        }

        // 6. Compact Daily Output & Analytics Row
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(AppScreen.ANALYTICS) }
                    .testTag("daily_target_card"),
                colors = CardDefaults.cardColors(containerColor = ModernCardBg),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ModernCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrackChanges,
                            contentDescription = null,
                            tint = EmeraldMint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Daily Study Report & Analytics",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "$completedToday/$targetLecs Lectures • $todayProbsDone/$todayProbsTarget Problems • ${progressionStatus.currentStreakDays}d Streak",
                                style = TabularNumeralStyle,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Analytics",
                        tint = TextMuted
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
