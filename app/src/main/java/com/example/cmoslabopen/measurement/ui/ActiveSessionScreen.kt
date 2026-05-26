package com.example.cmoslabopen.measurement.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cmoslabopen.measurement.ui.theme.NumericMediumStyle
import com.example.cmoslabopen.measurement.ui.theme.NumericReadoutStyle
import android.os.PowerManager

@Composable
fun ActiveSessionScreen(
    state: ActiveSessionUi,
    showAbortDialog: Boolean,
    onAbortClick: () -> Unit,
    onAbortConfirm: () -> Unit,
    onAbortDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (state.totalCaptures > 0) {
        (state.capturesCompleted.toFloat() / state.totalCaptures).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Session active",
            style = MaterialTheme.typography.titleLarge,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Elapsed",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTimer(state.elapsedSeconds, state.totalSeconds),
                style = NumericReadoutStyle,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Captures",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${state.capturesCompleted} / ${state.totalCaptures}",
                style = NumericMediumStyle,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Temperature",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.temperatureCelsius?.let { "%.1f °C".format(it) } ?: "—",
                    style = NumericMediumStyle,
                )
            }
            ThermalStatusBadge(status = state.thermalStatus)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Cameras",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.cameras.forEach { camera ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = camera.id,
                        style = NumericMediumStyle,
                    )
                    FormatBadge(supportsRaw = camera.supportsRaw)
                }
            }
        }

        OutlinedButton(
            onClick = onAbortClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Abort")
        }
    }

    if (showAbortDialog) {
        AlertDialog(
            onDismissRequest = onAbortDismiss,
            title = { Text("Abort session?") },
            text = { Text("Capture will stop. Already-written files are kept.") },
            confirmButton = {
                TextButton(onClick = onAbortConfirm) {
                    Text("Abort")
                }
            },
            dismissButton = {
                TextButton(onClick = onAbortDismiss) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ThermalStatusBadge(status: Int) {
    val (label, color) = thermalBadgeStyle(status)
    Surface(
        color = color.copy(alpha = 0.2f),
        contentColor = color,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FormatBadge(supportsRaw: Boolean) {
    val text = if (supportsRaw) "RAW" else "YUV"
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (supportsRaw) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

private fun formatTimer(elapsed: Int, total: Int): String {
    fun fmt(sec: Int) = "%02d:%02d".format(sec / 60, sec % 60)
    return "${fmt(elapsed)} / ${fmt(total)}"
}

private fun thermalBadgeStyle(status: Int): Pair<String, Color> = when (status) {
    PowerManager.THERMAL_STATUS_NONE -> "Nominal" to Color(0xFF4CAF50)
    PowerManager.THERMAL_STATUS_LIGHT -> "Light" to Color(0xFF8BC34A)
    PowerManager.THERMAL_STATUS_MODERATE -> "Moderate" to Color(0xFFFFC107)
    PowerManager.THERMAL_STATUS_SEVERE -> "Severe" to Color(0xFFFF9800)
    PowerManager.THERMAL_STATUS_CRITICAL -> "Critical" to Color(0xFFFF5722)
    PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency" to Color(0xFFF44336)
    PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown" to Color(0xFFB71C1C)
    else -> "Unknown" to Color(0xFFA0A0A0)
}
