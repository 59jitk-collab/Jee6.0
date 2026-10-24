package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores verifiable, idempotent XP award events.
 * The primary key [eventKey] uniquely identifies the specific achievement
 * (e.g., "LEC_12_3" for Chapter 12 Lecture 3, or "FOCUS_1729580000")
 * so repeated taps, screen opens, or re-imports can never farm duplicate XP.
 */
@Entity(tableName = "xp_events")
data class XpEventEntity(
    @PrimaryKey
    val eventKey: String,
    val category: String, // "LECTURE", "NOTES", "DPP", "PYQ", "DIBY", "WORKSHEET", "FOCUS", "REVISION"
    val description: String,
    val xpAmount: Int,
    val dateString: String, // YYYY-MM-DD local date
    val timestamp: Long = System.currentTimeMillis()
)
