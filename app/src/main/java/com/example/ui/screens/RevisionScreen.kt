package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.RevisionItemEntity
import com.example.data.local.entities.UserSettingsEntity
import com.example.data.revision.CategorizedRevisions
import com.example.data.revision.RevisionBucket
import com.example.data.revision.SpacedRevisionManager
import com.example.ui.components.GlowGlassCard
import com.example.ui.components.SubjectBadge
import com.example.ui.theme.ChemistryPurple
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import com.example.util.DateUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun RevisionScreen(
    categorized: CategorizedRevisions,
    userSettings: UserSettingsEntity,
    onBack: () -> Unit,
    onCompleteRevision: (RevisionItemEntity) -> Unit,
    onSnoozeRevision: (RevisionItemEntity, Int) -> Unit,
    onRescheduleRevision: (RevisionItemEntity, String) -> Unit,
    onUpdateIntervals: (String, Boolean) -> Unit,
    onNavigateToTracker: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overdue & Due, 1: Upcoming, 2: Completed
    var showSettingsDialog by remember { mutableStateOf(false) }
    var rescheduleTarget by remember { mutableStateOf<RevisionItemEntity?>(null) }
    val todayStr = remember { DateUtils.todayDateString() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .testTag("revision_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("revision_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Column {
                    Text(
                        text = "Spaced Revision Queue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Intervals: ${userSettings.parsedRevisionIntervals.joinToString(" → ")} days",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanAccent
                    )
                }
            }

            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier.testTag("revision_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Configure Revision Intervals",
                    tint = CyanAccent
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary HUD Card
            item {
                GlowGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = CyanAccent
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            RevisionCountBadge(
                                label = "Overdue",
                                count = categorized.overdue.size,
                                color = CoralRed
                            )
                            RevisionCountBadge(
                                label = "Due Today",
                                count = categorized.dueToday.size,
                                color = WarmAmber
                            )
                            RevisionCountBadge(
                                label = "Upcoming",
                                count = categorized.upcoming.size,
                                color = CyanAccent
                            )
                            RevisionCountBadge(
                                label = "Completed",
                                count = categorized.completed.size,
                                color = SoftGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Revisions never auto-complete when a notification arrives. Mark a revision complete only after actively recalling formulas, key concepts, and solved problems.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Filter Tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val activeDueCount = categorized.overdue.size + categorized.dueToday.size
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Due & Overdue ($activeDueCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue.copy(alpha = 0.22f),
                            selectedLabelColor = CyanAccent
                        ),
                        modifier = Modifier.testTag("revision_tab_due")
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Upcoming (${categorized.upcoming.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue.copy(alpha = 0.22f),
                            selectedLabelColor = CyanAccent
                        ),
                        modifier = Modifier.testTag("revision_tab_upcoming")
                    )
                    FilterChip(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text("Completed (${categorized.completed.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue.copy(alpha = 0.22f),
                            selectedLabelColor = CyanAccent
                        ),
                        modifier = Modifier.testTag("revision_tab_completed")
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    val combinedDue = categorized.overdue + categorized.dueToday
                    if (combinedDue.isEmpty()) {
                        item {
                            EmptyRevisionCard(
                                title = "No Due or Overdue Revisions",
                                subtitle = "You are caught up for today! Mark chapters as 'Studied' in the Backlog Tracker to schedule new spaced revisions.",
                                onGoToTracker = onNavigateToTracker
                            )
                        }
                    } else {
                        items(combinedDue, key = { it.id }) { item ->
                            RevisionRowCard(
                                item = item,
                                todayStr = todayStr,
                                intervals = userSettings.parsedRevisionIntervals,
                                onComplete = { onCompleteRevision(item) },
                                onSnooze1Day = { onSnoozeRevision(item, 1) },
                                onSnooze2Days = { onSnoozeRevision(item, 2) },
                                onReschedule = { rescheduleTarget = item }
                            )
                        }
                    }
                }
                1 -> {
                    if (categorized.upcoming.isEmpty()) {
                        item {
                            EmptyRevisionCard(
                                title = "No Upcoming Revisions Scheduled",
                                subtitle = "When you mark a chapter as studied or complete a due revision, its next interval will appear here.",
                                onGoToTracker = onNavigateToTracker
                            )
                        }
                    } else {
                        items(categorized.upcoming, key = { it.id }) { item ->
                            RevisionRowCard(
                                item = item,
                                todayStr = todayStr,
                                intervals = userSettings.parsedRevisionIntervals,
                                onComplete = { onCompleteRevision(item) },
                                onSnooze1Day = { onSnoozeRevision(item, 1) },
                                onSnooze2Days = { onSnoozeRevision(item, 2) },
                                onReschedule = { rescheduleTarget = item }
                            )
                        }
                    }
                }
                2 -> {
                    if (categorized.completed.isEmpty()) {
                        item {
                            EmptyRevisionCard(
                                title = "No Completed Revisions Yet",
                                subtitle = "Completed revision stages are logged here with timestamps for your weekly study report.",
                                onGoToTracker = onNavigateToTracker
                            )
                        }
                    } else {
                        items(categorized.completed, key = { it.id }) { item ->
                            RevisionRowCard(
                                item = item,
                                todayStr = todayStr,
                                intervals = userSettings.parsedRevisionIntervals,
                                onComplete = {},
                                onSnooze1Day = {},
                                onSnooze2Days = {},
                                onReschedule = {}
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showSettingsDialog) {
        RevisionSettingsDialog(
            currentCsv = userSettings.revisionIntervalsCsv,
            notificationsEnabled = userSettings.revisionNotificationsEnabled,
            onDismiss = { showSettingsDialog = false },
            onSave = { csv, enabled ->
                onUpdateIntervals(csv, enabled)
                showSettingsDialog = false
            }
        )
    }

    rescheduleTarget?.let { target ->
        RescheduleRevisionDialog(
            item = target,
            onDismiss = { rescheduleTarget = null },
            onConfirmDate = { newDate ->
                onRescheduleRevision(target, newDate)
                rescheduleTarget = null
            }
        )
    }
}

@Composable
private fun RevisionCountBadge(
    label: String,
    count: Int,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun RevisionRowCard(
    item: RevisionItemEntity,
    todayStr: String,
    intervals: List<Int>,
    onComplete: () -> Unit,
    onSnooze1Day: () -> Unit,
    onSnooze2Days: () -> Unit,
    onReschedule: () -> Unit
) {
    val bucket = SpacedRevisionManager.getBucketForRevision(item, todayStr)
    val daysDelta = DateUtils.daysBetween(todayStr, item.effectiveDueDate)
    val accent = when (bucket) {
        RevisionBucket.OVERDUE -> CoralRed
        RevisionBucket.DUE_TODAY -> WarmAmber
        RevisionBucket.UPCOMING -> CyanAccent
        RevisionBucket.COMPLETED -> SoftGreen
    }
    val statusLabel = when (bucket) {
        RevisionBucket.OVERDUE -> "OVERDUE (${kotlin.math.abs(daysDelta)}d ago)"
        RevisionBucket.DUE_TODAY -> "DUE TODAY"
        RevisionBucket.UPCOMING -> "IN $daysDelta DAY${if (daysDelta == 1L) "" else "S"}"
        RevisionBucket.COMPLETED -> "COMPLETED"
    }

    GlowGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("revision_card_${item.id}"),
        glowColor = accent
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubjectBadge(subject = item.subject)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ChemistryPurple.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Stage ${item.stage}/${intervals.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ChemistryPurple
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.chapterName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Scheduled: ${item.effectiveDueDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (item.lastRevisedDate.isNotBlank()) {
                    Text(
                        text = "Last: ${item.lastRevisedDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (bucket != RevisionBucket.COMPLETED) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("complete_revision_${item.id}"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark Revised", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onSnooze1Day,
                        modifier = Modifier.testTag("snooze_revision_${item.id}"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = "Snooze 1 day",
                            modifier = Modifier.size(15.dp),
                            tint = WarmAmber
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+1d", fontSize = 12.sp, color = WarmAmber)
                    }

                    OutlinedButton(
                        onClick = onSnooze2Days,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("+2d", fontSize = 12.sp, color = WarmAmber)
                    }

                    OutlinedButton(
                        onClick = onReschedule,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Reschedule",
                            modifier = Modifier.size(15.dp),
                            tint = CyanAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRevisionCard(
    title: String,
    subtitle: String,
    onGoToTracker: () -> Unit
) {
    GlowGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.EventRepeat,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onGoToTracker) {
                Text("Open Backlog Tracker", color = CyanAccent)
            }
        }
    }
}

@Composable
private fun RevisionSettingsDialog(
    currentCsv: String,
    notificationsEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var csvInput by remember { mutableStateOf(currentCsv) }
    var notifToggle by remember { mutableStateOf(notificationsEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateCard,
        title = { Text("Spaced Revision Settings", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Customize revision spacing intervals in days (comma-separated). Default is 1, 3, 7, 21 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = csvInput,
                    onValueChange = { csvInput = it },
                    label = { Text("Intervals (days)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Morning Revision Reminder",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Daily notification at 9:00 AM when chapters are due",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = notifToggle,
                        onCheckedChange = { notifToggle = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(csvInput, notifToggle) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RescheduleRevisionDialog(
    item: RevisionItemEntity,
    onDismiss: () -> Unit,
    onConfirmDate: (String) -> Unit
) {
    val today = LocalDate.now()
    val presets = listOf(
        "Tomorrow" to today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
        "In 3 Days" to today.plusDays(3).format(DateTimeFormatter.ISO_LOCAL_DATE),
        "In 7 Days" to today.plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE),
        "In 14 Days" to today.plusDays(14).format(DateTimeFormatter.ISO_LOCAL_DATE)
    )
    var customDate by remember { mutableStateOf(item.effectiveDueDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateCard,
        title = { Text("Reschedule ${item.chapterName}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Pick a quick date or enter YYYY-MM-DD:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.take(2).forEach { (label, iso) ->
                        OutlinedButton(
                            onClick = { customDate = iso },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(6.dp)
                        ) {
                            Text(label, fontSize = 11.sp, color = CyanAccent)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.drop(2).forEach { (label, iso) ->
                        OutlinedButton(
                            onClick = { customDate = iso },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(6.dp)
                        ) {
                            Text(label, fontSize = 11.sp, color = CyanAccent)
                        }
                    }
                }
                OutlinedTextField(
                    value = customDate,
                    onValueChange = { customDate = it },
                    label = { Text("Due Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val valid = runCatching { LocalDate.parse(customDate.trim()) }.isSuccess
                    if (valid) onConfirmDate(customDate.trim())
                }
            ) {
                Text("Reschedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
