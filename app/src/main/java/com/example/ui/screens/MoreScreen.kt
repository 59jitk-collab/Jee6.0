package com.example.ui.screens

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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.UserSettingsEntity
import com.example.data.progression.ProgressionStatus
import com.example.ui.theme.ChemistryPurple
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
import com.example.viewmodel.AppScreen

@Composable
fun MoreScreen(
    settings: UserSettingsEntity?,
    progressionStatus: ProgressionStatus = ProgressionStatus(),
    isFirebaseSignedIn: Boolean = false,
    firebaseUserEmail: String? = null,
    firebaseUserDisplayName: String? = null,
    isCloudSyncing: Boolean = false,
    lastCloudSyncStatus: String? = null,
    onSignInFirebaseGoogle: (android.content.Context) -> Unit = {},
    onSyncFirebaseCloud: () -> Unit = {},
    onSignOutFirebase: () -> Unit = {},
    onNavigate: (AppScreen) -> Unit,
    onOpenImportExport: () -> Unit,
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .testTag("more_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))

            // Profile & Private XP / Streak Card (Stage 4)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(ElectricBlue)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(SlateCardElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = ElectricBlueLight,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "JEE Warrior • Level ${progressionStatus.currentLevel}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${progressionStatus.levelTitle} • ${progressionStatus.totalXp} Total XP",
                                    fontSize = 12.sp,
                                    color = CyanAccent
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = WarmAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${progressionStatus.currentStreakDays}d",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarmAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val levelProgress = progressionStatus.levelProgressFraction.coerceIn(0.05f, 1f)

                    LinearProgressIndicator(
                        progress = { levelProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ChemistryPurple,
                        trackColor = SlateCardElevated
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Today's XP: ${progressionStatus.todayXpEarned}/${progressionStatus.dailyXpCap} (Idempotent & Private)",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Best Streak: ${progressionStatus.longestStreakDays}d",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = progressionStatus.healthReminderNote,
                        fontSize = 11.sp,
                        color = SoftGreen,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Firebase Cloud Firestore Backup & Google Sign-In Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("firebase_cloud_sync_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isFirebaseSignedIn) SoftGreen.copy(alpha = 0.5f) else SlateCardBorder
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Firebase Cloud Backup & Sync",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isFirebaseSignedIn) {
                                    "Signed in as ${firebaseUserEmail ?: firebaseUserDisplayName ?: "Google Account"}"
                                } else {
                                    "Sign in with Google to back up & sync chapters, 80-day schedule & mistakes to Cloud Firestore."
                                },
                                fontSize = 11.sp,
                                color = if (isFirebaseSignedIn) SoftGreen else TextSecondary
                            )
                            if (!lastCloudSyncStatus.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lastCloudSyncStatus,
                                    fontSize = 10.sp,
                                    color = CyanAccent
                                )
                            }
                        }
                        if (isCloudSyncing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = CyanAccent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isFirebaseSignedIn) {
                        androidx.compose.material3.Button(
                            onClick = { onSignInFirebaseGoogle(context) },
                            enabled = !isCloudSyncing,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("firebase_google_sign_in_button")
                        ) {
                            Text(
                                text = "Sign In with Google (Firebase Cloud)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Button(
                                onClick = onSyncFirebaseCloud,
                                enabled = !isCloudSyncing,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = SoftGreen
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("firebase_sync_now_button")
                            ) {
                                Text(
                                    text = "Sync Cloud Now",
                                    color = SlateCard,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            androidx.compose.material3.OutlinedButton(
                                onClick = onSignOutFirebase,
                                enabled = !isCloudSyncing,
                                modifier = Modifier.testTag("firebase_sign_out_button")
                            ) {
                                Text(
                                    text = "Sign Out",
                                    fontSize = 12.sp,
                                    color = WarmAmber
                                )
                            }
                        }
                    }
                }
            }
        }

        // Navigation Menu Items
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MoreMenuItem(
                        icon = Icons.Default.EventRepeat,
                        title = "Spaced Revision Queue (1-3-7-21d)",
                        onClick = { onNavigate(AppScreen.REVISION) }
                    )
                    MenuDivider()
                    MoreMenuItem(
                        icon = Icons.Default.BarChart,
                        title = "Study Analytics & Weekly Report",
                        onClick = { onNavigate(AppScreen.ANALYTICS) }
                    )
                    MenuDivider()
                    MoreMenuItem(
                        icon = Icons.Default.PhoneAndroid,
                        title = "Digital Wellbeing & Screen Time",
                        onClick = { onNavigate(AppScreen.DIGITAL_WELLBEING) }
                    )
                    MenuDivider()
                    MoreMenuItem(
                        icon = Icons.Default.CloudDownload,
                        title = "Import / Export",
                        onClick = onOpenImportExport
                    )
                    MenuDivider()
                    MoreMenuItem(
                        icon = Icons.Default.Warning,
                        title = "Mistake Notebook",
                        onClick = { onNavigate(AppScreen.MISTAKES) }
                    )
                    MenuDivider()
                    MoreMenuItem(
                        icon = Icons.Default.Nightlight,
                        title = "Rest, Sleep & Notification Center",
                        onClick = { onNavigate(AppScreen.REST_SLEEP) }
                    )
                    MenuDivider()
                    MoreMenuItem(
                        icon = Icons.Default.Psychology,
                        title = "AI Study Mentor",
                        onClick = { onNavigate(AppScreen.AI_MENTOR) }
                    )
                    MenuDivider()
                    MoreMenuItem(
                        icon = Icons.Default.Settings,
                        title = "Daily Target Settings",
                        onClick = { onNavigate(AppScreen.REST_SLEEP) }
                    )
                    MenuDivider()
                    MoreMenuItem(
                        icon = Icons.Default.HelpOutline,
                        title = "Help & Support",
                        onClick = { onNavigate(AppScreen.AI_MENTOR) }
                    )
                    MenuDivider()
                    MoreMenuItem(
                        icon = Icons.Default.Info,
                        title = "About App",
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }

        // Bottom Motivational Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("motivational_banner"),
                colors = CardDefaults.cardColors(containerColor = SlateCardElevated),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SoftGreen.copy(alpha = 0.12f))
                            .border(1.dp, SoftGreen.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Landscape,
                            contentDescription = null,
                            tint = SoftGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Discipline today,",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "freedom tomorrow.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoftGreen
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAboutDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = "JEE Study OS — Phase 4",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Version 1.4 • Built for Serious JEE Aspirants",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Includes 80-day backlog tracking, conceptual mastery tracking, spaced revision scheduler (1-3-7-21d), private idempotent XP & streaks, Focus Guard with real installed-app picker, Digital Wellbeing, and JEE AI Study Mentor.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK", color = ElectricBlueLight, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SlateCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElectricBlueLight,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
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

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SlateCardBorder)
            .padding(horizontal = 16.dp)
    )
}
