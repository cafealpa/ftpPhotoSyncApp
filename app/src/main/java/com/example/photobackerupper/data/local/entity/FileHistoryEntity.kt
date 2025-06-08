package com.example.photobackerupper.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_history")
data class FileHistoryEntity(
    @PrimaryKey val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val uploadTimestamp: Long,
    val uploadDurationMs: Long,
    val status: BackupStatus
)

enum class BackupStatus {
    SUCCESS, FAILURE
}