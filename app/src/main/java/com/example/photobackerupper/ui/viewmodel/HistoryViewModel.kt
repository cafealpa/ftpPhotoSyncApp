package com.example.photobackerupper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photobackerupper.data.local.dao.BackupSessionDao
import com.example.photobackerupper.data.local.entity.BackupResult
import com.example.photobackerupper.data.local.entity.BackupSessionEntity
import com.example.photobackerupper.data.local.entity.FileHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HistoryUiState(
    val sessions: List<BackupSessionUiModel> = emptyList(),
    val selectedSessionFiles: List<FileHistoryUiModel> = emptyList(),
    val selectedSessionId: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class BackupSessionUiModel(
    val id: Long,
    val date: String,
    val time: String,
    val result: String,
    val successCount: Int,
    val failureCount: Int,
    val totalDuration: String,
    val resultColor: androidx.compose.ui.graphics.Color
)

data class FileHistoryUiModel(
    val fileName: String,
    val fileSize: String,
    val uploadDuration: String,
    val status: String,
    val statusColor: androidx.compose.ui.graphics.Color
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val backupSessionDao: BackupSessionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    // 날짜 및 시간 포맷터
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                backupSessionDao.getAllSessions()
                    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
                    .collect { sessions ->
                        _uiState.value = _uiState.value.copy(
                            sessions = sessions.map { it.toUiModel() },
                            isLoading = false
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "세션 목록을 불러오는 중 오류가 발생했습니다."
                )
            }
        }
    }

    fun selectSession(sessionId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedSessionId = sessionId,
                isLoading = true
            )
            
            try {
                backupSessionDao.getFilesForSession(sessionId)
                    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
                    .collect { files ->
                        _uiState.value = _uiState.value.copy(
                            selectedSessionFiles = files.map { it.toUiModel() },
                            isLoading = false
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "파일 목록을 불러오는 중 오류가 발생했습니다."
                )
            }
        }
    }

    fun clearSelectedSession() {
        _uiState.value = _uiState.value.copy(
            selectedSessionId = null,
            selectedSessionFiles = emptyList()
        )
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun BackupSessionEntity.toUiModel(): BackupSessionUiModel {
        val date = Date(backupTimestamp)
        val resultText = when (backupResult) {
            BackupResult.COMPLETED -> "완료"
            BackupResult.USER_CANCELLED -> "사용자 중지"
            BackupResult.ERROR_STOPPED -> "오류 발생"
        }
        val resultColor = when (backupResult) {
            BackupResult.COMPLETED -> androidx.compose.ui.graphics.Color.Green
            BackupResult.USER_CANCELLED -> androidx.compose.ui.graphics.Color.Yellow
            BackupResult.ERROR_STOPPED -> androidx.compose.ui.graphics.Color.Red
        }
        val durationSeconds = totalDurationMs / 1000f
        val durationText = String.format("%.1f초", durationSeconds)

        return BackupSessionUiModel(
            id = id,
            date = dateFormatter.format(date),
            time = timeFormatter.format(date),
            result = resultText,
            successCount = successCount,
            failureCount = failureCount,
            totalDuration = durationText,
            resultColor = resultColor
        )
    }

    private fun FileHistoryEntity.toUiModel(): FileHistoryUiModel {
        val fileSizeText = if (fileSize < 1024 * 1024) {
            String.format("%.2f KB", fileSize / 1024f)
        } else {
            String.format("%.2f MB", fileSize / 1024f / 1024f)
        }
        
        val durationText = String.format("%.1f초", uploadDurationMs / 1000f)
        
        val statusText = when (status) {
            com.example.photobackerupper.data.local.entity.BackupStatus.SUCCESS -> "성공"
            com.example.photobackerupper.data.local.entity.BackupStatus.FAILURE -> "실패"
        }
        
        val statusColor = when (status) {
            com.example.photobackerupper.data.local.entity.BackupStatus.SUCCESS -> androidx.compose.ui.graphics.Color.Green
            com.example.photobackerupper.data.local.entity.BackupStatus.FAILURE -> androidx.compose.ui.graphics.Color.Red
        }

        return FileHistoryUiModel(
            fileName = fileName,
            fileSize = fileSizeText,
            uploadDuration = durationText,
            status = statusText,
            statusColor = statusColor
        )
    }
}