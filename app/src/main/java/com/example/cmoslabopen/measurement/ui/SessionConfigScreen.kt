package com.example.cmoslabopen.measurement.ui

import androidx.compose.runtime.Composable
import com.example.cmoslabopen.measurement.session.SessionConfig

/**
 * Lets the operator pick exposure / ISO sweeps, frame counts, and what
 * outputs (DNG, histogram) to save before choosing cameras on the next screen.
 */
@Composable
fun SessionConfigScreen(
    initial: SessionConfig,
    onContinue: (SessionConfig) -> Unit,
    onBack: () -> Unit,
) {
    TODO("Phase 11: render config screen")
}
