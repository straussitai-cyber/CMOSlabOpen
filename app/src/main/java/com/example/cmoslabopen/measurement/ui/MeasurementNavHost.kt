package com.example.cmoslabopen.measurement.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

@Composable
fun MeasurementNavHost(
    viewModel: MainViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val snackbarScope = rememberCoroutineScope()

    LaunchedEffect(uiState.route) {
        val current = navController.currentDestination?.route
        if (current != uiState.route) {
            navController.navigate(uiState.route) {
                if (uiState.route == NavRoutes.CAMERA_SELECT) {
                    popUpTo(NavRoutes.CAMERA_SELECT) { inclusive = true }
                } else {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.CAMERA_SELECT,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(NavRoutes.CAMERA_SELECT) {
                CameraSelectScreen(
                    cameras = uiState.cameras,
                    selectedIds = uiState.selectedCameraIds,
                    multiCameraMode = uiState.multiCameraMode,
                    canOpenConcurrently = viewModel::canOpenConcurrently,
                    onMultiCameraModeChange = viewModel::setMultiCameraMode,
                    onSelectionChange = viewModel::setSelectedCameraIds,
                    onContinue = viewModel::navigateToSessionConfig,
                )
            }

            composable(NavRoutes.SESSION_CONFIG) {
                SessionConfigScreen(
                    viewModel = viewModel.sessionConfigViewModel(),
                    onStart = viewModel::startSession,
                )
            }

            composable(NavRoutes.SESSION_ACTIVE) {
                val active = uiState.activeSession
                if (active != null) {
                    ActiveSessionScreen(
                        state = active,
                        showAbortDialog = uiState.showAbortDialog,
                        onAbortClick = viewModel::requestAbort,
                        onAbortConfirm = viewModel::confirmAbort,
                        onAbortDismiss = viewModel::dismissAbortDialog,
                    )
                }
            }

            composable(NavRoutes.SESSION_DONE) {
                val done = uiState.doneSession
                if (done != null) {
                    SessionDoneScreen(
                        state = done,
                        onOpenSessionFolder = {
                            // No standard MIME type opens a directory on Android,
                            // and getExternalFilesDir() is app-scoped on API 30+
                            // so file managers usually cannot navigate there.
                            // We attempt the intent for devices that do handle it,
                            // and otherwise show the absolute path so the user can
                            // access it via ADB or USB.
                            val launched = try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    done.sessionDir,
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "resource/folder")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                                true
                            } catch (_: ActivityNotFoundException) {
                                false
                            } catch (_: Throwable) {
                                false
                            }
                            if (!launched) {
                                snackbarScope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Folder at: ${done.sessionDir.absolutePath}",
                                    )
                                }
                            }
                        },
                        onNewSession = viewModel::startNewSession,
                    )
                }
            }
        }
    }
}
