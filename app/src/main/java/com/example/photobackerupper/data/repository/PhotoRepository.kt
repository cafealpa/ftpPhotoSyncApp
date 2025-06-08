package com.example.photobackerupper.data.repository

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.photobackerupper.data.local.dao.BackupHistoryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupHistoryDao: BackupHistoryDao
) {
    suspend fun createBackupTargetList(): List<File> = withContext(Dispatchers.IO) {
        val allLocalMediaPaths = getLocalImageAndVideoPaths()
        val successfullyBackedUpPaths = backupHistoryDao.getAllSuccessfulBackupPaths()

        val newFilePaths = allLocalMediaPaths.filterNot { successfullyBackedUpPaths.contains(it) }

        return@withContext newFilePaths.map { File(it) }.filter { it.exists() && it.length() > 0 }
    }

    private fun getLocalImageAndVideoPaths(): List<String> {
        val filePaths = mutableListOf<String>()
        val contentResolver = context.contentResolver

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn)
                filePaths.add(path)
            }
        }
        return filePaths
    }
}