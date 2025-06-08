package com.example.photobackerupper.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.photobackerupper.data.local.dao.BackupHistoryDao
import com.example.photobackerupper.data.local.entity.FileHistoryEntity

@Database(entities = [FileHistoryEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class) // 이 어노테이션을 추가!
abstract class AppDatabase : RoomDatabase() {
    abstract fun backupHistoryDao(): BackupHistoryDao
}