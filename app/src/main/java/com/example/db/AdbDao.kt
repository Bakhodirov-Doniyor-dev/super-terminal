package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AdbDao {
    // Scripts
    @Query("SELECT * FROM saved_scripts ORDER BY timestamp DESC")
    fun getAllScripts(): Flow<List<ScriptEntity>>

    @Query("SELECT COUNT(*) FROM saved_scripts")
    suspend fun getScriptCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: ScriptEntity)

    @Delete
    suspend fun deleteScript(script: ScriptEntity)

    @Query("DELETE FROM saved_scripts")
    suspend fun clearAllScripts()

    // History
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT 100")
    fun getCommandHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM command_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)

    @Query("DELETE FROM command_history")
    suspend fun clearHistory()

    // Anti-Overlay Events
    @Query("SELECT * FROM anti_overlay_events ORDER BY timestamp DESC LIMIT 150")
    fun getAllOverlayEvents(): Flow<List<AntiOverlayEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverlayEvent(event: AntiOverlayEventEntity)

    @Query("DELETE FROM anti_overlay_events WHERE id = :id")
    suspend fun deleteOverlayEventById(id: Int)

    @Query("DELETE FROM anti_overlay_events")
    suspend fun clearAllOverlayEvents()
}
