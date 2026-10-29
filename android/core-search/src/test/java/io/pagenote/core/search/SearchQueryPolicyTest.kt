package io.pagenote.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryPolicyTest {
  @Test
  fun emptyOrBlankQueriesPlanToEmpty() {
    assertEquals(SearchQueryPlan.Empty, SearchQueryPolicy.plan(""))
    assertEquals(SearchQueryPlan.Empty, SearchQueryPolicy.plan("   \n\t "))
  }

  @Test
  fun latinQueryBuildsQuotedPrefixMatchJoinedByAnd() {
    val plan = SearchQueryPolicy.plan("note page") as SearchQueryPlan.FullText
    assertEquals("\"note\"* \"page\"*", plan.match)
  }

  @Test
  fun mixedQueryBuildsFullTextMatch() {
    val plan = SearchQueryPolicy.plan("note 中文") as SearchQueryPlan.FullText
    assertEquals("\"note\"* \"中文\"*", plan.match)
  }

  @Test
  fun pureCjkQueryPlansLikeContains() {
    val plan = SearchQueryPolicy.plan("中文搜索") as SearchQueryPlan.Contains
    assertEquals("%中文搜索%", plan.pattern)
  }

  @Test
  fun cjkWithPunctuationStillPlansLikeContains() {
    val plan = SearchQueryPolicy.plan("中文，搜索！") as SearchQueryPlan.Contains
    assertEquals("%中文，搜索！%", plan.pattern)
  }

  @Test
  fun doubleQuotesAreDoubledInsideMatchTerms() {
    val plan = SearchQueryPolicy.plan("say \"hi\"") as SearchQueryPlan.FullText
    assertEquals("\"say\"* \"\"\"hi\"\"\"*", plan.match)
  }

  @Test
  fun likePatternEscapesLikeWildcardsAndEscapeChar() {
    assertEquals("%100\\%%", SearchQueryPolicy.likePattern("100%"))
    assertEquals("%a\\_b%", SearchQueryPolicy.likePattern("a_b"))
    assertEquals("%a\\\\b%", SearchQueryPolicy.likePattern("a\\b"))
  }

  @Test
  fun longQueriesAreTruncatedToPolicyLimit() {
    val longQuery = "a".repeat(SearchQueryPolicy.MAX_QUERY_LENGTH + 50)
    val plan = SearchQueryPolicy.plan(longQuery) as SearchQueryPlan.FullText
    assertEquals(
      "\"" + "a".repeat(SearchQueryPolicy.MAX_QUERY_LENGTH) + "\"*",
      plan.match,
    )
  }

  @Test
  fun cjkDetectionTreatsLatinAsNonCjk() {
    assertFalse(SearchQueryPolicy.isCjkQuery("note"))
    assertTrue(SearchQueryPolicy.isCjkQuery("笔记"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun buildMatchRejectsTermlessQueries() {
    SearchQueryPolicy.buildMatch("   ")
  }
}
