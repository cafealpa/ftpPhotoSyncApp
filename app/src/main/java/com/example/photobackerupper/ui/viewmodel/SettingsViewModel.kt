package com.example.photobackerupper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photobackerupper.data.model.FtpSettings
import com.example.photobackerupper.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<FtpSettings> = settingsRepository.ftpSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FtpSettings()
        )

    fun saveSettings(ip: String, portStr: String, folder: String, userName: String, password: String) {
        viewModelScope.launch {
            val port = portStr.toIntOrNull() ?: 21
            settingsRepository.saveSettings(
                FtpSettings(
                    serverIp = ip,
                    port = port,
                    uploadFolder = folder,
                    userName = userName,
                    password = password
                )
            )
        }
    }
}