package io.pagenote.android

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.pagenote.core.project.PageNotes
import io.pagenote.core.reader.PdfPageText
import io.pagenote.core.reader.PdfTextBounds
import io.pagenote.core.reader.PdfTextGlyph
import java.io.File
import java.io.Writer
import java.util.Collections
import java.util.IdentityHashMap

internal data class PdfBoxPageContent(
  val selectableText: PdfPageText?,
  val hasRasterImages: Boolean,
)

internal object PdfBoxTextExtractor {
  @Volatile
  private var initialized = false

  @Synchronized
  fun inspect(context: Context, source: File, pageIndex: Int): PdfBoxPageContent? {
    if (!source.isFile || pageIndex < 0) return null
    if (!initialized) {
      PDFBoxResourceLoader.init(context.applicationContext)
      initialized = true
    }

    return runCatching {
      PDDocument.load(source).use { document ->
        if (pageIndex >= document.numberOfPages) return null
        val page = document.getPage(pageIndex)
        val cropBox = page.cropBox
        val rotated = page.rotation % 180 != 0
        val hasRasterImages = hasRasterImages(
          resources = page.resources,
          visitedForms = Collections.newSetFromMap(IdentityHashMap()),
        )
        val stripper = PositionedTextStripper(pageIndex + 1)
        stripper.writeText(document, Writer.nullWriter())
        val text = stripper.pageText()
        val selectableText = if (text.isBlank() || stripper.glyphs.isEmpty()) {
          null
        } else {
          PdfPageText(
            text = text,
            revision = PageNotes.revision(text),
            pageWidth = (if (rotated) cropBox.height else cropBox.width).toDouble(),
            pageHeight = (if (rotated) cropBox.width else cropBox.height).toDouble(),
            glyphs = stripper.glyphs.toList(),
          )
        }
        PdfBoxPageContent(selectableText, hasRasterImages)
      }
    }.getOrNull()
  }

  private fun hasRasterImages(
    resources: PDResources?,
    visitedForms: MutableSet<PDFormXObject>,
  ): Boolean {
    if (resources == null) return false
    for (name in resources.xObjectNames) {
      when (val xObject = resources.getXObject(name)) {
        is PDImageXObject -> return true
        is PDFormXObject -> if (visitedForms.add(xObject) &&
          hasRasterImages(xObject.resources, visitedForms)
        ) return true
      }
    }
    return false
  }

  private class PositionedTextStripper(private val targetPage: Int) : PDFTextStripper() {
    private val output = StringBuilder()
    val glyphs = mutableListOf<PdfTextGlyph>()

    init {
      startPage = targetPage
      endPage = targetPage
      sortByPosition = true
      lineSeparator = "\n"
    }

    fun pageText(): String = output.toString()

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
      val lineStart = output.length
      output.append(text)
      var cursor = 0
      for (position in textPositions) {
        val value = position.unicode.orEmpty()
        if (value.isEmpty()) continue
        val offset = text.indexOf(value, cursor)
        if (offset < 0) continue
        val bounds = PdfTextBounds(
          left = position.xDirAdj.toDouble(),
          top = position.yDirAdj.toDouble(),
          right = (position.xDirAdj + position.widthDirAdj).toDouble(),
          bottom = (position.yDirAdj + position.heightDir).toDouble(),
        )
        if (bounds.isUsable) {
          glyphs += PdfTextGlyph(lineStart + offset, lineStart + offset + value.length, bounds)
        }
        cursor = offset + value.length
      }
    }

    override fun writeWordSeparator() {
      output.append(wordSeparator)
    }

    override fun writeLineSeparator() {
      output.append(lineSeparator)
    }
  }
}
