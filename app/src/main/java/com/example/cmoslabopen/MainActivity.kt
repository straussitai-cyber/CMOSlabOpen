package com.example.cmoslabopen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.cmoslabopen.measurement.ui.MainScreen
import com.example.cmoslabopen.measurement.ui.theme.CMOSlabOpenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CMOSlabOpenTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraPermissionGate(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        MainScreen(
                            onStartNewSession = {
                                // Wired up in Phase 11 when navigation is added.
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        content()
    } else {
        CameraPermissionRequest(
            modifier = modifier,
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
        )
    }
}

@Composable
private fun CameraPermissionRequest(
    modifier: Modifier = Modifier,
    onRequest: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "CMOSlabOpen needs camera access to capture measurements.")
        Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text(text = "Grant camera permission")
        }
    }
}
