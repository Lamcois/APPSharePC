package com.appsharepc.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
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

    // Android 8-9 không có MediaStore.Downloads: phải có quyền ghi bộ nhớ thì mới lưu được tệp tải về
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Chưa cấp quyền bộ nhớ: sẽ không tải được tệp về thư mục Download",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSendIntent(intent)
        requestLegacyStoragePermissionIfNeeded()

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
                                viewModel.addCapturedPhoto(photoFile)
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

    private fun requestLegacyStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
