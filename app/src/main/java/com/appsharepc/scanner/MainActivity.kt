package com.appsharepc.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.appsharepc.scanner.ui.screens.CameraCaptureScreen
import com.appsharepc.scanner.ui.screens.HomeScreen
import com.appsharepc.scanner.ui.theme.APPSharePCTheme
import com.appsharepc.scanner.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSendIntent(intent)

        setContent {
            APPSharePCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val showCamera by viewModel.showCamera.collectAsState()
                    val capturedPhotos by viewModel.capturedPhotos.collectAsState()

                    val cameraPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted) {
                            viewModel.setShowCamera(true)
                        } else {
                            Toast.makeText(
                                this,
                                "Cần cấp quyền máy ảnh để chụp tài liệu",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    if (showCamera) {
                        BackHandler {
                            viewModel.setShowCamera(false)
                        }

                        CameraCaptureScreen(
                            totalPhotoCount = capturedPhotos.size,
                            onPhotoCaptured = { photoFile ->
                                viewModel.addPhoto(photoFile)
                            },
                            onFinish = {
                                viewModel.setShowCamera(false)
                            }
                        )
                    } else {
                        HomeScreen(
                            viewModel = viewModel,
                            onRequestCameraPermission = {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    viewModel.setShowCamera(true)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                ?: intent.data?.toString()
            if (!text.isNullOrBlank()) {
                viewModel.setIncomingSharedLink(text)
            }
        }
    }
}
