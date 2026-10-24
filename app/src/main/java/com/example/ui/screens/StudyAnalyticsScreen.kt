package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.analytics.DayStudyPoint
import com.example.data.analytics.WeeklyReport
import com.example.ui.theme.ChemistryPurple
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CoralRedContainer
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.ElectricBlueSubtle
import com.example.ui.theme.ElectricBlueVariant
import com.example.ui.theme.MathOrange
import com.example.ui.theme.PhysicsCyan
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.SoftGreenContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WarmAmberContainer

@Composable
fun StudyAnalyticsScreen(
    report: WeeklyReport,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .testTag("study_analytics_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))

                // 1. Header with Back button and subtitle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SlateCardElevated)
                            .border(1.dp, SlateCardBorder, CircleShape)
                            .clickable { onBack() }
                            .testTag("analytics_back_button"),
                        contentAlignment = Alignment.Center
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
                            text = "Study Analytics",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "7-Day Performance & Empirical Diagnostics",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 2. DAILY REPORT Card + 4-Tile Weekly Overview Grid (Matches Reports / RankMentor Screenshots)
            item {
                val hours = report.totalStudyMinutes / 60
                val mins = report.totalStudyMinutes % 60
                val totalHoursDecimal = String.format(java.util.Locale.US, "%.1fh", report.totalStudyMinutes / 60.0)
                val avgHoursDecimal = String.format(java.util.Locale.US, "%.1fh", report.averageDailyStudyMinutes / 60.0)
                val totalTasksCompleted = report.totalLecturesCompleted + report.totalProblemsSolved

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weekly_summary_card"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // DAILY REPORT Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "DAILY REPORT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.4.sp,
                                        color = SoftGreen
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = report.weekEndDate,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SoftGreen.copy(alpha = 0.16f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${report.daysWithRecordedStudy}/7 Active Days",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SoftGreen
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(SlateCardElevated)
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text("FOCUS TIME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (report.totalStudyMinutes > 0) "${hours}h ${mins}m" else "0h 0m",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextPrimary
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(SlateCardElevated)
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text("TASKS DONE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "$totalTasksCompleted",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = SoftGreen
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(SlateCardElevated)
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text("TARGET MET", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${report.targetDaysMetCount}d",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = CyanAccent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Weekly Overview 4-Tile Strip (Total | Avg/Day | Tasks | Streak) with soft fading mixed gradients
                    Text(
                        text = "Weekly Overview",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val overviewTiles = listOf(
                            Triple(totalHoursDecimal, "Total", listOf(Color(0xFF064E3B), Color(0xFF191D20))),
                            Triple(avgHoursDecimal, "Avg/Day", listOf(Color(0xFF1E3A8A), Color(0xFF191D20))),
                            Triple("$totalTasksCompleted", "Tasks", listOf(Color(0xFF78350F), Color(0xFF191D20))),
                            Triple("${report.daysWithRecordedStudy}d", "Streak", listOf(Color(0xFF4C1D95), Color(0xFF191D20)))
                        )
                        val accentColors = listOf(
                            SoftGreen,
                            Color(0xFF60A5FA),
                            WarmAmber,
                            Color(0xFFC084FC)
                        )
                        overviewTiles.forEachIndexed { idx, (valText, labelText, gradientPair) ->
                            val accent = accentColors[idx]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                gradientPair[0].copy(alpha = 0.55f),
                                                gradientPair[1]
                                            )
                                        )
                                    )
                                    .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                                    .padding(vertical = 14.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = valText,
                                        style = com.example.ui.theme.TabularNumeralStyle,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = labelText,
                                        fontSize = 11.sp,
                                        color = accent
                                    )
                                }
                            }
                        }
                    }

                    // Activity Calendar Card (Matches reference screenshot: "Activity Calendar", "X active days", 5x7 grid + Less/More legend)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Activity Calendar",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${report.daysWithRecordedStudy} active days",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val weekHeaders = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    weekHeaders.forEach { d ->
                                        Text(
                                            text = d,
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                // 5 weeks x 7 days grid (last row reflects actual recorded 7-day study trend)
                                val trendMinutes = report.dailyTrend.map { it.studyMinutes }
                                for (weekRow in 0 until 5) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        for (col in 0 until 7) {
                                            val minsForCell = if (weekRow == 4 && col < trendMinutes.size) {
                                                trendMinutes[col]
                                            } else {
                                                0
                                            }
                                            val cellColor = when {
                                                minsForCell >= 180 -> SoftGreen
                                                minsForCell >= 90 -> SoftGreen.copy(alpha = 0.7f)
                                                minsForCell >= 30 -> SoftGreen.copy(alpha = 0.45f)
                                                minsForCell > 0 -> SoftGreen.copy(alpha = 0.25f)
                                                else -> SlateCardElevated
                                            }
                                            val isTodayCell = weekRow == 4 && col == (trendMinutes.size - 1).coerceAtLeast(0)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(cellColor)
                                                    .then(
                                                        if (isTodayCell) {
                                                            Modifier.border(1.5.dp, SoftGreen, RoundedCornerShape(8.dp))
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Less [ ][ ][ ][ ] More legend
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Less", fontSize = 11.sp, color = TextMuted)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    listOf(
                                        SlateCardElevated,
                                        SoftGreen.copy(alpha = 0.28f),
                                        SoftGreen.copy(alpha = 0.52f),
                                        SoftGreen.copy(alpha = 0.78f),
                                        SoftGreen
                                    ).forEach { legendColor ->
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 2.dp)
                                                .size(12.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(legendColor)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("More", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                }
            }

            // 3. 7-Day Daily Study Time Bar Chart
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("study_time_chart_card"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL EFFORT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", report.totalStudyMinutes / 60.0)} hrs",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "GROWTH RATE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SoftGreen.copy(alpha = 0.16f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Active 7d Trend",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SoftGreen
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (report.hasEnoughData) {
                            WeeklyBarChart(
                                days = report.dailyTrend,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            )
                        } else {
                            // Non-invented empty state
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SlateCardElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.BarChart,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Not enough data yet",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "Log study sessions to view your 7-day focus distribution",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Planned vs Completed Target Breakdown
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("planned_vs_completed_card"),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Target Adherence (Planned vs Completed)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Lectures Row
                        TargetProgressRow(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            iconTint = ElectricBlue,
                            title = "Lectures Completed",
                            completed = report.totalLecturesCompleted,
                            planned = report.totalLecturesPlanned,
                            percentage = report.lectureAdherencePercent,
                            unit = "lecs"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Problems Row
                        TargetProgressRow(
                            icon = Icons.Default.Speed,
                            iconTint = SoftGreen,
                            title = "Questions Solved (DPP/PYQ)",
                            completed = report.totalProblemsSolved,
                            planned = report.totalProblemsPlanned,
                            percentage = report.problemAdherencePercent,
                            unit = "probs"
                        )
                    }
                }
            }

            // 5. Mastery vs Completion Separation (Strictly Separated)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mastery_vs_completion_card"),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SoftGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Completion vs True Mastery",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = "Syllabus progress is kept distinct from diagnostic test performance",
                            fontSize = 10.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Syllabus Completion Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SlateCardElevated)
                                    .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("Syllabus Coverage", fontSize = 11.sp, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${report.syllabusCompletionPercent}%",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent
                                    )
                                    Text("Completed tasks", fontSize = 10.sp, color = TextMuted)
                                }
                            }

                            // Mock Test Mastery Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SlateCardElevated)
                                    .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("Sunday Test Mastery", fontSize = 11.sp, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val scoreText = report.averageTestScorePercent?.let { "$it%" } ?: "No data"
                                    Text(
                                        text = scoreText,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (report.averageTestScorePercent != null) SoftGreen else TextMuted
                                    )
                                    val testCountLabel = if (report.assessedTestCount > 0) "${report.assessedTestCount} test(s) assessed" else "Not assessed yet"
                                    Text(testCountLabel, fontSize = 10.sp, color = TextMuted)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Mistake Resolution Status
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SlateCardElevated)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mistake Notebook Resolution", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = "${report.resolvedMistakesCount} Resolved / ${report.activeMistakesCount} Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (report.activeMistakesCount > 0) WarmAmber else SoftGreen
                            )
                        }
                    }
                }
            }

            // 6. Empirical Weak Chapter Diagnostic (Zero guessing)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weak_chapters_diagnostic_card"),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = WarmAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Weak Chapters (Evidence-Based)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (report.weakChapters.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                report.weakChapters.forEach { diag ->
                                    val badgeColor = when (diag.subject) {
                                        "Physics" -> PhysicsCyan
                                        "Chemistry" -> ChemistryPurple
                                        else -> MathOrange
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SlateCardElevated)
                                            .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(badgeColor)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = diag.chapterName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = diag.evidenceSnippet,
                                                fontSize = 10.sp,
                                                color = CoralRed
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(CoralRedContainer)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "Needs Review",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CoralRed
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Empirical message when no data evidence exists
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SlateCardElevated)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Insufficient test or mistake data to flag weak chapters. Chapters are only marked when supported by recorded test errors.",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // 7. Objective, Non-Judgmental Study Recommendation Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("study_recommendation_card"),
                    colors = CardDefaults.cardColors(containerColor = SlateCardElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ElectricBlueSubtle),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = ElectricBlueLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Weekly Recommendation",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = report.recommendation,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    helper: String
) {
    Column {
        Text(text = label, fontSize = 10.sp, color = TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(text = helper, fontSize = 9.sp, color = TextSecondary)
    }
}

@Composable
private fun TargetProgressRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    completed: Int,
    planned: Int,
    percentage: Int,
    unit: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontSize = 11.sp, color = TextSecondary)
            }

            Text(
                text = "$completed / $planned $unit ($percentage%)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (percentage >= 80) SoftGreen else if (percentage >= 50) WarmAmber else TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { (percentage / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = iconTint,
            trackColor = SlateCardElevated
        )
    }
}

@Composable
private fun WeeklyBarChart(
    days: List<DayStudyPoint>,
    modifier: Modifier = Modifier
) {
    val maxMinutes = (days.maxOfOrNull { it.studyMinutes } ?: 240).coerceAtLeast(240).toFloat()

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600),
        label = "bar_anim"
    )

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .semantics {
                    contentDescription = "7-day study minutes bar chart showing daily focus hours against 4-hour benchmark"
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val barCount = days.size.coerceAtLeast(1)
            val slotWidth = canvasWidth / barCount
            val barWidth = (slotWidth * 0.45f).coerceAtMost(28.dp.toPx())

            // Dotted 4-hour goal line (240 mins)
            val goalY = canvasHeight * (1f - (240f / maxMinutes))
            drawLine(
                color = CyanAccent.copy(alpha = 0.35f),
                start = Offset(0f, goalY),
                end = Offset(canvasWidth, goalY),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )

            days.forEachIndexed { index, point ->
                val centerX = slotWidth * index + (slotWidth / 2f)
                val barHeight = (canvasHeight * (point.studyMinutes.toFloat() / maxMinutes)) * animatedProgress
                val topY = canvasHeight - barHeight

                val isGoalMet = point.studyMinutes >= point.targetMinutes
                val barGradient = Brush.verticalGradient(
                    colors = if (point.studyMinutes == 0) {
                        listOf(SlateCardBorder, SlateCardBorder)
                    } else if (isGoalMet) {
                        listOf(CyanAccent, ElectricBlue)
                    } else {
                        listOf(ElectricBlueLight, ElectricBlueVariant)
                    }
                )

                // Render background empty slot
                drawRoundRect(
                    color = SlateCardElevated,
                    topLeft = Offset(centerX - (barWidth / 2f), 0f),
                    size = Size(barWidth, canvasHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // Render active bar
                if (barHeight > 4f) {
                    drawRoundRect(
                        brush = barGradient,
                        topLeft = Offset(centerX - (barWidth / 2f), topY),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Day labels row (Mon, Tue, Wed...)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            days.forEach { point ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = point.dayLabel,
                        fontSize = 10.sp,
                        fontWeight = if (point.isSunday) FontWeight.Bold else FontWeight.Medium,
                        color = if (point.isSunday) WarmAmber else TextSecondary
                    )
                    Text(
                        text = if (point.studyMinutes > 0) "${point.studyMinutes}m" else "-",
                        fontSize = 8.sp,
                        color = if (point.studyMinutes >= point.targetMinutes) SoftGreen else TextMuted
                    )
                }
            }
        }
    }
}
