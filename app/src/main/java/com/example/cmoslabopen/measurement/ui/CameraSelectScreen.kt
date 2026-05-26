package com.example.cmoslabopen.measurement.ui

import androidx.compose.runtime.Composable
import com.example.cmoslabopen.measurement.session.SessionConfig

/**
 * Lists the device's cameras and concurrent camera sets so the operator
 * can pick which sensors take part in the session.
 */
@Composable
fun CameraSelectScreen(
    config: SessionConfig,
    onStart: (SessionConfig) -> Unit,
    onBack: () -> Unit,
) {
    TODO("Phase 11: render camera selection screen")
}
