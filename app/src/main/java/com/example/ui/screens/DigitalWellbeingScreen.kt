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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.focus.FocusGuardManager
import com.example.data.wellbeing.DailyWellbeingStats
import com.example.data.wellbeing.DigitalWellbeingManager
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.ElectricBlueSubtle
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.SoftGreenContainer
import com.example.ui.theme.TabularNumeralStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

@Composable
fun DigitalWellbeingScreen(
    stats: DailyWellbeingStats,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenFocusGuard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isUsageGranted by remember(stats.isPermissionGranted) {
        mutableStateOf(DigitalWellbeingManager.hasUsageStatsPermission(context))
    }
    var isAccessibilityGuardEnabled by remember {
        mutableStateOf(FocusGuardManager.isAccessibilityServiceEnabled(context))
    }

    // Automatically re-verify OS permissions and refresh UsageStats when returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isUsageGranted = DigitalWellbeingManager.hasUsageStatsPermission(context)
                isAccessibilityGuardEnabled = FocusGuardManager.isAccessibilityServiceEnabled(context)
                onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .testTag("digital_wellbeing_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))

                // 1. Header with back button and refresh
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SlateCardElevated)
                                .border(1.dp, SlateCardBorder, CircleShape)
                                .clickable { onBack() }
                                .testTag("wellbeing_back_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Digital Wellbeing",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Device Screen Time & Focus App Guard",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            isUsageGranted = DigitalWellbeingManager.hasUsageStatsPermission(context)
                            isAccessibilityGuardEnabled = FocusGuardManager.isAccessibilityServiceEnabled(context)
                            onRefresh()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SlateCardElevated)
                            .border(1.dp, SlateCardBorder, CircleShape)
                            .testTag("wellbeing_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = SoftGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 2. Permissions & App Guard Control Card (Usage Access + Focus Guard Accessibility Service)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wellbeing_permissions_card"),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Permissions & Focus Guard Status",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            val bothReady = isUsageGranted && isAccessibilityGuardEnabled
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (bothReady) SoftGreenContainer else WarmAmber.copy(alpha = 0.16f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (bothReady) "All Active" else "Action Needed",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (bothReady) SoftGreen else WarmAmber
                                )
                            }
                        }

                        // Permission 1: Usage Access (PACKAGE_USAGE_STATS)
                        WellbeingPermissionItemRow(
                            title = "Usage Access (Screen Time & App Audit)",
                            description = "Reads daily foreground app time from Android UsageStatsManager.",
                            isGranted = isUsageGranted,
                            actionLabel = if (isUsageGranted) "Granted" else "Grant Access",
                            onAction = {
                                try {
                                    context.startActivity(DigitalWellbeingManager.createUsageAccessSettingsIntent())
                                } catch (_: Exception) {}
                            },
                            testTag = "wellbeing_grant_usage_access_button"
                        )

                        // Permission 2: Focus Guard Accessibility Service (App Blocking)
                        WellbeingPermissionItemRow(
                            title = "Focus App Guard (Block Distracting Apps)",
                            description = "Redirects blocked apps back to your study timer during active Focus sessions.",
                            isGranted = isAccessibilityGuardEnabled,
                            actionLabel = if (isAccessibilityGuardEnabled) "Enabled" else "Enable Guard",
                            onAction = {
                                try {
                                    context.startActivity(FocusGuardManager.createAccessibilitySettingsIntent())
                                } catch (_: Exception) {}
                            },
                            testTag = "wellbeing_enable_accessibility_guard_button"
                        )

                        // Shortcut to Focus Mode & Blocked Apps List
                        Button(
                            onClick = onOpenFocusGuard,
                            colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SoftGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .testTag("wellbeing_open_focus_guard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = SoftGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Configure Blocked Apps & Start Focus Timer",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoftGreen
                            )
                        }
                    }
                }
            }

            // 3. Screen Time Summary Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("screentime_summary_card"),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Today's Screen Time",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            if (stats.isPermissionGranted) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SoftGreenContainer)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Live On-Device",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SoftGreen
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (stats.isPermissionGranted && stats.hasUsageData) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stats.formattedTotalTime,
                                        style = TabularNumeralStyle,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Total foreground time today",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                val studyMins = (stats.studyAppsTimeMillis / (1000 * 60)).toInt()
                                val otherMins = (stats.otherAppsTimeMillis / (1000 * 60)).toInt()

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Study: ${studyMins}m",
                                        style = TabularNumeralStyle,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SoftGreen
                                    )
                                    Text(
                                        text = "Other: ${otherMins}m",
                                        style = TabularNumeralStyle,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        } else if (stats.isPermissionGranted) {
                            Text(
                                text = "No app usage recorded yet for today.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        } else {
                            Text(
                                text = "Tap 'Grant Access' in the card above to view your live device screen time and app breakdown.",
                                fontSize = 12.sp,
                                color = WarmAmber
                            )
                        }
                    }
                }
            }

            // 4. Most Used Apps Breakdown
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("most_used_apps_card"),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Most Used Apps Today",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            if (stats.topApps.isNotEmpty()) {
                                Text(
                                    text = "${stats.topApps.size} apps audited",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (stats.topApps.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                stats.topApps.forEach { appItem ->
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.AppShortcut,
                                                    contentDescription = null,
                                                    tint = if (appItem.isStudyOrProductiveApp) SoftGreen else WarmAmber,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = appItem.appName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary
                                                )
                                            }

                                            Text(
                                                text = "${appItem.formattedDuration} (${appItem.usagePercentage.toInt()}%)",
                                                style = TabularNumeralStyle,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        LinearProgressIndicator(
                                            progress = { (appItem.usagePercentage / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(2.5.dp)),
                                            color = if (appItem.isStudyOrProductiveApp) SoftGreen else ElectricBlue,
                                            trackColor = SlateCardElevated
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SlateCardElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (!stats.isPermissionGranted) {
                                        "Grant Usage Access above to load today's most-used apps"
                                    } else {
                                        "No foreground app history recorded yet today"
                                    },
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // 5. Privacy & Security Assurance Note
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("privacy_assurance_card"),
                    colors = CardDefaults.cardColors(containerColor = SlateCardElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ElectricBlueSubtle),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = ElectricBlueLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Strict On-Device Privacy",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Screen time is retrieved directly through the Android UsageStatsManager API. It is processed solely on your device and is never uploaded.",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun WellbeingPermissionItemRow(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SlateCardElevated)
            .border(
                width = 1.dp,
                color = if (isGranted) SoftGreen.copy(alpha = 0.4f) else WarmAmber.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) SoftGreen else WarmAmber,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                fontSize = 10.sp,
                color = TextSecondary,
                lineHeight = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) SlateCard else SoftGreen
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .height(34.dp)
                .testTag(testTag)
        ) {
            Text(
                text = actionLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGranted) SoftGreen else Color(0xFF042F2E)
            )
        }
    }
}
