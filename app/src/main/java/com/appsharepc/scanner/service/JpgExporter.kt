package com.appsharepc.scanner.service

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object JpgExporter {

    fun prepareJpgFiles(
        imageFiles: List<File>,
        outputDir: File,
        baseFileName: String
    ): List<File> {
        outputDir.mkdirs()
        val results = mutableListOf<File>()

        if (imageFiles.size == 1) {
            val targetFile = File(outputDir, "$baseFileName.jpg")
            copyFile(imageFiles[0], targetFile)
            results.add(targetFile)
        } else {
            imageFiles.forEachIndexed { index, file ->
                val numStr = String.format("%02d", index + 1)
                val targetFile = File(outputDir, "${baseFileName}_$numStr.jpg")
                copyFile(file, targetFile)
                results.add(targetFile)
            }
        }

        return results
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                input.copyTo(output)
            }
        }
    }
}
