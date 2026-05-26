package com.example.cmoslabopen.measurement.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Landing screen. From here the operator starts a new session
 * (-> SessionConfigScreen) or reviews the device's camera inventory.
 * Full implementation: Phase 11.
 */
@Composable
fun MainScreen(
    onStartNewSession: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "CMOSlab Open",
            style = MaterialTheme.typography.titleLarge,
        )
        Button(
            onClick = onStartNewSession,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text("New session")
        }
    }
}
