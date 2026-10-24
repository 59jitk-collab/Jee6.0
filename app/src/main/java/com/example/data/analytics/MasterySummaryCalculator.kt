package com.example.data.analytics

import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.MasteryLevel

data class SubjectMasterySummary(
    val subject: String,
    val totalChapters: Int,
    val assessedCount: Int,
    val unassessedCount: Int,
    val checklistCompletedCount: Int,
    val masteryCounts: Map<MasteryLevel, Int>
) {
    val confidentCount: Int get() = masteryCounts[MasteryLevel.CONFIDENT] ?: 0
    val practicingCount: Int get() = masteryCounts[MasteryLevel.PRACTICING] ?: 0
    val learningCount: Int get() = masteryCounts[MasteryLevel.LEARNING] ?: 0
    val needsRevisionCount: Int get() = masteryCounts[MasteryLevel.NEEDS_REVISION] ?: 0
    val notAssessedMasteryCount: Int get() = masteryCounts[MasteryLevel.NOT_ASSESSED] ?: 0
}

object MasterySummaryCalculator {

    /**
     * Computes subject-level or overall mastery and task-completion summaries strictly from stored chapters.
     * Task checklist completion is kept strictly separate from conceptual mastery.
     */
    fun computeSummary(subjectLabel: String, chapters: List<ChapterEntity>): SubjectMasterySummary {
        val counts = mutableMapOf<MasteryLevel, Int>()
        MasteryLevel.entries.forEach { counts[it] = 0 }

        var assessed = 0
        var unassessed = 0
        var checklistDone = 0

        for (ch in chapters) {
            if (ch.isAssessed) {
                assessed++
                if (ch.completionPercentage >= 100) {
                    checklistDone++
                }
            } else {
                unassessed++
            }
            val level = ch.resolvedMastery
            counts[level] = (counts[level] ?: 0) + 1
        }

        return SubjectMasterySummary(
            subject = subjectLabel,
            totalChapters = chapters.size,
            assessedCount = assessed,
            unassessedCount = unassessed,
            checklistCompletedCount = checklistDone,
            masteryCounts = counts
        )
    }
}
