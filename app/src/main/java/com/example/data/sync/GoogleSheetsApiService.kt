package com.example.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RemoteSpreadsheetMeta(
    val spreadsheetId: String,
    val title: String,
    val spreadsheetUrl: String,
    val sheetTitles: List<String>
)

data class RemoteWorkbookSnapshot(
    val spreadsheetId: String,
    val chaptersRows: List<List<String>>, // Includes header at index 0 if present
    val scheduleRows: List<List<String>>,
    val mistakeRows: List<List<String>>,
    val metaRows: List<List<String>>
)

data class SheetValueRangeUpdate(
    val range: String, // e.g., "App_Chapters_Sync!A2:AB2"
    val values: List<List<String>>
)

sealed class SheetsApiException(message: String, val httpCode: Int = 0) : Exception(message) {
    class AuthRevokedException(message: String, httpCode: Int = 401) : SheetsApiException(message, httpCode)
    class RateLimitedException(message: String, val retryAfterSeconds: Long = 30L) : SheetsApiException(message, 429)
    class NotFoundException(message: String) : SheetsApiException(message, 404)
    class NetworkOrServerException(message: String, httpCode: Int = 500) : SheetsApiException(message, httpCode)
}

interface GoogleSheetsRemoteGateway {
    suspend fun convertXlsxToGoogleSpreadsheet(
        accessToken: String,
        title: String,
        xlsxBytes: ByteArray
    ): Result<RemoteSpreadsheetMeta>

    suspend fun createSpreadsheet(
        accessToken: String,
        title: String
    ): Result<RemoteSpreadsheetMeta>

    suspend fun getSpreadsheetMetadata(
        accessToken: String,
        spreadsheetId: String
    ): Result<RemoteSpreadsheetMeta>

    suspend fun ensureSyncTabsAndHeaders(
        accessToken: String,
        spreadsheetId: String,
        existingSheetTitles: Set<String>
    ): Result<Unit>

    suspend fun fetchAllSyncTabs(
        accessToken: String,
        spreadsheetId: String
    ): Result<RemoteWorkbookSnapshot>

    suspend fun batchUpdateSyncTabs(
        accessToken: String,
        spreadsheetId: String,
        updates: List<SheetValueRangeUpdate>
    ): Result<Unit>
}

/**
 * Production Google Sheets API v4 & Google Drive API v3 client using OkHttp.
 * Preserves existing worksheets, writes only to app-owned sync tabs, and classifies
 * 401/403 (auth revoked), 429 (rate limit), and 5xx/network errors explicitly.
 */
class GoogleSheetsApiService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) : GoogleSheetsRemoteGateway {

    companion object {
        private const val SHEETS_BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets"
        private const val DRIVE_UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".toMediaType()

        /**
         * Extracts the spreadsheetId from either a full Google Sheets URL or a raw spreadsheet ID string.
         */
        fun extractSpreadsheetId(input: String): String {
            val trimmed = input.trim()
            val regex = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
            val match = regex.find(trimmed)
            return match?.groupValues?.get(1) ?: trimmed
        }
    }

    override suspend fun convertXlsxToGoogleSpreadsheet(
        accessToken: String,
        title: String,
        xlsxBytes: ByteArray
    ): Result<RemoteSpreadsheetMeta> = withContext(Dispatchers.IO) {
        try {
            val metadataJson = JSONObject().apply {
                put("name", title.removeSuffix(".xlsx"))
                put("mimeType", "application/vnd.google-apps.spreadsheet")
            }.toString()

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
                .addPart(xlsxBytes.toRequestBody(XLSX_MEDIA_TYPE))
                .build()

            val request = Request.Builder()
                .url(DRIVE_UPLOAD_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(resp.code, bodyStr, resp.header("Retry-After")))
                }
                val json = JSONObject(bodyStr)
                val sheetId = json.optString("id", "")
                if (sheetId.isBlank()) {
                    return@withContext Result.failure(
                        SheetsApiException.NetworkOrServerException("Drive conversion succeeded but returned empty file ID.")
                    )
                }
                // Fetch full spreadsheet metadata including converted tabs
                getSpreadsheetMetadata(accessToken, sheetId)
            }
        } catch (e: IOException) {
            Result.failure(SheetsApiException.NetworkOrServerException("Network error during XLSX conversion: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createSpreadsheet(
        accessToken: String,
        title: String
    ): Result<RemoteSpreadsheetMeta> = withContext(Dispatchers.IO) {
        try {
            val reqJson = JSONObject().apply {
                put("properties", JSONObject().put("title", title))
                val sheetsArr = JSONArray()
                listOf(
                    WorkbookMappingEngine.TAB_CHAPTERS,
                    WorkbookMappingEngine.TAB_SCHEDULES,
                    WorkbookMappingEngine.TAB_MISTAKES,
                    WorkbookMappingEngine.TAB_META
                ).forEach { tabName ->
                    sheetsArr.put(
                        JSONObject().put("properties", JSONObject().put("title", tabName))
                    )
                }
                put("sheets", sheetsArr)
            }

            val req = Request.Builder()
                .url(SHEETS_BASE_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(reqJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(resp.code, bodyStr, resp.header("Retry-After")))
                }
                val meta = parseSpreadsheetMeta(bodyStr)
                // Write initial header rows
                ensureSyncTabsAndHeaders(accessToken, meta.spreadsheetId, meta.sheetTitles.toSet())
                Result.success(meta)
            }
        } catch (e: IOException) {
            Result.failure(SheetsApiException.NetworkOrServerException("Network error creating spreadsheet: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSpreadsheetMetadata(
        accessToken: String,
        spreadsheetId: String
    ): Result<RemoteSpreadsheetMeta> = withContext(Dispatchers.IO) {
        val cleanId = extractSpreadsheetId(spreadsheetId)
        try {
            val url = "$SHEETS_BASE_URL/$cleanId?fields=spreadsheetId,properties.title,spreadsheetUrl,sheets.properties(sheetId,title)"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(resp.code, bodyStr, resp.header("Retry-After")))
                }
                Result.success(parseSpreadsheetMeta(bodyStr))
            }
        } catch (e: IOException) {
            Result.failure(SheetsApiException.NetworkOrServerException("Network error reading spreadsheet metadata: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun ensureSyncTabsAndHeaders(
        accessToken: String,
        spreadsheetId: String,
        existingSheetTitles: Set<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = extractSpreadsheetId(spreadsheetId)
        try {
            val requiredTabs = listOf(
                WorkbookMappingEngine.TAB_CHAPTERS,
                WorkbookMappingEngine.TAB_SCHEDULES,
                WorkbookMappingEngine.TAB_MISTAKES,
                WorkbookMappingEngine.TAB_META
            )
            val missingTabs = requiredTabs.filter { reqTab ->
                existingSheetTitles.none { it.equals(reqTab, ignoreCase = true) }
            }

            if (missingTabs.isNotEmpty()) {
                val requestsArray = JSONArray()
                missingTabs.forEach { tabTitle ->
                    val addSheetObj = JSONObject().put(
                        "addSheet",
                        JSONObject().put("properties", JSONObject().put("title", tabTitle))
                    )
                    requestsArray.put(addSheetObj)
                }
                val batchReqBody = JSONObject().put("requests", requestsArray).toString()
                val req = Request.Builder()
                    .url("$SHEETS_BASE_URL/$cleanId:batchUpdate")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(batchReqBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(mapHttpError(resp.code, bodyStr, resp.header("Retry-After")))
                    }
                }
            }

            // Write headers to the 4 sync tabs
            val headerUpdates = listOf(
                SheetValueRangeUpdate("${WorkbookMappingEngine.TAB_CHAPTERS}!A1:AB1", listOf(WorkbookMappingEngine.CHAPTER_HEADERS)),
                SheetValueRangeUpdate("${WorkbookMappingEngine.TAB_SCHEDULES}!A1:W1", listOf(WorkbookMappingEngine.SCHEDULE_HEADERS)),
                SheetValueRangeUpdate("${WorkbookMappingEngine.TAB_MISTAKES}!A1:R1", listOf(WorkbookMappingEngine.MISTAKE_HEADERS)),
                SheetValueRangeUpdate("${WorkbookMappingEngine.TAB_META}!A1:C1", listOf(listOf("Key", "Value", "UpdatedAt")))
            )
            batchUpdateSyncTabs(accessToken, cleanId, headerUpdates)
        } catch (e: IOException) {
            Result.failure(SheetsApiException.NetworkOrServerException("Network error ensuring sync tabs: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchAllSyncTabs(
        accessToken: String,
        spreadsheetId: String
    ): Result<RemoteWorkbookSnapshot> = withContext(Dispatchers.IO) {
        val cleanId = extractSpreadsheetId(spreadsheetId)
        try {
            val r1 = URLEncoder.encode("${WorkbookMappingEngine.TAB_CHAPTERS}!A1:AB2000", "UTF-8")
            val r2 = URLEncoder.encode("${WorkbookMappingEngine.TAB_SCHEDULES}!A1:W500", "UTF-8")
            val r3 = URLEncoder.encode("${WorkbookMappingEngine.TAB_MISTAKES}!A1:R2000", "UTF-8")
            val r4 = URLEncoder.encode("${WorkbookMappingEngine.TAB_META}!A1:C100", "UTF-8")

            val url = "$SHEETS_BASE_URL/$cleanId/values:batchGet?ranges=$r1&ranges=$r2&ranges=$r3&ranges=$r4&valueRenderOption=FORMATTED_VALUE"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(resp.code, bodyStr, resp.header("Retry-After")))
                }
                val root = JSONObject(bodyStr)
                val valueRanges = root.optJSONArray("valueRanges") ?: JSONArray()

                fun parseRowsAt(index: Int): List<List<String>> {
                    val vr = valueRanges.optJSONObject(index) ?: return emptyList()
                    val valuesArr = vr.optJSONArray("values") ?: return emptyList()
                    val list = mutableListOf<List<String>>()
                    for (i in 0 until valuesArr.length()) {
                        val rowArr = valuesArr.optJSONArray(i) ?: continue
                        val row = mutableListOf<String>()
                        for (j in 0 until rowArr.length()) {
                            row.add(rowArr.optString(j, ""))
                        }
                        list.add(row)
                    }
                    return list
                }

                Result.success(
                    RemoteWorkbookSnapshot(
                        spreadsheetId = cleanId,
                        chaptersRows = parseRowsAt(0),
                        scheduleRows = parseRowsAt(1),
                        mistakeRows = parseRowsAt(2),
                        metaRows = parseRowsAt(3)
                    )
                )
            }
        } catch (e: IOException) {
            Result.failure(SheetsApiException.NetworkOrServerException("Network error reading Google Sheet values: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun batchUpdateSyncTabs(
        accessToken: String,
        spreadsheetId: String,
        updates: List<SheetValueRangeUpdate>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (updates.isEmpty()) return@withContext Result.success(Unit)
        val cleanId = extractSpreadsheetId(spreadsheetId)
        try {
            val dataArray = JSONArray()
            for (u in updates) {
                val outerValues = JSONArray()
                for (row in u.values) {
                    val rowArr = JSONArray()
                    for (cell in row) {
                        rowArr.put(cell)
                    }
                    outerValues.put(rowArr)
                }
                val itemObj = JSONObject().apply {
                    put("range", u.range)
                    put("majorDimension", "ROWS")
                    put("values", outerValues)
                }
                dataArray.put(itemObj)
            }

            val payload = JSONObject().apply {
                put("valueInputOption", "USER_ENTERED")
                put("data", dataArray)
            }.toString()

            val req = Request.Builder()
                .url("$SHEETS_BASE_URL/$cleanId/values:batchUpdate")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(resp.code, bodyStr, resp.header("Retry-After")))
                }
                Result.success(Unit)
            }
        } catch (e: IOException) {
            Result.failure(SheetsApiException.NetworkOrServerException("Network error writing to Google Sheets: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSpreadsheetMeta(bodyStr: String): RemoteSpreadsheetMeta {
        val json = JSONObject(bodyStr)
        val id = json.optString("spreadsheetId", "")
        val title = json.optJSONObject("properties")?.optString("title", "JEE Study OS Tracker") ?: "JEE Study OS Tracker"
        val url = json.optString("spreadsheetUrl", "https://docs.google.com/spreadsheets/d/$id/edit")
        val sheetsArr = json.optJSONArray("sheets") ?: JSONArray()
        val titles = mutableListOf<String>()
        for (i in 0 until sheetsArr.length()) {
            val t = sheetsArr.optJSONObject(i)?.optJSONObject("properties")?.optString("title", "")
            if (!t.isNullOrBlank()) {
                titles.add(t)
            }
        }
        return RemoteSpreadsheetMeta(
            spreadsheetId = id,
            title = title,
            spreadsheetUrl = url,
            sheetTitles = titles
        )
    }

    private fun mapHttpError(code: Int, bodyStr: String, retryAfterHeader: String?): SheetsApiException {
        val message = try {
            JSONObject(bodyStr).optJSONObject("error")?.optString("message", bodyStr) ?: bodyStr
        } catch (_: Exception) {
            bodyStr.ifBlank { "HTTP $code" }
        }
        return when (code) {
            401, 403 -> SheetsApiException.AuthRevokedException(
                "Google authorization expired or access denied ($code): $message",
                code
            )
            404 -> SheetsApiException.NotFoundException(
                "Google Spreadsheet not found ($code). Verify the Spreadsheet ID and account permissions."
            )
            429 -> {
                val retrySec = retryAfterHeader?.trim()?.toLongOrNull() ?: 30L
                SheetsApiException.RateLimitedException(
                    "Google Sheets API quota rate-limited (429). Retrying with backoff in ${retrySec}s.",
                    retrySec
                )
            }
            else -> SheetsApiException.NetworkOrServerException(
                "Google Sheets API error ($code): $message",
                code
            )
        }
    }
}
