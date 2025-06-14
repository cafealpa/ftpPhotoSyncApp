package com.example.photobackerupper.data.remote

import com.example.photobackerupper.data.model.FtpSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import java.util.logging.Logger

@Singleton
// 로거 추가
class FtpClientWrapper @Inject constructor() {

    private val logger = Logger.getLogger(FtpClientWrapper::class.java.name)
    suspend fun uploadFile(
        localFile: File,
        settings: FtpSettings
    ): Result<Long> = withContext(Dispatchers.IO) {
        val ftpClient = FTPClient()
        try {
            // 타임아웃 설정 (더 짧게 설정하여 빠른 재시도)
            ftpClient.connectTimeout = 20000 // 20초
            ftpClient.defaultTimeout = 20000 // 20초
            ftpClient.dataTimeout = Duration.of(20, ChronoUnit.SECONDS)
            ftpClient.setControlKeepAliveTimeout(10) // 10초마다 NOOP 명령어 전송
            ftpClient.controlKeepAliveReplyTimeout = 20000 // 제어 연결 유지 응답 대기 시간

            // 소켓 버퍼 크기 설정
            ftpClient.bufferSize = 1024 * 1024 // 1MB 버퍼 크기

            // 연결 재시도 메커니즘
            var connected = false
            var retries = 0
            val maxRetries = 5 // 최대 재시도 횟수 증가

            while (!connected && retries < maxRetries) {
                try {
                    // 연결 시도 전에 네트워크 연결이 활성 상태인지 확인하기 위한 작은 지연 추가
                    if (retries > 0) {
                        // 지수 백오프 전략으로 대기 시간 늘리기 (1초, 2초, 4초, 8초...)
                        val backoffTime = (Math.pow(2.0, retries.toDouble()) * 1000).toLong().coerceAtMost(10000)
                        logger.info("Waiting for ${backoffTime}ms before retry attempt ${retries + 1}")
                        kotlinx.coroutines.delay(backoffTime) 
                    }

                    logger.info("Attempting to connect to FTP server: ${settings.serverIp}:${settings.port}")
                    // 연결
                    ftpClient.connect(settings.serverIp, settings.port)

                    // 소켓 옵션 설정
                    ftpClient.keepAlive = true
                    ftpClient.soTimeout = 30000 // 소켓 타임아웃 30초

                    val reply = ftpClient.replyCode
                    if (!org.apache.commons.net.ftp.FTPReply.isPositiveCompletion(reply)) {
                        logger.info("FTP server refused connection. Reply code: $reply")
                        if (ftpClient.isConnected) {
                            ftpClient.disconnect()
                        }
                        throw IOException("FTP server refused connection.")
                    }
                    connected = true
                    logger.info("Successfully connected to FTP server: ${settings.serverIp}:${settings.port}")
                } catch (e: IOException) {
                    retries++
                    logger.warning("FTP connection attempt $retries failed: ${e.message}.")
                    if (retries >= maxRetries) {
                        logger.severe("All connection attempts failed after $maxRetries tries")
                        throw IOException("Failed to connect to FTP server after $maxRetries attempts: ${e.message}")
                    }
                    logger.info("Will retry connection...")
                }
            }

            // 로그인 재시도 메커니즘
            var loggedIn = false
            var loginRetries = 0
            val maxLoginRetries = 3

            while (!loggedIn && loginRetries < maxLoginRetries) {
                try {
                    // 로그인
                    if (ftpClient.login(settings.userName, settings.password)) {
                        loggedIn = true
                        logger.info("Logged in as user: ${settings.userName}")
                    } else {
                        throw IOException("FTP login failed.")
                    }
                } catch (e: IOException) {
                    loginRetries++
                    if (loginRetries >= maxLoginRetries) {
                        throw e
                    }
                    logger.warning("FTP login attempt $loginRetries failed. Retrying...")
                    kotlinx.coroutines.delay(1000) // 1초 대기 후 재시도
                }
            }

            if (!loggedIn) {
                logger.info("FTP login failed for user: ${settings.userName} after $maxLoginRetries attempts")
                return@withContext Result.failure(IOException("FTP login failed after multiple attempts."))
            }

            // 설정
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
            ftpClient.enterLocalPassiveMode()
            // 폴더 생성 및 이동
            if (!ftpClient.changeWorkingDirectory(settings.uploadFolder)) {
                if (ftpClient.makeDirectory(settings.uploadFolder)) {
                    ftpClient.changeWorkingDirectory(settings.uploadFolder)
                } else {
                    return@withContext Result.failure(IOException("Could not create or access upload folder on FTP server."))
                }
                logger.info("Created and changed to directory: ${settings.uploadFolder}")
            } else {
                logger.info("Changed to directory: ${settings.uploadFolder}")
            }
            // 파일 크기 확인
            val fileSize = localFile.length()
            val maxFileSize = 50 * 1024 * 1024L // 50MB

            if (fileSize > maxFileSize) {
                logger.warning("File size (${fileSize/1024/1024}MB) exceeds limit of ${maxFileSize/1024/1024}MB: ${localFile.name}")
                // 큰 파일은 여기서 처리 로직을 추가할 수 있음
                // 예: 파일 분할 또는 특별 처리
            }

            // 업로드 재시도 메커니즘
            var uploadSuccess = false
            var uploadRetries = 0
            val maxUploadRetries = 3
            var uploadTime = 0L

            while (!uploadSuccess && uploadRetries < maxUploadRetries) {
                try {
                    val startTime = System.currentTimeMillis()
                    val inputStream = FileInputStream(localFile)

                    uploadSuccess = inputStream.use {
                        ftpClient.storeFile(localFile.name, it)
                    }

                    val endTime = System.currentTimeMillis()
                    uploadTime = endTime - startTime

                    if (!uploadSuccess) {
                        throw IOException("FTP upload returned false: ${ftpClient.replyString}")
                    }

                    logger.info("File uploaded successfully: ${localFile.name}, size: ${fileSize/1024}KB, time: ${uploadTime}ms")
                } catch (e: IOException) {
                    uploadRetries++
                    logger.warning("Upload attempt $uploadRetries failed for ${localFile.name}: ${e.message}")

                    if (uploadRetries >= maxUploadRetries) {
                        throw e
                    }

                    // 지수 백오프로 대기 시간 설정
                    val backoffTime = (Math.pow(2.0, uploadRetries.toDouble()) * 1000).toLong().coerceAtMost(10000)
                    logger.info("Waiting ${backoffTime}ms before retry upload")
                    kotlinx.coroutines.delay(backoffTime)

                    // FTP 연결 상태 확인 및 필요시 재연결
                    if (!ftpClient.isConnected) {
                        logger.info("FTP connection lost, reconnecting...")
                        ftpClient.connect(settings.serverIp, settings.port)
                        ftpClient.login(settings.userName, settings.password)
                        ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                        ftpClient.enterLocalPassiveMode()
                        ftpClient.changeWorkingDirectory(settings.uploadFolder)
                    }
                }
            }

            if (uploadSuccess) {
                logger.info("Final upload result: Success for ${localFile.name}")
                Result.success(uploadTime) // 성공: 업로드 시간 반환
            } else {
                logger.warning("Final upload result: Failed for ${localFile.name} after $maxUploadRetries attempts")
                Result.failure(IOException("FTP upload failed after multiple attempts: ${ftpClient.replyString}"))
            }
        } catch (e: Exception) {
            logger.severe("Exception during FTP operation: ${e.message}")
            Result.failure(e)
        } finally {
            if (ftpClient.isConnected) {
                try {
                    ftpClient.logout()
                    ftpClient.disconnect()
                    logger.info("Logged out and disconnected from FTP server.")
                } catch (ex: IOException) {
                    logger.warning("IOException during FTP logout/disconnect: ${ex.message}")
                    // 무시
                }
            }
        }
    }
}