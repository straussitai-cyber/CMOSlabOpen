package com.example.cmoslabopen.measurement.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import android.hardware.camera2.CameraCharacteristics
import com.example.cmoslabopen.measurement.camera.CameraEnumerator

@Composable
fun CameraSelectScreen(
    cameras: List<CameraEnumerator.CameraInfo>,
    selectedIds: Set<String>,
    multiCameraMode: Boolean,
    canOpenConcurrently: (Set<String>) -> Boolean,
    onMultiCameraModeChange: (Boolean) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Select cameras",
            style = MaterialTheme.typography.titleLarge,
        )

        ModeToggle(
            multiCameraMode = multiCameraMode,
            onChange = onMultiCameraModeChange,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(cameras, key = { it.id }) { info ->
                val isSelected = info.id in selectedIds
                val candidate = selectedIds + info.id
                val rowEnabled = when {
                    isSelected -> true
                    !multiCameraMode -> true
                    candidate.size == 1 -> true
                    else -> canOpenConcurrently(candidate)
                }
                CameraRow(
                    info = info,
                    selected = isSelected,
                    enabled = rowEnabled,
                    onCheckedChange = { checked ->
                        val newSelection = computeNewSelection(
                            current = selectedIds,
                            id = info.id,
                            checked = checked,
                            multiCameraMode = multiCameraMode,
                        )
                        onSelectionChange(newSelection)
                    },
                )
            }
        }

        Button(
            onClick = onContinue,
            enabled = selectedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun ModeToggle(
    multiCameraMode: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeButton(
            label = "Single camera",
            selected = !multiCameraMode,
            onClick = { onChange(false) },
        )
        ModeButton(
            label = "Multi-camera",
            selected = multiCameraMode,
            onClick = { onChange(true) },
        )
    }
}

@Composable
private fun ModeButton(
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

@Composable
private fun CameraRow(
    info: CameraEnumerator.CameraInfo,
    selected: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Camera ${info.id}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = facingLabel(info.facing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FormatBadge(supportsRaw = info.supportsRaw)
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

private fun computeNewSelection(
    current: Set<String>,
    id: String,
    checked: Boolean,
    multiCameraMode: Boolean,
): Set<String> = when {
    checked && multiCameraMode -> current + id
    checked && !multiCameraMode -> setOf(id)
    !checked -> current - id
    else -> current
}

private fun facingLabel(facing: Int): String = when (facing) {
    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
    CameraCharacteristics.LENS_FACING_BACK -> "Back"
    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
    else -> "Unknown"
}
