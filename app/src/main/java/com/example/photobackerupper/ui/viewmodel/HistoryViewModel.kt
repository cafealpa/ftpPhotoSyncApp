package com.example.photobackerupper.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photobackerupper.data.local.dao.BackupHistoryDao
import com.example.photobackerupper.data.local.dao.BackupSessionDao
import com.example.photobackerupper.data.local.entity.BackupResult
import com.example.photobackerupper.data.local.entity.BackupSessionEntity
import com.example.photobackerupper.data.local.entity.FileHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 백업 이력 화면의 UI 상태를 나타내는 데이터 클래스입니다.
 * 
 * @property sessions 백업 세션 목록
 * @property selectedSessionFiles 선택된 세션의 파일 목록
 * @property selectedSessionId 선택된 세션의 ID
 * @property isLoading 로딩 중인지 여부
 * @property errorMessage 오류 메시지 (있는 경우)
 */
data class HistoryUiState(
    val sessions: List<BackupSessionUiModel> = emptyList(),
    val selectedSessionFiles: List<FileHistoryUiModel> = emptyList(),
    val selectedSessionId: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 백업 세션의 UI 표시를 위한 데이터 모델입니다.
 * 
 * @property id 세션 ID
 * @property date 백업 날짜 (yyyy-MM-dd 형식)
 * @property time 백업 시간 (HH:mm:ss 형식)
 * @property result 백업 결과 (완료, 사용자 중지, 오류 발생)
 * @property successCount 성공한 파일 수
 * @property failureCount 실패한 파일 수
 * @property totalDuration 총 소요 시간 (초 단위)
 * @property resultColor 결과에 따른 색상 (완료: 녹색, 사용자 중지: 노란색, 오류 발생: 빨간색)
 */
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

/**
 * 백업 파일 이력의 UI 표시를 위한 데이터 모델입니다.
 * 
 * @property fileName 파일 이름
 * @property fileSize 파일 크기 (KB 또는 MB 단위)
 * @property uploadDuration 업로드 소요 시간 (초 단위)
 * @property status 백업 상태 (성공 또는 실패)
 * @property statusColor 상태에 따른 색상 (성공: 녹색, 실패: 빨간색)
 */
data class FileHistoryUiModel(
    val fileName: String,
    val fileSize: String,
    val uploadDuration: String,
    val status: String,
    val statusColor: androidx.compose.ui.graphics.Color
)

/**
 * 백업 이력 화면의 데이터를 관리하는 ViewModel입니다.
 * 백업 세션 목록과 선택된 세션의 파일 목록을 로드하고 UI 상태를 관리합니다.
 * 
 * 주요 기능:
 * 1. 백업 세션 목록 로드
 * 2. 선택된 세션의 파일 목록 로드
 * 3. 세션 선택 및 초기화
 * 4. 오류 처리
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val backupSessionDao: BackupSessionDao,
    private val backupHistoryDao: BackupHistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    // 선택된 세션 ID를 위한 별도의 private StateFlow 생성
    private val selectedSessionId = MutableStateFlow<Long?>(null)

    // 날짜 및 시간 포맷터
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * ViewModel 초기화 시 실행되는 코드 블록입니다.
     * 1. 세션 목록을 로드합니다.
     * 2. 선택된 세션 ID가 변경될 때마다 해당 세션의 파일 목록을 로드하는 Flow를 설정합니다.
     */
    init {
        // 1. 세션 목록 로드
        loadSessions()

        // 2. selectedSessionId가 변경될 때마다 flatMapLatest를 사용해 파일 목록 Flow를 전환
        viewModelScope.launch {
            try {
                selectedSessionId.flatMapLatest { id ->
                    // 실제로 어떤 ID가 넘어오는지 확인하기 위한 로그
                    println("Current session ID received: $id")
                    if (id == null) {
                        flowOf(emptyList())
                    } else {
                        backupHistoryDao.getFilesForSession(id)
                    }
                }.collect { files ->
                    // DAO로부터 몇 개의 파일이 수집되었는지 확인하기 위한 로그
                    println("Collected files size: ${files.size}")
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

    override fun onCleared() {
        super.onCleared()
        // ViewModel이 제거될 때 필요한 정리 작업 수행
        // viewModelScope는 자동으로 취소되므로 추가 작업 필요 없음
    }

    /**
     * 백업 세션 목록을 데이터베이스에서 로드합니다.
     * 로드된 세션 목록은 UI 상태에 저장되어 화면에 표시됩니다.
     */
    private fun loadSessions() {
        viewModelScope.launch {
            // 로딩 상태로 UI 업데이트
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // 데이터베이스에서 모든 세션 가져오기
                backupSessionDao.getAllSessions()
                    .stateIn(
                        scope = viewModelScope,
                        started = SharingStarted.WhileSubscribed(5000), // 5초 타임아웃
                        initialValue = emptyList()
                    )
                    .collect { sessions ->
                        // 세션 목록으로 UI 상태 업데이트
                        _uiState.value = _uiState.value.copy(
                            sessions = sessions.map { it.toUiModel() },
                            isLoading = false
                        )
                    }
            } catch (e: Exception) {
                // 오류 발생 시 UI 상태 업데이트
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "세션 목록을 불러오는 중 오류가 발생했습니다."
                )
            }
        }
    }

    /**
     * 세션을 선택하고 해당 세션의 파일 목록을 로드합니다.
     * selectedSessionId를 업데이트하면 flatMapLatest에 의해 자동으로 파일 목록이 로드됩니다.
     * 
     * @param sessionId 선택할 세션의 ID
     */
    fun selectSession(sessionId: Long) {
        // 로딩 상태로 UI 업데이트
        _uiState.value = _uiState.value.copy(isLoading = true, selectedSessionId = sessionId)
        // selectedSessionId 업데이트 - 이것이 flatMapLatest를 트리거합니다
        selectedSessionId.value = sessionId
    }



    /**
     * 선택된 세션을 초기화하고 파일 목록을 비웁니다.
     * 세션 목록 화면으로 돌아갈 때 사용됩니다.
     */
    fun clearSelectedSession() {
        // UI 상태 업데이트
        _uiState.value = _uiState.value.copy(
            selectedSessionId = null,
            selectedSessionFiles = emptyList()
        )
        // selectedSessionId 업데이트 - 이것이 flatMapLatest를 트리거합니다
        selectedSessionId.value = null
    }

    /**
     * 오류 메시지를 초기화합니다.
     * 오류 다이얼로그를 닫을 때 사용됩니다.
     */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * BackupSessionEntity를 UI에서 사용할 수 있는 BackupSessionUiModel로 변환합니다.
     * 
     * @return UI에서 사용할 수 있는 형태로 변환된 BackupSessionUiModel
     */
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

    /**
     * FileHistoryEntity를 UI에서 사용할 수 있는 FileHistoryUiModel로 변환합니다.
     * 파일 크기를 KB 또는 MB 단위로 변환하고, 업로드 시간을 초 단위로 변환합니다.
     * 
     * @return UI에서 사용할 수 있는 형태로 변환된 FileHistoryUiModel
     */
    private fun FileHistoryEntity.toUiModel(): FileHistoryUiModel {
        // 파일 크기를 KB 또는 MB 단위로 변환
        val fileSizeText = if (fileSize < 1024 * 1024) {
            String.format("%.2f KB", fileSize / 1024f)
        } else {
            String.format("%.2f MB", fileSize / 1024f / 1024f)
        }

        // 업로드 시간을 초 단위로 변환
        val durationText = String.format("%.1f초", uploadDurationMs / 1000f)

        // 백업 상태에 따른 텍스트 설정
        val statusText = when (status) {
            com.example.photobackerupper.data.local.entity.BackupStatus.SUCCESS -> "성공"
            com.example.photobackerupper.data.local.entity.BackupStatus.FAILURE -> "실패"
        }

        // 백업 상태에 따른 색상 설정
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