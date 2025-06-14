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

    @Query("SELECT * FROM file_history WHERE uploadTimestamp >= :startTimestamp ORDER BY uploadTimestamp DESC")
    suspend fun getHistoryAfterTimestamp(startTimestamp: Long): List<FileHistoryEntity> // 특정 시간 이후 완료된 항목
}