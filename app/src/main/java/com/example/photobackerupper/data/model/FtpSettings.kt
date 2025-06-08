package com.example.photobackerupper.data.model

data class FtpSettings(
    val serverIp: String = "",
    val port: Int = 21,
    val uploadFolder: String = "backup",
    val userName: String = "",
    val password: String = ""
)
