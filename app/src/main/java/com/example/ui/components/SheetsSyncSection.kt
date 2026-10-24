package com.example.ui.components

import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.SyncConflictEntity
import com.example.data.local.entities.SyncWorkbookConfigEntity
import com.example.data.sync.ConflictResolutionChoice
import com.example.data.sync.WorkbookDiscoveryPreview
import com.example.data.sync.WorkbookMappingEngine
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SheetsSyncSection(
    chapters: List<ChapterEntity>,
    workbookConfig: SyncWorkbookConfigEntity?,
    pendingOutboxCount: Int,
    unresolvedConflicts: List<SyncConflictEntity>,
    isSyncInProgress: Boolean,
    connectedGoogleEmail: String?,
    pendingAuthConsentIntent: PendingIntent?,
    onRequestGoogleAuth: () -> Unit,
    onGoogleAuthConsentResult: (Intent?) -> Unit,
    onClearPendingAuthConsent: () -> Unit,
    onLinkSpreadsheet: (
        spreadsheetIdOrUrl: String?,
        xlsxBytesToImport: ByteArray?,
        xlsxFileName: String?,
        convertXlsxOnDrive: Boolean,
        createNewSpreadsheetTitle: String?
    ) -> Unit,
    onSyncNow: () -> Unit,
    onRefreshFromSheet: () -> Unit,
    onResolveConflict: (String, ConflictResolutionChoice, String?) -> Unit,
    onDisconnect: () -> Unit,
    onExportBackupXlsx: () -> Unit
) {
    val context = LocalContext.current
    var sheetUrlOrIdInput by remember(workbookConfig?.spreadsheetId) {
        mutableStateOf(workbookConfig?.spreadsheetId ?: "")
    }
    var newSheetTitleInput by remember {
        mutableStateOf("JEE 80-Day Master Tracker")
    }
    var selectedXlsxBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedXlsxFileName by remember { mutableStateOf<String?>(null) }
    var discoveryPreview by remember { mutableStateOf<WorkbookDiscoveryPreview?>(null) }
    var activeConflictForMerge by remember { mutableStateOf<SyncConflictEntity?>(null) }
    var manualMergeJsonText by remember { mutableStateOf("") }

    // Launch Google OAuth consent PendingIntent when requested by GoogleAuthManager
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        onGoogleAuthConsentResult(activityResult.data)
    }

    LaunchedEffect(pendingAuthConsentIntent) {
        val pendingIntent = pendingAuthConsentIntent ?: return@LaunchedEffect
        try {
            val request = IntentSenderRequest.Builder(pendingIntent).build()
            consentLauncher.launch(request)
        } catch (_: Exception) {
            onClearPendingAuthConsent()
        }
    }

    // Picker for selecting .xlsx workbook to inspect & convert/link
    val pickXlsxForSheetsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val fileName = uri.lastPathSegment ?: "JEE_80_Day_Backlog_Tracker.xlsx"
                    selectedXlsxBytes = bytes
                    selectedXlsxFileName = fileName
                    discoveryPreview = WorkbookMappingEngine.discoverAndPreviewXlsxWorkbook(
                        bytes = bytes,
                        fileName = fileName,
                        existingChapters = chapters,
                        existingSchedules = emptyList(),
                        existingMistakes = emptyList()
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    val isLinked = workbookConfig?.isSpreadsheetLinked == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sheets_sync_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Sync Status Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(
                1.dp,
                when {
                    unresolvedConflicts.isNotEmpty() -> WarmAmber
                    isLinked -> SoftGreen.copy(alpha = 0.5f)
                    else -> SlateCardBorder
                }
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                unresolvedConflicts.isNotEmpty() -> Icons.Default.SyncProblem
                                isLinked -> Icons.Default.CloudDone
                                else -> Icons.Default.CloudOff
                            },
                            contentDescription = "Google Sheets Sync Status",
                            tint = when {
                                unresolvedConflicts.isNotEmpty() -> WarmAmber
                                isLinked -> SoftGreen
                                else -> TextMuted
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isLinked) {
                                    workbookConfig?.spreadsheetTitle.orEmpty().ifBlank { "Linked Google Sheet" }
                                } else {
                                    "Google Sheets Two-Way Sync"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = buildSyncSubtitle(
                                    workbookConfig = workbookConfig,
                                    connectedEmail = connectedGoogleEmail,
                                    pendingCount = pendingOutboxCount,
                                    conflictCount = unresolvedConflicts.size
                                ),
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    if (isSyncInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = CyanAccent
                        )
                    }
                }

                if (!workbookConfig?.lastErrorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Last warning: ${workbookConfig?.lastErrorMessage}",
                        fontSize = 11.sp,
                        color = WarmAmber
                    )
                }

                if (isLinked && workbookConfig != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sheet ID: ${workbookConfig.spreadsheetId}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onSyncNow,
                            enabled = !isSyncInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheets_sync_now_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = SlateCard,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Now", color = SlateCard, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onRefreshFromSheet,
                            enabled = !isSyncInProgress,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheets_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pull Sheet", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = onDisconnect,
                            enabled = !isSyncInProgress,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarmAmber),
                            modifier = Modifier.testTag("sheets_disconnect_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = "Disconnect Google Sheet",
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Conflict Resolution Queue (if any simultaneous edits occurred)
        if (unresolvedConflicts.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheets_conflicts_card"),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, WarmAmber),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarmAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Unresolved Sync Conflicts (${unresolvedConflicts.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = WarmAmber
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Both the app and Google Sheet modified these rows since the last sync. Neither version was overwritten—choose which version to keep:",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    unresolvedConflicts.forEach { conflict ->
                        ConflictItemRow(
                            conflict = conflict,
                            isEditingManualMerge = activeConflictForMerge?.conflictId == conflict.conflictId,
                            manualMergeJsonText = manualMergeJsonText,
                            onManualMergeTextChange = { manualMergeJsonText = it },
                            onKeepLocal = {
                                onResolveConflict(conflict.conflictId, ConflictResolutionChoice.KEEP_LOCAL, null)
                            },
                            onKeepRemote = {
                                onResolveConflict(conflict.conflictId, ConflictResolutionChoice.KEEP_REMOTE, null)
                            },
                            onOpenManualMerge = {
                                activeConflictForMerge = conflict
                                manualMergeJsonText = conflict.localPayloadJson
                            },
                            onConfirmManualMerge = {
                                onResolveConflict(
                                    conflict.conflictId,
                                    ConflictResolutionChoice.MANUAL_MERGE,
                                    manualMergeJsonText
                                )
                                activeConflictForMerge = null
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // 3. Step 1: Google OAuth Authorization Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateCardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Step 1 · Authorize Google Sheets & Drive (OAuth 2.0)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = CyanAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Requests only minimum scopes: spreadsheets (read/write synced tabs) and drive.file (convert/create your tracker workbook). Never asks for passwords or embeds secrets.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRequestGoogleAuth,
                        enabled = !isSyncInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("google_oauth_connect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!connectedGoogleEmail.isNullOrBlank()) "Connected ($connectedGoogleEmail)" else "Connect Google Account",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onExportBackupXlsx,
                        modifier = Modifier.testTag("pre_sync_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = "Backup local .xlsx before sync",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Backup .xlsx", fontSize = 11.sp)
                    }
                }
            }
        }

        // 4. Step 2: Workbook Discovery, .xlsx Conversion, or Sheet ID Linking
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateCardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Step 2 · Inspect .xlsx Workbook or Link Google Sheet",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = CyanAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Original user worksheets are preserved untouched. Sync uses dedicated canonical tabs (App_Chapters_Sync, App_Schedule_80D_Sync, App_Mistakes_Sync, App_Sync_Meta).",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        pickXlsxForSheetsLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inspect_xlsx_for_sheets_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedXlsxFileName?.let { "Selected: $it (Tap to change)" }
                            ?: "Select .xlsx Workbook to Inspect & Convert",
                        fontSize = 11.sp
                    )
                }

                // Workbook Discovery Preview Summary
                val preview = discoveryPreview
                if (preview != null) {
                    val sheetNamesList = preview.sheetInspections.map { it.sheetName }
                    val recognizedCols = preview.sheetInspections.flatMap { it.mappedColumns }.distinct()
                    val unmappedCols = preview.sheetInspections.flatMap { it.unmappedColumns }.distinct()

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateCardBorder.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .testTag("workbook_discovery_preview_box")
                    ) {
                        Column {
                            Text(
                                text = "Workbook Discovery: ${preview.fileName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = SoftGreen
                            )
                            Text(
                                text = "• Worksheets found (${sheetNamesList.size}): ${sheetNamesList.joinToString(", ").ifBlank { "Sheet1" }}",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "• Recognized columns: ${recognizedCols.joinToString(", ").ifBlank { "Standard positional A..P" }}",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                            if (unmappedCols.isNotEmpty()) {
                                Text(
                                    text = "• Unmapped columns (preserved in original tab): ${unmappedCols.joinToString(", ")}",
                                    fontSize = 10.sp,
                                    color = WarmAmber
                                )
                            }
                            Text(
                                text = "• Impact Preview: +${preview.chaptersToAdd} new, ↻${preview.chaptersToUpdate} updates, ⚠${preview.potentialConflictsCount} conflicts, ${preview.ignoredRowsCount} ignored rows",
                                fontSize = 10.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "• Unknown / Organic Chemistry totals preserved as NULL: ${preview.unassessedChaptersCount} chapter(s)",
                                fontSize = 10.sp,
                                color = CyanAccent
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    onLinkSpreadsheet(
                                        sheetUrlOrIdInput.takeIf { it.isNotBlank() },
                                        selectedXlsxBytes,
                                        selectedXlsxFileName,
                                        sheetUrlOrIdInput.isBlank(),
                                        null
                                    )
                                },
                                enabled = !isSyncInProgress,
                                colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_convert_and_sync_xlsx_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = SlateCard,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (sheetUrlOrIdInput.isBlank()) {
                                        "Convert .xlsx to Google Sheet in Drive & Start 2-Way Sync"
                                    } else {
                                        "Import .xlsx & Sync with Linked Sheet ID"
                                    },
                                    color = SlateCard,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = SlateCardBorder)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Or paste an existing Google Sheet URL / Spreadsheet ID:",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = sheetUrlOrIdInput,
                    onValueChange = { sheetUrlOrIdInput = it },
                    placeholder = {
                        Text(
                            "https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spreadsheet_id_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = SlateCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onLinkSpreadsheet(
                                sheetUrlOrIdInput.trim(),
                                selectedXlsxBytes,
                                selectedXlsxFileName,
                                false,
                                null
                            )
                        },
                        enabled = sheetUrlOrIdInput.isNotBlank() && !isSyncInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("link_existing_sheet_button")
                    ) {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Link Sheet & Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            onLinkSpreadsheet(
                                null,
                                selectedXlsxBytes,
                                selectedXlsxFileName,
                                false,
                                newSheetTitleInput.ifBlank { "JEE 80-Day Master Tracker" }
                            )
                        },
                        enabled = !isSyncInProgress,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_new_sheet_button")
                    ) {
                        Text("Create New Sheet", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictItemRow(
    conflict: SyncConflictEntity,
    isEditingManualMerge: Boolean,
    manualMergeJsonText: String,
    onManualMergeTextChange: (String) -> Unit,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit,
    onOpenManualMerge: () -> Unit,
    onConfirmManualMerge: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateCardBorder.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("conflict_item_${conflict.syncId}")
    ) {
        Text(
            text = "${conflict.entityType}: ${conflict.entityTitle}",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = TextPrimary
        )
        Text(
            text = "Conflicting fields: ${conflict.conflictingFieldsSummary.ifBlank { "Multiple fields" }}",
            fontSize = 10.sp,
            color = WarmAmber
        )
        Text(
            text = "Local edit: ${formatSyncTimestamp(conflict.localUpdatedAt)} · Sheet edit: ${formatSyncTimestamp(conflict.remoteUpdatedAt)}",
            fontSize = 10.sp,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (!isEditingManualMerge) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onKeepLocal,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("conflict_keep_local_${conflict.syncId}")
                ) {
                    Text("Keep App", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onKeepRemote,
                    colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("conflict_keep_remote_${conflict.syncId}")
                ) {
                    Text("Keep Sheet", color = SlateCard, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenManualMerge,
                    modifier = Modifier.testTag("conflict_manual_merge_${conflict.syncId}")
                ) {
                    Text("Merge", fontSize = 10.sp)
                }
            }
        } else {
            OutlinedTextField(
                value = manualMergeJsonText,
                onValueChange = onManualMergeTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onConfirmManualMerge,
                colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Merged Version & Archive Losing Copy", color = SlateCard, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun buildSyncSubtitle(
    workbookConfig: SyncWorkbookConfigEntity?,
    connectedEmail: String?,
    pendingCount: Int,
    conflictCount: Int
): String {
    val status = workbookConfig?.syncStatus ?: "NOT_CONFIGURED"
    val lastSyncMillis = workbookConfig?.lastSuccessfulSyncAt ?: 0L
    val lastSyncStr = if (lastSyncMillis > 0L) "Last sync: ${formatSyncTimestamp(lastSyncMillis)}" else "Never synced"
    val acct = connectedEmail?.takeIf { it.isNotBlank() }
        ?: workbookConfig?.connectedAccountEmail?.takeIf { it.isNotBlank() }
        ?: "Not signed in"
    return "$status · $lastSyncStr · Outbox: $pendingCount · Conflicts: $conflictCount · $acct"
}

private fun formatSyncTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "N/A"
    return runCatching {
        SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(epochMillis))
    }.getOrDefault("N/A")
}
