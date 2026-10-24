package com.example.ui.components

import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.importer.BacklogImportExport
import com.example.data.importer.CurriculumScheduleIngestionUtility
import com.example.data.importer.ImportValidationResult
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.SyncConflictEntity
import com.example.data.local.entities.SyncWorkbookConfigEntity
import com.example.data.sync.ConflictResolutionChoice
import com.example.ui.theme.CyanAccent
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
fun ImportExportDialog(
    chapters: List<ChapterEntity>,
    onImportText: (String) -> Unit,
    onImportXlsxBytes: (ByteArray) -> Unit = {},
    onSyncScheduleFromBacklog: () -> Unit = {},
    onLoadTemplate: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    workbookConfig: SyncWorkbookConfigEntity? = null,
    pendingOutboxCount: Int = 0,
    unresolvedConflicts: List<SyncConflictEntity> = emptyList(),
    isSyncInProgress: Boolean = false,
    connectedGoogleEmail: String? = null,
    pendingAuthConsentIntent: PendingIntent? = null,
    onRequestGoogleAuth: () -> Unit = {},
    onGoogleAuthConsentResult: (Intent?) -> Unit = {},
    onClearPendingAuthConsent: () -> Unit = {},
    onLinkSpreadsheet: (
        spreadsheetIdOrUrl: String?,
        xlsxBytesToImport: ByteArray?,
        xlsxFileName: String?,
        convertXlsxOnDrive: Boolean,
        createNewSpreadsheetTitle: String?
    ) -> Unit = { _, _, _, _, _ -> },
    onSyncNow: () -> Unit = {},
    onRefreshFromSheet: () -> Unit = {},
    onResolveConflict: (String, ConflictResolutionChoice, String?) -> Unit = { _, _, _ -> },
    onDisconnectGoogleSheet: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var importText by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var pendingXlsxBytes by remember { mutableStateOf<ByteArray?>(null) }
    var exportFormat by remember { mutableStateOf("json") }
    var previewResult by remember { mutableStateOf<ImportValidationResult?>(null) }

    // Zero-permission Android system document picker for .xlsx, .csv, or .json files
    val openSpreadsheetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    selectedFileName = uri.lastPathSegment ?: "Selected Spreadsheet"
                    // Detect ZIP header ('PK\x03\x04') for .xlsx OpenXML workbook
                    val isZipXlsx = bytes.size >= 4 &&
                            bytes[0] == 0x50.toByte() &&
                            bytes[1] == 0x4B.toByte()
                    if (isZipXlsx) {
                        pendingXlsxBytes = bytes
                        previewResult = BacklogImportExport.validateAndPreviewXlsxImport(bytes, chapters)
                    } else {
                        pendingXlsxBytes = null
                        val textContent = bytes.toString(Charsets.UTF_8)
                        importText = textContent
                        previewResult = BacklogImportExport.validateAndPreviewImport(textContent, chapters)
                    }
                }
            } catch (_: Exception) {
                previewResult = ImportValidationResult(
                    parsedChapters = emptyList(),
                    newChapters = emptyList(),
                    duplicateChapters = emptyList(),
                    invalidLinesCount = 1,
                    warnings = listOf("Could not read selected file. Ensure it is a valid .xlsx, .csv, or .json file."),
                    isValid = false,
                    summaryText = "File Read Error"
                )
            }
        }
    }

    // Zero-permission Android system document creator for exporting .xlsx spreadsheet
    val saveXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            try {
                val workbookBytes = CurriculumScheduleIngestionUtility.generateXlsxWorkbookBytes(
                    chapters.ifEmpty { BacklogImportExport.getStandardJeeTemplate() }
                )
                context.contentResolver.openOutputStream(uri)?.use { it.write(workbookBytes) }
            } catch (_: Exception) {
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = ".xlsx Spreadsheet & Google Sheets Sync Hub",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Two-way Google Sheets sync · Preserves nullable JEE totals & 80-Day Schedule",
                    fontSize = 11.sp,
                    color = CyanAccent
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("import_export_dialog")
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SlateCardElevated,
                    contentColor = ElectricBlue,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = ElectricBlue
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            previewResult = null
                        },
                        text = { Text(".xlsx / Import", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Export .xlsx", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            val badge = if (unresolvedConflicts.isNotEmpty()) " (${unresolvedConflicts.size})" else ""
                            Text("80-Day & Sheets$badge", fontSize = 11.sp)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        if (previewResult == null) {
                            // 1. Direct .xlsx Spreadsheet File Picker Button
                            Button(
                                onClick = {
                                    openSpreadsheetLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.ms-excel",
                                            "text/csv",
                                            "text/comma-separated-values",
                                            "application/json",
                                            "text/plain",
                                            "*/*"
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("pick_xlsx_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = SlateCard,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Select .xlsx / CSV File from Device",
                                    color = SlateCard,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 1-Tap Sample .xlsx Workbook Ingestion for instant testing
                            OutlinedButton(
                                onClick = {
                                    val sampleBytes = CurriculumScheduleIngestionUtility.generateXlsxWorkbookBytes(
                                        BacklogImportExport.getStandardJeeTemplate()
                                    )
                                    selectedFileName = "JEE_80_Day_Backlog_Tracker.xlsx"
                                    pendingXlsxBytes = sampleBytes
                                    previewResult = BacklogImportExport.validateAndPreviewXlsxImport(sampleBytes, chapters)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = SlateCard,
                                    contentColor = CyanAccent
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("test_sample_xlsx_import_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TableChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Preview Built-In .xlsx Workbook Mapping", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Or paste CSV / JSON rows below to map into the 80-Day Schedule:",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = importText,
                                onValueChange = { importText = it },
                                placeholder = {
                                    Text(
                                        "Subject, ChapterName, TotalLectures, CompletedLectures, DPP, PYQ\nPhysics, Kinematics 1D & 2D, 8, 4, 6, 45",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .testTag("import_text_field"),
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
                            Button(
                                onClick = {
                                    if (importText.isNotBlank()) {
                                        pendingXlsxBytes = null
                                        selectedFileName = null
                                        previewResult = BacklogImportExport.validateAndPreviewImport(importText, chapters)
                                    }
                                },
                                enabled = importText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("parse_import_preview_button")
                            ) {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Validate & Preview 80-Day Mapping", fontSize = 12.sp)
                            }
                        } else {
                            // Import Preview Mode
                            val res = previewResult!!
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SlateCard),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (res.isValid) SoftGreen.copy(alpha = 0.5f) else WarmAmber
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (res.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (res.isValid) SoftGreen else WarmAmber,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (selectedFileName != null) "Validated: $selectedFileName" else "Curriculum Validation & Schedule Preview",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = TextPrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = res.summaryText,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )

                                    if (res.warnings.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        res.warnings.forEach { warn ->
                                            Text("• $warn", fontSize = 11.sp, color = WarmAmber)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        previewResult = null
                                        pendingXlsxBytes = null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Back", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        val xlsx = pendingXlsxBytes
                                        if (xlsx != null) {
                                            onImportXlsxBytes(xlsx)
                                        } else {
                                            onImportText(importText)
                                        }
                                        onDismiss()
                                    },
                                    enabled = res.isValid && res.parsedChapters.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                                    modifier = Modifier
                                        .weight(1.6f)
                                        .testTag("confirm_import_button")
                                ) {
                                    Text("Ingest & Map 80 Days", color = SlateCard, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    1 -> {
                        Text(
                            text = "Export active database (${chapters.size} chapters tracked):",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                saveXlsxLauncher.launch("JEE_80_Day_Backlog_Tracker.xlsx")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_xlsx_file_button")
                        ) {
                            Icon(imageVector = Icons.Default.TableChart, contentDescription = null, tint = SlateCard, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save as .xlsx Excel Workbook", color = SlateCard, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val exportContent = if (exportFormat == "json") {
                            remember(chapters) { BacklogImportExport.exportChaptersToJson(chapters) }
                        } else {
                            remember(chapters) { BacklogImportExport.exportChaptersToCsv(chapters) }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { exportFormat = "json" },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (exportFormat == "json") ElectricBlue.copy(alpha = 0.2f) else SlateCard
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("JSON Format", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { exportFormat = "csv" },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (exportFormat == "csv") ElectricBlue.copy(alpha = 0.2f) else SlateCard
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("CSV Format", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = exportContent,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextSecondary,
                                unfocusedTextColor = TextSecondary,
                                focusedContainerColor = SlateCard,
                                unfocusedContainerColor = SlateCard
                            )
                        )
                    }

                    2 -> {
                        // Google Sheets Two-Way Sync Section
                        SheetsSyncSection(
                            chapters = chapters,
                            workbookConfig = workbookConfig,
                            pendingOutboxCount = pendingOutboxCount,
                            unresolvedConflicts = unresolvedConflicts,
                            isSyncInProgress = isSyncInProgress,
                            connectedGoogleEmail = connectedGoogleEmail,
                            pendingAuthConsentIntent = pendingAuthConsentIntent,
                            onRequestGoogleAuth = onRequestGoogleAuth,
                            onGoogleAuthConsentResult = onGoogleAuthConsentResult,
                            onClearPendingAuthConsent = onClearPendingAuthConsent,
                            onLinkSpreadsheet = onLinkSpreadsheet,
                            onSyncNow = onSyncNow,
                            onRefreshFromSheet = onRefreshFromSheet,
                            onResolveConflict = onResolveConflict,
                            onDisconnect = onDisconnectGoogleSheet,
                            onExportBackupXlsx = {
                                saveXlsxLauncher.launch("JEE_80_Day_PreSync_Backup.xlsx")
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = SlateCardBorder)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Local Room Data Ingestion & 80-Day Schedule Engine:",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                onSyncScheduleFromBacklog()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sync_schedule_from_backlog_button")
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generate 80-Day Schedule from Current Backlog", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                onLoadTemplate()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("load_template_button")
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = SlateCard)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load JEE 80-Day Mission Blueprint", color = SlateCard, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Validates Physics, Chemistry, and Mathematics chapters against JEE curriculum standards and distributes remaining lectures across all 80 days.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                onClearAll()
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SlateCard,
                                contentColor = WarmAmber
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear All Chapter Data", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ElectricBlue)
            }
        },
        containerColor = SlateCardElevated
    )
}
