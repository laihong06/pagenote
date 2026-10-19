package io.pagenote.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTablesTest {
  @Test
  fun extractsHeadersRowsAndSource() {
    val markdown = "# t\n\n| Name | Age |\n| --- | ---: |\n| **Ann** | `30` |\n| [Bob](https://x) | 25 |\n"

    val tables = MarkdownTables.extract(markdown)

    assertEquals(1, tables.size)
    assertEquals(listOf("Name", "Age"), tables[0].headers)
    assertEquals(listOf(listOf("Ann", "30"), listOf("Bob", "25")), tables[0].rows)
    assertTrue(tables[0].source.contains("| Name | Age |"))
  }

  @Test
  fun ignoresInvalidDelimiterAndKeepsValid() {
    val markdown = "| a | b |\n| --- | xyz |\n| 1 | 2 |\n\n| h1 | h2 |\n| --- | --- |\n| 3 | 4 |\n"

    val tables = MarkdownTables.extract(markdown)

    assertEquals(1, tables.size)
    assertEquals(listOf("h1", "h2"), tables[0].headers)
    assertEquals(listOf(listOf("3", "4")), tables[0].rows)
  }

  @Test
  fun emptyWhenNoTables() {
    assertTrue(MarkdownTables.extract("# plain\n\nno tables here\n").isEmpty())
    assertTrue(MarkdownTables.extract("").isEmpty())
  }
}
