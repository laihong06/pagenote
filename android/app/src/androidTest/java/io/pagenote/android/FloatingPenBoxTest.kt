package io.pagenote.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingPenBoxTest {
  @get:Rule val compose = createComposeRule()

  @Before fun resetPreferences() {
    InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("reader_floating_pen_box", 0).edit().clear().commit()
  }

  @Test fun selectsToolAndOpensWidthSettings() {
    var tool by mutableStateOf(PenCanvasTool.PEN)
    var style by mutableStateOf(PenStyle.DEFAULT)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          FloatingPenBox(tool, null, style, true, true,
            onTool = { tool = it }, onShape = {},
            onColor = { style = requireNotNull(PenStyle.create(it, style.width)) },
            onWidth = { style = requireNotNull(PenStyle.create(style.color, it)) }, onCustomColor = {})
        }
      }
    }
    compose.onNodeWithTag("floating_pen_box").assertIsDisplayed()
    compose.onNodeWithTag("reader_pen_ballpoint").performClick()
    compose.runOnIdle { assertEquals(PenCanvasTool.BALLPOINT, tool) }
    compose.onNodeWithTag("reader_pen_ballpoint").performClick()
    compose.onNodeWithTag("reader_pen_style_width_slider").assertIsDisplayed()
  }

  @Test fun dragsToOtherSideAndRestoresPosition() {
    var generation by mutableStateOf(0)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          key(generation) { FloatingPenBox(PenCanvasTool.PEN, null, PenStyle.DEFAULT, true, true,
            onTool = {}, onShape = {}, onColor = {}, onWidth = {}, onCustomColor = {}) }
        }
      }
    }
    val before = compose.onNodeWithTag("floating_pen_box_handle").fetchSemanticsNode().boundsInRoot.left
    compose.onNodeWithTag("floating_pen_box_handle").performTouchInput {
      swipe(center, center + Offset(800f, 0f), 800)
    }
    compose.waitForIdle()
    val after = compose.onNodeWithTag("floating_pen_box_handle").fetchSemanticsNode().boundsInRoot.left
    assertTrue("drag should move the box to the right", after > before + 50f)
    val saved = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("reader_floating_pen_box", 0)
    assertEquals("RIGHT", saved.getString("edge", null))
    compose.runOnIdle { generation++ }
    val restored = compose.onNodeWithTag("floating_pen_box_handle").fetchSemanticsNode().boundsInRoot.left
    assertTrue("recreated box should stay on right", restored > before + 50f)
  }

  @Test fun switchingPensRestoresTheirOwnColors() {
    var tool by mutableStateOf(PenCanvasTool.PEN)
    var style by mutableStateOf(PenStyle.DEFAULT)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          FloatingPenBox(tool, null, style, true, true,
            onTool = { tool = it }, onShape = {},
            onColor = { style = requireNotNull(PenStyle.create(it, style.width)) },
            onWidth = { style = requireNotNull(PenStyle.create(style.color, it)) }, onCustomColor = {})
        }
      }
    }
    compose.onNodeWithTag("reader_pen_pen").performClick()
    compose.onNodeWithTag("reader_pen_color_185abd").performClick()
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    compose.onNodeWithTag("reader_pen_ballpoint").performClick()
    compose.onNodeWithTag("reader_pen_ballpoint").performClick()
    compose.onNodeWithTag("reader_pen_color_2e7d32").performClick()
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    compose.onNodeWithTag("reader_pen_pen").performClick()
    compose.runOnIdle { assertEquals("#185abd", style.color) }
  }

  @Test fun longDragReordersPenSlotsWithoutMovingTheBox() {
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          FloatingPenBox(PenCanvasTool.PEN, null, PenStyle.DEFAULT, true, true,
            onTool = {}, onShape = {}, onColor = {}, onWidth = {}, onCustomColor = {})
        }
      }
    }
    compose.onNodeWithTag("reader_pen_pen").performTouchInput {
      down(center)
      advanceEventTime(700)
      moveBy(Offset(0f, 250f))
      up()
    }
    compose.waitForIdle()
    val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("reader_floating_pen_box", 0)
    assertTrue("order should be saved", prefs.getString("order", "")!!.startsWith("BALLPOINT"))
    assertEquals("LEFT", prefs.getString("edge", "LEFT"))
  }

  @Test fun duplicatePenCreatesIndependentSlot() {
    var tool by mutableStateOf(PenCanvasTool.PEN)
    var style by mutableStateOf(PenStyle.DEFAULT)
    var generation by mutableStateOf(0)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          key(generation) { FloatingPenBox(tool, null, style, true, true,
            onTool = { tool = it }, onShape = {},
            onColor = { style = requireNotNull(PenStyle.create(it, style.width)) },
            onWidth = { style = requireNotNull(PenStyle.create(style.color, it)) }, onCustomColor = {}) }
        }
      }
    }
    compose.onNodeWithTag("reader_pen_pen").performClick()
    compose.onNodeWithTag("floating_pen_duplicate").performClick()
    compose.onAllNodesWithTag("reader_pen_pen").assertCountEquals(2)
    compose.onAllNodesWithTag("reader_pen_pen")[1].performClick()
    compose.onNodeWithTag("reader_pen_color_185abd").performClick()
    InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    compose.onAllNodesWithTag("reader_pen_pen")[0].performClick()
    compose.runOnIdle { assertEquals("#dc3545", style.color) }
    compose.onAllNodesWithTag("reader_pen_pen")[1].performClick()
    compose.runOnIdle { assertEquals("#185abd", style.color) }
    compose.runOnIdle { generation++ }
    compose.onAllNodesWithTag("reader_pen_pen").assertCountEquals(2)
    compose.runOnIdle { assertEquals("#185abd", style.color) }
  }

  @Test fun longPenBoxScrollsInsideSmallReader() {
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(320.dp, 160.dp)) {
          FloatingPenBox(PenCanvasTool.PEN, null, PenStyle.DEFAULT, true, true,
            onTool = {}, onShape = {}, onColor = {}, onWidth = {}, onCustomColor = {})
        }
      }
    }
    compose.onNodeWithTag("floating_pen_box").performTouchInput {
      swipe(Offset(center.x, center.y * 1.8f), Offset(center.x, 20f), 500)
      swipe(Offset(center.x, center.y * 1.8f), Offset(center.x, 20f), 500)
    }
    compose.onNodeWithTag("reader_pen_shapes").assertIsDisplayed()
  }

  @Test fun mountingInSelectionModePreservesToolAndOffersStyle() {
    var toolChanges = 0
    var selectedColor = ""
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          FloatingPenBox(PenCanvasTool.SELECT, null, PenStyle.DEFAULT, true, true,
            onTool = { toolChanges++ }, onShape = {}, onColor = { selectedColor = it },
            onWidth = {}, onCustomColor = {})
        }
      }
    }
    compose.runOnIdle { assertEquals(0, toolChanges) }
    compose.onNodeWithTag("reader_pen_style").performClick()
    compose.onNodeWithTag("reader_pen_color_185abd").performClick()
    compose.runOnIdle {
      assertEquals(0, toolChanges)
      assertEquals("#185abd", selectedColor)
    }
  }

  @Test fun externalToolChangeSynchronizesSelectedPen() {
    val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("reader_floating_pen_box", 0)
    prefs.edit().putString("HIGHLIGHTER_color", "#185abd").putFloat("HIGHLIGHTER_width", 4f).commit()
    var tool by mutableStateOf(PenCanvasTool.PEN)
    var style by mutableStateOf(PenStyle.DEFAULT)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          FloatingPenBox(tool, null, style, true, true,
            onTool = { tool = it }, onShape = {}, onColor = {}, onWidth = {}, onCustomColor = {},
            onRestoreStyle = { color, width -> style = requireNotNull(PenStyle.create(color, width)) })
        }
      }
    }
    compose.runOnIdle { tool = PenCanvasTool.HIGHLIGHTER }
    compose.waitForIdle()
    compose.runOnIdle { assertEquals("#185abd", style.color); assertEquals(4.0, style.width, .001) }
    compose.onNodeWithTag("reader_pen_highlighter").performClick()
    compose.onNodeWithTag("reader_pen_style_width_slider").assertIsDisplayed()
  }

  @Test fun externalToolChangeRestoresMissingPenSlot() {
    val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("reader_floating_pen_box", 0)
    prefs.edit().putString("order", "PEN").commit()
    var tool by mutableStateOf(PenCanvasTool.PEN)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          FloatingPenBox(tool, null, PenStyle.DEFAULT, true, true,
            onTool = { tool = it }, onShape = {}, onColor = {}, onWidth = {}, onCustomColor = {})
        }
      }
    }
    compose.runOnIdle { tool = PenCanvasTool.HIGHLIGHTER }
    compose.onNodeWithTag("reader_pen_highlighter").assertIsDisplayed()
    compose.runOnIdle { assertTrue(prefs.getString("order", "")!!.contains("HIGHLIGHTER")) }
  }

  @Test fun returningFromSelectionOrEraserRestoresPenStyle() {
    val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("reader_floating_pen_box", 0)
    prefs.edit().putString("PEN_color", "#185abd").putFloat("PEN_width", 2f).commit()
    var tool by mutableStateOf(PenCanvasTool.PEN)
    var style by mutableStateOf(PenStyle.DEFAULT)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          FloatingPenBox(tool, null, style, true, true,
            onTool = { tool = it }, onShape = {}, onColor = {}, onWidth = {}, onCustomColor = {},
            onRestoreStyle = { color, width -> style = requireNotNull(PenStyle.create(color, width)) })
        }
      }
    }
    listOf(PenCanvasTool.SELECT, PenCanvasTool.ERASER).forEach { temporaryTool ->
      compose.runOnIdle { tool = temporaryTool }
      compose.runOnIdle { style = requireNotNull(PenStyle.create("#dc3545", 8.0)) }
      compose.runOnIdle { tool = PenCanvasTool.PEN }
      compose.runOnIdle { assertEquals("#185abd", style.color); assertEquals(2.0, style.width, .001) }
    }
  }

  @Test fun penBoxGroupsKeepSeparatePenStyles() {
    var tool by mutableStateOf(PenCanvasTool.PEN)
    var style by mutableStateOf(PenStyle.DEFAULT)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          FloatingPenBox(tool, null, style, true, true,
            onTool = { tool = it }, onShape = {},
            onColor = { style = requireNotNull(PenStyle.create(it, style.width)) },
            onWidth = { style = requireNotNull(PenStyle.create(style.color, it)) }, onCustomColor = {},
            onRestoreStyle = { color, width -> style = requireNotNull(PenStyle.create(color, width)) })
        }
      }
    }
    compose.onNodeWithTag("floating_pen_groups").performClick()
    compose.onNodeWithTag("floating_pen_group_add").performClick()
    compose.onNodeWithTag("reader_pen_pen").performClick()
    compose.onNodeWithTag("reader_pen_color_185abd").performClick()
    InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    compose.onNodeWithTag("floating_pen_groups").performClick()
    compose.onNodeWithTag("floating_pen_group_0").performClick()
    compose.runOnIdle { assertEquals("#dc3545", style.color) }
    compose.onNodeWithTag("floating_pen_groups").performClick()
    compose.onNodeWithTag("floating_pen_group_1").performClick()
    compose.runOnIdle { assertEquals("#185abd", style.color) }
    compose.onNodeWithTag("floating_pen_groups").performClick()
    compose.onNodeWithTag("floating_pen_group_edit_1").performClick()
    compose.onNodeWithTag("floating_pen_group_name").performTextClearance()
    compose.onNodeWithTag("floating_pen_group_name").performTextInput("Study")
    compose.onNodeWithTag("floating_pen_group_save").performClick()
    compose.onNodeWithTag("floating_pen_groups").performClick()
    compose.onNodeWithTag("floating_pen_group_edit_1").performClick()
    compose.onNodeWithTag("floating_pen_group_delete").performClick()
    compose.onNodeWithTag("floating_pen_group_confirm_delete").performClick()
    compose.onNodeWithTag("floating_pen_groups").performClick()
    compose.onAllNodesWithTag("floating_pen_group_1").assertCountEquals(0)
  }

  @Test fun copiedBoxPreservesSelectedPen() {
    var tool by mutableStateOf(PenCanvasTool.PEN)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          FloatingPenBox(tool, null, PenStyle.DEFAULT, true, true,
            onTool = { tool = it }, onShape = {}, onColor = {}, onWidth = {}, onCustomColor = {})
        }
      }
    }
    compose.onNodeWithTag("reader_pen_ballpoint").performClick()
    compose.onNodeWithTag("floating_pen_groups").performClick()
    compose.onNodeWithTag("floating_pen_group_add").performClick()
    compose.onNodeWithTag("reader_pen_ballpoint").assertIsSelected()
    compose.runOnIdle { assertEquals(PenCanvasTool.BALLPOINT, tool) }
  }

  @Test fun createsEmptyBoxAndAddsPen() {
    var tool by mutableStateOf(PenCanvasTool.PEN)
    var generation by mutableStateOf(0)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          key(generation) { FloatingPenBox(tool, null, PenStyle.DEFAULT, true, true,
            onTool = { tool = it }, onShape = {}, onColor = {}, onWidth = {}, onCustomColor = {}) }
        }
      }
    }
    compose.onNodeWithTag("floating_pen_groups").performClick()
    compose.onNodeWithTag("floating_pen_group_new").performClick()
    compose.onAllNodesWithTag("reader_pen_pen").assertCountEquals(0)
    compose.runOnIdle { generation++ }
    compose.onAllNodesWithTag("reader_pen_pen").assertCountEquals(0)
    compose.onNodeWithTag("floating_pen_add").performClick()
    compose.onNodeWithTag("floating_pen_add_pen").performClick()
    compose.onNodeWithTag("reader_pen_pen").assertIsSelected()
    compose.onNodeWithTag("reader_pen_pen").performClick()
    compose.onNodeWithTag("floating_pen_remove").performClick()
    compose.runOnIdle { generation++ }
    compose.onAllNodesWithTag("reader_pen_pen").assertCountEquals(0)
  }

  @Test fun mountingRestoresMissingCurrentPen() {
    val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("reader_floating_pen_box", 0)
    prefs.edit().putString("order", "PEN").commit()
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          FloatingPenBox(PenCanvasTool.HIGHLIGHTER, null, PenStyle.DEFAULT, true, true,
            onTool = {}, onShape = {}, onColor = {}, onWidth = {}, onCustomColor = {})
        }
      }
    }
    compose.onNodeWithTag("reader_pen_highlighter").assertIsSelected()
  }

  @Test fun recreatedPenSlotDoesNotOverwriteAnotherGroup() {
    val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("reader_floating_pen_box", 0)
    prefs.edit().putString("HIGHLIGHTER_color", "#2e7d32").commit()
    var tool by mutableStateOf(PenCanvasTool.PEN)
    var style by mutableStateOf(PenStyle.DEFAULT)
    compose.setContent {
      MaterialTheme {
        Box(Modifier.size(600.dp, 600.dp)) {
          FloatingPenBox(tool, null, style, true, true,
            onTool = { tool = it }, onShape = {},
            onColor = { style = requireNotNull(PenStyle.create(it, style.width)) },
            onWidth = { style = requireNotNull(PenStyle.create(style.color, it)) }, onCustomColor = {},
            onRestoreStyle = { color, width -> style = requireNotNull(PenStyle.create(color, width)) })
        }
      }
    }
    compose.onNodeWithTag("floating_pen_groups").performClick()
    compose.onNodeWithTag("floating_pen_group_add").performClick()
    compose.onNodeWithTag("reader_pen_highlighter").performClick()
    compose.onNodeWithTag("reader_pen_highlighter").performClick()
    compose.onNodeWithTag("floating_pen_remove").performClick()
    compose.runOnIdle { tool = PenCanvasTool.HIGHLIGHTER }
    compose.onNodeWithTag("reader_pen_highlighter").performClick()
    compose.onNodeWithTag("reader_pen_color_185abd").performClick()
    InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    compose.onNodeWithTag("floating_pen_groups").performClick()
    compose.onNodeWithTag("floating_pen_group_0").performClick()
    compose.onNodeWithTag("reader_pen_highlighter").performClick()
    compose.runOnIdle { assertEquals("#2e7d32", style.color) }
  }
}
