package io.pagenote.android

import org.junit.Assert.*
import org.junit.Test

class BookReadingPositionTest {
  @Test fun positionRoundTripsIndependentlyForEachProject() {
    val position = BookReadingPosition("project-a", 8, 2f, .3f, .7f, "PEN")
    assertEquals(position, BookReadingPosition.decode(position.encode()))
    assertNotEquals(position, position.copy(projectId = "project-b"))
  }
  @Test fun malformedPositionsAreNotRestored() {
    assertThrows(IllegalArgumentException::class.java) { BookReadingPosition.decode("{}") }
    assertThrows(IllegalArgumentException::class.java) {
      BookReadingPosition.decode("""{"version":1,"projectId":"a","page":-1,"scale":1,"x":0.5,"y":0.5,"pen":"PEN"}""")
    }
  }
}
