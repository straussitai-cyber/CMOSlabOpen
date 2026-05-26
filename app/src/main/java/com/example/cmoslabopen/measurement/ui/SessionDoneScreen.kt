package com.example.cmoslabopen.measurement.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cmoslabopen.measurement.ui.theme.NumericMediumStyle

@Composable
fun SessionDoneScreen(
    state: DoneSessionUi,
    onOpenSessionFolder: () -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (state.wasCancelled) "Session aborted" else "Session complete",
            style = MaterialTheme.typography.titleLarge,
        )

        SummaryRow("Session ID", state.sessionId)
        SummaryRow("Captures", state.totalCaptures.toString())
        SummaryRow("Output mode", state.outputMode)
        SummaryRow("Duration", "${state.durationSeconds} s")

        Text(
            text = "Temperature",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SummaryRow("Min", formatTemp(state.temperatureMin))
        SummaryRow("Max", formatTemp(state.temperatureMax))
        SummaryRow("Mean", formatTemp(state.temperatureMean))

        OutlinedButton(
            onClick = onOpenSessionFolder,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open session folder")
        }

        Button(
            onClick = onNewSession,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("New session")
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = NumericMediumStyle,
        )
    }
}

private fun formatTemp(value: Float?): String =
    value?.let { "%.1f °C".format(it) } ?: "—"
