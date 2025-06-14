package com.example.photobackerupper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.photobackerupper.data.local.entity.BackupSessionEntity
import com.example.photobackerupper.data.local.entity.FileHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: BackupSessionEntity): Long

    @Query("SELECT * FROM backup_session ORDER BY backupTimestamp DESC")
    fun getAllSessions(): Flow<List<BackupSessionEntity>>

    @Query("SELECT * FROM file_history WHERE sessionId = :sessionId ORDER BY uploadTimestamp DESC")
    fun getFilesForSession(sessionId: Long): Flow<List<FileHistoryEntity>>

    @Query("SELECT * FROM backup_session WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): BackupSessionEntity?
}