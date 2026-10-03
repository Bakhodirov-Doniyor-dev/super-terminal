package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "anti_overlay_events")
data class AntiOverlayEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val appName: String,
    val windowTitle: String,
    val actionTaken: String,
    val threatLevel: String = "HIGH", // "HIGH", "CRITICAL", "PREVENTED"
    val details: String = ""
)
