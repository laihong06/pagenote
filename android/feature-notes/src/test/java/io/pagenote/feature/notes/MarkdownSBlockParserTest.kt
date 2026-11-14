package io.pagenote.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownSBlockParserTest {
  @Test
  fun findsOnlySupportedFencesAndReportsUtf16SourceRanges() {
    val markdown = listOf(
      "Before", "", " ~~~~markdowns",
      " {\"version\":1,\"id\":\"汉\",\"kind\":\"genetic-lab\",\"genes\":[1,0,1,0,1,0,1,0,1,0,1,0]}",
      " ~~~~", "", "```mds-html", "<script>ignored()</script>", "```", "",
      "````mds-native",
      "{\"version\":1,\"id\":\"other\",\"kind\":\"genetic-lab\",\"genes\":[1,0,1,0,1,0,1,0,1,0,1,0]}",
      "```", "still inside", "````", "",
    ).joinToString("\n")
    val blocks = MarkdownSBlockParser.parse(markdown)

    assertEquals(2, blocks.size)
    assertEquals(listOf("markdowns", "mds-native"), blocks.map { it.engine })
    assertEquals("汉", blocks.first().parseResult.state!!.id)
    assertEquals(markdown.substring(blocks.first().sourceStart, blocks.first().sourceEnd), blocks.first().source)
    assertEquals("still inside", blocks.last().source.trimEnd().lines().last())
    assertEquals(true, markdown.substring(blocks.last().fenceStart, blocks.last().fenceEnd).contains("````"))
  }

  @Test
  fun handlesCrLfAndUnclosedFencesWithoutScanningCodeTextAsFence() {
    val markdown = "Before\r\n~~~markdowns\r\n{\"version\":1,\"id\":\"a\",\"kind\":\"genetic-lab\",\"genes\":[1,0,1,0,1,0,1,0,1,0,1,0]}\r\n"
    val block = MarkdownSBlockParser.parse(markdown).single()

    assertEquals(false, block.closed)
    assertEquals("a", block.parseResult.state!!.id)
    assertEquals(markdown.substring(block.sourceStart, block.sourceEnd), block.source)
  }
}
