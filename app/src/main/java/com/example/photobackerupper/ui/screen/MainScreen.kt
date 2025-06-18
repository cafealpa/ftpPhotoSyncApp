package com.example.photobackerupper.ui.screen

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.photobackerupper.ui.viewmodel.MainUiState
import com.example.photobackerupper.ui.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 권한 요청
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(key1 = true) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    // 에러 메시지 스낵바
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("사진 백업") },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Filled.List, contentDescription = "이력")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "설정")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isBackingUp) {
                BackupProgressView(state = uiState, viewModel = viewModel)
            } else {
                Button(
                    onClick = {
                        if (permissionState.allPermissionsGranted) {
                            viewModel.startBackup()
                        } else {
                            Toast.makeText(context, "사진 접근 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(200.dp, 60.dp)
                ) {
                    Text("백업 실행", fontSize = 20.sp)
                }
            }
        }
    }

    // 백업 완료 팝업
    if (uiState.backupResult != null) {
        BackupResultDialog(
            result = uiState.backupResult!!,
            onDismiss = { viewModel.dismissResultDialog() }
        )
    }
}

@Composable
fun BackupProgressView(
    state: MainUiState,
    viewModel: MainViewModel = hiltViewModel()
) {
    // 중지 확인 다이얼로그 상태 관리
    var showStopConfirmDialog by remember { mutableStateOf(false) }

    // 중지 확인 다이얼로그
    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            title = { Text("백업 중지") },
            text = { Text("백업을 중지하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.stopBackup()
                        showStopConfirmDialog = false
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
//            val progress = if (state.totalFilesToBackup > 0) {
//                state.completedFileCount.toFloat() / state.totalFilesToBackup.toFloat()
//            } else {
//                0f
//            }
            var progress by remember {
                mutableFloatStateOf(
                    if (state.totalFilesToBackup == 0) 0f
                    else state.completedFileCount.toFloat() / state.totalFilesToBackup.toFloat()
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${state.processingText} (${state.completedFileCount} / ${state.totalFilesToBackup})",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { showStopConfirmDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("중지")
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(state.completedFiles) { file ->
                BackupItem(file)
            }
        }
    }
}

@Composable
fun BackupItem(file: com.example.photobackerupper.ui.viewmodel.BackupFileUiModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = file.thumbnailUri,
                contentDescription = file.name,
                modifier = Modifier.size(60.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = String.format(Locale.getDefault(), "%.2f MB | %.1f 초", file.sizeMb, file.durationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BackupResultDialog(result: com.example.photobackerupper.ui.viewmodel.BackupResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("백업 완료") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("성공: ${result.successCount}개, 실패: ${result.failureCount}개")
                Text("총 백업 파일 수: ${result.totalBackedUpFiles}개")
                Text(String.format(Locale.getDefault(), "총 백업 크기: %.2f MB", result.totalBackedUpSizeMb))
                Text(String.format(Locale.getDefault(), "총 소요 시간: %.1f 초", result.totalTimeSeconds))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}
