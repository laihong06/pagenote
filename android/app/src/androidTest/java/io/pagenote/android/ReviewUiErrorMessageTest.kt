package io.pagenote.android

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewUiErrorMessageTest {
  private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext

  @Test
  fun invalidReviewInputUsesLocalizedFeedbackWithoutExposingValidationText() {
    val error = IllegalArgumentException("quote too large")

    listOf("en", "zh-CN").forEach { languageTag ->
      val context = contextFor(languageTag)
      val message = reviewCreateErrorMessage(context, error)

      assertEquals(context.getString(R.string.review_invalid_input), message)
      assertFalse(message.contains("quote too large"))
    }
  }

  @Test
  fun nonValidationFailureRetainsItsProviderDetail() {
    val context = contextFor("en")
    val error = IOException("provider unavailable")

    assertEquals(
      context.getString(R.string.review_save_failed, "provider unavailable"),
      reviewCreateErrorMessage(context, error),
    )
  }

  @Test
  fun incompleteReviewFileWarningUsesLocalizedFeedback() {
    val error = IncompleteReviewFileInitializationException(
      IOException("provider write failed"),
      IOException("provider delete failed"),
    )

    listOf("en", "zh-CN").forEach { languageTag ->
      val context = contextFor(languageTag)

      assertEquals(
        context.getString(R.string.review_incomplete_file_may_remain),
        reviewCreateErrorMessage(context, error),
      )
    }
  }

  private fun contextFor(languageTag: String): Context {
    val configuration = Configuration(targetContext.resources.configuration).apply {
      setLocale(Locale.forLanguageTag(languageTag))
    }
    return targetContext.createConfigurationContext(configuration)
  }
}
