package com.example.photobackerupper.ui.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photobackerupper.data.local.dao.BackupHistoryDao
import com.example.photobackerupper.data.local.entity.BackupStatus
import com.example.photobackerupper.data.local.entity.FileHistoryEntity
import com.example.photobackerupper.data.remote.FtpClientWrapper
import com.example.photobackerupper.data.repository.PhotoRepository
import com.example.photobackerupper.data.repository.SettingsRepository
import com.example.photobackerupper.service.BackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.logging.Logger
import javax.inject.Inject

data class MainUiState(
    val isBackingUp: Boolean = false,
    val completedFiles: List<BackupFileUiModel> = emptyList(),
    val totalFilesToBackup: Int = 0,
    val completedFileCount: Int = 0,
    val processingText: String = "",
    val backupResult: BackupResult? = null,
    val errorMessage: String? = null
)

data class BackupFileUiModel(
    val thumbnailUri: Uri,
    val name: String,
    val sizeMb: Float,
    val durationSeconds: Float
)

data class BackupResult(
    val successCount: Int,
    val failureCount: Int,
    val totalBackedUpFiles: Int,
    val totalBackedUpSizeMb: Float,
    val totalTimeSeconds: Float
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    private val ftpClient: FtpClientWrapper,
    private val backupHistoryDao: BackupHistoryDao
) : ViewModel() {

    private val logger = Logger.getLogger(MainViewModel::class.java.name)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private var backupJob: Job? = null
    private var backupReceiver: BroadcastReceiver? = null

    init {
        // Initialize UI state
        _uiState.update {
            it.copy(
                isBackingUp = false,
                completedFiles = emptyList(),
                totalFilesToBackup = 0,
                completedFileCount = 0,
                processingText = "",
                errorMessage = null,
                backupResult = null
            )
        }

        // Register broadcast receiver
        registerBackupReceiver()
    }

    override fun onCleared() {
        super.onCleared()
        unregisterBackupReceiver()
    }

    private fun registerBackupReceiver() {

        logger.info("BackupReceiver registered")

        backupReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // 로그추가
                logger.info("Received broadcast ACTION=${intent.action}, COMPLETED=${intent.getIntExtra(BackupService.EXTRA_COMPLETED_COUNT, -1)}, total=${intent.getIntExtra(BackupService.EXTRA_TOTAL_COUNT, -1)}")

                when (intent.action) {
                    BackupService.ACTION_BACKUP_STARTED -> {
                        _uiState.update {
                            it.copy(
                                isBackingUp = true,
                                completedFiles = emptyList(),
                                processingText = "시작준비 중",
                                totalFilesToBackup = 0,
                                completedFileCount = 0,
                                errorMessage = null,
                                backupResult = null
                            )
                        }
                    }
                    BackupService.ACTION_BACKUP_PROGRESS -> {
                        val completedCount = intent.getIntExtra(BackupService.EXTRA_COMPLETED_COUNT, 0)
                        val totalCount = intent.getIntExtra(BackupService.EXTRA_TOTAL_COUNT, 0)

                        logger.info("res completedCnt: $completedCount")
                        logger.info("res totalCnt: $totalCount")

                        // Update UI immediately with the latest counts
                        _uiState.update {
                            it.copy(
                                processingText = "진행중",
                                completedFileCount = completedCount,
                                totalFilesToBackup = totalCount
                            )
                        }

                        // Fetch only the files completed in the current backup process
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                // 백업 시작 시점의 타임스탬프 가져오기
                                val sessionStartTime = intent.getLongExtra(BackupService.EXTRA_SESSION_START_TIME, 0L)

                                // 현재 백업 세션에서 완료된 항목만 가져오기
                                val currentSessionHistory = backupHistoryDao.getHistoryAfterTimestamp(sessionStartTime)

                                if (currentSessionHistory.isNotEmpty() && completedCount > 0) {
                                    val fileUiModels = currentSessionHistory.map { historyItem ->
                                        BackupFileUiModel(
                                            thumbnailUri = File(historyItem.filePath).toUri(),
                                            name = historyItem.fileName,
                                            sizeMb = historyItem.fileSize / 1024f / 1024f,
                                            durationSeconds = historyItem.uploadDurationMs / 1000f
                                        )
                                    }

                                    // Update UI with current session files
                                    _uiState.update { state ->
                                        state.copy(
                                            completedFiles = fileUiModels,
                                            completedFileCount = completedCount,
                                            totalFilesToBackup = totalCount
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                // Log any errors but don't crash
                                e.printStackTrace()
                            }
                        }
                    }
                    BackupService.ACTION_BACKUP_COMPLETED -> {
                        val successCount = intent.getIntExtra(BackupService.EXTRA_SUCCESS_COUNT, 0)
                        val failureCount = intent.getIntExtra(BackupService.EXTRA_FAILURE_COUNT, 0)
                        val totalSizeMb = intent.getFloatExtra(BackupService.EXTRA_TOTAL_SIZE_MB, 0f)
                        val durationSeconds = intent.getFloatExtra(BackupService.EXTRA_DURATION_SECONDS, 0f)

                        val result = BackupResult(
                            successCount = successCount,
                            failureCount = failureCount,
                            totalBackedUpFiles = successCount + failureCount,
                            totalBackedUpSizeMb = totalSizeMb,
                            totalTimeSeconds = durationSeconds
                        )

                        _uiState.update {
                            it.copy(
                                processingText = "완료",
                                isBackingUp = false,
                                backupResult = result
                            )
                        }
                    }
                    BackupService.ACTION_BACKUP_ERROR -> {
                        val errorMessage = intent.getStringExtra(BackupService.EXTRA_ERROR_MESSAGE)

                        _uiState.update {
                            it.copy(
                                processingText = "에러발생",
                                isBackingUp = false,
                                errorMessage = errorMessage
                            )
                        }
                    }
                }
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(BackupService.ACTION_BACKUP_STARTED)
            addAction(BackupService.ACTION_BACKUP_PROGRESS)
            addAction(BackupService.ACTION_BACKUP_COMPLETED)
            addAction(BackupService.ACTION_BACKUP_ERROR)
        }

        // Fix: Use RECEIVER_NOT_EXPORTED for API 33+ or for internal broadcasts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Since these are internal broadcasts from BackupService,
            // it's safer to use RECEIVER_NOT_EXPORTED.
            context.registerReceiver(backupReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(context, backupReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    private fun unregisterBackupReceiver() {
        backupReceiver?.let {
            context.unregisterReceiver(it)
            backupReceiver = null
        }
    }

    fun startBackup() {
        // Update UI state to show backup is in progress
        _uiState.update {
            it.copy(
                isBackingUp = true,
                completedFiles = emptyList(),
                totalFilesToBackup = 0,
                completedFileCount = 0,
                errorMessage = null,
                backupResult = null
            )
        }

        // Start the backup service
        val intent = Intent(context, BackupService::class.java).apply {
            action = BackupService.ACTION_START_BACKUP
        }
        context.startService(intent)
    }

//    private fun createHistoryEntity(file: File, result: Result<Long>): FileHistoryEntity {
//        return result.fold(
//            onSuccess = { duration ->
//                FileHistoryEntity(
//                    filePath = file.absolutePath,
//                    fileName = file.name,
//                    fileSize = file.length(),
//                    uploadTimestamp = System.currentTimeMillis(),
//                    uploadDurationMs = duration,
//                    status = BackupStatus.SUCCESS,
//                    sessionId = currentSessionId
//                )
//            },
//            onFailure = {
//                FileHistoryEntity(
//                    filePath = file.absolutePath,
//                    fileName = file.name,
//                    fileSize = file.length(),
//                    uploadTimestamp = System.currentTimeMillis(),
//                    uploadDurationMs = 0,
//                    status = BackupStatus.FAILURE,
//                    sessionId = currentSessionId
//                )
//            }
//        )
//    }

    fun dismissResultDialog() {
        _uiState.update { it.copy(backupResult = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun stopBackup() {
        // Update UI state to show backup has been stopped
        _uiState.update { 
            it.copy(
                isBackingUp = false,
                errorMessage = "백업이 사용자에 의해 중지되었습니다."
            )
        }

        // Stop the backup service
        val intent = Intent(context, BackupService::class.java).apply {
            action = BackupService.ACTION_STOP_BACKUP
        }
        context.startService(intent)
    }

    fun showBackupProgress() {
        // isBackingUp 상태를 true로 변경하여 UI가 BackupProgressView를 표시하도록 함
        // 필요하다면, 여기서 실제 서비스로부터 현재 백업 진행률을 가져오는 로직을 추가할 수 있습니다.
        _uiState.update { currentState ->
            currentState.copy(isBackingUp = true)
        }

    }
}
