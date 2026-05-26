package com.example.cmoslabopen.measurement.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cmoslabopen.measurement.session.OutputMode
import com.example.cmoslabopen.measurement.session.SessionConfig
import com.example.cmoslabopen.measurement.session.SessionConfigViewModel

@Composable
fun SessionConfigScreen(
    viewModel: SessionConfigViewModel,
    onStart: (SessionConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val config by viewModel.config.collectAsState()
    val validationErrors by viewModel.validationErrors.collectAsState()

    val exposureMicros = config.exposureTimeNanos / 1_000L
    var exposureText by remember(exposureMicros) { mutableStateOf(exposureMicros.toString()) }
    var isoText by remember(config.iso) { mutableStateOf(config.iso.toString()) }
    var durationText by remember(config.measurementDurationSeconds) {
        mutableStateOf(config.measurementDurationSeconds.toString())
    }
    var intervalText by remember(config.captureIntervalSeconds) {
        mutableStateOf(config.captureIntervalSeconds.toString())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Session configuration",
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = exposureText,
            onValueChange = { text ->
                exposureText = text
                text.toLongOrNull()?.let { viewModel.setExposureMicros(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Exposure (µs)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = validationErrors.containsKey("exposure"),
            supportingText = {
                Column {
                    viewModel.exposureRangeHelperText()?.let { helper ->
                        Text(
                            text = helper,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    validationErrors["exposure"]?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            singleLine = true,
        )

        OutlinedTextField(
            value = isoText,
            onValueChange = { text ->
                isoText = text
                text.toIntOrNull()?.let { viewModel.setIso(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ISO") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = validationErrors.containsKey("iso"),
            supportingText = {
                Column {
                    viewModel.isoRangeHelperText()?.let { helper ->
                        Text(
                            text = helper,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    validationErrors["iso"]?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            singleLine = true,
        )

        OutlinedTextField(
            value = durationText,
            onValueChange = { text ->
                durationText = text
                text.toIntOrNull()?.let { viewModel.setMeasurementDurationSeconds(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Duration (s)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = validationErrors.containsKey("duration"),
            supportingText = {
                validationErrors["duration"]?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            singleLine = true,
        )

        OutlinedTextField(
            value = intervalText,
            onValueChange = { text ->
                intervalText = text
                text.toFloatOrNull()?.let { viewModel.setCaptureIntervalSeconds(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Interval (s)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = validationErrors.containsKey("interval"),
            supportingText = {
                validationErrors["interval"]?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            singleLine = true,
        )

        Text(
            text = "Output mode",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutputModeToggle(
                label = "DNG",
                selected = config.outputMode == OutputMode.DNG,
                onClick = { viewModel.setOutputMode(OutputMode.DNG) },
            )
            OutputModeToggle(
                label = "Histogram",
                selected = config.outputMode == OutputMode.HISTOGRAM,
                onClick = { viewModel.setOutputMode(OutputMode.HISTOGRAM) },
            )
        }

        Button(
            onClick = { onStart(config) },
            enabled = validationErrors.isEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Start session")
        }
    }
}

@Composable
private fun OutputModeToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            text = if (selected) "[$label]" else label,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
