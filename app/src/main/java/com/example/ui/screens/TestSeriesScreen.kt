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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.importer.BacklogImportExport
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.MistakeEntryEntity
import com.example.data.local.entities.TestRecordEntity
import com.example.ui.components.RecordTestDialog
import com.example.ui.theme.ChemistryPurple
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.MathOrange
import com.example.ui.theme.PhysicsCyan
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.TabularNumeralStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TestSeriesScreen(
    tests: List<TestRecordEntity>,
    onSaveTest: (TestRecordEntity) -> Unit,
    onDeleteTest: (Long) -> Unit,
    onSaveMistake: (MistakeEntryEntity) -> Unit = {},
    chapters: List<ChapterEntity> = emptyList(),
    onAutoEvaluateTest: (TestRecordEntity) -> Unit = {},
    onAutoEvaluateAllTests: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editingTest by remember { mutableStateOf<TestRecordEntity?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }

    val filterOptions = listOf("All", "Physics", "Chemistry", "Mathematics")

    val displayTests = remember(tests) {
        if (tests.isNotEmpty()) {
            tests
        } else {
            BacklogImportExport.generateSampleSundayTests()
        }
    }

    val assessedCount = remember(displayTests) { displayTests.count { it.isAssessed } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .testTag("test_series_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Header + 1-Tap Auto-Fill All Banner
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SlateCardElevated)
                                .border(1.dp, SlateCardBorder, CircleShape)
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
                                text = "Sunday Mock Test Series",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "$assessedCount of ${displayTests.size} evaluated • Pre-mapped to 80-Day Syllabus",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onAutoEvaluateAllTests,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("auto_evaluate_all_tests_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Auto-Fill All",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                    }
                }
            }

            // 2. Single-line Subject Filter Tabs: All | Physics | Chemistry | Mathematics
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filterOptions.forEach { opt ->
                        val isSelected = selectedFilter == opt
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) ElectricBlue else SlateCard)
                                .border(
                                    1.dp,
                                    if (isSelected) ElectricBlue else SlateCardBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedFilter = opt }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = opt,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 3. Compact Sunday Mock Test Cards (no misleading progress bar on unassessed tests)
            items(displayTests, key = { it.id }) { test ->
                val isAssessed = test.isAssessed
                val totalScore = test.totalScore ?: 0

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingTest = test }
                        .testTag("test_card_${test.id}"),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isAssessed) SoftGreen.copy(alpha = 0.35f) else SlateCardBorder
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = test.testName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = ElectricBlueLight,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = test.testDate,
                                        style = TabularNumeralStyle,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "•",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = if (isAssessed) {
                                            when (selectedFilter) {
                                                "Physics" -> "Phy: ${test.physicsMarks ?: 0}/100"
                                                "Chemistry" -> "Chem: ${test.chemistryMarks ?: 0}/100"
                                                "Mathematics" -> "Math: ${test.mathMarks ?: 0}/100"
                                                else -> "Score: $totalScore/300"
                                            }
                                        } else {
                                            "Unassessed"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = if (isAssessed) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isAssessed) SoftGreen else TextMuted
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!isAssessed) {
                                    OutlinedButton(
                                        onClick = {
                                            val autoScored = BacklogImportExport.autoGenerateMockScoreForTest(test, chapters)
                                            onAutoEvaluateTest(test)
                                            onSaveTest(autoScored)
                                        },
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("auto_fill_test_button_${test.id}"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = CyanAccent,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Auto",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyanAccent
                                        )
                                    }
                                }

                                Button(
                                    onClick = { editingTest = test },
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("enter_marks_button_${test.id}"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAssessed) SlateCardElevated else ElectricBlue
                                    )
                                ) {
                                    Text(
                                        text = if (isAssessed) "Edit Score" else "Enter Marks",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAssessed) ElectricBlueLight else Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Pre-mapped syllabus preview for unassessed tests (compact single line, no progress bar)
                        if (!isAssessed && test.analysisNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = test.analysisNotes,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Only show progress bar and subject breakdown for assessed tests
                        if (isAssessed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (totalScore / 300f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = SoftGreen,
                                trackColor = SlateCardElevated
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Phy: ${test.physicsMarks ?: 0}/100",
                                    style = TabularNumeralStyle,
                                    fontSize = 11.sp,
                                    color = PhysicsCyan
                                )
                                Text(
                                    text = "Chem: ${test.chemistryMarks ?: 0}/100",
                                    style = TabularNumeralStyle,
                                    fontSize = 11.sp,
                                    color = ChemistryPurple
                                )
                                Text(
                                    text = "Math: ${test.mathMarks ?: 0}/100",
                                    style = TabularNumeralStyle,
                                    fontSize = 11.sp,
                                    color = MathOrange
                                )
                            }
                            if (test.weakTopics.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Focus Areas: ${test.weakTopics}",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (editingTest != null) {
        RecordTestDialog(
            initialTest = editingTest,
            chapters = chapters,
            onDismiss = { editingTest = null },
            onSaveTest = { updated ->
                onSaveTest(updated)
                editingTest = null
            }
        )
    }
}
