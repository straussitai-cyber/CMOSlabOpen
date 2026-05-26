package com.example.cmoslabopen.measurement.ui

import androidx.compose.runtime.Composable
import com.example.cmoslabopen.measurement.session.SessionConfig

/**
 * Shows live progress of the running [com.example.cmoslabopen.measurement.session.SessionRunner]:
 * current frame, elapsed time, thermal level, and a cancel control.
 * No preview surface — this is a measurement instrument, not a viewfinder.
 */
@Composable
fun ActiveSessionScreen(
    config: SessionConfig,
    onSessionDone: () -> Unit,
    onSessionFailed: (Throwable) -> Unit,
) {
    TODO("Phase 11: render active-session screen")
}
