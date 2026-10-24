package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.MistakeEntryEntity
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogMistakeDialog(
    initialMistake: MistakeEntryEntity? = null,
    defaultTestName: String? = null,
    onSaveMistake: (MistakeEntryEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var subject by remember { mutableStateOf(initialMistake?.subject ?: "Physics") }
    var chapterName by remember { mutableStateOf(initialMistake?.chapterName ?: "") }
    var testName by remember { mutableStateOf(initialMistake?.testName ?: defaultTestName ?: "Sunday Mock Test Analysis") }
    var questionSnippet by remember { mutableStateOf(initialMistake?.questionSnippet ?: "") }
    var errorType by remember { mutableStateOf(initialMistake?.errorType ?: "Concept Gap") }
    var conceptMissed by remember { mutableStateOf(initialMistake?.conceptMissed ?: "") }
    var lessonLearned by remember { mutableStateOf(initialMistake?.lessonLearned ?: initialMistake?.correctTakeaway ?: "") }

    val errorTypes = listOf(
        "Concept Gap",
        "Calculation Slip",
        "Time Pressure",
        "Misread Question",
        "Formula Forgot",
        "Trap Option"
    )

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = WarmAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (initialMistake != null) "Edit Test Mistake" else "Sunday Test Mistake Entry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .testTag("log_mistake_dialog")
            ) {
                Text(
                    text = "Record root cause analysis to avoid repeating errors in future Sunday mock tests.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Subject Picker
                Text("Subject:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Physics", "Chemistry", "Mathematics").forEach { sub ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { subject = sub },
                            colors = CardDefaults.cardColors(
                                containerColor = if (subject == sub) ElectricBlue.copy(alpha = 0.2f) else SlateCard
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (subject == sub) ElectricBlue else SlateCardBorder
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = sub.take(4),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (subject == sub) ElectricBlue else TextSecondary,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sunday Test Identifier
                OutlinedTextField(
                    value = testName,
                    onValueChange = { testName = it },
                    label = { Text("Sunday Test / Source", fontSize = 11.sp) },
                    placeholder = { Text("e.g. Sunday Mock Test 1, Full Syllabus Test", fontSize = 11.sp, color = TextMuted) },
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

                Spacer(modifier = Modifier.height(8.dp))

                // Chapter
                OutlinedTextField(
                    value = chapterName,
                    onValueChange = { chapterName = it },
                    label = { Text("Chapter / Topic", fontSize = 11.sp) },
                    placeholder = { Text("e.g. Rotational Dynamics, Ionic Equilibrium", fontSize = 11.sp, color = TextMuted) },
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

                Spacer(modifier = Modifier.height(10.dp))

                // Error Classification Chips
                Text("Specific Error Type:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    errorTypes.forEach { type ->
                        val isSelected = errorType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) WarmAmber.copy(alpha = 0.25f) else SlateCard)
                                .border(1.dp, if (isSelected) WarmAmber else SlateCardBorder, RoundedCornerShape(6.dp))
                                .clickable { errorType = type }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = type,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) WarmAmber else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Question Context
                OutlinedTextField(
                    value = questionSnippet,
                    onValueChange = { questionSnippet = it },
                    label = { Text("Question Concept / Trap Description", fontSize = 11.sp) },
                    placeholder = { Text("Problem asked for angular acceleration about contact point...", fontSize = 11.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = SlateCardBorder,
                        focusedContainerColor = SlateCard,
                        unfocusedContainerColor = SlateCard
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Concept Missed (User explicitly requested!)
                OutlinedTextField(
                    value = conceptMissed,
                    onValueChange = { conceptMissed = it },
                    label = { Text("Concept Missed (Theory / Formula)", fontSize = 11.sp) },
                    placeholder = { Text("Missed torque of normal force when cylinder begins tilting...", fontSize = 11.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = WarmAmber,
                        unfocusedBorderColor = SlateCardBorder,
                        focusedContainerColor = SlateCard,
                        unfocusedContainerColor = SlateCard
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Lesson Learned (User explicitly requested!)
                OutlinedTextField(
                    value = lessonLearned,
                    onValueChange = { lessonLearned = it },
                    label = { Text("Lesson Learned (Rule for Next Test)", fontSize = 11.sp) },
                    placeholder = { Text("Always take torque about contact point to eliminate normal and friction forces immediately...", fontSize = 11.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = SoftGreen,
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
                    if (chapterName.isNotBlank() && questionSnippet.isNotBlank()) {
                        val entry = MistakeEntryEntity(
                            id = initialMistake?.id ?: 0L,
                            subject = subject,
                            chapterName = chapterName,
                            testName = testName.ifBlank { "Sunday Test Analysis" },
                            questionSnippet = questionSnippet,
                            errorType = errorType,
                            conceptMissed = conceptMissed,
                            correctTakeaway = lessonLearned,
                            lessonLearned = lessonLearned
                        )
                        onSaveMistake(entry)
                        onDismiss()
                    }
                },
                enabled = chapterName.isNotBlank() && questionSnippet.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.testTag("save_mistake_button")
            ) {
                Text("Save to Notebook")
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
