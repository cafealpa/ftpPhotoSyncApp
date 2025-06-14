package com.example.photobackerupper.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.photobackerupper.data.local.dao.BackupHistoryDao
import com.example.photobackerupper.data.local.dao.BackupSessionDao
import com.example.photobackerupper.data.local.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create the backup_session table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `backup_session` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `backupTimestamp` INTEGER NOT NULL,
                    `backupResult` TEXT NOT NULL,
                    `successCount` INTEGER NOT NULL,
                    `failureCount` INTEGER NOT NULL,
                    `totalDurationMs` INTEGER NOT NULL
                )
                """
            )

            // Add sessionId column to file_history table
            database.execSQL("ALTER TABLE file_history ADD COLUMN sessionId INTEGER NOT NULL DEFAULT 0")

            // Create index on sessionId
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_file_history_sessionId` ON `file_history` (`sessionId`)")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "photo_backer_upper_db"
        )
        .addMigrations(MIGRATION_1_2)
        .build()
    }

    @Provides
    @Singleton
    fun provideBackupHistoryDao(appDatabase: AppDatabase): BackupHistoryDao {
        return appDatabase.backupHistoryDao()
    }

    @Provides
    @Singleton
    fun provideBackupSessionDao(appDatabase: AppDatabase): BackupSessionDao {
        return appDatabase.backupSessionDao()
    }
}
