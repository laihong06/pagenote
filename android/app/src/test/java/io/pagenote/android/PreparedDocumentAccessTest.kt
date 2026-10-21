package io.pagenote.android

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PreparedDocumentAccessTest {
  @Test fun nullChildQueryCursorIsIoFailure() {
    val error = assertThrows(IOException::class.java) {
      requireChildQueryCursor<Any>(null)
    }

    assertEquals("SAF provider returned no child query cursor", error.message)
  }

  @Test fun childQueryCursorIsReturnedUnchanged() {
    val cursor = Any()

    assertSame(cursor, requireChildQueryCursor(cursor))
  }
}
