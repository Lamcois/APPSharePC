package com.appsharepc.scanner.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageUtils {

    fun createTempImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        val storageDir = File(context.cacheDir, "photos").apply { mkdirs() }
        return File(storageDir, "IMG_$timeStamp.jpg")
    }

    fun copyUriToTempFile(context: Context, uri: Uri): File? {
        return try {
            val tempFile = createTempImageFile(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            // Normalize orientation
            normalizeImageOrientation(tempFile)
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun normalizeImageOrientation(file: File) {
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationDegrees != 0f) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                FileOutputStream(file).use { out ->
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                bitmap.recycle()
                rotatedBitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        var inSampleSize = 1
        val height = options.outHeight
        val width = options.outWidth

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    fun getImageDimensions(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return Pair(options.outWidth, options.outHeight)
    }
}
