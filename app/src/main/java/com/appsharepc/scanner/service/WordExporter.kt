package com.appsharepc.scanner.service

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object WordExporter {

    // A4 dimensions and margins in EMUs (1 dxa = 635 EMUs, 1 pt = 12700 EMUs)
    // A4 page: 11906 x 16838 dxa. Printable area with 720 dxa (0.5 in) margins:
    private const val MAX_PRINTABLE_EMU_W = 6645910L
    private const val MAX_PRINTABLE_EMU_H = 9777730L

    fun exportToDocx(
        imageFiles: List<File>,
        outputFile: File,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): File {
        outputFile.parentFile?.mkdirs()

        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // 1. [Content_Types].xml
                zos.putNextEntry(ZipEntry("[Content_Types].xml"))
                zos.write(getContentTypesXml().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. _rels/.rels
                zos.putNextEntry(ZipEntry("_rels/.rels"))
                zos.write(getRootRelsXml().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 3. word/_rels/document.xml.rels
                zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
                zos.write(getDocumentRelsXml(imageFiles.size).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 4. word/media/imageX.jpg
                imageFiles.forEachIndexed { index, file ->
                    onProgress?.invoke(index + 1, imageFiles.size)
                    val imgIndex = index + 1
                    zos.putNextEntry(ZipEntry("word/media/image$imgIndex.jpg"))
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }

                // 5. word/document.xml
                zos.putNextEntry(ZipEntry("word/document.xml"))
                zos.write(getDocumentXml(imageFiles).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }

        return outputFile
    }

    private fun getContentTypesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private fun getRootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private fun getDocumentRelsXml(imageCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..imageCount) {
            sb.append("""
  <Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image$i.jpg"/>""")
        }
        sb.append("""
</Relationships>""")
        return sb.toString()
    }

    private fun getDocumentXml(imageFiles: List<File>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:body>""")

        imageFiles.forEachIndexed { index, file ->
            val imgIndex = index + 1
            val (origW, origH) = try { ImageUtils.getImageDimensions(file) } catch (_: Throwable) { Pair(1200, 1600) }
            val validW = if (origW > 0) origW else 1200
            val validH = if (origH > 0) origH else 1600

            val scaleW = MAX_PRINTABLE_EMU_W.toDouble() / validW
            val scaleH = MAX_PRINTABLE_EMU_H.toDouble() / validH
            val scale = minOf(scaleW, scaleH)

            val cx = (validW * scale).toLong()
            val cy = (validH * scale).toLong()

            sb.append("""
    <w:p>
      <w:pPr>
        <w:jc w:val="center"/>
      </w:pPr>
      <w:r>
        <w:drawing>
          <wp:inline distT="0" distB="0" distL="0" distR="0">
            <wp:extent cx="$cx" cy="$cy"/>
            <wp:docPr id="$imgIndex" name="Picture $imgIndex"/>
            <wp:cNvGraphicFramePr>
              <a:graphicFrameLocks noChangeAspect="1"/>
            </wp:cNvGraphicFramePr>
            <a:graphic>
              <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                <pic:pic>
                  <pic:nvPicPr>
                    <pic:cNvPr id="$imgIndex" name="Picture $imgIndex"/>
                    <pic:cNvPicPr/>
                  </pic:nvPicPr>
                  <pic:blipFill>
                    <a:blip r:embed="rId$imgIndex"/>
                    <a:stretch>
                      <a:fillRect/>
                    </a:stretch>
                  </pic:blipFill>
                  <pic:spPr>
                    <a:xfrm>
                      <a:off x="0" y="0"/>
                      <a:ext cx="$cx" cy="$cy"/>
                    </a:xfrm>
                    <a:prstGeom prst="rect">
                      <a:avLst/>
                    </a:prstGeom>
                  </pic:spPr>
                </pic:pic>
              </a:graphicData>
            </a:graphic>
          </wp:inline>
        </w:drawing>
      </w:r>
    </w:p>""")

            // Add page break if not the last image
            if (index < imageFiles.size - 1) {
                sb.append("""
    <w:p>
      <w:r>
        <w:br w:type="page"/>
      </w:r>
    </w:p>""")
            }
        }

        // Standard A4 page setup: 11906 x 16838 dxa, 720 dxa (0.5 in) margins
        sb.append("""
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720" w:header="720" w:footer="720" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>""")

        return sb.toString()
    }
}
