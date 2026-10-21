package io.pagenote.feature.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineOcrEngineTest {
  @Test
  fun bundledModelsRecognizeChineseAndEnglishWithoutChangingInputImage() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val bitmap = Bitmap.createBitmap(1200, 180, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    canvas.drawText(
      "离线 OCR Test 123",
      24f,
      125f,
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 76f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
      },
    )
    val originalPixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(originalPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    assertTrue(originalPixels.any { it != Color.WHITE })

    val engine = OfflineOcrEngine.create(context)
    try {
      val result = engine.recognize(bitmap)
      val recognized = OcrResultPolicy.textOf(result)
      assertFalse(result.warnings.joinToString(" "), result.status == "failed")
      assertTrue("Expected Chinese in: $recognized", recognized.contains("离线"))
      assertTrue("Expected English in: $recognized", recognized.lowercase().contains("test"))

      val afterPixels = IntArray(bitmap.width * bitmap.height)
      bitmap.getPixels(afterPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
      assertTrue(originalPixels.contentEquals(afterPixels))
    } finally {
      engine.close()
      bitmap.recycle()
    }
  }

  @Test
  fun recycledBitmapReturnsAnExplicitFailure() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val engine = OfflineOcrEngine.create(context)
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { recycle() }
    try {
      val result = engine.recognize(bitmap)
      assertTrue(OcrResultPolicy.needsVisualReview(result))
      assertTrue(result.warnings.single().contains("recycled"))
    } finally {
      engine.close()
    }
  }

  @Test
  fun regionRecognitionKeepsCallerBitmapAndReturnsPageRelativeBounds() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val bitmap = Bitmap.createBitmap(1200, 180, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    canvas.drawText(
      "离线 OCR Test 123",
      24f,
      125f,
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 76f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
      },
    )
    val originalPixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(originalPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val engine = OfflineOcrEngine.create(context)
    try {
      val result = engine.recognize(bitmap, NormalizedOcrRegion(0, 0, 600, 1000))

      assertFalse(result.warnings.joinToString(" "), result.status == "failed")
      assertTrue(OcrResultPolicy.textOf(result).contains("离线"))
      assertTrue(result.blocks.isNotEmpty())
      assertTrue(result.blocks.all { it.x + it.width <= 600.0 })
      assertFalse(bitmap.isRecycled)
      val afterPixels = IntArray(bitmap.width * bitmap.height)
      bitmap.getPixels(afterPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
      assertTrue(originalPixels.contentEquals(afterPixels))
    } finally {
      engine.close()
      bitmap.recycle()
    }
  }

  @Test
  fun outsideRegionReturnsAnExplicitFailureWithoutRecyclingInput() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val engine = OfflineOcrEngine.create(context)
    val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
    try {
      val result = engine.recognize(bitmap, NormalizedOcrRegion(0, 0, 1001, 1000))

      assertTrue(result.status == "failed")
      assertTrue(result.warnings.single().contains("region"))
      assertFalse(bitmap.isRecycled)
    } finally {
      engine.close()
      bitmap.recycle()
    }
  }

  @Test
  fun fullPageRegionLeavesCallerBitmapPixelsAndOwnershipUnchanged() = runBlocking {
    val bitmap = createOwnershipBitmap()
    val originalPixels = pixelsOf(bitmap)
    val engine = OfflineOcrEngine { OcrResult(engine = "fixture", status = "ok") }

    try {
      val result = engine.recognize(bitmap, NormalizedOcrRegion(0, 0, 1000, 1000))

      assertEquals("ok", result.status)
      assertCallerBitmapUnchanged(bitmap, originalPixels)
    } finally {
      engine.close()
      bitmap.recycle()
    }
  }

  @Test
  fun inferenceFailureRecyclesTemporaryCropAndPreservesCallerBitmap() = runBlocking {
    val bitmap = createOwnershipBitmap()
    val originalPixels = pixelsOf(bitmap)
    var inferredBitmap: Bitmap? = null
    var inferredCropSize: Pair<Int, Int>? = null
    val engine = OfflineOcrEngine { input ->
      inferredBitmap = input
      inferredCropSize = input.width to input.height
      throw IllegalStateException("fixture inference failure")
    }

    try {
      val result = engine.recognize(bitmap, NormalizedOcrRegion(250, 250, 750, 750))
      val crop = checkNotNull(inferredBitmap) { "inference did not receive a crop" }

      assertEquals("failed", result.status)
      assertTrue(result.warnings.single().contains("fixture inference failure"))
      assertNotSame(bitmap, crop)
      assertEquals(40 to 30, inferredCropSize)
      assertTrue("temporary crop should be recycled", crop.isRecycled)
      assertCallerBitmapUnchanged(bitmap, originalPixels)
    } finally {
      engine.close()
      inferredBitmap?.let { if (!it.isRecycled) it.recycle() }
      bitmap.recycle()
    }
  }

  @Test
  fun cancellationRecyclesTemporaryCropPreservesCallerAndPropagates() = runBlocking {
    val bitmap = createOwnershipBitmap()
    val originalPixels = pixelsOf(bitmap)
    var inferredBitmap: Bitmap? = null
    var inferredCropSize: Pair<Int, Int>? = null
    val engine = OfflineOcrEngine { input ->
      inferredBitmap = input
      inferredCropSize = input.width to input.height
      throw CancellationException("fixture inference cancellation")
    }

    try {
      try {
        engine.recognize(bitmap, NormalizedOcrRegion(250, 250, 750, 750))
        fail("inference cancellation should propagate")
      } catch (cancelled: CancellationException) {
        assertEquals("fixture inference cancellation", cancelled.message)
      }
      val crop = checkNotNull(inferredBitmap) { "inference did not receive a crop" }

      assertNotSame(bitmap, crop)
      assertEquals(40 to 30, inferredCropSize)
      assertTrue("temporary crop should be recycled", crop.isRecycled)
      assertCallerBitmapUnchanged(bitmap, originalPixels)
    } finally {
      engine.close()
      inferredBitmap?.let { if (!it.isRecycled) it.recycle() }
      bitmap.recycle()
    }
  }

  private fun createOwnershipBitmap(): Bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply {
    eraseColor(Color.WHITE)
    Canvas(this).drawRect(8f, 9f, 72f, 51f, Paint().apply { color = Color.BLACK })
  }

  private fun pixelsOf(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
    bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
  }

  private fun assertCallerBitmapUnchanged(bitmap: Bitmap, originalPixels: IntArray) {
    assertFalse("caller bitmap should remain unrecycled", bitmap.isRecycled)
    assertArrayEquals(originalPixels, pixelsOf(bitmap))
  }
}
