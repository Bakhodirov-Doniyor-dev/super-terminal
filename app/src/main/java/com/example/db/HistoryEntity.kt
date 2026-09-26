package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val command: String,
    val output: String,
    val isSuccess: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
