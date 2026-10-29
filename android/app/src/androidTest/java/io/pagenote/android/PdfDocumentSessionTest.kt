package io.pagenote.android

import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.feature.ocr.OcrBlock
import io.pagenote.feature.ocr.OcrResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfDocumentSessionTest {
  @Test
  fun thumbnailIsBoundedAndDoesNotCloseTheReaderSession() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val source = File.createTempFile("pdf-thumbnail-test", ".pdf", context.cacheDir)
    source.writeBytes(minimalPdf())
    val session = PdfDocumentSession.open(context, Uri.fromFile(source), context.cacheDir)
    try {
      val thumbnail = session.renderThumbnail(0)
      assertTrue(maxOf(thumbnail.width, thumbnail.height) <= 360)
      assertEquals(android.graphics.Color.WHITE, thumbnail.getPixel(0, 0))
      thumbnail.recycle()
      session.render(0).recycle()
    } finally { session.close(); source.delete() }
  }

  @Test
  fun closeIsIdempotentForAnOpenedPdfRenderer() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val source = File(context.cacheDir, "pdf-close-once-test.pdf")
    source.writeBytes(minimalPdf())
    try {
      val session = PdfDocumentSession.open(context, Uri.fromFile(source), context.cacheDir)
      assertEquals(1, session.pageCount)

      session.close()
      session.close()
    } finally {
      source.delete()
    }
  }

  @Test
  fun pageTutorUsesPdfBoxTextAndOcrForMixedPagesOnAndroid14() = runBlocking {
    assumeTrue("This regression targets Android 14 only", Build.VERSION.SDK_INT == 34)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val source = File(context.cacheDir, "pdf-page-tutor-searchable-text-test.pdf")
    source.writeBytes(searchableAndMixedPdf())
    val session = PdfDocumentSession.open(context, Uri.fromFile(source), context.cacheDir)
    try {
      var ocrCalls = 0
      val loader = PageTutorSourceLoader(
        nativeText = { page -> session.extractText(page) },
        searchablePdfText = { page -> session.extractSelectableText(page)?.text },
        ocrText = { page ->
          ocrCalls += 1
          OcrResult(
            engine = "test",
            status = "ok",
            blocks = listOf(OcrBlock("image text ${page + 1}", 0.95, 10.0, 10.0, 200.0, 50.0)),
          )
        },
        hasBitmap = { page -> session.hasRasterImageContent(page) },
      )

      val searchable = loader.loadCurrent(0)
      val mixed = loader.loadCurrent(1)

      assertEquals("Android 14 searchable PDF text", searchable.text.trim())
      assertFalse(searchable.usedOcr)
      assertTrue(mixed.text.contains("Fig 1"))
      assertTrue(mixed.text.contains("image text 2"))
      assertTrue(mixed.usedOcr)
      assertTrue(mixed.warnings.any { it.contains("both", ignoreCase = true) })
      assertEquals(1, ocrCalls)
    } finally {
      session.close()
      source.delete()
    }
  }

  private fun minimalPdf(): ByteArray {
    val output = ByteArrayOutputStream()
    val offsets = mutableListOf(0)
    output.write("%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
    val objects = listOf(
      "<< /Type /Catalog /Pages 2 0 R >>",
      "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
      "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> /Contents 4 0 R >>",
      "<< /Length 0 >>\nstream\n\nendstream",
    )
    objects.forEachIndexed { index, body ->
      offsets += output.size()
      output.write("${index + 1} 0 obj\n$body\nendobj\n".toByteArray(Charsets.US_ASCII))
    }
    val xrefOffset = output.size()
    output.write("xref\n0 ${offsets.size}\n0000000000 65535 f \n".toByteArray(Charsets.US_ASCII))
    offsets.drop(1).forEach { offset ->
      output.write("%010d 00000 n \n".format(offset).toByteArray(Charsets.US_ASCII))
    }
    output.write(
      "trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
        .toByteArray(Charsets.US_ASCII),
    )
    return output.toByteArray()
  }

  private fun searchableAndMixedPdf(): ByteArray {
    val searchablePage = "BT /F1 18 Tf 72 720 Td (Android 14 searchable PDF text) Tj ET\n"
      .toByteArray(Charsets.US_ASCII)
    val mixedPage = (
      "q 100 0 0 100 72 600 cm /Im0 Do Q\n" +
        "BT /F1 18 Tf 72 720 Td (Fig 1) Tj ET\n"
      ).toByteArray(Charsets.US_ASCII)
    val output = ByteArrayOutputStream()
    val offsets = mutableListOf(0)
    output.write("%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
    val objects = listOf(
      ascii("<< /Type /Catalog /Pages 2 0 R >>"),
      ascii("<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>"),
      ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
        "/Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >>"),
      ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
        "/Resources << /Font << /F1 5 0 R >> /XObject << /Im0 8 0 R >> >> /Contents 7 0 R >>"),
      ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"),
      streamObject(searchablePage),
      streamObject(mixedPage),
      streamObject(
        byteArrayOf(0, 0, 0),
        "/Type /XObject /Subtype /Image /Width 1 /Height 1 /ColorSpace /DeviceRGB /BitsPerComponent 8",
      ),
    )
    objects.forEachIndexed { index, body ->
      offsets += output.size()
      output.write("${index + 1} 0 obj\n".toByteArray(Charsets.US_ASCII))
      output.write(body)
      output.write("\nendobj\n".toByteArray(Charsets.US_ASCII))
    }
    val xrefOffset = output.size()
    output.write("xref\n0 ${offsets.size}\n0000000000 65535 f \n".toByteArray(Charsets.US_ASCII))
    offsets.drop(1).forEach { offset ->
      output.write("%010d 00000 n \n".format(offset).toByteArray(Charsets.US_ASCII))
    }
    output.write(
      "trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
        .toByteArray(Charsets.US_ASCII),
    )
    return output.toByteArray()
  }

  private fun ascii(value: String) = value.toByteArray(Charsets.US_ASCII)

  private fun streamObject(content: ByteArray, dictionary: String = ""): ByteArray =
    ByteArrayOutputStream().apply {
      val entries = listOf(dictionary, "/Length ${content.size}").filter(String::isNotBlank).joinToString(" ")
      write(ascii("<< $entries >>\nstream\n"))
      write(content)
      write(ascii("\nendstream"))
    }.toByteArray()
}
