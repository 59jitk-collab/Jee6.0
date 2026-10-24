package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.importer.BacklogImportExport
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.TestRecordEntity
import com.example.ui.theme.ChemistryPurple
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricBlue
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
import com.example.util.DateUtils
import kotlin.math.roundToInt

@Composable
fun RecordTestDialog(
    initialTest: TestRecordEntity? = null,
    chapters: List<ChapterEntity> = emptyList(),
    onSaveTest: (TestRecordEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var testName by remember { mutableStateOf(initialTest?.testName ?: "Sunday Mock Test") }
    var testDate by remember { mutableStateOf(initialTest?.testDate ?: DateUtils.todayDateString()) }
    var physicsMarks by remember { mutableStateOf(initialTest?.physicsMarks?.toString() ?: "") }
    var chemistryMarks by remember { mutableStateOf(initialTest?.chemistryMarks?.toString() ?: "") }
    var mathMarks by remember { mutableStateOf(initialTest?.mathMarks?.toString() ?: "") }
    var negativeMarks by remember { mutableStateOf(initialTest?.negativeMarks?.toString() ?: "0") }
    var weakTopics by remember { mutableStateOf(initialTest?.weakTopics ?: "") }
    var analysisNotes by remember { mutableStateOf(initialTest?.analysisNotes ?: "") }
    var selectedSubjectTab by remember { mutableStateOf("Physics") }

    val subjectTabs = listOf(
        Triple("Physics", PhysicsCyan, physicsMarks),
        Triple("Chemistry", ChemistryPurple, chemistryMarks),
        Triple("Mathematics", MathOrange, mathMarks)
    )

    val computedTotal = (physicsMarks.toIntOrNull() ?: 0) +
            (chemistryMarks.toIntOrNull() ?: 0) +
            (mathMarks.toIntOrNull() ?: 0)
    val hasAnyMarks = physicsMarks.toIntOrNull() != null ||
            chemistryMarks.toIntOrNull() != null ||
            mathMarks.toIntOrNull() != null

    val activeSubjectScore = when (selectedSubjectTab) {
        "Physics" -> physicsMarks.toIntOrNull() ?: 0
        "Chemistry" -> chemistryMarks.toIntOrNull() ?: 0
        else -> mathMarks.toIntOrNull() ?: 0
    }
    val activeSubjectAccent = when (selectedSubjectTab) {
        "Physics" -> PhysicsCyan
        "Chemistry" -> ChemistryPurple
        else -> MathOrange
    }

    val suggestedWeakChapters = remember(chapters, selectedSubjectTab) {
        val fromDb = chapters
            .filter { it.subject.equals(selectedSubjectTab, ignoreCase = true) }
            .sortedBy { it.completionPercentage }
            .take(5)
            .map { it.chapterName }
        if (fromDb.isNotEmpty()) {
            fromDb
        } else {
            when (selectedSubjectTab) {
                "Physics" -> listOf("Rotational Motion", "Electrostatics", "Ray Optics", "Thermodynamics")
                "Chemistry" -> listOf("Chemical Bonding", "Thermodynamics", "Electrochemistry", "Coordination Compounds")
                else -> listOf("Complex Numbers", "Definite Integration", "Conic Sections", "Vectors & 3D")
            }
        }
    }

    val diagnosticPresets = listOf(
        "High accuracy (>85%)",
        "Silly calculation errors",
        "Time pressure in Math",
        "Formula recall gap",
        "Skipped multi-concept Qs"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (initialTest != null) "Sunday Mock Scorecard" else "Log Sunday Test Score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "$testName • $testDate",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (hasAnyMarks) SoftGreen.copy(alpha = 0.16f) else ElectricBlue.copy(alpha = 0.16f)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "$computedTotal / 300",
                        style = TabularNumeralStyle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (hasAnyMarks) SoftGreen else ElectricBlue
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("record_test_dialog")
            ) {
                // 1-Tap Auto-Generate Scorecard Button (no manual form filling needed!)
                OutlinedButton(
                    onClick = {
                        val base = initialTest ?: TestRecordEntity(
                            testName = testName,
                            testDate = testDate
                        )
                        val generated = BacklogImportExport.autoGenerateMockScoreForTest(base, chapters)
                        physicsMarks = (generated.physicsMarks ?: 68).toString()
                        chemistryMarks = (generated.chemistryMarks ?: 72).toString()
                        mathMarks = (generated.mathMarks ?: 64).toString()
                        negativeMarks = generated.negativeMarks.toString()
                        weakTopics = generated.weakTopics
                        analysisNotes = generated.analysisNotes
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_auto_fill_scorecard_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "1-Tap Auto-Fill Scorecard from Study Progress",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Single-line compact Subject Tabs: Physics | Chemistry | Mathematics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mock_test_subject_tabs"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    subjectTabs.forEach { (subject, accent, scoreText) ->
                        val isSelected = selectedSubjectTab == subject
                        val scoreDisplay = scoreText.toIntOrNull()?.let { " ($it)" } ?: ""
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) accent.copy(alpha = 0.2f) else SlateCard
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) accent else SlateCardBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedSubjectTab = subject }
                                .padding(horizontal = 4.dp)
                                .testTag("dialog_tab_${subject.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$subject$scoreDisplay",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) accent else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Interactive Slider + Quick Score Presets for Selected Subject Tab
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateCard)
                        .border(1.dp, activeSubjectAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$selectedSubjectTab Score Slider",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeSubjectAccent
                            )
                            Text(
                                text = "$activeSubjectScore / 100",
                                style = TabularNumeralStyle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        }

                        Slider(
                            value = activeSubjectScore.toFloat().coerceIn(0f, 100f),
                            onValueChange = { newVal ->
                                val rounded = newVal.roundToInt().toString()
                                when (selectedSubjectTab) {
                                    "Physics" -> physicsMarks = rounded
                                    "Chemistry" -> chemistryMarks = rounded
                                    else -> mathMarks = rounded
                                }
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = activeSubjectAccent,
                                activeTrackColor = activeSubjectAccent
                            ),
                            modifier = Modifier.height(28.dp)
                        )

                        // Quick 1-Tap Score Preset Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(45, 60, 72, 84, 95).forEach { preset ->
                                val isPresetActive = activeSubjectScore == preset
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isPresetActive) activeSubjectAccent.copy(alpha = 0.25f)
                                            else SlateCardElevated
                                        )
                                        .border(
                                            1.dp,
                                            if (isPresetActive) activeSubjectAccent else SlateCardBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            val str = preset.toString()
                                            when (selectedSubjectTab) {
                                                "Physics" -> physicsMarks = str
                                                "Chemistry" -> chemistryMarks = str
                                                else -> mathMarks = str
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+$preset",
                                        style = TabularNumeralStyle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPresetActive) activeSubjectAccent else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Compact 3-column Subject Score Inputs (out of 100 each)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = physicsMarks,
                        onValueChange = {
                            physicsMarks = it
                            selectedSubjectTab = "Physics"
                        },
                        label = {
                            Text(
                                "Physics",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        placeholder = { Text("/100", fontSize = 11.sp, color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_physics_marks"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PhysicsCyan,
                            unfocusedBorderColor = if (selectedSubjectTab == "Physics") PhysicsCyan.copy(alpha = 0.6f) else SlateCardBorder,
                            focusedContainerColor = SlateCard,
                            unfocusedContainerColor = SlateCard
                        )
                    )

                    OutlinedTextField(
                        value = chemistryMarks,
                        onValueChange = {
                            chemistryMarks = it
                            selectedSubjectTab = "Chemistry"
                        },
                        label = {
                            Text(
                                "Chemistry",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        placeholder = { Text("/100", fontSize = 11.sp, color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_chemistry_marks"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = ChemistryPurple,
                            unfocusedBorderColor = if (selectedSubjectTab == "Chemistry") ChemistryPurple.copy(alpha = 0.6f) else SlateCardBorder,
                            focusedContainerColor = SlateCard,
                            unfocusedContainerColor = SlateCard
                        )
                    )

                    OutlinedTextField(
                        value = mathMarks,
                        onValueChange = {
                            mathMarks = it
                            selectedSubjectTab = "Mathematics"
                        },
                        label = {
                            Text(
                                "Mathematics",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        placeholder = { Text("/100", fontSize = 11.sp, color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_mathematics_marks"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = MathOrange,
                            unfocusedBorderColor = if (selectedSubjectTab == "Mathematics") MathOrange.copy(alpha = 0.6f) else SlateCardBorder,
                            focusedContainerColor = SlateCard,
                            unfocusedContainerColor = SlateCard
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 1-Tap Weak Chapter Selector Pills
                Text(
                    text = "Tap Weak Chapters ($selectedSubjectTab):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestedWeakChapters.forEach { chName ->
                        val isSelected = weakTopics.contains(chName, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) activeSubjectAccent.copy(alpha = 0.22f) else SlateCard
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) activeSubjectAccent else SlateCardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    val currentList = weakTopics.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .toMutableList()
                                    if (currentList.any { it.equals(chName, ignoreCase = true) }) {
                                        currentList.removeAll { it.equals(chName, ignoreCase = true) }
                                    } else {
                                        currentList.add(chName)
                                    }
                                    weakTopics = currentList.joinToString(", ")
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = chName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) activeSubjectAccent else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 1-Tap Diagnostic Takeaway Chips
                Text(
                    text = "Tap Post-Test Diagnostic Tags:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    diagnosticPresets.forEach { tag ->
                        val isSelected = analysisNotes.contains(tag, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) ElectricBlue.copy(alpha = 0.22f) else SlateCard
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) ElectricBlue else SlateCardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    analysisNotes = if (analysisNotes.isBlank()) {
                                        tag
                                    } else if (isSelected) {
                                        analysisNotes
                                    } else {
                                        "$analysisNotes • $tag"
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) CyanAccent else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = weakTopics,
                    onValueChange = { weakTopics = it },
                    label = { Text("Focus Chapters", fontSize = 11.sp) },
                    placeholder = { Text("Tap chips above or type...", fontSize = 11.sp, color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = SlateCardBorder,
                        focusedContainerColor = SlateCard,
                        unfocusedContainerColor = SlateCard
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = analysisNotes,
                    onValueChange = { analysisNotes = it },
                    label = { Text("Diagnostic Summary", fontSize = 11.sp) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
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
                    val p = physicsMarks.toIntOrNull()
                    val c = chemistryMarks.toIntOrNull()
                    val m = mathMarks.toIntOrNull()
                    val isAssessed = p != null || c != null || m != null
                    val record = TestRecordEntity(
                        id = initialTest?.id ?: 0L,
                        testName = testName,
                        testDate = testDate,
                        physicsMarks = p,
                        chemistryMarks = c,
                        mathMarks = m,
                        negativeMarks = negativeMarks.toIntOrNull() ?: 0,
                        weakTopics = weakTopics,
                        analysisNotes = analysisNotes,
                        isAssessed = isAssessed
                    )
                    onSaveTest(record)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.testTag("save_test_button")
            ) {
                Text("Save Scorecard", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SlateCardElevated
    )
}
