package com.example.photobackerupper.service

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.photobackerupper.MainActivity
import com.example.photobackerupper.R
import com.example.photobackerupper.data.local.dao.BackupHistoryDao
import com.example.photobackerupper.data.local.dao.BackupSessionDao
import com.example.photobackerupper.data.local.entity.BackupResult
import com.example.photobackerupper.data.local.entity.BackupSessionEntity
import com.example.photobackerupper.data.local.entity.BackupStatus
import com.example.photobackerupper.data.local.entity.FileHistoryEntity
import com.example.photobackerupper.data.remote.FtpClientWrapper
import com.example.photobackerupper.data.repository.PhotoRepository
import com.example.photobackerupper.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.logging.Logger
import javax.inject.Inject
import android.os.PowerManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * 백그라운드에서 사진 백업을 처리하는 서비스.
 * 이 서비스는 포그라운드 서비스로 실행되어 백업 진행 상황을 사용자에게 알립니다.
 * Hilt를 사용하여 의존성 주입을 지원합니다.
 */
@AndroidEntryPoint
class BackupService : Service() {

    private val logger = Logger.getLogger(BackupService::class.java.name)

    // PhotoRepository 주입: 장치에서 사진을 검색하는 데 사용됩니다.
    @Inject
    lateinit var photoRepository: PhotoRepository

    // SettingsRepository 주입: FTP 설정과 같은 앱 설정을 관리하는 데 사용됩니다.
    @Inject
    lateinit var settingsRepository: SettingsRepository

    // FtpClientWrapper 주입: FTP 서버와의 상호 작용을 처리합니다.
    @Inject
    lateinit var ftpClient: FtpClientWrapper

    // BackupHistoryDao 주입: 백업 기록을 로컬 데이터베이스에 저장하는 데 사용됩니다.
    @Inject
    lateinit var backupHistoryDao: BackupHistoryDao

    // BackupSessionDao 주입: 백업 세션 기록을 로컬 데이터베이스에 저장하는 데 사용됩니다.
    @Inject
    lateinit var backupSessionDao: BackupSessionDao

    // 서비스 수명 주기 동안 코루틴을 관리하기 위한 CoroutineScope.
    // SupervisorJob은 자식 코루틴이 실패하더라도 다른 자식 코루틴이 계속 실행되도록 합니다.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 현재 진행 중인 백업 작업을 나타내는 Job.
    private var backupJob: Job? = null

    // 현재 백업 세션의 ID
    private var currentSessionId: Long = 0

    // 백업 진행 상황 및 상태를 UI에 노출하기 위한 MutableStateFlow.
    private val _backupState = MutableStateFlow(BackupState())

    // 백업 상태의 읽기 전용 버전으로, 외부에서 관찰할 수 있습니다.
    val backupState = _backupState.asStateFlow()

    // 알림을 관리하기 위한 NotificationManager.
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    // WakeLock을 사용하여 백업 중에 기기가 절전 모드로 전환되지 않도록 함
    private var wakeLock: PowerManager.WakeLock? = null

    // 네트워크 상태 모니터링을 위한 변수
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isNetworkAvailable = true

    /**
     * 서비스 전체에서 사용되는 상수 및 작업 정의를 포함하는 동반 객체입니다.
     */
    companion object {
        private const val NOTIFICATION_ID = 1 // 포그라운드 서비스 알림의 고유 ID
        private const val CHANNEL_ID = "backup_service_channel" // 알림 채널의 ID
        private const val CHANNEL_NAME = "Backup Service" // 알림 채널의 이름

        // 서비스 작업을 시작하고 중지하기 위한 인텐트 작업.
        const val ACTION_START_BACKUP = "com.example.photobackerupper.action.START_BACKUP"
        const val ACTION_STOP_BACKUP = "com.example.photobackerupper.action.STOP_BACKUP"
        const val EXTRA_RESULT = "com.example.photobackerupper.extra.RESULT"

        // 백업 진행 상황을 UI에 브로드캐스트하기 위한 작업.
        const val ACTION_BACKUP_STARTED = "com.example.photobackerupper.action.BACKUP_STARTED"
        const val ACTION_BACKUP_PROGRESS = "com.example.photobackerupper.action.BACKUP_PROGRESS"
        const val ACTION_BACKUP_COMPLETED = "com.example.photobackerupper.action.BACKUP_COMPLETED"
        const val ACTION_BACKUP_ERROR = "com.example.photobackerupper.action.BACKUP_ERROR"

        // 백업 브로드캐스트와 함께 전송되는 추가 데이터 키.
        const val EXTRA_COMPLETED_COUNT = "com.example.photobackerupper.extra.COMPLETED_COUNT"
        const val EXTRA_TOTAL_COUNT = "com.example.photobackerupper.extra.TOTAL_COUNT"
        const val EXTRA_SUCCESS_COUNT = "com.example.photobackerupper.extra.SUCCESS_COUNT"
        const val EXTRA_FAILURE_COUNT = "com.example.photobackerupper.extra.FAILURE_COUNT"
        const val EXTRA_TOTAL_SIZE_MB = "com.example.photobackerupper.extra.TOTAL_SIZE_MB"
        const val EXTRA_DURATION_SECONDS = "com.example.photobackerupper.extra.DURATION_SECONDS"
        const val EXTRA_ERROR_MESSAGE = "com.example.photobackerupper.extra.ERROR_MESSAGE"
        const val EXTRA_SESSION_START_TIME = "com.example.photobackerupper.extra.SESSION_START_TIME"
    }

    /**
     * 백업 서비스의 현재 상태를 나타내는 데이터 클래스입니다.
     * @param isBackingUp 백업이 현재 진행 중인지 여부.
     * @param completedCount 완료된 파일 수.
     * @param totalCount 총 파일 수.
     * @param successCount 성공적으로 백업된 파일 수.
     * @param failureCount 백업에 실패한 파일 수.
     * @param totalUploadedSize 총 업로드된 크기(바이트).
     * @param errorMessage 오류 메시지(오류가 발생한 경우).
     */
    data class BackupState(
        val isBackingUp: Boolean = false,
        val completedCount: Int = 0,
        val totalCount: Int = 0,
        val successCount: Int = 0,
        val failureCount: Int = 0,
        val totalUploadedSize: Long = 0,
        val errorMessage: String? = null
    )

    /**
     * 서비스가 생성될 때 호출됩니다.
     * 알림 채널을 생성하여 백업 진행 상황 알림을 표시합니다.
     */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // WakeLock 초기화
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PhotoBackerUpper::BackupWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        // 네트워크 모니터링 설정
        setupNetworkMonitoring()
    }

    /**
     * startService()를 통해 클라이언트가 서비스를 시작하려고 할 때마다 시스템에서 호출됩니다.
     * 인텐트 작업을 기반으로 백업을 시작하거나 중지합니다.
     * @param intent 서비스를 시작하는 데 사용된 인텐트.
     * @param flags 추가 플래그에 대한 정수.
     * @param startId 이 특정 시작 요청을 나타내는 고유 정수 ID.
     * @return 서비스가 종료된 후 시스템이 서비스를 다시 만들지 않음을 나타내는 START_NOT_STICKY.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BACKUP -> {
                // 백업을 시작하고 서비스를 포그라운드로 전환합니다.
                startForeground(NOTIFICATION_ID, createNotification())
                startBackup()
            }

            ACTION_STOP_BACKUP -> {
                // 백업을 중지하고 서비스를 중지합니다.
                stopBackup()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 다른 구성 요소(예: Activity)가 서비스에 바인딩하려고 할 때 호출됩니다.
     * 이 서비스는 바인딩을 지원하지 않으므로 null을 반환합니다.
     * @param intent 바인딩에 사용된 인텐트.
     * @return 바인딩을 지원하지 않음을 나타내는 null.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 서비스가 더 이상 사용되지 않고 소멸될 때 호출됩니다.
     * 실행 중인 모든 코루틴을 취소하여 리소스를 해제합니다.
     */
    override fun onDestroy() {
        // WakeLock 해제
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }

        // 네트워크 콜백 해제
        unregisterNetworkCallback()

        serviceScope.cancel() // 모든 코루틴 취소
        super.onDestroy()
    }

    /**
     * 백업 진행 상황 알림을 위한 알림 채널을 생성합니다.
     * 이 채널은 Android 8.0(API 레벨 26) 이상에서 필요합니다.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW ,// 백업 진행 상황이므로 낮은 중요도

        ).apply {
            description = "Shows backup progress"
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 백업 진행 상황을 표시하는 알림을 생성합니다.
     * @return 생성된 Notification 객체.
     */
    private fun createNotification(): Notification {

        val intent = Intent(this, MainActivity::class.java).apply {
            action = "SHOW_BACKUP_PROGRESS"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // 알림을 클릭하면 MainActivity로 이동하는 PendingIntent.
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val state = backupState.value // 현재 백업 상태 가져오기
        val contentText = if (state.isBackingUp) {
            if (!isNetworkAvailable) {
                // 네트워크 연결이 끊겼을 때
                "네트워크 연결 대기 중... (${state.completedCount}/${state.totalCount})"
            } else {
                // 백업 중일 때 진행 상황 텍스트 표시
                val percentComplete = if (state.totalCount > 0) {
                    (state.completedCount.toFloat() / state.totalCount.toFloat() * 100).toInt()
                } else 0
                "${state.completedCount}/${state.totalCount} 파일 백업 중... ($percentComplete%)"
            }
        } else {
            // 백업이 진행 중이 아닐 때 준비 텍스트 표시
            "백업 서비스 준비 중..."
        }

        // 알림 빌더를 사용하여 알림 구성
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("사진 백업") // 알림 제목
            .setContentText(contentText) // 알림 내용
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 작은 아이콘
            .setContentIntent(pendingIntent) // 알림 클릭 시 실행될 인텐트
            .setProgress(state.totalCount, state.completedCount, state.totalCount == 0) // 진행률 표시줄
            .setOngoing(true) // 알림을 스와이프하여 해제할 수 없도록 합니다.
            .setOnlyAlertOnce(false) // 업데이트마다 알림이 표시되도록 설정
            .build()
    }

    /**
     * 현재 백업 상태로 알림을 업데이트합니다.
     */
    private fun updateNotification() {
        serviceScope.launch(Dispatchers.Main) {
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }

    /**
     * 실제 백업 프로세스를 시작합니다.
     * 이 함수는 서비스 코루틴 스코프 내에서 비동기적으로 실행됩니다.
     */
    private fun startBackup() {
        // WakeLock 획득
        wakeLock?.acquire(3 * 60 * 60 * 1000L) // 최대 3시간 동안 WakeLock 유지

        // 기존 백업 작업이 있으면 취소합니다.
        backupJob?.cancel()

        // 새 백업 작업을 시작합니다.
        backupJob = serviceScope.launch {
            // 1. 백업 상태 초기화
            _backupState.update {
                it.copy(
                    isBackingUp = true, // 백업이 시작되었음을 나타냅니다.
                    completedCount = 0,
                    totalCount = 0,
                    successCount = 0,
                    failureCount = 0,
                    totalUploadedSize = 0,
                    errorMessage = null
                )
            }
            updateNotification() // 알림 업데이트

            // 백업 시작 시간 기록
            val sessionStartTime = System.currentTimeMillis()

            // 백업 세션 엔티티 생성 및 저장
            val sessionEntity = BackupSessionEntity(
                backupTimestamp = sessionStartTime,
                backupResult = BackupResult.COMPLETED, // 초기값, 나중에 업데이트됨
                successCount = 0,
                failureCount = 0,
                totalDurationMs = 0
            )

            // 세션 ID 저장
            currentSessionId = backupSessionDao.insertSession(sessionEntity)

            // 백업 시작 브로드캐스트를 보냅니다.
            sendBroadcast(Intent(ACTION_BACKUP_STARTED).apply {
                putExtra(EXTRA_SESSION_START_TIME, sessionStartTime)
            }.setPackage(packageName))

            val startTime = System.currentTimeMillis() // 백업 시작 시간 기록
            var successCount = 0
            var failureCount = 0
            var totalUploadedSize = 0L

            try {
                // FTP 설정 확인
                val ftpSettings = settingsRepository.ftpSettingsFlow.first()
                if (ftpSettings.serverIp.isBlank()) {
                    // FTP 서버 IP가 설정되지 않은 경우 오류 처리
                    throw IllegalStateException("FTP 서버 IP가 설정되지 않았습니다. 설정 화면에서 IP를 입력해주세요.")
                }

                // 2. 백업 대상 파일 목록 생성
                val targetFiles = photoRepository.createBackupTargetList()
                if (targetFiles.isEmpty()) {
                    // 백업할 새 파일이 없는 경우 처리
                    _backupState.update { it.copy(isBackingUp = false, errorMessage = "백업할 새로운 파일이 없습니다.") }
                    updateNotification()
                    sendBroadcast(Intent(ACTION_BACKUP_ERROR).apply {
                        putExtra(EXTRA_ERROR_MESSAGE, "백업할 새로운 파일이 없습니다.")
                    }.setPackage(packageName))
                    stopSelf() // 서비스 중지
                    return@launch
                }

                _backupState.update { it.copy(totalCount = targetFiles.size) } // 총 파일 수 업데이트
                updateNotification() // 알림 업데이트

                // --- 병렬 처리 제한을 위한 Semaphore 생성 ---
                // 동시에 5개의 업로드 작업만 허용합니다.
                val semaphore = Semaphore(5)

                // 3. 병렬 업로드 실행 (Semaphore로 제어)
                val uploadJobs = targetFiles.map { file ->
                    async(Dispatchers.IO) { // 각 파일을 병렬로 처리할 코루틴을 생성합니다.
                        // 작업을 시작하기 전에 허가를 얻을 때까지 대기하고, 작업 후 자동 반납
                        semaphore.withPermit {
                            // 이 블록 안의 코드는 동시에 10개 이하로만 실행됨
                            val result = ftpClient.uploadFile(file, ftpSettings)

                            // 4. 업로드 완료 후 DB 저장 및 상태 업데이트
                            val historyEntity = createHistoryEntity(file, result)
                            backupHistoryDao.insertFileHistory(historyEntity)

                            // 백업 상태 업데이트
                            _backupState.update { state ->
                                val newCompletedCount = state.completedCount + 1
                                val newSuccessCount = if (historyEntity.status == BackupStatus.SUCCESS) state.successCount + 1 else state.successCount
                                val newFailureCount = if (historyEntity.status == BackupStatus.FAILURE) state.failureCount + 1 else state.failureCount
                                val newTotalSize = if (historyEntity.status == BackupStatus.SUCCESS) state.totalUploadedSize + file.length() else state.totalUploadedSize

                                state.copy(
                                    completedCount = newCompletedCount,
                                    successCount = newSuccessCount,
                                    failureCount = newFailureCount,
                                    totalUploadedSize = newTotalSize
                                )
                            }

                            // 백업 상태가 변경될 때마다 UI 스레드에서 알림을 업데이트합니다
                            serviceScope.launch(Dispatchers.Main) {
                                updateNotification() // 알림 업데이트
                            }

                            // 진행 상황 브로드캐스트
                            serviceScope.launch(Dispatchers.Main) {
                                sendBroadcast(Intent(ACTION_BACKUP_PROGRESS).apply {
                                    putExtra(EXTRA_COMPLETED_COUNT, _backupState.value.completedCount)
                                    putExtra(EXTRA_TOTAL_COUNT, _backupState.value.totalCount)
                                    putExtra(EXTRA_SESSION_START_TIME, startTime) // 세션 시작 시간 추가

                                    logger.info("EXTRA_COMPLETED_COUNT: ${_backupState.value.completedCount}, EXTRA_TOTAL_COUNT: ${_backupState.value.totalCount}");
                                }.setPackage(packageName))
                                // 알림도 즉시 업데이트
                                updateNotification()
                                // 포그라운드 서비스 알림 업데이트
                                notificationManager.notify(NOTIFICATION_ID, createNotification())
                            }

                            historyEntity // async의 결과로 historyEntity를 반환하여 나중에 집계
                        }
                    }
                }

                // 모든 업로드 작업이 완료될 때까지 기다리고 결과를 집계합니다.
                val results = uploadJobs.awaitAll()
                results.forEach {
                    if (it.status == BackupStatus.SUCCESS) {
                        successCount++
                        totalUploadedSize += it.fileSize
                    } else {
                        failureCount++
                    }
                }

                val endTime = System.currentTimeMillis() // 백업 종료 시간 기록
                val durationMs = endTime - startTime // 총 백업 시간 계산 (밀리초)
                val durationSeconds = durationMs / 1000f // 초 단위로 변환

                // 백업 세션 엔티티 업데이트
                if (currentSessionId > 0) {
                    val updatedSession = BackupSessionEntity(
                        id = currentSessionId,
                        backupTimestamp = startTime,
                        backupResult = BackupResult.COMPLETED,
                        successCount = successCount,
                        failureCount = failureCount,
                        totalDurationMs = durationMs
                    )
                    backupSessionDao.insertSession(updatedSession)
                }

                // 5. 최종 결과 업데이트
                _backupState.update {
                    it.copy(
                        isBackingUp = false, // 백업 완료
                        successCount = successCount,
                        failureCount = failureCount,
                        totalUploadedSize = totalUploadedSize
                    )
                }

                // 백업 완료 브로드캐스트를 보냅니다.
                sendBroadcast(Intent(ACTION_BACKUP_COMPLETED).apply {
                    putExtra(EXTRA_SUCCESS_COUNT, successCount)
                    putExtra(EXTRA_FAILURE_COUNT, failureCount)
                    putExtra(EXTRA_TOTAL_SIZE_MB, totalUploadedSize / 1024f / 1024f) // MB 단위로 변환
                    putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
                }.setPackage(packageName))

                stopSelf() // 서비스 중지

            } catch (e: Exception) {
                // 6. 예외 처리
                _backupState.update {
                    it.copy(
                        isBackingUp = false,
                        errorMessage = e.message ?: "알 수 없는 오류가 발생했습니다." // 오류 메시지 설정
                    )
                }

                // 백업 세션 엔티티 업데이트 - 에러로 인한 중지
                if (currentSessionId > 0) {
                    val endTime = System.currentTimeMillis()
                    val durationMs = endTime - startTime
                    val updatedSession = BackupSessionEntity(
                        id = currentSessionId,
                        backupTimestamp = startTime,
                        backupResult = BackupResult.ERROR_STOPPED,
                        successCount = _backupState.value.successCount,
                        failureCount = _backupState.value.failureCount,
                        totalDurationMs = durationMs
                    )
                    backupSessionDao.insertSession(updatedSession)
                }

                // 백업 오류 브로드캐스트를 보냅니다.
                sendBroadcast(Intent(ACTION_BACKUP_ERROR).apply {
                    putExtra(EXTRA_ERROR_MESSAGE, e.message ?: "알 수 없는 오류가 발생했습니다.")
                }.setPackage(packageName))

                stopSelf() // 서비스 중지
            }
        }
    }

    /**
     * 백업 프로세스를 중지하고 관련 상태를 업데이트합니다.
     */
    private fun stopBackup() {
        // WakeLock 해제
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }

        backupJob?.cancel() // 진행 중인 백업 작업 취소
        backupJob = null

        // 백업 상태를 업데이트하여 백업이 중지되었음을 반영합니다.
        _backupState.update {
            it.copy(
                isBackingUp = false,
                errorMessage = "백업이 사용자에 의해 중지되었습니다."
            )
        }

        // 백업 세션 엔티티 업데이트 - 사용자에 의한 중지
        if (currentSessionId > 0) {
            serviceScope.launch(Dispatchers.IO) {
                val endTime = System.currentTimeMillis()
                val session = backupSessionDao.getSessionById(currentSessionId)
                if (session != null) {
                    val durationMs = endTime - session.backupTimestamp
                    val updatedSession = BackupSessionEntity(
                        id = currentSessionId,
                        backupTimestamp = session.backupTimestamp,
                        backupResult = BackupResult.USER_CANCELLED,
                        successCount = _backupState.value.successCount,
                        failureCount = _backupState.value.failureCount,
                        totalDurationMs = durationMs
                    )
                    backupSessionDao.insertSession(updatedSession)
                }
            }
        }

        // 백업 중지 오류 브로드캐스트를 보냅니다.
        sendBroadcast(Intent(ACTION_BACKUP_ERROR).apply {
            putExtra(EXTRA_ERROR_MESSAGE, "백업이 사용자에 의해 중지되었습니다.")
        }.setPackage(packageName))
    }

    /**
     * 파일과 업로드 결과를 기반으로 FileHistoryEntity를 생성합니다.
     * @param file 백업된 파일.
     * @param result 업로드 작업의 결과 (성공 또는 실패).
     * @return 생성된 FileHistoryEntity 객체.
     */
    private fun createHistoryEntity(file: File, result: Result<Long>): FileHistoryEntity {
        return result.fold(
            onSuccess = { duration ->
                // 업로드 성공 시 기록 엔터티 생성
                FileHistoryEntity(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    fileSize = file.length(),
                    uploadTimestamp = System.currentTimeMillis(),
                    uploadDurationMs = duration,
                    status = BackupStatus.SUCCESS,
                    sessionId = currentSessionId
                )
            },
            onFailure = {
                // 업로드 실패 시 기록 엔터티 생성
                FileHistoryEntity(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    fileSize = file.length(),
                    uploadTimestamp = System.currentTimeMillis(),
                    uploadDurationMs = 0, // 실패 시 지속 시간 0
                    status = BackupStatus.FAILURE,
                    sessionId = currentSessionId
                )
            }
        )
    }

    /**
     * 네트워크 상태 모니터링을 설정합니다.
     * 네트워크 변경 사항을 감지하고 백업 프로세스에 영향을 줄 수 있는 변화에 대응합니다.
     */
    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // 네트워크 요청 구성
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        // 네트워크 콜백 정의
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // 네트워크가 사용 가능해졌을 때
                logger.info("Network became available")
                isNetworkAvailable = true

                // FTP 연결에 문제가 있었던 경우 네트워크가 복구된 것이므로 알림
                if (_backupState.value.isBackingUp) {
                    updateNotification() // 알림 업데이트
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                // 네트워크 연결이 끊겼을 때
                logger.warning("Network connection lost")
                isNetworkAvailable = false

                if (_backupState.value.isBackingUp) {
                    // 네트워크 연결이 끊겼음을 알림에 표시
                    updateNotification()
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                // 네트워크 기능이 변경되었을 때 (예: WiFi에서 모바일 데이터로 전환)
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val hasNotMetered = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

                logger.info("Network capabilities changed: Internet=$hasInternet, NotMetered=$hasNotMetered")

                // 인터넷 연결 상태가 변경되었을 때만 상태 업데이트
                if (isNetworkAvailable != hasInternet) {
                    isNetworkAvailable = hasInternet
                    if (_backupState.value.isBackingUp) {
                        updateNotification()
                    }
                }
            }
        }

        // 네트워크 콜백 등록
        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    /**
     * 네트워크 콜백을 해제합니다.
     */
    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
                networkCallback = null
            } catch (e: Exception) {
                logger.warning("Error unregistering network callback: ${e.message}")
            }
        }
    }

    /**
     * 현재 네트워크 연결 상태를 확인합니다.
     * @return 네트워크가 연결되어 있으면 true, 그렇지 않으면 false
     */
    private fun isNetworkConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
