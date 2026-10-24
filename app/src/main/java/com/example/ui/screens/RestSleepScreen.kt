package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.entities.UserSettingsEntity
import com.example.ui.components.GlowGlassCard
import com.example.ui.theme.AmberGlowPrimary
import com.example.ui.theme.CopperGlowAccent
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import com.example.util.StudyNotificationManager
import com.example.viewmodel.AppScreen
import com.example.viewmodel.TimerMode

@Composable
fun RestSleepScreen(
    settings: UserSettingsEntity?,
    isTimerRunning: Boolean,
    timerSeconds: Int,
    timerMode: TimerMode,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSetTimerMode: (TimerMode) -> Unit,
    onSetCustomTimerDuration: (Int, Int) -> Unit = { _, _ -> },
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    onNavigate: (AppScreen) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var selectedPresetMinutes by remember { mutableIntStateOf(25) }
    var isCustomSelected by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableIntStateOf(25) }
    var customSeconds by remember { mutableIntStateOf(0) }
    var showCustomTimerDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }

    var sleepStart by remember(settings) { mutableStateOf(settings?.sleepStartTime ?: "23:00") }
    var sleepEnd by remember(settings) { mutableStateOf(settings?.sleepEndTime ?: "06:30") }

    var isDailyReminderEnabled by remember {
        mutableStateOf(StudyNotificationManager.getStatusInfo(context).isReminderScheduled)
    }
    var notificationDiagnosticMessage by remember {
        mutableStateOf<String?>(null)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isDailyReminderEnabled = true
            val result = StudyNotificationManager.scheduleDailyStudyReminder(
                context = context,
                hourOfDay = 8,
                minute = 0
            )
            notificationDiagnosticMessage = result.diagnosticMessage
        }
    }

    val minutes = timerSeconds / 60
    val seconds = timerSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .testTag("rest_sleep_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header: Rest & Sleep + subtitle
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Rest, Sleep & Study Reminders",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Better rest, scheduled study reminders, and focus recovery.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // 2. Circular Focus Timer Dial
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("circular_timer_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 8.dp.toPx()
                            drawCircle(
                                color = Color(0xFF1E283D),
                                style = Stroke(strokeWidth)
                            )
                            val totalSecs = (selectedPresetMinutes * 60).coerceAtLeast(1)
                            val progress = (timerSeconds.toFloat() / totalSecs).coerceIn(0f, 1f)
                            drawArc(
                                color = AmberGlowPrimary,
                                startAngle = -90f,
                                sweepAngle = progress * 360f,
                                useCenter = false,
                                style = Stroke(strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Nightlight,
                                contentDescription = null,
                                tint = AmberGlowPrimary,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = timeFormatted,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Text(
                                text = "Focus Time",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(AmberGlowPrimary)
                                    .clickable { onToggleTimer() }
                                    .testTag("timer_play_pause_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isTimerRunning) "Pause" else "Start",
                                    tint = DeepNavyBg,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { showTimeDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 4.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SlateCardElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 3. Preset Duration Pills: [25m Focus] [45m Deep] [60m Study] [Custom (Min & Sec)]
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    Pair(25, "25m\nFocus"),
                    Pair(45, "45m\nDeep"),
                    Pair(60, "60m\nStudy"),
                    Pair(-1, if (isCustomSelected) "Custom\n${String.format("%02d:%02d", customMinutes, customSeconds)}" else "Custom\nMin : Sec")
                )

                presets.forEach { (mins, label) ->
                    val isSelected = if (mins == -1) isCustomSelected else (!isCustomSelected && selectedPresetMinutes == mins)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag(if (mins == -1) "custom_timer_preset_button" else "preset_${mins}m_button")
                            .clickable {
                                if (mins == -1) {
                                    customMinutes = timerSeconds / 60
                                    customSeconds = timerSeconds % 60
                                    showCustomTimerDialog = true
                                } else {
                                    isCustomSelected = false
                                    selectedPresetMinutes = mins
                                    onSetCustomTimerDuration(mins, 0)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) SoftGreen.copy(alpha = 0.18f) else SlateCard
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) SoftGreen else SlateCardBorder
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) SoftGreen else TextSecondary,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // 4. Clean Daily Study Reminder Card (no in-app permission status clutter)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notifications_setup_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SoftGreen.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = SoftGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Daily 08:00 AM Study Reminder",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Morning mission reminder & focus completion alerts",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = isDailyReminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !StudyNotificationManager.hasPostNotificationsPermission(context)
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        isDailyReminderEnabled = true
                                        val result = StudyNotificationManager.scheduleDailyStudyReminder(
                                            context = context,
                                            hourOfDay = 8,
                                            minute = 0
                                        )
                                        notificationDiagnosticMessage = result.diagnosticMessage
                                    }
                                } else {
                                    isDailyReminderEnabled = false
                                    StudyNotificationManager.cancelDailyStudyReminder(context)
                                    notificationDiagnosticMessage = "Daily study reminder turned off."
                                }
                            },
                            modifier = Modifier.testTag("daily_reminder_schedule_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepNavyBg,
                                checkedTrackColor = SoftGreen
                            )
                        )
                    }

                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !StudyNotificationManager.hasPostNotificationsPermission(context)
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val result = StudyNotificationManager.sendTestNotification(context)
                                notificationDiagnosticMessage = result.diagnosticMessage
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
                            .testTag("send_test_notification_button")
                    ) {
                        Text(
                            text = "Send Test Notification",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    if (!notificationDiagnosticMessage.isNullOrBlank()) {
                        Text(
                            text = notificationDiagnosticMessage!!,
                            fontSize = 11.sp,
                            color = SoftGreen,
                            modifier = Modifier.testTag("notification_diagnostic_text")
                        )
                    }
                }
            }
        }

        // 5. Sleep Schedule Card: Sleep Window 23:00 > | Wakeup 06:30 >
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateCardElevated)
                            .clickable { showTimeDialog = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(ElectricBlueLight.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = null,
                                    tint = ElectricBlueLight,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Sleep Window", fontSize = 10.sp, color = TextMuted)
                                Text(sleepStart, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateCardElevated)
                            .clickable { showTimeDialog = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(CyanAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Wakeup", fontSize = 10.sp, color = TextMuted)
                                Text(sleepEnd, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // 6. Good sleep tip card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sleep_tip_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SoftGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Nightlight,
                                contentDescription = null,
                                tint = SoftGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "Good sleep improves memory, focus and reaction time.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            lineHeight = 16.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showCustomTimerDialog) {
        var minInput by remember(showCustomTimerDialog) { mutableStateOf(customMinutes.toString()) }
        var secInput by remember(showCustomTimerDialog) { mutableStateOf(customSeconds.toString()) }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCustomTimerDialog = false },
            title = {
                Text(
                    text = "Custom Timer (Minutes & Seconds)",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Choose exact minutes and seconds for your study or break countdown.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    // Minutes selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("MINUTES (0 - 180)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SoftGreen)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = minInput,
                                onValueChange = { str ->
                                    val filtered = str.filter { it.isDigit() }.take(3)
                                    minInput = filtered
                                    customMinutes = (filtered.toIntOrNull() ?: 0).coerceIn(0, 180)
                                },
                                label = { Text("Min") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_timer_minutes_input")
                            )
                            listOf(-5, -1, +1, +5).forEach { delta ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SlateCardElevated)
                                        .border(1.dp, SlateCardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            customMinutes = (customMinutes + delta).coerceIn(0, 180)
                                            minInput = customMinutes.toString()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
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

                    // Seconds selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("SECONDS (0 - 59)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = secInput,
                                onValueChange = { str ->
                                    val filtered = str.filter { it.isDigit() }.take(2)
                                    secInput = filtered
                                    customSeconds = (filtered.toIntOrNull() ?: 0).coerceIn(0, 59)
                                },
                                label = { Text("Sec") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_timer_seconds_input")
                            )
                            listOf(-15, -5, +5, +15).forEach { delta ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SlateCardElevated)
                                        .border(1.dp, SlateCardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            customSeconds = (customSeconds + delta).coerceIn(0, 59)
                                            secInput = customSeconds.toString()
                                        }
                                        .padding(horizontal = 9.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
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
                            text = "Selected Duration: ${String.format("%02d:%02d", customMinutes, customSeconds)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SoftGreen
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalMin = (minInput.toIntOrNull() ?: customMinutes).coerceIn(0, 180)
                        val finalSec = (secInput.toIntOrNull() ?: customSeconds).coerceIn(0, 59)
                        customMinutes = finalMin
                        customSeconds = finalSec
                        isCustomSelected = true
                        onSetCustomTimerDuration(finalMin, finalSec)
                        showCustomTimerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("apply_custom_timer_button")
                ) {
                    Text("Set Timer", color = DeepNavyBg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTimerDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SlateCard,
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (showTimeDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimeDialog = false },
            title = { Text("Configure Sleep Window", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = sleepStart,
                        onValueChange = { sleepStart = it },
                        label = { Text("Sleep Start (e.g. 23:00)") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sleepEnd,
                        onValueChange = { sleepEnd = it },
                        label = { Text("Wakeup Time (e.g. 06:30)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settings?.let { current ->
                            onUpdateSettings(current.copy(sleepStartTime = sleepStart, sleepEndTime = sleepEnd))
                        }
                        showTimeDialog = false
                    }
                ) {
                    Text("Save", color = AmberGlowPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimeDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SlateCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
