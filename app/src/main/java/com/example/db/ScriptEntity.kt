package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_scripts")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val command: String,
    val description: String,
    val category: String, // e.g. "Sistem", "Ekran", "Ilova", "Shaxsiy"
    val timestamp: Long = System.currentTimeMillis()
)
