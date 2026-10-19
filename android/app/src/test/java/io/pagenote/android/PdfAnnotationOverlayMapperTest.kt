package io.pagenote.android

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfAnnotationOverlayMapperTest {
  private val json = Json

  @Test
  fun parsesRectangleAndTextInSourceOrder() {
    val items = PdfAnnotationOverlayItem.fromPage(json.parseToJsonElement("""
      [
        {"type":"rect","color":"#12abef","width":3,"points":[[300,1200],[100,1400]],"text":"region"},
        {"type":"text","color":"#010203","width":4,"points":[[50,1300]],"text":"first line\nsecond line"}
      ]
    """))

    assertEquals(2, items.size)
    assertEquals("rect", items[0].type)
    assertEquals(listOf(100.0 to 1200.0, 300.0 to 1400.0), items[0].points)
    assertEquals("text", items[1].type)
    assertEquals("first line\nsecond line", items[1].text)
  }

  @Test
  fun parsesLineArrowAndNormalizesReverseEllipseBounds() {
    val items = PdfAnnotationOverlayItem.fromPage(json.parseToJsonElement("""
      [
        {"type":"line","color":"#112233","width":3,"points":[[50,1200],[250,1400]]},
        {"type":"arrow","color":"#223344","width":4,"points":[[400,1200],[600,1300]]},
        {"type":"ellipse","color":"#334455","width":5,"points":[[900,1400],[700,1200]]}
      ]
    """))

    assertEquals(listOf("line", "arrow", "ellipse"), items.map { it.type })
    assertEquals(listOf(50.0 to 1200.0, 250.0 to 1400.0), items[0].points)
    assertEquals(listOf(400.0 to 1200.0, 600.0 to 1300.0), items[1].points)
    assertEquals(listOf(700.0 to 1200.0, 900.0 to 1400.0), items[2].points)
  }

  @Test
  fun vectorWithOnePointReusesItAsTheSecondEndpoint() {
    val items = PdfAnnotationOverlayItem.fromPage(json.parseToJsonElement("""
      [
        {"type":"line","color":"#112233","width":3,"points":[[125,1100]]},
        {"type":"arrow","color":"#223344","width":4,"points":[[225,1200]]},
        {"type":"ellipse","color":"#334455","width":5,"points":[[325,1300]]}
      ]
    """))

    assertEquals(3, items.size)
    assertEquals(listOf(125.0 to 1100.0, 125.0 to 1100.0), items[0].points)
    assertEquals(listOf(225.0 to 1200.0, 225.0 to 1200.0), items[1].points)
    assertEquals(listOf(325.0 to 1300.0, 325.0 to 1300.0), items[2].points)
  }

  @Test
  fun skipsMalformedAndUnsupportedItems() {
    val items = PdfAnnotationOverlayItem.fromPage(json.parseToJsonElement("""
      [
        {"type":"rect","color":"red","width":3,"points":[[0,0],[100,100]]},
        {"type":"text","color":"#ffffff","width":3,"points":[[0,0]],"text":""},
        {"type":"line","color":"#ffffff","width":3,"points":[]},
        {"type":"line","color":"#ffffff","width":3,"points":[[1000001,0],[0,0]]},
        {"type":"arrow","color":"#ffffff","width":101,"points":[[0,0],[100,100]]},
        {"type":"image","color":"#ffffff","width":3,"points":[[0,0],[100,100]]},
        {"type":"rect","color":"#ffffff","width":3,"points":[[0,0],[100,100]]}
      ]
    """))

    assertEquals(1, items.size)
    assertEquals("rect", items.single().type)
  }
}
