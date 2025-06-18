package com.example.photobackerupper.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photobackerupper.ui.viewmodel.BackupSessionUiModel
import com.example.photobackerupper.ui.viewmodel.FileHistoryUiModel
import com.example.photobackerupper.ui.viewmodel.HistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.selectedSessionId == null) "백업 이력" else "백업 파일 이력"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.selectedSessionId != null) {
                            viewModel.clearSelectedSession()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로 가기"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.errorMessage != null) {
                ErrorMessage(
                    message = uiState.errorMessage!!,
                    onDismiss = { viewModel.dismissError() }
                )
            } else if (uiState.selectedSessionId == null) {
                // 세션 목록 화면
                SessionListScreen(
                    sessions = uiState.sessions,
                    onSessionClick = { viewModel.selectSession(it) }
                )
            } else {
                // 파일 목록 화면
                FileListScreen(
                    files = uiState.selectedSessionFiles,
                    session = uiState.sessions.find { it.id == uiState.selectedSessionId }
                )
            }
        }
    }

    // 에러 다이얼로그
    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("오류") },
            text = { Text(uiState.errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("확인")
                }
            }
        )
    }
}

@Composable
fun SessionListScreen(
    sessions: List<BackupSessionUiModel>,
    onSessionClick: (Long) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("백업 이력이 없습니다.")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(sessions) { session ->
                SessionItem(
                    session = session,
                    onClick = { onSessionClick(session.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun SessionItem(
    session: BackupSessionUiModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${session.date} ${session.time}",
                    style = MaterialTheme.typography.titleMedium
                )
                Box(
                    modifier = Modifier
                        .background(session.resultColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = session.result,
                        color = session.resultColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("성공: ${session.successCount}개")
                Text("실패: ${session.failureCount}개")
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "총 소요 시간: ${session.totalDuration}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun FileListScreen(
    files: List<FileHistoryUiModel>,
    session: BackupSessionUiModel?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 세션 요약 정보
        session?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "${it.date} ${it.time}",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("상태: ${it.result}", color = it.resultColor)
                        Text("총 파일: ${it.successCount + it.failureCount}개")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("성공: ${it.successCount}개")
                        Text("실패: ${it.failureCount}개")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text("총 소요 시간: ${it.totalDuration}")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 파일 목록
        Text(
            text = "백업 파일 목록",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("파일 이력이 없습니다.")

                    Spacer(modifier = Modifier.height(8.dp))

                    // 세션 상태에 따른 추가 메시지 표시
                    session?.let {
                        when (it.result) {
                            "사용자 중지" -> Text("백업이 사용자에 의해 중지되었습니다.", color = Color.Gray)
                            "오류 발생" -> Text("백업 중 오류가 발생했습니다.", color = Color.Gray)
                            else -> {}
                        }
                    }
                }
            }
        } else {
            // 파일이 있으면 항상 표시
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(files) { file ->
                    FileItem(file = file)
                    Divider()
                }
            }
        }
    }
}

@Composable
fun FileItem(file: FileHistoryUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Add thumbnail image on the left
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(file.filePath)
                .crossfade(true)
                .build(),
            contentDescription = "File thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .padding(end = 8.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "크기: ${file.fileSize}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "시간: ${file.uploadDuration}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .background(file.statusColor.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = file.status,
                color = file.statusColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ErrorMessage(
    message: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "오류가 발생했습니다",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Red
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onDismiss) {
                Text("확인")
            }
        }
    }
}
