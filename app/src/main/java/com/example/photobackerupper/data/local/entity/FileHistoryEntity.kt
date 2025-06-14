package com.example.photobackerupper.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "file_history",
    foreignKeys = [
        ForeignKey(
            entity = BackupSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class FileHistoryEntity(
    @PrimaryKey val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val uploadTimestamp: Long,
    val uploadDurationMs: Long,
    val status: BackupStatus,
    val sessionId: Long
)

enum class BackupStatus {
    SUCCESS, FAILURE
}
