package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BitmapRecycleAfterDrawFramesTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun recycleRequestedWithoutLooperIsScheduledOnMainLooper() {
    val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    val failure = AtomicReference<Throwable?>()
    val requestFinished = CountDownLatch(1)

    Thread {
      try {
        bitmap.recycleAfterDrawFrames()
      } catch (error: Throwable) {
        failure.set(error)
      } finally {
        requestFinished.countDown()
      }
    }.start()

    assertTrue("worker did not finish scheduling bitmap retirement", requestFinished.await(5, TimeUnit.SECONDS))
    assertNull("bitmap retirement must not depend on a worker Looper", failure.get())
    composeRule.waitUntil(timeoutMillis = 5_000) { bitmap.isRecycled }
  }
}
