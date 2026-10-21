package io.pagenote.android

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPaneDividerTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun draggingDividerChangesRatioAndCommitsOnRelease() {
    var dragFinished by mutableIntStateOf(0)
    composeRule.setContent {
      var percent by remember { mutableFloatStateOf(42f) }
      MaterialTheme {
        Column {
          Text(percent.toInt().toString(), Modifier.testTag("split_percent"))
          ReaderPaneDivider(
            leftPercent = percent,
            availableWidthPx = 1_000f,
            onPercentChange = { percent = it },
            onDragFinished = { dragFinished++ },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    composeRule.onNodeWithTag("reader_pane_divider").performTouchInput {
      down(center)
      moveTo(center + Offset(120f, 0f), delayMillis = 300)
      up()
    }

    composeRule.onNodeWithTag("split_percent").assertTextEquals("52")
    composeRule.runOnIdle { assertTrue(dragFinished > 0) }
  }
}
