package com.example.photobackerupper.data.remote

import com.example.photobackerupper.data.model.FtpSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.FileInputStream
import java.io.IOException
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
            // 연결
            ftpClient.connect(settings.serverIp, settings.port)
            val reply = ftpClient.replyCode
            if (!org.apache.commons.net.ftp.FTPReply.isPositiveCompletion(reply)) {
                logger.info("FTP server refused connection. Reply code: $reply")
                ftpClient.disconnect()
                return@withContext Result.failure(IOException("FTP server refused connection."))
            }
            logger.info("Connected to FTP server: ${settings.serverIp}:${settings.port}")

            // 로그인
            if (!ftpClient.login(settings.userName, settings.password)) {
                logger.info("FTP login failed for user: ${settings.userName}")
                return@withContext Result.failure(IOException("FTP login failed."))
            }
            logger.info("Logged in as user: ${settings.userName}")

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
            // 업로드
            val startTime = System.currentTimeMillis()
            val inputStream = FileInputStream(localFile)
            val success = inputStream.use {
                ftpClient.storeFile(localFile.name, it)
            }
            val endTime = System.currentTimeMillis()

            if (success) {
                logger.info("File uploaded successfully: ${localFile.name}, time taken: ${endTime - startTime}ms")
                Result.success(endTime - startTime) // 성공: 업로드 시간 반환
            } else {
                logger.warning("FTP upload failed for file: ${localFile.name}. Reply string: ${ftpClient.replyString}")
                Result.failure(IOException("FTP upload failed: ${ftpClient.replyString}"))
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