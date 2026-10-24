package com.example.ui.screens

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.DailyPlanTaskItem
import com.example.data.local.entities.DailyScheduleEntity
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.TabularNumeralStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import com.example.util.DateUtils

@Composable
fun SchedulerScreen(
    schedules: List<DailyScheduleEntity>,
    missionStartDate: String = "2026-10-23",
    onUpdateLecture: (DailyScheduleEntity, Int) -> Unit,
    onUpdateProblems: (DailyScheduleEntity, Int) -> Unit,
    onToggleNotes: (DailyScheduleEntity) -> Unit,
    onUpdateAssignedChapters: (DailyScheduleEntity, String) -> Unit = { _, _ -> },
    onExecutePlanTask: (DailyScheduleEntity, DailyPlanTaskItem) -> Unit = { _, _ -> },
    onRebalanceRemaining: (Int) -> Unit,
    onNavigateToTests: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val todayStr = remember { DateUtils.todayDateString() }

    val todayResolvedSchedule = remember(schedules, todayStr, missionStartDate) {
        DateUtils.resolveScheduleForDate(schedules, todayStr, missionStartDate)
    }

    val initialDayNumber = todayResolvedSchedule?.dayNumber ?: remember(todayStr, missionStartDate) {
        (DateUtils.daysBetween(missionStartDate, todayStr).toInt() + 1).coerceIn(1, 80)
    }

    var selectedDayNumber by remember { mutableIntStateOf(initialDayNumber) }
    var hasInitializedSelection by remember { mutableStateOf(false) }

    LaunchedEffect(todayResolvedSchedule?.dayNumber) {
        if (!hasInitializedSelection && todayResolvedSchedule != null) {
            selectedDayNumber = todayResolvedSchedule.dayNumber
            hasInitializedSelection = true
        }
    }

    val activeSchedule = schedules.find { it.dayNumber == selectedDayNumber }
        ?: todayResolvedSchedule
        ?: schedules.firstOrNull()

    var showAssignDialog by remember { mutableStateOf(false) }

    // Dynamically compute the 7-day strip centered around selectedDayNumber using actual schedule dates
    val weekDays = remember(schedules, selectedDayNumber, missionStartDate) {
        val totalDays = schedules.size.coerceAtLeast(80)
        val startDay = (selectedDayNumber - 3).coerceIn(1, (totalDays - 6).coerceAtLeast(1))
        val endDay = (startDay + 6).coerceAtMost(totalDays)
        (startDay..endDay).map { dayNum ->
            val sched = schedules.find { it.dayNumber == dayNum }
            val dateStr = sched?.date ?: DateUtils.addDays(missionStartDate, dayNum - 1)
            val dayOfWeek = DateUtils.formatDayOfWeekShort(dateStr)
            val dayOfMonth = DateUtils.extractDayOfMonth(dateStr)
            Triple(dayOfWeek, dayOfMonth, dayNum)
        }
    }

    val activeDateStr = activeSchedule?.date ?: DateUtils.addDays(missionStartDate, selectedDayNumber - 1)
    val monthYearHeader = remember(activeDateStr) { DateUtils.formatMonthYearLabel(activeDateStr) }
    val synchronizedTasks = remember(activeSchedule) {
        activeSchedule?.buildSynchronizedPlanTasks().orEmpty()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .testTag("scheduler_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header: 80-Day Schedule
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "80-Day Schedule",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Synchronized with Home Today's Plan • Mission Start: $missionStartDate",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                if (todayResolvedSchedule != null && selectedDayNumber != todayResolvedSchedule.dayNumber) {
                    TextButton(
                        onClick = { selectedDayNumber = todayResolvedSchedule.dayNumber },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Jump to Today",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlueLight
                        )
                    }
                }
            }
        }

        // 2. Dynamic Month Header & 7-Day Selector Strip
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$monthYearHeader • Day $selectedDayNumber of 80",
                            style = TabularNumeralStyle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (selectedDayNumber > 1) selectedDayNumber--
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Day",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (selectedDayNumber < 80) selectedDayNumber++
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Day",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekDays.forEach { (dayName, dateNum, targetDay) ->
                            val isSelected = selectedDayNumber == targetDay
                            val isToday = todayResolvedSchedule?.dayNumber == targetDay
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedDayNumber = targetDay }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                    .testTag("schedule_day_pill_$targetDay")
                            ) {
                                Text(
                                    text = dayName,
                                    fontSize = 10.sp,
                                    color = if (isSelected) ElectricBlueLight else TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) ElectricBlue else Color.Transparent)
                                        .border(
                                            width = if (isToday && !isSelected) 1.dp else 0.dp,
                                            color = if (isToday && !isSelected) ElectricBlueLight else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dateNum,
                                        style = TabularNumeralStyle,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Synchronized Day Schedule Card (Shared source of truth with Dashboard Today's Plan)
        item {
            val completedCount = activeSchedule?.completedLectures ?: 0
            val targetCount = activeSchedule?.targetLectures ?: 4
            val completedProbs = activeSchedule?.completedProblems ?: 0
            val targetProbs = activeSchedule?.targetProblems ?: 25
            val isSunday = activeSchedule?.isSunday ?: false

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("day_schedule_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Day $selectedDayNumber • $activeDateStr",
                                style = TabularNumeralStyle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (activeSchedule?.parsedAssignedChapters?.isNotEmpty() == true) {
                                    "Assigned: ${activeSchedule.parsedAssignedChapters.joinToString(", ")}"
                                } else {
                                    "No specific chapters assigned • Tap 'Assign Chapters' to customize"
                                },
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSunday) WarmAmber.copy(alpha = 0.15f) else SoftGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isSunday) "Sunday Mock" else "Study Day",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSunday) WarmAmber else SoftGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lectures: $completedCount/$targetCount • Problems: $completedProbs/$targetProbs",
                            style = TabularNumeralStyle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        TextButton(
                            onClick = { showAssignDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.testTag("assign_day_chapters_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = ElectricBlueLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Assign Chapters",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricBlueLight
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = {
                            if (targetCount > 0) (completedCount.toFloat() / targetCount).coerceIn(0f, 1f) else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = SoftGreen,
                        trackColor = SlateCardElevated
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Synchronized Task Checklist (Identical to Home Today's Plan when viewing Today)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        synchronizedTasks.forEach { task ->
                            ScheduleTaskRow(
                                task = task,
                                onToggle = {
                                    activeSchedule?.let { s ->
                                        onExecutePlanTask(s, task)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Quick Actions: Rebalance Plan & Sunday Tests
        item {
            Text(
                text = "Schedule Actions",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onRebalanceRemaining(selectedDayNumber) }
                        .testTag("rebalance_schedule_card"),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElectricBlueLight.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = ElectricBlueLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "Rebalance",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "From Day $selectedDayNumber",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToTests() },
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(WarmAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = WarmAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "Sunday",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Mock Tests",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showAssignDialog && activeSchedule != null) {
        var assignedInput by remember(activeSchedule) {
            mutableStateOf(activeSchedule.assignedChapterNames)
        }
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = {
                Text(
                    text = "Assign Chapters for Day ${activeSchedule.dayNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Enter comma-separated chapters for ${activeSchedule.date}. Both Home ('Today's Plan') and 80-Day Schedule will display these exact tasks.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = assignedInput,
                        onValueChange = { assignedInput = it },
                        label = { Text("Assigned Chapters (comma-separated)", fontSize = 12.sp) },
                        placeholder = {
                            Text(
                                "Physics: Kinematics, Chemistry: Mole Concept",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("assign_chapters_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = SlateCardBorder,
                            focusedContainerColor = SlateCard,
                            unfocusedContainerColor = SlateCard
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateAssignedChapters(activeSchedule, assignedInput)
                        showAssignDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier.testTag("save_assigned_chapters_button")
                ) {
                    Text("Save Plan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssignDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SlateCardElevated
        )
    }
}

@Composable
private fun ScheduleTaskRow(
    task: DailyPlanTaskItem,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCardElevated)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("schedule_task_${task.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (task.isCompleted) "Completed" else "Pending",
                tint = if (task.isCompleted) SoftGreen else TextSecondary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = task.subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = task.progressLabel,
            style = TabularNumeralStyle,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (task.isCompleted) SoftGreen else ElectricBlueLight
        )
    }
}
