package com.example.photobackerupper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.photobackerupper.data.local.entity.FileHistoryEntity

@Dao
interface BackupHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFileHistory(fileHistory: FileHistoryEntity)

    @Query("SELECT filePath FROM file_history WHERE status = 'SUCCESS'")
    suspend fun getAllSuccessfulBackupPaths(): List<String>

    @Query("SELECT * FROM file_history ORDER BY uploadTimestamp DESC")
    suspend fun getAllHistory(): List<FileHistoryEntity> // History 화면용
}