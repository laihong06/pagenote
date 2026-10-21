package io.pagenote.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptIndexTest {
  @Test
  fun moduleIdsAlignWithDesktop() {
    val ids = PromptIndex.MODULES.map { it.id }
    assertEquals(
      listOf("entry", "generation", "profile", "tag-edit", "learning", "page-tutor", "output", "learner"),
      ids,
    )
  }

  @Test
  fun generationRouteReadsNeighborsFirst() {
    val route = PromptIndex.routeFor("generation")
    assertTrue(route.modules.contains("page-tutor"))
    assertTrue(route.first.contains("相邻源页"))
  }

  @Test
  fun revisionRouteReadsAnnotationAndNoteFirst() {
    val route = PromptIndex.routeFor("revision")
    assertEquals(listOf("tag-edit", "output", "learner", "profile"), route.modules)
  }

  @Test
  fun unknownScenarioFallsBackToGeneric() {
    val route = PromptIndex.routeFor("nope")
    assertEquals("generic", route.scenario)
    assertTrue(route.modules.isEmpty())
  }

  @Test
  fun revisionHitSkipsText() {
    val first = PromptIndex.read("learning", "teach page 3")
    assertFalse(first.unchanged)
    assertEquals("teach page 3", first.text)
    val second = PromptIndex.read("learning", "teach page 3", knownRevision = first.revision)
    assertTrue(second.unchanged)
    assertNull(second.text)
  }

  @Test(expected = IllegalArgumentException::class)
  fun unknownModuleRejected() {
    PromptIndex.read("nope", "text")
  }
}
