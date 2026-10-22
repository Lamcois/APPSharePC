package com.appsharepc.scanner.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    // Standard A4 dimensions in PostScript points (72 DPI)
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val MARGIN = 20f

    fun exportToPdf(
        imageFiles: List<File>,
        outputFile: File,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): File {
        val pdfDocument = PdfDocument()
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        try {
            imageFiles.forEachIndexed { index, imageFile ->
                onProgress?.invoke(index + 1, imageFiles.size)

                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                // Load bitmap efficiently for A4 (scale down if unnecessarily huge)
                val bitmap = decodeOptimalBitmap(imageFile, PAGE_WIDTH * 2, PAGE_HEIGHT * 2)

                if (bitmap != null) {
                    val availWidth = PAGE_WIDTH - (MARGIN * 2)
                    val availHeight = PAGE_HEIGHT - (MARGIN * 2)

                    val imgWidth = bitmap.width.toFloat()
                    val imgHeight = bitmap.height.toFloat()

                    val scale = minOf(availWidth / imgWidth, availHeight / imgHeight)
                    val drawWidth = imgWidth * scale
                    val drawHeight = imgHeight * scale

                    // Center on page
                    val left = MARGIN + (availWidth - drawWidth) / 2f
                    val top = MARGIN + (availHeight - drawHeight) / 2f
                    val dstRect = RectF(left, top, left + drawWidth, top + drawHeight)
                    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)

                    canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
                    bitmap.recycle()
                }

                pdfDocument.finishPage(page)
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        return outputFile
    }

    private fun decodeOptimalBitmap(file: File, maxW: Int, maxH: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        var sampleSize = 1
        while ((options.outWidth / sampleSize) > maxW || (options.outHeight / sampleSize) > maxH) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inJustDecodeBounds = false
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }
}
