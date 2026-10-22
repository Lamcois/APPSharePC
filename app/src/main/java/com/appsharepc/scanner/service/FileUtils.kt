package com.appsharepc.scanner.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown_file"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex) ?: name
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size: Long = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, index.toDouble())) + " " + units[index]
    }

    fun formatDate(timestampMillis: Long): String {
        if (timestampMillis <= 0) return ""
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestampMillis))
    }

    fun getFileIcon(fileName: String): ImageVector {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> Icons.Default.PictureAsPdf
            "doc", "docx" -> Icons.Default.Description
            "xls", "xlsx", "csv" -> Icons.Default.TableChart
            "ppt", "pptx" -> Icons.Default.Slideshow
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> Icons.Default.Image
            "mp4", "mkv", "avi", "mov", "webm" -> Icons.Default.VideoFile
            "mp3", "m4a", "wav", "flac", "aac" -> Icons.Default.AudioFile
            "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
            "apk" -> Icons.Default.Android
            "txt", "log", "md", "json", "xml" -> Icons.Default.Article
            else -> Icons.Default.InsertDriveFile
        }
    }

    /**
     * Mở OutputStream để ghi file tải từ PC vào thư mục Downloads của điện thoại.
     * Sử dụng MediaStore cho Android 10+ (Q+) và File trực tiếp cho Android 8-9.
     */
    fun openDownloadOutputStream(context: Context, relativeFilePath: String): Pair<OutputStream, Uri?> {
        val cleanPath = relativeFilePath.replace('/', '\\').trim('\\')
        val fileName = if (cleanPath.contains('\\')) cleanPath.substringAfterLast('\\') else cleanPath
        val subDir = if (cleanPath.contains('\\')) cleanPath.substringBeforeLast('\\').replace('\\', '/') else ""

        val mimeType = getMimeType(fileName) ?: "application/octet-stream"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = if (subDir.isBlank()) {
                Environment.DIRECTORY_DOWNLOADS + "/APPSharePC"
            } else {
                Environment.DIRECTORY_DOWNLOADS + "/APPSharePC/" + subDir
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val itemUri = context.contentResolver.insert(collection, contentValues)
                ?: throw IllegalStateException("Không thể tạo mục tải về trong MediaStore: $relativeFilePath")

            val outputStream = context.contentResolver.openOutputStream(itemUri)
                ?: throw IllegalStateException("Không thể mở luồng ghi vào MediaStore: $relativeFilePath")

            return Pair(outputStream, itemUri)
        } else {
            val downloadDir = if (subDir.isBlank()) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "APPSharePC")
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "APPSharePC/$subDir")
            }.apply { mkdirs() }

            var targetFile = File(downloadDir, fileName)
            if (targetFile.exists()) {
                val nameWithoutExt = fileName.substringBeforeLast('.')
                val ext = fileName.substringAfterLast('.', "")
                val extPart = if (ext.isNotEmpty()) ".$ext" else ""
                targetFile = File(downloadDir, "${nameWithoutExt}_${System.currentTimeMillis()}$extPart")
            }

            val outputStream = FileOutputStream(targetFile)
            return Pair(outputStream, Uri.fromFile(targetFile))
        }
    }

    /**
     * Hoàn tất quá trình tải về, bỏ cờ IS_PENDING trên Android 10+.
     */
    fun finishDownload(context: Context, uri: Uri?) {
        if (uri == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, contentValues, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getMimeType(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    /**
     * Quét đệ quy toàn bộ thư mục được chọn qua OpenDocumentTree.
     * Giữ nguyên cấu trúc thư mục con khi gửi sang PC.
     */
    fun traverseDocumentFolder(
        context: Context,
        folderUri: Uri
    ): List<com.appsharepc.scanner.viewmodel.SelectedUploadItem> {
        val result = mutableListOf<com.appsharepc.scanner.viewmodel.SelectedUploadItem>()
        val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri) ?: return result
        val rootFolderName = rootDoc.name ?: "ThuMuc"

        fun scan(dir: androidx.documentfile.provider.DocumentFile, currentPath: String) {
            val children = dir.listFiles()
            for (child in children) {
                val childName = child.name ?: "unnamed"
                val relativePath = if (currentPath.isEmpty()) childName else "$currentPath\\$childName"
                if (child.isDirectory) {
                    scan(child, relativePath)
                } else if (child.isFile) {
                    val size = child.length()
                    result.add(
                        com.appsharepc.scanner.viewmodel.SelectedUploadItem(
                            uri = child.uri,
                            name = relativePath,
                            size = size,
                            formattedSize = formatFileSize(size)
                        )
                    )
                }
            }
        }

        scan(rootDoc, rootFolderName)
        return result
    }
}
