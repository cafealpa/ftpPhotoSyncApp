package com.example.photobackerupper.di

import android.content.Context
import androidx.room.Room
import com.example.photobackerupper.data.local.dao.BackupHistoryDao
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

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "photo_backer_upper_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideBackupHistoryDao(appDatabase: AppDatabase): BackupHistoryDao {
        return appDatabase.backupHistoryDao()
    }
}