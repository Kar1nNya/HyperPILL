package com.karin.hyperpill

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.karin.hyperpill.ui.MainScreen
import com.karin.hyperpill.ui.theme.HyperPILLTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PillViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.refreshDevices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HyperPILLTheme {
                val state by viewModel.uiState.collectAsState()
                val context = LocalContext.current
                val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                LaunchedEffect(permissionGranted) {
                    if (permissionGranted) viewModel.refreshDevices()
                }

                MainScreen(
                    state = state,
                    permissionGranted = permissionGranted,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            viewModel.refreshDevices()
                        }
                    },
                    onSelectDevice = { viewModel.connect(it) },
                    onDisconnect = { viewModel.disconnect() },
                    onRefreshDevices = { viewModel.refreshDevices() },
                    onRefreshBattery = { viewModel.refreshBattery() },
                    onSetGain = { viewModel.setGain(it) },
                    onSelectEq = { viewModel.selectEqSet(it) },
                    onSetOneBringTwo = { viewModel.setOneBringTwoState(it) },
                    onSetOneBringTwoTimeout = { viewModel.setOneBringTwoTimeout(it) },
                    onSetVoiceEnabled = { viewModel.setVoiceEnabled(it) },
                    onSetVoiceVolume = { viewModel.setVoiceVolume(it) },
                    onSpoofDevice = { viewModel.spoofDevice(it) }
                )
            }
        }
    }
}
