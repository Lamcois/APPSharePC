package com.appsharepc.scanner.ui.screens

import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.appsharepc.scanner.service.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CameraCaptureScreen(
    totalPhotoCount: Int,
    onPhotoCaptured: (File) -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_AUTO) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode)
            .build()
    }
    var isCapturing by remember { mutableStateOf(false) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(lensFacing, flashMode) {
        // get() chặn luồng cho tới khi CameraX khởi tạo xong -> chờ ở luồng IO, không treo giao diện
        val cameraProvider = withContext(Dispatchers.IO) { cameraProviderFuture.get() }
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        imageCapture.flashMode = flashMode

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Top Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close / Back button
            IconButton(
                onClick = onFinish,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = Color.White
                )
            }

            // Photo Count Badge (Hiển thị chính xác tổng số ảnh đã chụp)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tổng: $totalPhotoCount ảnh",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Flash Mode Toggle
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                        else -> ImageCapture.FLASH_MODE_AUTO
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                        else -> Icons.Default.FlashAuto
                    },
                    contentDescription = "Đèn flash",
                    tint = Color.White
                )
            }
        }

        // Bottom Capture Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Switch Camera Lens (Front/Back)
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Đổi camera",
                        tint = Color.White
                    )
                }

                // Continuous Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (isCapturing) Color.Gray else Color.White)
                        .clickable(enabled = !isCapturing) {
                            isCapturing = true
                            val photoFile = ImageUtils.createTempImageFile(context)
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            val executor = ContextCompat.getMainExecutor(context)

                            imageCapture.takePicture(
                                outputOptions,
                                executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        isCapturing = false
                                        // Chỉ thêm file vào ViewModel (tăng đúng 1 ảnh).
                                        // Việc xoay ảnh theo EXIF chạy nền trong ViewModel để không treo camera.
                                        onPhotoCaptured(photoFile)
                                    }

                                    override fun onError(exc: ImageCaptureException) {
                                        isCapturing = false
                                        exc.printStackTrace()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                }

                // Done Button (Finish capture session)
                Button(
                    onClick = onFinish,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "Xong",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
