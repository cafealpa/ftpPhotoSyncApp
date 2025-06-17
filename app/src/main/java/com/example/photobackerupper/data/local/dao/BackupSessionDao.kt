package com.example.photobackerupper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.photobackerupper.data.local.entity.BackupSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupSessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: BackupSessionEntity): Long

    @Query("SELECT * FROM backup_session ORDER BY backupTimestamp DESC")
    fun getAllSessions(): Flow<List<BackupSessionEntity>>

    @Query("SELECT * FROM backup_session WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): BackupSessionEntity?
}