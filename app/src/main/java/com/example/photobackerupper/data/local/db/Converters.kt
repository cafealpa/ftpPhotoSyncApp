package com.example.photobackerupper.data.local.db

import androidx.room.TypeConverter
import com.example.photobackerupper.data.local.entity.BackupResult
import com.example.photobackerupper.data.local.entity.BackupStatus

class Converters {
    @TypeConverter
    fun fromBackupStatus(status: BackupStatus): String {
        return status.name // Enum을 String으로 변환 (e.g., BackupStatus.SUCCESS -> "SUCCESS")
    }

    @TypeConverter
    fun toBackupStatus(status: String): BackupStatus {
        return BackupStatus.valueOf(status) // String을 다시 Enum으로 변환
    }

    @TypeConverter
    fun fromBackupResult(result: BackupResult): String {
        return result.name // Enum을 String으로 변환
    }

    @TypeConverter
    fun toBackupResult(result: String): BackupResult {
        return BackupResult.valueOf(result) // String을 다시 Enum으로 변환
    }
}
