package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.ChapterEntity
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AddChapterDialog(
    defaultSubject: String = "Physics",
    onSaveChapter: (ChapterEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var subject by remember { mutableStateOf(defaultSubject) }
    var chapterName by remember { mutableStateOf("") }
    var isUnassessed by remember { mutableStateOf(false) }
    var totalLectures by remember { mutableStateOf("8") }
    var dppTotal by remember { mutableStateOf("6") }
    var pyqTotal by remember { mutableStateOf("45") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add New Backlog Chapter",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_chapter_dialog")
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Physics", "Chemistry", "Mathematics").forEach { sub ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { subject = sub },
                            colors = CardDefaults.cardColors(
                                containerColor = if (subject == sub) ElectricBlue.copy(alpha = 0.2f) else SlateCard
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = sub.take(4),
                                fontSize = 11.sp,
                                color = if (subject == sub) ElectricBlue else TextSecondary,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = chapterName,
                    onValueChange = { chapterName = it },
                    label = { Text("Chapter Title", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Thermodynamics", fontSize = 11.sp, color = TextMuted) },
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isUnassessed = !isUnassessed }
                ) {
                    Checkbox(
                        checked = isUnassessed,
                        onCheckedChange = { isUnassessed = it },
                        colors = CheckboxDefaults.colors(checkedColor = ElectricBlue)
                    )
                    Text(
                        text = "Mark as 'Not assessed' (Pending counts)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                if (!isUnassessed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = totalLectures,
                            onValueChange = { totalLectures = it },
                            label = { Text("Lecs", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = SlateCardBorder,
                                focusedContainerColor = SlateCard,
                                unfocusedContainerColor = SlateCard
                            )
                        )

                        OutlinedTextField(
                            value = dppTotal,
                            onValueChange = { dppTotal = it },
                            label = { Text("DPPs", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = SlateCardBorder,
                                focusedContainerColor = SlateCard,
                                unfocusedContainerColor = SlateCard
                            )
                        )

                        OutlinedTextField(
                            value = pyqTotal,
                            onValueChange = { pyqTotal = it },
                            label = { Text("PYQs", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
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
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (chapterName.isNotBlank()) {
                        val lecs = if (isUnassessed) null else totalLectures.toIntOrNull()
                        val dpp = if (isUnassessed) null else dppTotal.toIntOrNull()
                        val pyq = if (isUnassessed) null else pyqTotal.toIntOrNull()
                        val chapter = ChapterEntity(
                            subject = subject,
                            chapterName = chapterName,
                            totalLectures = lecs,
                            completedLectures = 0,
                            dppTotal = dpp,
                            dppDone = 0,
                            pyqTotal = pyq,
                            pyqDone = 0,
                            dibyTotal = if (isUnassessed) null else 20,
                            dibyDone = 0,
                            worksheetsTotal = if (isUnassessed) null else 2,
                            worksheetsDone = 0,
                            status = if (isUnassessed) "NOT_ASSESSED" else "NOT_STARTED"
                        )
                        onSaveChapter(chapter)
                        onDismiss()
                    }
                },
                enabled = chapterName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.testTag("submit_add_chapter")
            ) {
                Text("Add Chapter")
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
