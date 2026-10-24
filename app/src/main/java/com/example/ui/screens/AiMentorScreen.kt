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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.data.ai.AiBackendHealthState
import com.example.data.ai.ChatMessage
import com.example.data.local.entities.ChapterEntity
import com.example.ui.theme.CoralRed
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiMentorScreen(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    chapters: List<ChapterEntity>,
    selectedChapterId: Long?,
    isRemoteBackendConfigured: Boolean,
    userApprovedExternalSend: Boolean,
    aiBackendHealthState: AiBackendHealthState = AiBackendHealthState.ONLINE,
    aiBackendHealthDetail: String = "",
    onVerifyBackendHealth: () -> Unit = {},
    onSelectChapterContext: (Long?) -> Unit,
    onSetExternalSendConsent: (Boolean) -> Unit,
    onSetCustomApiKey: (String) -> Unit = {},
    onSendMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onNavigate: (AppScreen) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var promptText by remember { mutableStateOf("") }
    var showPrivacyDetails by remember { mutableStateOf(false) }
    var customApiKeyInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val selectedChapter = remember(chapters, selectedChapterId) {
        chapters.find { it.id == selectedChapterId }
    }

    // Trigger health check when entering the screen if remote backend is configured
    LaunchedEffect(isRemoteBackendConfigured) {
        if (isRemoteBackendConfigured && aiBackendHealthState == AiBackendHealthState.OFFLINE) {
            onVerifyBackendHealth()
        }
    }

    val isCloudOnline = isRemoteBackendConfigured &&
            userApprovedExternalSend &&
            aiBackendHealthState == AiBackendHealthState.ONLINE

    val badgeLabel = when {
        aiBackendHealthState == AiBackendHealthState.CHECKING -> "Syncing..."
        isCloudOnline -> "Cloud + JEE Engine"
        else -> "JEE Mentor Ready"
    }

    val badgeColor = when {
        aiBackendHealthState == AiBackendHealthState.CHECKING -> CyanAccent
        else -> SoftGreen
    }

    val displayMessages = remember(messages, isRemoteBackendConfigured) {
        if (messages.isNotEmpty()) {
            messages
        } else {
            listOf(
                ChatMessage(
                    sender = "ai",
                    text = "Welcome to JEE AI Study Mentor!\n\n" +
                            "Ask any Physics, Chemistry, or Mathematics doubt, request high-yield formulas, PYQ step-by-step breakdowns, or a personalized 7-day revision plan.\n\n" +
                            "Tip: Tap any chapter pill above to attach your live backlog progress (lectures, DPPs, PYQs, and mastery) to your question.",
                    timestamp = System.currentTimeMillis() - 60000
                )
            )
        }
    }

    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    val quickQuestions = listOf(
        "Concept summary & high-yield formulas",
        "Step-by-step JEE problem approach",
        "Common JEE traps & negative mark fixes",
        "7-day revision plan for this chapter"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .testTag("ai_mentor_screen")
    ) {
        // 1. Header: Avatar + AI Mentor + Status Badge + Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ElectricBlueLight.copy(alpha = 0.2f))
                        .border(1.5.dp, ElectricBlueLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = ElectricBlueLight,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "JEE AI Study Mentor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.16f))
                                .clickable { onVerifyBackendHealth() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("ai_backend_status_badge")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = badgeLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Text(
                        text = aiBackendHealthDetail.ifBlank {
                            "Physics, Chemistry & Math JEE Solver + 80-Day Strategy Coach"
                        },
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onVerifyBackendHealth,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("verify_ai_health_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh AI Mentor",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { showPrivacyDetails = !showPrivacyDetails },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("ai_settings_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "AI Settings & Cloud Key",
                        tint = if (userApprovedExternalSend) SoftGreen else CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (messages.isNotEmpty()) {
                    IconButton(
                        onClick = onClearChat,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Chat",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Expandable Cloud API Key & Privacy Settings Panel (only shown when lock icon is tapped)
        if (showPrivacyDetails) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateCard)
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cloud Gemini Mode + Built-in JEE Knowledge Engine",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Automatically uses Gemini Cloud when a key is present and falls back to the built-in JEE solver instantly.",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = userApprovedExternalSend,
                        onCheckedChange = onSetExternalSendConsent,
                        modifier = Modifier.testTag("ai_external_consent_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customApiKeyInput,
                        onValueChange = { customApiKeyInput = it },
                        placeholder = { Text("Optional: Paste Gemini API Key (AIza...)", fontSize = 11.sp, color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_gemini_key_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlueLight,
                            unfocusedBorderColor = SlateCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedButton(
                        onClick = {
                            onSetCustomApiKey(customApiKeyInput)
                            customApiKeyInput = ""
                            showPrivacyDetails = false
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Apply Key", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                    }
                }
            }
        }

        // Chapter Context Selector Row (Stage 5)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                val noChapterSelected = selectedChapterId == null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (noChapterSelected) ElectricBlue.copy(alpha = 0.22f) else SlateCard)
                        .border(1.dp, if (noChapterSelected) CyanAccent else SlateCardBorder, RoundedCornerShape(12.dp))
                        .clickable { onSelectChapterContext(null) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "General JEE",
                        fontSize = 10.sp,
                        fontWeight = if (noChapterSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (noChapterSelected) CyanAccent else TextSecondary
                    )
                }
            }
            items(chapters.take(25), key = { it.id }) { ch ->
                val isSelected = selectedChapterId == ch.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) ElectricBlue.copy(alpha = 0.22f) else SlateCard)
                        .border(1.dp, if (isSelected) CyanAccent else SlateCardBorder, RoundedCornerShape(12.dp))
                        .clickable { onSelectChapterContext(if (isSelected) null else ch.id) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${ch.subject.take(1)}: ${ch.chapterName}",
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) CyanAccent else TextSecondary
                    )
                }
            }
        }

        if (selectedChapter != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = SoftGreen,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val lecInfo = if (selectedChapter.totalLectures != null) {
                    "${selectedChapter.completedLectures}/${selectedChapter.totalLectures} lecs"
                } else {
                    "Not Assessed"
                }
                Text(
                    text = "Attaching Verified App Data: ${selectedChapter.chapterName} ($lecInfo • ${selectedChapter.resolvedMastery.displayLabel})",
                    fontSize = 10.sp,
                    color = SoftGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SlateCardBorder)
        )

        // 2. Chat Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
            }

            items(displayMessages, key = { it.id }) { msg ->
                val isUser = msg.sender == "user"
                val timeStr = remember(msg.timestamp) {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    sdf.format(Date(msg.timestamp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!isUser) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(SlateCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = ElectricBlueLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Box(
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                )
                            )
                            .background(if (isUser) ElectricBlue else SlateCard)
                            .border(
                                1.dp,
                                if (msg.isError) CoralRed else if (isUser) ElectricBlue else SlateCardBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column {
                            if (msg.verifiedContextSummary != null && isUser) {
                                Text(
                                    text = msg.verifiedContextSummary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                            }
                            Text(
                                text = msg.text,
                                fontSize = 13.sp,
                                color = if (isUser) Color.White else TextPrimary,
                                lineHeight = 18.sp
                            )
                            if (msg.isError) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { onRetryMessage("") },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry Request", fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = timeStr,
                                fontSize = 9.sp,
                                color = if (isUser) Color.White.copy(alpha = 0.7f) else TextMuted,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }

            if (isGenerating) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 36.dp, top = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = ElectricBlueLight,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Preparing JEE response...",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Quick Suggestion Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickQuestions) { q ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(SlateCard)
                        .border(1.dp, SlateCardBorder, RoundedCornerShape(14.dp))
                        .clickable { onSendMessage(q) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = q,
                        fontSize = 11.sp,
                        color = ElectricBlueLight,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. Bottom Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("ai_message_input"),
                placeholder = { Text("Ask a JEE concept, strategy, or PYQ doubt...", fontSize = 12.sp, color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlueLight,
                    unfocusedBorderColor = SlateCardBorder,
                    focusedContainerColor = SlateCard,
                    unfocusedContainerColor = SlateCard,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ElectricBlue)
                    .clickable {
                        if (promptText.isNotBlank() && !isGenerating) {
                            val textToSend = promptText
                            promptText = ""
                            onSendMessage(textToSend)
                        }
                    }
                    .testTag("send_ai_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
