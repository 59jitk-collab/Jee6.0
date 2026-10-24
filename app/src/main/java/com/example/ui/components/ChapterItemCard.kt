package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.ChapterEntity
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

@Composable
fun ChapterItemCard(
    chapter: ChapterEntity,
    onLectureChange: (Int) -> Unit,
    onNotesToggle: () -> Unit,
    onDppChange: (Int) -> Unit,
    onPyqChange: (Int) -> Unit,
    onDibyChange: (Int) -> Unit,
    onWorksheetChange: (Int) -> Unit,
    onToggleWeak: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chapter_card_${chapter.id}"),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Subject Badge, Chapter Title, Status Chip, Weak Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    SubjectBadge(subject = chapter.subject)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = chapter.chapterName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                IconButton(
                    onClick = onToggleWeak,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("weak_toggle_${chapter.id}")
                ) {
                    Icon(
                        imageVector = if (chapter.isWeak) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Flag Weak Topic",
                        tint = if (chapter.isWeak) WarmAmber else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status & Completion Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusChip(status = if (chapter.isWeak) "WEAK" else chapter.status)

                if (chapter.isAssessed) {
                    Text(
                        text = "${chapter.completionPercentage}% Complete",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (chapter.completionPercentage >= 100) SoftGreen else ElectricBlue
                    )
                } else {
                    Text(
                        text = "Pending counts (Unassessed)",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            if (chapter.isAssessed) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (chapter.completionPercentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (chapter.completionPercentage >= 100) SoftGreen else ElectricBlue,
                    trackColor = SlateCardElevated
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Overview Trackers (Lectures & Notes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (chapter.totalLectures != null) {
                    TaskCounterPill(
                        label = "Lec",
                        current = chapter.completedLectures,
                        total = chapter.totalLectures,
                        onDecrement = { onLectureChange(-1) },
                        onIncrement = { onLectureChange(1) }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateCardElevated)
                            .border(1.dp, SlateCardBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Lec: Not assessed",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onNotesToggle() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Checkbox(
                        checked = chapter.notesDone,
                        onCheckedChange = { onNotesToggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = SoftGreen,
                            uncheckedColor = TextMuted,
                            checkmarkColor = DeepNavyBg
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Notes",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (chapter.notesDone) SoftGreen else TextSecondary
                    )
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand Subtasks",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Subtask Trackers: DIBY, DPP, PYQ, Worksheets
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateCardElevated)
                        .border(1.dp, SlateCardBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Practice & Material Breakdown",
                        fontSize = 11.sp,
                        color = ElectricBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (chapter.dppTotal != null) {
                        TaskCounterRow(
                            label = "DPP Problem Sets",
                            current = chapter.dppDone ?: 0,
                            total = chapter.dppTotal,
                            onDecrement = { onDppChange(-1) },
                            onIncrement = { onDppChange(1) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (chapter.pyqTotal != null) {
                        TaskCounterRow(
                            label = "10-Year PYQ Archives",
                            current = chapter.pyqDone ?: 0,
                            total = chapter.pyqTotal,
                            onDecrement = { onPyqChange(-1) },
                            onIncrement = { onPyqChange(1) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (chapter.dibyTotal != null) {
                        TaskCounterRow(
                            label = "DIBY Questions",
                            current = chapter.dibyDone ?: 0,
                            total = chapter.dibyTotal,
                            onDecrement = { onDibyChange(-1) },
                            onIncrement = { onDibyChange(1) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (chapter.worksheetsTotal != null) {
                        TaskCounterRow(
                            label = "Worksheets",
                            current = chapter.worksheetsDone ?: 0,
                            total = chapter.worksheetsTotal,
                            onDecrement = { onWorksheetChange(-1) },
                            onIncrement = { onWorksheetChange(1) }
                        )
                    }

                    if (!chapter.isAssessed) {
                        Text(
                            text = "This chapter is marked 'Not assessed' per zero-fabrication guidelines. Import or edit to record verified counts.",
                            fontSize = 10.sp,
                            color = TextMuted,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Delete Chapter",
                            color = CoralRed.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onDelete() }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskCounterPill(
    label: String,
    current: Int,
    total: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SlateCardElevated)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDecrement,
            modifier = Modifier.size(24.dp),
            enabled = current > 0
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease $label",
                tint = if (current > 0) TextPrimary else TextMuted,
                modifier = Modifier.size(13.dp)
            )
        }

        Text(
            text = "$current / $total $label",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (current >= total) SoftGreen else TextPrimary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        IconButton(
            onClick = onIncrement,
            modifier = Modifier.size(24.dp),
            enabled = current < total
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase $label",
                tint = if (current < total) ElectricBlue else TextMuted,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
fun TaskCounterRow(
    label: String,
    current: Int,
    total: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onDecrement,
                modifier = Modifier.size(24.dp),
                enabled = current > 0
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease $label",
                    tint = if (current > 0) TextPrimary else TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }

            Text(
                text = "$current / $total",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (current >= total) SoftGreen else TextPrimary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            IconButton(
                onClick = onIncrement,
                modifier = Modifier.size(24.dp),
                enabled = current < total
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase $label",
                    tint = if (current < total) ElectricBlue else TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
