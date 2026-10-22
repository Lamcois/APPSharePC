package com.appsharepc.scanner

import com.appsharepc.scanner.service.WordExporter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class WordExporterTest {

    @Test
    fun testWordExportCreatesValidDocxStructure() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_word_export").apply { mkdirs() }
        val dummyImg1 = File(tempDir, "img1.jpg").apply {
            FileOutputStream(this).use { it.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())) }
        }
        val dummyImg2 = File(tempDir, "img2.jpg").apply {
            FileOutputStream(this).use { it.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())) }
        }

        val outputFile = File(tempDir, "test_doc.docx")
        WordExporter.exportToDocx(listOf(dummyImg1, dummyImg2), outputFile)

        assertTrue("Output docx file should exist", outputFile.exists())
        assertTrue("Output docx file size should be > 0", outputFile.length() > 0)

        // Validate ZIP OpenXML entries
        ZipFile(outputFile).use { zip ->
            assertNotNull("[Content_Types].xml should exist", zip.getEntry("[Content_Types].xml"))
            assertNotNull("_rels/.rels should exist", zip.getEntry("_rels/.rels"))
            assertNotNull("word/document.xml should exist", zip.getEntry("word/document.xml"))
            assertNotNull("word/_rels/document.xml.rels should exist", zip.getEntry("word/_rels/document.xml.rels"))
            assertNotNull("word/media/image1.jpg should exist", zip.getEntry("word/media/image1.jpg"))
            assertNotNull("word/media/image2.jpg should exist", zip.getEntry("word/media/image2.jpg"))

            // Verify document.xml content contains drawing and page break
            val docXml = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText()
            assertTrue("document.xml should contain picture tag", docXml.contains("pic:pic"))
            assertTrue("document.xml should contain page break", docXml.contains("w:br w:type=\"page\""))
            assertTrue("document.xml should contain A4 size", docXml.contains("w:w=\"11906\" w:h=\"16838\""))
        }

        // Cleanup
        outputFile.delete()
        dummyImg1.delete()
        dummyImg2.delete()
        tempDir.delete()
    }
}
