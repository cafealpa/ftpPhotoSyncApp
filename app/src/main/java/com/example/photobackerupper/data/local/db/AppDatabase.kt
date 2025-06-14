package com.example.photobackerupper.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.photobackerupper.data.local.dao.BackupHistoryDao
import com.example.photobackerupper.data.local.dao.BackupSessionDao
import com.example.photobackerupper.data.local.entity.BackupSessionEntity
import com.example.photobackerupper.data.local.entity.FileHistoryEntity

@Database(entities = [FileHistoryEntity::class, BackupSessionEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun backupHistoryDao(): BackupHistoryDao
    abstract fun backupSessionDao(): BackupSessionDao
}
