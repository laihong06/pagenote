package io.pagenote.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MarkdownSStateTest {
  @Test
  fun parsesDesktopGeneticLabStateAndRetainsUnknownFields() {
    val parsed = MarkdownSState.parse(
      """{"version":1,"id":"lab-1","kind":"genetic-lab","genes":[1,0,1,0,1,0,1,0,1,0,1,0],"position":50,"generation":4,"paused":true,"angle":0.15,"futureField":{"enabled":true}}""",
    )

    assertEquals("lab-1", parsed.id)
    assertEquals(listOf(1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0), parsed.genes)
    assertEquals(50.0, parsed.position!!, 0.0)
    assertEquals(4, parsed.generation)
    assertTrue(parsed.paused!!)
    assertTrue(parsed.extraFields.containsKey("futureField"))
  }

  @Test
  fun rejectsInvalidContractAndUtf8SizeLimit() {
    val base = """{"version":1,"id":"lab","kind":"genetic-lab","genes":[1,0,1,0,1,0,1,0,1,0,1,0]}"""
    val invalid = listOf(
      base.replace("\"version\":1", "\"version\":2"),
      base.replace("\"id\":\"lab\"", "\"id\":\" \""),
      base.replace("\"genes\":[1,0,1,0,1,0,1,0,1,0,1,0]", "\"genes\":[1,0]"),
      base.replace("\"genes\":[1,0,1,0,1,0,1,0,1,0,1,0]", "\"genes\":[1,0,1,0,1,0,1,0,1,0,1,2]"),
      base.replace("\"id\":\"lab\"", "\"id\":1"),
      base.dropLast(1) + ",\"seed\":\"not-a-number\"}",
      "{" + "\"version\":1," + "\"id\":\"lab\",\"kind\":\"genetic-lab\",\"genes\":[1,0,1,0,1,0,1,0,1,0,1,0],\"position\":101}",
      "not json",
    )
    invalid.forEach { source ->
      try {
        MarkdownSState.parse(source)
        fail("Expected invalid state to be rejected: $source")
      } catch (_: MarkdownSInvalidStateException) {
      }
    }

    val oversized = base.dropLast(1) + ",\"padding\":\"${"汉".repeat(70_000)}\"}"
    try {
      MarkdownSState.parse(oversized)
      fail("Expected UTF-8 limit to reject source")
    } catch (error: MarkdownSInvalidStateException) {
      assertTrue(error.message.orEmpty().contains("200 KiB"))
    }
  }
}
