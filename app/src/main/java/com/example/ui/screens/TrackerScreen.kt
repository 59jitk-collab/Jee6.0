package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.analytics.MasterySummaryCalculator
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.MasteryLevel
import com.example.ui.components.AddChapterDialog
import com.example.ui.theme.ChemistryPurple
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

@Composable
fun TrackerScreen(
    chapters: List<ChapterEntity>,
    selectedSubject: String,
    searchQuery: String,
    showOnlyWeak: Boolean,
    onSelectSubject: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleShowOnlyWeak: () -> Unit,
    onLectureChange: (ChapterEntity, Int) -> Unit,
    onNotesToggle: (ChapterEntity) -> Unit,
    onDppChange: (ChapterEntity, Int) -> Unit,
    onPyqChange: (ChapterEntity, Int) -> Unit,
    onDibyChange: (ChapterEntity, Int) -> Unit,
    onClassProbChange: (ChapterEntity, Int) -> Unit,
    onWorksheetChange: (ChapterEntity, Int) -> Unit,
    onToggleWeak: (ChapterEntity) -> Unit,
    onMasteryChange: (ChapterEntity, MasteryLevel) -> Unit,
    onMarkStudied: (ChapterEntity) -> Unit,
    onSaveChapter: (ChapterEntity) -> Unit,
    onDeleteChapter: (Long) -> Unit,
    onOpenImportExport: () -> Unit,
    onLoadDefaultTemplate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    val currentSubject = if (selectedSubject == "All") "Physics" else selectedSubject
    val subjects = listOf("Physics", "Chemistry", "Mathematics")

    val subjectChapters = remember(chapters, currentSubject) {
        chapters.filter { it.subject.equals(currentSubject, ignoreCase = true) }
    }

    val subjectSummary = remember(subjectChapters, currentSubject) {
        MasterySummaryCalculator.computeSummary(currentSubject, subjectChapters)
    }

    val totalCount = chapters.size
    val completedCount = chapters.count { (it.totalLectures ?: 0) > 0 && it.completedLectures >= (it.totalLectures ?: 0) }
    val remainingCount = totalCount - completedCount

    val subTotal = subjectChapters.size
    val subDone = subjectChapters.count { (it.totalLectures ?: 0) > 0 && it.completedLectures >= (it.totalLectures ?: 0) }

    val filteredChapters = remember(subjectChapters, searchQuery, showOnlyWeak) {
        subjectChapters.filter { ch ->
            val matchQuery = searchQuery.isBlank() || ch.chapterName.contains(searchQuery, ignoreCase = true)
            val matchWeak = !showOnlyWeak || ch.isWeak || ch.resolvedMastery == MasteryLevel.NEEDS_REVISION
            matchQuery && matchWeak
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .testTag("tracker_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Header: Backlog & Chapter Mastery
            Text(
                text = "Backlog & Chapter Mastery",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Task completion is tracked separately from conceptual mastery",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Search Bar with Filter Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("chapter_search_input"),
                    placeholder = { Text("Search chapters...", fontSize = 12.sp, color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
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

                // Filter icon button (toggles weak / Needs Revision filter)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (showOnlyWeak) WarmAmber.copy(alpha = 0.2f) else SlateCard)
                        .border(1.dp, if (showOnlyWeak) WarmAmber else SlateCardBorder, RoundedCornerShape(12.dp))
                        .clickable { onToggleShowOnlyWeak() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter Weak or Needs Revision",
                        tint = if (showOnlyWeak) WarmAmber else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Subject Filter Pills: [Physics] [Chemistry] [Mathematics]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subjects.forEach { sub ->
                    val isSelected = currentSubject.equals(sub, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) ElectricBlue else Color.Transparent)
                            .border(1.dp, if (isSelected) ElectricBlue else SlateCardBorder, RoundedCornerShape(18.dp))
                            .clickable { onSelectSubject(sub) }
                            .testTag("tab_${sub.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sub,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Subject Mastery & Checklist Summary Strip (Stage 2)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$currentSubject Mastery Breakdown",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (subjectSummary.assessedCount > 0) {
                                "Assessed Done: ${subjectSummary.checklistCompletedCount}/${subjectSummary.assessedCount} (${subjectSummary.unassessedCount} unassessed)"
                            } else {
                                "Not Assessed (${subjectSummary.unassessedCount} unassessed)"
                            },
                            fontSize = 10.sp,
                            color = CyanAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MasteryMiniStat("Confident", subjectSummary.confidentCount, SoftGreen)
                        MasteryMiniStat("Practicing", subjectSummary.practicingCount, CyanAccent)
                        MasteryMiniStat("Learning", subjectSummary.learningCount, ElectricBlueLight)
                        MasteryMiniStat("Revise", subjectSummary.needsRevisionCount, CoralRed)
                        MasteryMiniStat("Unassessed", subjectSummary.notAssessedMasteryCount, TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Subject Section Header + Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$currentSubject Chapters (Total $totalCount • Done $completedCount • Left $remainingCount)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Text(
                    text = "$subDone/$subTotal",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ElectricBlueLight
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { if (subTotal > 0) (subDone.toFloat() / subTotal).coerceIn(0f, 1f) else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = ElectricBlueLight,
                trackColor = SlateCardElevated
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 6. Chapters List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredChapters, key = { it.id }) { ch ->
                    var isExpanded by remember { mutableStateOf(false) }
                    val mastery = ch.resolvedMastery
                    val masteryBadgeColor = masteryColor(mastery)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                            .testTag("chapter_card_${ch.id}"),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(masteryBadgeColor.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Book,
                                            contentDescription = null,
                                            tint = masteryBadgeColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ch.chapterName,
                                            fontSize = 14.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(masteryBadgeColor.copy(alpha = 0.16f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = mastery.displayLabel,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = masteryBadgeColor
                                                )
                                            }
                                            val dppText = if (ch.dppTotal != null) "${ch.dppDone ?: 0}/${ch.dppTotal}" else "${ch.dppDone ?: 0}"
                                            val pyqText = if (ch.pyqTotal != null) "${ch.pyqDone ?: 0}/${ch.pyqTotal}" else "${ch.pyqDone ?: 0}"
                                            Text(
                                                text = "DPP $dppText • PYQ $pyqText",
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (ch.lastStudiedDate.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Last studied: ${ch.lastStudiedDate}",
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    val totalLec = ch.totalLectures
                                    if (totalLec == null) {
                                        Text(
                                            text = "Unassessed",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextMuted
                                        )
                                    } else {
                                        Text(
                                            text = "${ch.completedLectures}/$totalLec",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (ch.completedLectures >= totalLec && totalLec > 0) SoftGreen else TextSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ChevronRight,
                                        contentDescription = if (isExpanded) "Collapse chapter details" else "Expand chapter details",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Expanded controls for Mastery, Spaced Revision, Lectures, DPP, PYQ, DIBY, Class Problems, Worksheets, Notes
                            AnimatedVisibility(visible = isExpanded) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(SlateCardBorder)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (!ch.isAssessed) {
                                        Text(
                                            text = "Notice: Lecture/practice totals are missing for this chapter (Not Assessed). Counts are not fabricated.",
                                            fontSize = 10.sp,
                                            color = WarmAmber
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }

                                    // Conceptual Mastery Level Selector (kept separate from task checklist)
                                    Text(
                                        text = "Conceptual Mastery Level (Separate from Checklist):",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(MasteryLevel.entries) { level ->
                                            val selected = mastery == level
                                            val chipColor = masteryColor(level)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (selected) chipColor.copy(alpha = 0.24f)
                                                        else SlateCardElevated
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (selected) chipColor else SlateCardBorder,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { onMasteryChange(ch, level) }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = level.displayLabel,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (selected) chipColor else TextSecondary
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Task Steppers
                                    TaskCounterRow(
                                        label = "Lectures Completed",
                                        done = ch.completedLectures,
                                        total = ch.totalLectures,
                                        onDelta = { onLectureChange(ch, it) }
                                    )
                                    TaskCounterRow(
                                        label = "DPP Solved",
                                        done = ch.dppDone ?: 0,
                                        total = ch.dppTotal,
                                        onDelta = { onDppChange(ch, it) }
                                    )
                                    TaskCounterRow(
                                        label = "PYQ Solved",
                                        done = ch.pyqDone ?: 0,
                                        total = ch.pyqTotal,
                                        onDelta = { onPyqChange(ch, it) }
                                    )
                                    TaskCounterRow(
                                        label = "Class Problems Solved",
                                        done = ch.classProblemsDone ?: 0,
                                        total = ch.classProblemsTotal,
                                        onDelta = { onClassProbChange(ch, it) }
                                    )
                                    TaskCounterRow(
                                        label = "DIBY Completed",
                                        done = ch.dibyDone ?: 0,
                                        total = ch.dibyTotal,
                                        onDelta = { onDibyChange(ch, it) }
                                    )
                                    TaskCounterRow(
                                        label = "Worksheets Completed",
                                        done = ch.worksheetsDone ?: 0,
                                        total = ch.worksheetsTotal,
                                        onDelta = { onWorksheetChange(ch, it) }
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Notes Toggle, Mark Studied / Schedule Spaced Revision, Weak Bookmark & Delete
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = if (ch.notesDone) "Notes: Done ✓" else "Notes: Pending",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (ch.notesDone) SoftGreen else TextMuted,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { onNotesToggle(ch) }
                                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                                            )

                                            OutlinedButton(
                                                onClick = { onMarkStudied(ch) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.EventRepeat,
                                                    contentDescription = null,
                                                    tint = CyanAccent,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (ch.nextRevisionDate.isNotBlank()) "Due ${ch.nextRevisionDate}" else "Schedule Revision",
                                                    fontSize = 10.sp,
                                                    color = CyanAccent
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { onToggleWeak(ch) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (ch.isWeak) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "Mark Weak",
                                                    tint = if (ch.isWeak) WarmAmber else TextMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteChapter(ch.id) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = CoralRed.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. Bottom Action Bar: "Import" & "+ Add Chapter"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenImportExport,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("import_button"),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SlateCard,
                        contentColor = TextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }

                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(44.dp)
                        .testTag("add_chapter_button"),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Add Chapter", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    if (showAddDialog) {
        AddChapterDialog(
            defaultSubject = currentSubject,
            onDismiss = { showAddDialog = false },
            onSaveChapter = { ch ->
                onSaveChapter(ch)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun MasteryMiniStat(
    label: String,
    count: Int,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun TaskCounterRow(
    label: String,
    done: Int,
    total: Int?,
    onDelta: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onDelta(-1) },
                modifier = Modifier.size(26.dp)
            ) {
                Text("-", fontSize = 16.sp, color = TextPrimary)
            }
            Text(
                text = if (total != null) "$done/$total" else "$done",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            IconButton(
                onClick = { onDelta(1) },
                modifier = Modifier.size(26.dp)
            ) {
                Text("+", fontSize = 16.sp, color = ElectricBlueLight)
            }
        }
    }
}

private fun masteryColor(level: MasteryLevel): Color = when (level) {
    MasteryLevel.CONFIDENT -> SoftGreen
    MasteryLevel.PRACTICING -> CyanAccent
    MasteryLevel.LEARNING -> ElectricBlueLight
    MasteryLevel.NEEDS_REVISION -> CoralRed
    MasteryLevel.NOT_ASSESSED -> ChemistryPurple
}
