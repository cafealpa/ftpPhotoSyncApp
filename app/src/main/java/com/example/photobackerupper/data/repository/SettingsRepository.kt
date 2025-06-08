package com.example.photobackerupper.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.photobackerupper.data.model.FtpSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object PreferencesKeys {
        val FTP_SERVER_IP = stringPreferencesKey("ftp_server_ip")
        val FTP_PORT = intPreferencesKey("ftp_port")
        val UPLOAD_FOLDER = stringPreferencesKey("upload_folder")
        val USER_NAME = stringPreferencesKey("user_name")
        val PASSWORD = stringPreferencesKey("password")
    }

    val ftpSettingsFlow: Flow<FtpSettings> = context.dataStore.data
        .map { preferences ->
            FtpSettings(
                serverIp = preferences[PreferencesKeys.FTP_SERVER_IP] ?: "",
                port = preferences[PreferencesKeys.FTP_PORT] ?: 21,
                uploadFolder = preferences[PreferencesKeys.UPLOAD_FOLDER] ?: "backup",
                userName = preferences[PreferencesKeys.USER_NAME] ?: "",
                password = preferences[PreferencesKeys.PASSWORD] ?: ""
            )
        }

    suspend fun saveSettings(settings: FtpSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FTP_SERVER_IP] = settings.serverIp
            preferences[PreferencesKeys.FTP_PORT] = settings.port
            preferences[PreferencesKeys.UPLOAD_FOLDER] = settings.uploadFolder
            preferences[PreferencesKeys.USER_NAME] = settings.userName
            preferences[PreferencesKeys.PASSWORD] = settings.password
        }
    }
}