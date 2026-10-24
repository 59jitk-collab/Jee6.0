package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.entities.ChapterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user" or "ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isGroundingUsed: Boolean = false,
    val searchSources: List<String> = emptyList(),
    val isLocalFallback: Boolean = false,
    val verifiedContextSummary: String? = null,
    val isError: Boolean = false
)

enum class AiBackendHealthState {
    UNCONFIGURED,
    CHECKING,
    ONLINE,
    OFFLINE
}

class GeminiChatService {

    companion object {
        // Ordered list of supported v1beta Gemini models so 404 or transient errors on any single model alias gracefully fall back
        private val CANDIDATE_MODELS = listOf(
            "gemini-3.5-flash",
            "gemini-flash-latest",
            "gemini-3.1-flash-lite-preview"
        )
    }

    @Volatile
    var runtimeApiKeyOverride: String = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val healthCheckClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val systemPrompt = """
        You are 'JEE Study OS AI Mentor' — a rigorous academic study coach for Class 11 & 12 JEE Main and JEE Advanced (Physics, Chemistry, and Mathematics).
        Follow these strict rules:
        1. Explain concepts in clear, step-by-step derivations.
        2. Help solve problems with an explicit approach, physical/mathematical reasoning, and checked calculations.
        3. Offer targeted practice questions and concise revision summaries.
        4. Clearly separate verified app progress data provided in the context from generated educational suggestions.
        5. Never fabricate citations, never claim to have run tools that were not run, and state uncertainty honestly if a question is ambiguous.
    """.trimIndent()

    fun resolveActiveApiKey(): String {
        val overrideKey = runtimeApiKeyOverride.trim()
        if (overrideKey.isNotBlank() && overrideKey != "MY_GEMINI_API_KEY" && !overrideKey.startsWith("YOUR_")) {
            return overrideKey
        }
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        return if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY" && !buildKey.startsWith("YOUR_")) {
            buildKey
        } else {
            ""
        }
    }

    fun isRemoteBackendConfigured(): Boolean {
        return resolveActiveApiKey().isNotBlank()
    }

    /**
     * Performs a real lightweight GET probe against the Gemini v1beta models endpoint
     * to verify network reachability, endpoint validity, and API key authorization.
     */
    suspend fun checkBackendHealth(): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = resolveActiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.success("Built-in JEE Engine Ready")
        }
        var lastErrorMsg = "Endpoint unreachable"
        for (modelName in CANDIDATE_MODELS) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                healthCheckClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful && body.contains("\"name\"")) {
                        return@withContext Result.success(modelName)
                    } else {
                        lastErrorMsg = "HTTP ${response.code}"
                    }
                }
            } catch (e: Exception) {
                lastErrorMsg = e.localizedMessage ?: "Network timeout"
            }
        }
        Result.failure(Exception("Backend health check failed ($lastErrorMsg)"))
    }

    fun formatVerifiedChapterContext(chapter: ChapterEntity?): String? {
        if (chapter == null) return null
        val masteryLabel = chapter.resolvedMastery.displayLabel
        return if (!chapter.isAssessed) {
            "Verified App Data • ${chapter.subject} — ${chapter.chapterName}: Status = Not Assessed (Lecture/DPP/PYQ counts not yet entered), Mastery = $masteryLabel."
        } else {
            "Verified App Data • ${chapter.subject} — ${chapter.chapterName}: Lectures ${chapter.completedLectures}/${chapter.totalLectures ?: 0}, " +
                    "Notes ${if (chapter.notesDone) "Done" else "Pending"}, " +
                    "DPP ${chapter.dppDone ?: 0}/${chapter.dppTotal ?: 0}, " +
                    "PYQ ${chapter.pyqDone ?: 0}/${chapter.pyqTotal ?: 0}, " +
                    "Self-Rated Mastery = $masteryLabel."
        }
    }

    suspend fun sendMessage(
        history: List<ChatMessage>,
        newPrompt: String,
        selectedChapter: ChapterEntity? = null,
        userApprovedExternalSend: Boolean = true,
        enableSearchGrounding: Boolean = false
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        val verifiedSummary = formatVerifiedChapterContext(selectedChapter)
        val apiKey = resolveActiveApiKey()

        if (apiKey.isBlank() || !userApprovedExternalSend) {
            val localGuideText = generateLocalStructuredGuide(newPrompt, selectedChapter)
            return@withContext Result.success(
                ChatMessage(
                    sender = "ai",
                    text = localGuideText,
                    isLocalFallback = true,
                    verifiedContextSummary = verifiedSummary
                )
            )
        }

        val contentsArray = JSONArray()
        val recentHistory = history.filter { !it.isError }.takeLast(6)
        for (msg in recentHistory) {
            val turn = JSONObject()
            turn.put("role", if (msg.sender == "user") "user" else "model")
            val parts = JSONArray()
            parts.put(JSONObject().put("text", msg.text))
            turn.put("parts", parts)
            contentsArray.put(turn)
        }

        val contextualizedPrompt = if (verifiedSummary != null) {
            "[$verifiedSummary]\n\nStudent Question: $newPrompt"
        } else {
            newPrompt
        }

        val currentTurn = JSONObject()
        currentTurn.put("role", "user")
        val currentParts = JSONArray()
        currentParts.put(JSONObject().put("text", contextualizedPrompt))
        currentTurn.put("parts", currentParts)
        contentsArray.put(currentTurn)

        val rootJson = JSONObject()
        rootJson.put("contents", contentsArray)

        val sysInstruction = JSONObject()
        val sysParts = JSONArray()
        sysParts.put(JSONObject().put("text", systemPrompt))
        sysInstruction.put("parts", sysParts)
        rootJson.put("systemInstruction", sysInstruction)

        val genConfig = JSONObject()
        genConfig.put("temperature", 0.3)
        genConfig.put("topP", 0.9)
        rootJson.put("generationConfig", genConfig)

        val requestPayload = rootJson.toString()

        for (modelName in CANDIDATE_MODELS) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val requestBody = requestPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful || responseBody == null) {
                        val detail = parseApiErrorMessage(responseBody, response.code)
                        Log.w("GeminiChatService", "Model $modelName returned HTTP ${response.code}: $detail")
                        // Try next candidate model
                        return@use
                    }

                    val responseJson = JSONObject(responseBody)
                    val candidates = responseJson.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        return@use
                    }

                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    val partsArr = contentObj?.optJSONArray("parts")
                    val textBuilder = StringBuilder()
                    if (partsArr != null) {
                        for (i in 0 until partsArr.length()) {
                            val p = partsArr.getJSONObject(i)
                            if (p.has("text")) {
                                textBuilder.append(p.getString("text"))
                            }
                        }
                    }

                    val sources = mutableListOf<String>()
                    var isGrounded = false
                    val groundingMetadata = firstCandidate.optJSONObject("groundingMetadata")
                    if (groundingMetadata != null) {
                        isGrounded = true
                        val webSearchQueries = groundingMetadata.optJSONArray("webSearchQueries")
                        if (webSearchQueries != null) {
                            for (q in 0 until webSearchQueries.length()) {
                                sources.add(webSearchQueries.getString(q))
                            }
                        }
                    }

                    val answerText = textBuilder.toString()
                    if (answerText.isNotBlank()) {
                        return@withContext Result.success(
                            ChatMessage(
                                sender = "ai",
                                text = answerText,
                                isGroundingUsed = isGrounded,
                                searchSources = sources,
                                isLocalFallback = false,
                                verifiedContextSummary = verifiedSummary
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiChatService", "Failure calling Gemini API ($modelName)", e)
            }
        }

        // Seamless fallback to built-in JEE Mentor Engine if cloud endpoint is unreachable
        val fallbackText = generateLocalStructuredGuide(newPrompt, selectedChapter)
        Result.success(
            ChatMessage(
                sender = "ai",
                text = fallbackText,
                isLocalFallback = true,
                verifiedContextSummary = verifiedSummary,
                isError = false
            )
        )
    }

    private fun parseApiErrorMessage(responseBody: String?, code: Int): String {
        if (responseBody.isNullOrBlank()) return "HTTP $code from remote endpoint"
        return try {
            val errObj = JSONObject(responseBody).optJSONObject("error")
            errObj?.optString("message")?.takeIf { it.isNotBlank() } ?: "HTTP $code from remote endpoint"
        } catch (_: Exception) {
            "HTTP $code from remote endpoint"
        }
    }

    fun generateLocalStructuredGuide(prompt: String, selectedChapter: ChapterEntity?): String {
        val lower = prompt.lowercase()
        val chapterContextLine = if (selectedChapter != null) {
            val progressText = if (selectedChapter.isAssessed) {
                "Lectures ${selectedChapter.completedLectures}/${selectedChapter.totalLectures ?: 0} • DPP ${selectedChapter.dppDone ?: 0}/${selectedChapter.dppTotal ?: 0} • PYQ ${selectedChapter.pyqDone ?: 0}/${selectedChapter.pyqTotal ?: 0} • Mastery: ${selectedChapter.resolvedMastery.displayLabel}"
            } else {
                "Status: Not Assessed • Mastery: ${selectedChapter.resolvedMastery.displayLabel}"
            }
            "Context: ${selectedChapter.subject} • ${selectedChapter.chapterName} ($progressText)\n\n"
        } else {
            ""
        }

        val combinedText = "$lower ${selectedChapter?.chapterName?.lowercase().orEmpty()} ${selectedChapter?.subject?.lowercase().orEmpty()}"

        val body = when {
            lower.contains("7-day") || lower.contains("revision plan") || lower.contains("schedule") || lower.contains("backlog") -> {
                val targetName = selectedChapter?.chapterName ?: "Active Backlog Chapters"
                val remLecs = if (selectedChapter?.totalLectures != null) {
                    (selectedChapter.totalLectures - selectedChapter.completedLectures).coerceAtLeast(0)
                } else 6
                "Educational Suggestion — 7-Day High-Retention Sprint Plan for $targetName:\n" +
                        "• Days 1–2 (Core Theory & Derivations): Finish $remLecs remaining lectures at 1.25x speed while writing a 2-page formula sheet.\n" +
                        "• Days 3–4 (Guided Problem Solving): Solve 25 Class Illustrations + 3 DPPs under a 45-minute Focus Timer.\n" +
                        "• Days 5–6 (JEE Main PYQ Drill): Solve 35 PYQs (2019–2024) in 2 rounds (Round 1: direct formula, Round 2: multi-concept).\n" +
                        "• Day 7 (Mistake Audit & Spaced Recall): Log any wrong questions in your Mistake Notebook and schedule Stage-1 revision (+1d, +3d, +7d, +21d)."
            }
            lower.contains("organic") || lower.contains("goc") || lower.contains("hydrocarbon") || lower.contains("aldehyde") || lower.contains("ketone") || lower.contains("haloalkane") || lower.contains("amine") -> {
                "Educational Suggestion — Organic Chemistry Mechanism & Stability Framework:\n" +
                        "1. Electronic Effects (GOC): Master Inductive (-I/+I), Resonance/Mesomeric (-M/+M), and Hyperconjugation stability order of carbocations, free radicals, and carbanions.\n" +
                        "2. Reaction Pathway Matrix:\n" +
                        "   • Strong unhindered nucleophile + polar aprotic solvent (DMSO/Acetone) → SN2 (Inversion).\n" +
                        "   • Bulky base (t-BuOK) + heat → E2 (Hofmann product).\n" +
                        "   • 3° substrate + polar protic solvent (H2O/ROH) → SN1 + E1 carbocation rearrangement.\n" +
                        "3. Named Reactions Must-Know: Aldol Condensation, Cannizzaro, Reimer-Tiemann, Hofmann Bromamide, and Ozonolysis cleavage."
            }
            lower.contains("moment of inertia") || lower.contains("rotation") || lower.contains("torque") || lower.contains("angular") -> {
                "Educational Suggestion — Rotational Mechanics Step-by-Step:\n" +
                        "1. Parallel Axis Theorem: I_axis = I_cm + M · d² (d MUST be the perpendicular distance between the Center of Mass axis and the target parallel axis).\n" +
                        "2. Pure Rolling Condition (No Slipping on Stationary Surface): v_cm = ω · R and a_cm = α · R. Static friction does zero net work!\n" +
                        "3. Checked Standard Values:\n" +
                        "   • Uniform Thin Rod (about center, ⊥ to length): M·L² / 12\n" +
                        "   • Solid Disc/Cylinder (about symmetry axis): M·R² / 2\n" +
                        "   • Solid Sphere (about diameter): (2/5)·M·R² | Hollow Sphere: (2/3)·M·R²"
            }
            lower.contains("electrostatics") || lower.contains("gauss") || lower.contains("capacitor") || lower.contains("current") || lower.contains("kirchhoff") -> {
                "Educational Suggestion — Electromagnetism Core Laws & Shortcuts:\n" +
                        "1. Gauss's Law: ∮ E · dA = Q_enclosed / ε₀. E inside a conducting shell is 0; E inside a uniformly charged solid non-conducting sphere is (k·Q·r)/R³.\n" +
                        "2. Potential & Field Link: E = -∇V. Always conserve total energy (ΔK + q·ΔV = 0) for point charge motion.\n" +
                        "3. RC & Kirchhoff Circuits: At t = 0⁺, an uncharged capacitor acts as a short circuit (zero resistance wire); at t → ∞ (steady state), it acts as an open circuit."
            }
            lower.contains("optics") || lower.contains("lens") || lower.contains("mirror") || lower.contains("ydse") || lower.contains("interference") -> {
                "Educational Suggestion — Ray & Wave Optics Formula Sheet:\n" +
                        "1. Mirror & Lens Equations: Mirror: 1/v + 1/u = 1/f (m = -v/u) | Lens: 1/v - 1/u = 1/f (m = +v/u). Apply sign convention strictly along incident ray direction.\n" +
                        "2. Lens Maker's Formula: 1/f = (n_lens / n_medium - 1) · (1/R₁ - 1/R₂).\n" +
                        "3. YDSE Interference: Fringe width β = λ·D / d. Introducing a thin slab of thickness t and refractive index μ shifts the central maxima by Δy = (μ - 1)·t·D / d toward the slab."
            }
            lower.contains("modern physics") || lower.contains("photoelectric") || lower.contains("bohr") || lower.contains("nuclear") || lower.contains("semiconductor") -> {
                "Educational Suggestion — Modern Physics High-Yield Equations:\n" +
                        "1. Photoelectric Effect: K_max = e·V₀ = (h·c / λ) - φ, where h·c ≈ 12400 eV·Å.\n" +
                        "2. Bohr Hydrogen-Like Atom: Radius r_n = 0.529 · (n² / Z) Å; Energy E_n = -13.6 · (Z² / n²) eV; Speed v_n = 2.18×10⁶ · (Z / n) m/s.\n" +
                        "3. De-Broglie Wavelength: λ = h / √(2·m·K) = h / √(2·m·q·V)."
            }
            lower.contains("thermodynamics") || lower.contains("equilibrium") || lower.contains("electrochemistry") || lower.contains("kinetics") || lower.contains("mole") -> {
                "Educational Suggestion — Physical Chemistry Sign Conventions & Master Equations:\n" +
                        "1. First Law & Free Energy: ΔU = q + w (IUPAC: expansion work w = -P_ext·ΔV). Spontaneity: ΔG = ΔH - T·ΔS = -n·F·E_cell = -2.303·R·T·log₁₀(K_eq).\n" +
                        "2. Nernst Equation (at 298 K): E_cell = E°_cell - (0.0591 / n) · log₁₀(Q).\n" +
                        "3. First-Order Kinetics: k = (2.303 / t) · log₁₀([A]₀ / [A]_t), Half-life t_1/2 = 0.693 / k (independent of initial concentration)."
            }
            lower.contains("calculus") || lower.contains("integration") || lower.contains("leibnitz") || lower.contains("limit") || lower.contains("derivative") || lower.contains("differential") -> {
                "Educational Suggestion — Calculus & Definite Integration Toolkit:\n" +
                        "1. Newton-Leibnitz Rule: d/dx [ ∫_{u(x)}^{v(x)} f(t) dt ] = f(v(x)) · v'(x) - f(u(x)) · u'(x).\n" +
                        "2. King's Property: ∫_{a}^{b} f(x) dx = ∫_{a}^{b} f(a + b - x) dx (add both integrals I + I = 2I to cancel symmetric denominators).\n" +
                        "3. Standard expansions at x → 0: sin(x) ≈ x - x³/6, cos(x) ≈ 1 - x²/2, ln(1+x) ≈ x - x²/2, e^x ≈ 1 + x + x²/2."
            }
            lower.contains("vector") || lower.contains("3d") || lower.contains("matrix") || lower.contains("determinant") || lower.contains("conic") || lower.contains("circle") || lower.contains("quadratic") || lower.contains("complex") || lower.contains("probability") -> {
                "Educational Suggestion — JEE Mathematics High-Scoring Strategy:\n" +
                        "1. Vectors & 3D Geometry: Shortest distance between skew lines r = a₁ + λb₁ and r = a₂ + μb₂ is |(a₂ - a₁) · (b₁ × b₂)| / |b₁ × b₂|.\n" +
                        "2. Matrices & Determinants: |adj(A)| = |A|^(n-1), adj(AB) = adj(B)·adj(A), and A·adj(A) = |A|·I_n.\n" +
                        "3. Coordinate & Conics: Tangent in slope form: Parabola y = mx + a/m; Ellipse y = mx ± √(a²m² + b²); Hyperbola y = mx ± √(a²m² - b²)."
            }
            lower.contains("negative marks") || lower.contains("sunday test") || lower.contains("mock") || lower.contains("trap") -> {
                "Educational Suggestion — Sunday Mock Test & Negative-Mark Elimination Protocol:\n" +
                        "1. Round 1 (First 55 mins): Attempt only direct single-concept questions across Chemistry (20m), Physics (20m), and Maths (15m).\n" +
                        "2. Round 2 (Next 85 mins): Solve multi-step numerical problems where you know the exact governing conservation law or theorem.\n" +
                        "3. Zero-Guessing Rule: Never guess on Section-A MCQs (-1 penalty). Reserve intelligent elimination strictly for Section-B or verified 2-option splits.\n" +
                        "4. Post-Test Audit: Use the 1-tap 'Auto-Fill / Quick Scorecard' in Sunday Tests to log weak topics directly into Spaced Revision."
            }
            combinedText.contains("physics") -> {
                val chTitle = selectedChapter?.chapterName ?: "Physics"
                "Educational Suggestion — $chTitle Concept & Problem-Solving Blueprint:\n" +
                        "1. Free-Body & Reference Frame Audit: Draw all forces/fields, choose orthogonal axes aligned with acceleration, and verify inertial vs non-inertial frame (add -m·a_frame pseudo force if accelerating).\n" +
                        "2. Conservation Check: Before writing differential equations, check if Mechanical Energy (W_non-conservative = 0), Linear Momentum (F_ext = 0), or Angular Momentum (τ_ext = 0) is conserved.\n" +
                        "3. Dimensional & Limiting-Case Check: Verify units and test extreme limits (e.g. θ → 0, m₁ ≫ m₂, t → ∞) to catch algebraic sign mistakes."
            }
            combinedText.contains("chemistry") -> {
                val chTitle = selectedChapter?.chapterName ?: "Chemistry"
                "Educational Suggestion — $chTitle High-Yield Mastery Guide:\n" +
                        "1. NCERT & Exception Matrix: Highlight all periodic/structural exceptions (bond angles, ionization enthalpies, d-block colors, and coordination hybridization).\n" +
                        "2. Quantitative Stoichiometry & Equilibrium: Write the balanced reaction, track limiting reagent moles or degree of dissociation α, and verify units of R (8.314 J/mol·K vs 0.0821 L·atm/mol·K).\n" +
                        "3. Recommended Drill: Solve 25 JEE Main PYQs (2020–2024) for $chTitle and tag any missed exceptions in your Mistake Notebook."
            }
            combinedText.contains("math") -> {
                val chTitle = selectedChapter?.chapterName ?: "Mathematics"
                "Educational Suggestion — $chTitle Problem-Solving & Speed Framework:\n" +
                        "1. Domain & Boundary Constraints First: Always write domain restrictions (denominator ≠ 0, log argument > 0, discriminant D ≥ 0) before squaring or simplifying.\n" +
                        "2. Symmetry & Substitution: Look for symmetric expressions, graphical intersections, or standard trigonometric/algebraic substitutions to cut calculation steps in half.\n" +
                        "3. Verification: Test small boundary values (x = 0, 1, -1) on objective options to confirm your derived expression."
            }
            else -> {
                "Educational Suggestion — Structured JEE Problem-Solving Approach:\n" +
                        "1. Identify Given & Unknowns: Write down all constraints, boundary conditions, and SI units for ${selectedChapter?.chapterName ?: "this topic"}.\n" +
                        "2. Core Principle Selection: Link the question to fundamental conservation laws, theorems, or reaction mechanisms.\n" +
                        "3. Self-Verification: Check limiting cases and dimensional consistency before locking your final answer."
            }
        }

        return chapterContextLine + body
    }
}
