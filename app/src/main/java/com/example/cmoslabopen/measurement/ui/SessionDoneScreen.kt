package com.example.cmoslabopen.measurement.ui

import androidx.compose.runtime.Composable
import java.io.File

/**
 * Terminal screen after a session completes: shows the output folder, lets
 * the operator share it via the FileProvider declared in the manifest, and
 * returns to [MainScreen].
 */
@Composable
fun SessionDoneScreen(
    sessionDir: File,
    onShare: () -> Unit,
    onDone: () -> Unit,
) {
    TODO("Phase 11: render session-done screen")
}
