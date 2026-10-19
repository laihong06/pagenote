package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlToMarkdownTest {
  @Test
  fun convertsSemanticBlocksAndInlineFormatting() {
    val markdown = HtmlToMarkdown.convert(
      "<h2>Review</h2><p><strong>Bold</strong> and <em>italic</em>, " +
        "<del>removed</del>, <u>underlined</u>, and <a href='https://example.com/a'>link</a>.</p>",
    )

    assertEquals("## Review\n\n**Bold** and *italic*, ~~removed~~, <u>underlined</u>, and " +
      "[link](https://example.com/a).", markdown)
  }

  @Test
  fun convertsListsQuotesCodeAndTables() {
    val markdown = HtmlToMarkdown.convert(
      "<blockquote><p>Quoted</p></blockquote>" +
        "<ol start='3'><li>first</li><li>second</li></ol>" +
        "<ul><li>nested<ul><li>child</li></ul></li></ul>" +
        "<pre><code class='language-kotlin'>val answer = 42</code></pre>" +
        "<table><thead><tr><th>Name</th><th>Value</th></tr></thead>" +
        "<tbody><tr><td>A</td><td>B|C</td></tr></tbody></table>",
    )

    assertEquals(
      "> Quoted\n\n3. first\n4. second\n\n- nested\n  - child\n\n" +
        "```kotlin\nval answer = 42\n```\n\n| Name | Value |\n| --- | --- |\n| A | B\\|C |",
      markdown,
    )
  }

  @Test
  fun dropsActiveContentUnsafeLinksAndImageUrls() {
    val markdown = HtmlToMarkdown.convert(
      "<p>safe<script>steal()</script><style>.x{display:none}</style>" +
        "<a href='javascript:alert(1)' onclick='steal()'>unsafe link</a>" +
        "<a href='https://safe.example' onmouseover='steal()'>safe link</a>" +
        "<img src='https://tracker.example/pixel' alt='figure'></p>",
    )

    assertEquals("safeunsafe link[safe link](https://safe.example)figure", markdown)
  }

  @Test
  fun returnsNullForHtmlWithoutUsefulText() {
    assertNull(HtmlToMarkdown.convert("<script>run()</script><style>body{}</style><img src='x'>"))
  }

  @Test
  fun escapesMarkdownLinkDestinations() {
    val markdown = HtmlToMarkdown.convert("<a href='https://example.com/a_(b)'>label</a>")

    assertEquals("[label](https://example.com/a_%28b%29)", markdown)
  }
}
