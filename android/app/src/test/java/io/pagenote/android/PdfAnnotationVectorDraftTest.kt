package io.pagenote.android

import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenRecognizedShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PdfAnnotationVectorDraftTest {
  @Test
  fun serializesEachToolWithOrderedPointsAndDesktopDefaults() {
    listOf("line", "arrow", "rect", "ellipse").forEach { type ->
      val draft = requireNotNull(
        PdfAnnotationVectorDraft.create(
          type = type,
          first = 400.0 to 1200.0,
          second = 650.0 to 1450.0,
        ),
      )

      assertEquals(
        mapOf(
          "type" to type,
          "color" to "#dc3545",
          "width" to 3.0,
          "points" to listOf(listOf(400.0, 1200.0), listOf(650.0, 1450.0)),
        ),
        draft.toItem(),
      )
      assertEquals(listOf(400.0 to 1200.0, 650.0 to 1450.0), draft.toOverlayItem().points)
      assertEquals(null, draft.toOverlayItem().text)
    }
  }

  @Test
  fun rejectsUnsupportedOrDegenerateShapesAndInvalidStyle() {
    val point = 10.0 to 20.0
    assertNull(PdfAnnotationVectorDraft.create("line", point, point))
    assertNull(PdfAnnotationVectorDraft.create("arrow", point, point))
    assertNull(PdfAnnotationVectorDraft.create("rect", point, 10.0 to 30.0))
    assertNull(PdfAnnotationVectorDraft.create("ellipse", point, 30.0 to 20.0))
    assertNull(PdfAnnotationVectorDraft.create("image", point, 30.0 to 40.0))
    assertNull(PdfAnnotationVectorDraft.create("line", point, 30.0 to 40.0, color = "red"))
    assertNull(PdfAnnotationVectorDraft.create("line", point, 30.0 to 40.0, width = 101.0))
  }

  @Test
  fun serializesActiveColorAndWidthForNewVectorAnnotations() {
    val draft = requireNotNull(
      PdfAnnotationVectorDraft.create(
        type = "arrow",
        first = 10.0 to 20.0,
        second = 40.0 to 50.0,
        color = "#185abd",
        width = 10.0,
      ),
    )

    assertEquals("#185abd", draft.toItem()["color"])
    assertEquals(10.0, draft.toItem()["width"])
    assertEquals("#185abd", draft.toOverlayItem().color)
    assertEquals(10.0, draft.toOverlayItem().width, 0.0)
  }

  @Test
  fun convertsRecognizedInkToNativeShapeWithoutPressureSamples() {
    val draft = requireNotNull(
      PdfAnnotationVectorDraft.fromRecognizedShape(
        PenRecognizedShape(
          type = "arrow",
          first = PenPoint(10f, 20f, 0.3f),
          second = PenPoint(40f, 50f, 0.9f),
        ),
        color = "#185abd",
        width = 10.0,
      ),
    )

    assertEquals(
      mapOf(
        "type" to "arrow",
        "color" to "#185abd",
        "width" to 10.0,
        "points" to listOf(listOf(10.0, 20.0), listOf(40.0, 50.0)),
      ),
      draft.toItem(),
    )
  }

  @Test
  fun serializesCatalogShapeKindsAsShapeExtensionOnBaseTypes() {
    // 目录扩展项（D5）：type 取基类型，kind id 写入 extension 字段 `shape`，两点保持不变。
    val draft = requireNotNull(
      PdfAnnotationVectorDraft.create(
        type = "rect",
        first = 400.0 to 1200.0,
        second = 650.0 to 1450.0,
        shapeKind = "pentagon",
      ),
    )
    assertEquals(
      mapOf(
        "type" to "rect",
        "color" to "#dc3545",
        "width" to 3.0,
        "points" to listOf(listOf(400.0, 1200.0), listOf(650.0, 1450.0)),
        "shape" to "pentagon",
      ),
      draft.toItem(),
    )
    // 基础四项不带 shapeKind 时不得写出 `shape` 字段（见 serializesEachToolWithOrderedPointsAndDesktopDefaults）。
    val baseDraft = requireNotNull(
      PdfAnnotationVectorDraft.create("rect", 400.0 to 1200.0, 650.0 to 1450.0),
    )
    assertFalse(baseDraft.toItem().containsKey("shape"))
  }

  @Test
  fun everyDialogToolRoundTripsThroughCreate() {
    // 形状对话框的全部工具（含目录扩展项）都能以合法拖拽两点落盘，且两点恒为 2。
    PdfAnnotationDrawTool.entries.forEach { tool ->
      val first = 100.0 to 100.0
      val second = if (tool.type == "line") 400.0 to 260.0 else 300.0 to 260.0
      val draft = requireNotNull(
        PdfAnnotationVectorDraft.create(
          tool.type,
          first,
          second,
          shapeKind = tool.shapeKind,
        ),
      ) { "tool ${tool.name} must produce a draft" }
      val item = draft.toItem()
      assertEquals(tool.type, item["type"])
      assertEquals(tool.shapeKind, item["shape"])
      assertEquals(2, (item["points"] as List<*>).size)
    }
  }

  @Test
  fun rejectsUnknownOrMismatchedShapeKindAndKindDegenerateDrags() {
    val point = 10.0 to 20.0
    // 未知 kind id 拒绝。
    assertNull(PdfAnnotationVectorDraft.create("rect", point, 300.0 to 40.0, shapeKind = "no-such-kind"))
    // kind 与基类型不匹配拒绝（pentagon 的基类型是 rect，不是 line）。
    assertNull(PdfAnnotationVectorDraft.create("line", point, 300.0 to 40.0, shapeKind = "pentagon"))
    // 椭圆族 kind 拒绝零面积拖拽；线族 kind 拒绝两点重合、但接受纯垂直拖拽。
    assertNull(PdfAnnotationVectorDraft.create("ellipse", point, 300.0 to 20.0, shapeKind = "circle"))
    assertNull(PdfAnnotationVectorDraft.create("line", point, point, shapeKind = "wave"))
    assertNotNull(PdfAnnotationVectorDraft.create("line", point, 10.0 to 300.0, shapeKind = "wave"))
    assertNotNull(PdfAnnotationVectorDraft.create("line", point, 300.0 to 20.0, shapeKind = "dashed-line"))
  }
}
