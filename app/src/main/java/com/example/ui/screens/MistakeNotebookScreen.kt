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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.local.entities.MistakeEntryEntity
import com.example.data.local.entities.RevisionItemEntity
import com.example.ui.components.LogMistakeDialog
import com.example.ui.theme.CoralRed
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
fun MistakeNotebookScreen(
    mistakes: List<MistakeEntryEntity>,
    revisions: List<RevisionItemEntity>,
    onSaveMistake: (MistakeEntryEntity) -> Unit,
    onDeleteMistake: (Long) -> Unit,
    onToggleMistakeResolved: (MistakeEntryEntity) -> Unit,
    onCompleteRevision: (RevisionItemEntity) -> Unit,
    onNavigate: (AppScreen) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedSubject by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var editingMistake by remember { mutableStateOf<MistakeEntryEntity?>(null) }

    val filterOptions = listOf("All", "Physics", "Chemistry", "Maths")

    val filteredMistakes = remember(mistakes, selectedSubject, searchQuery) {
        mistakes.filter { m ->
            val matchSub = selectedSubject == "All" ||
                    (selectedSubject == "Maths" && m.subject.startsWith("Math", ignoreCase = true)) ||
                    m.subject.equals(selectedSubject, ignoreCase = true)
            val matchQuery = searchQuery.isBlank() ||
                    m.chapterName.contains(searchQuery, ignoreCase = true) ||
                    m.questionSnippet.contains(searchQuery, ignoreCase = true) ||
                    m.conceptMissed.contains(searchQuery, ignoreCase = true)
            matchSub && matchQuery
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .testTag("mistake_notebook_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Header: Mistake Notebook + subtitle (Screen 5 in blueprint)
            Text(
                text = "Mistake Notebook",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Learn from your mistakes • Synced with Google Sheets when connected.",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Filter Pills: [All] [Physics] [Chemistry] [Maths]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterOptions.forEach { opt ->
                    val isSelected = selectedSubject == opt
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(if (isSelected) ElectricBlue else Color.Transparent)
                            .border(1.dp, if (isSelected) ElectricBlue else SlateCardBorder, RoundedCornerShape(17.dp))
                            .clickable { selectedSubject = opt },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = opt,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Search Bar with Filter Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("mistake_search_input"),
                    placeholder = { Text("Search mistakes...", fontSize = 12.sp, color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlueLight,
                        unfocusedBorderColor = SlateCardBorder,
                        focusedContainerColor = SlateCard,
                        unfocusedContainerColor = SlateCard,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateCard)
                        .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Mistake Cards List matching Screen 5 in blueprint
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredMistakes.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "No mistakes logged yet",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap '+ Add Mistake' below to log a mistake from a test or DPP session. All entries sync two-way with Google Sheets when connected.",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                items(filteredMistakes, key = { it.id }) { mistake ->
                    val (tagColor, tagBg) = when {
                        mistake.isResolved -> Pair(SoftGreen, SoftGreen.copy(alpha = 0.15f))
                        mistake.errorType.contains("Concept", ignoreCase = true) -> Pair(CoralRed, CoralRed.copy(alpha = 0.15f))
                        mistake.errorType.contains("Calculation", ignoreCase = true) -> Pair(WarmAmber, WarmAmber.copy(alpha = 0.15f))
                        else -> Pair(ElectricBlueLight, ElectricBlueLight.copy(alpha = 0.15f))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingMistake = mistake }
                            .testTag("mistake_item_${mistake.id}"),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
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
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(tagBg)
                                        .clickable { onToggleMistakeResolved(mistake) }
                                        .testTag("toggle_mistake_resolved_${mistake.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = if (mistake.isResolved) "Resolved" else "Mark Resolved",
                                        tint = tagColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "${mistake.chapterName} (${mistake.subject.take(4)})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mistake.questionSnippet.ifBlank { mistake.conceptMissed.ifBlank { "Concept Review" } },
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(tagBg)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (mistake.isResolved) "${mistake.errorType} • Resolved" else mistake.errorType,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tagColor
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        val formattedDate = remember(mistake.timestamp) {
                                            try {
                                                java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(java.util.Date(mistake.timestamp))
                                            } catch (_: Exception) {
                                                "Recent"
                                            }
                                        }

                                        Text(
                                            text = formattedDate,
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onDeleteMistake(mistake.id) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("delete_mistake_${mistake.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete Mistake",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
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
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(70.dp))
                }
            }
        }

        // 5. Floating "+ Add Mistake" Blue Button on bottom right (Blueprint Screen 5)
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .height(42.dp)
                .testTag("add_mistake_fab"),
            shape = RoundedCornerShape(21.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "+ Add Mistake",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }

    if (showAddDialog) {
        LogMistakeDialog(
            defaultTestName = "Daily Practice",
            onDismiss = { showAddDialog = false },
            onSaveMistake = { m ->
                onSaveMistake(m)
                showAddDialog = false
            }
        )
    }

    if (editingMistake != null) {
        val current = editingMistake!!
        LogMistakeDialog(
            initialMistake = current,
            defaultTestName = current.testName,
            onDismiss = { editingMistake = null },
            onSaveMistake = { updated ->
                onSaveMistake(
                    updated.copy(
                        id = current.id,
                        syncId = current.effectiveSyncId,
                        createdAt = current.createdAt,
                        lastSyncedAt = current.lastSyncedAt,
                        remoteVersionHash = current.remoteVersionHash,
                        lastSyncedBaselineJson = current.lastSyncedBaselineJson,
                        originDeviceId = current.originDeviceId
                    )
                )
                editingMistake = null
            }
        )
    }
}
