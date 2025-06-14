package com.example.photobackerupper.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_session")
data class BackupSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val backupTimestamp: Long,
    val backupResult: BackupResult,
    val successCount: Int,
    val failureCount: Int,
    val totalDurationMs: Long
)

enum class BackupResult {
    COMPLETED, USER_CANCELLED, ERROR_STOPPED
}