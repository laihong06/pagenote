package io.pagenote.android

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecentProjectsStoreTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun upsertMovesSameProjectToFrontWithoutCreatingDuplicate() {
    val older = entry(rootDocumentId = "root-a", name = "Old name", lastOpenedAtMillis = 10)
    val other = entry(rootDocumentId = "root-b", name = "Other", lastOpenedAtMillis = 20)
    val reopened = older.copy(name = "Renamed", lastOpenedAtMillis = 30)

    val result = RecentProjectsPolicy.upsert(listOf(other, older), reopened)

    assertEquals(listOf(reopened, other), result)
  }

  @Test
  fun projectsUnderSameTreeRemainDistinctByRootDocumentId() {
    val first = entry(rootDocumentId = "root-a")
    val second = entry(rootDocumentId = "root-b")

    assertEquals(listOf(second, first), RecentProjectsPolicy.upsert(listOf(first), second))
  }

  @Test
  fun upsertKeepsAtMostTwelveRecentProjects() {
    val initial = (1..14).map { entry(rootDocumentId = "root-$it", lastOpenedAtMillis = it.toLong()) }
    val newest = entry(rootDocumentId = "root-15", lastOpenedAtMillis = 15)

    val result = RecentProjectsPolicy.upsert(initial, newest)

    assertEquals(12, result.size)
    assertEquals(newest, result.first())
    assertEquals("root-11", result.last().rootDocumentId)
  }

  @Test
  fun removeUsesTreeAndRootIdentityOnly() {
    val selected = entry(rootDocumentId = "root-a")
    val sameTreeDifferentRoot = entry(rootDocumentId = "root-b")

    assertEquals(
      listOf(sameTreeDifferentRoot),
      RecentProjectsPolicy.remove(listOf(selected, sameTreeDifferentRoot), selected),
    )
  }

  @Test
  fun storeRoundTripsRecentProjectsAcrossInstances() = runBlocking {
    val file = File(temporaryFolder.root, "recent-projects.preferences_pb")
    val firstScope = newScope()
    try {
      RecentProjectsStore(newDataStore(file, firstScope)).record(entry(rootDocumentId = "root-a"))
    } finally {
      firstScope.cancel()
    }

    val secondScope = newScope()
    try {
      assertEquals(
        listOf(entry(rootDocumentId = "root-a")),
        RecentProjectsStore(newDataStore(file, secondScope)).recentProjects.first(),
      )
    } finally {
      secondScope.cancel()
    }
  }

  @Test
  fun malformedAndInvalidStoredRecordsRecoverAsEmpty() = runBlocking {
    val scope = newScope()
    try {
      val dataStore = newDataStore(File(temporaryFolder.root, "malformed.preferences_pb"), scope)
      dataStore.edit { it[stringPreferencesKey("recent_projects")] = "[{broken" }
      assertTrue(RecentProjectsStore(dataStore).recentProjects.first().isEmpty())

      dataStore.edit {
        it[stringPreferencesKey("recent_projects")] = """[{"accessTreeUri":"file:///tmp","rootDocumentId":"root","projectId":"id","name":"Bad path","lastOpenedAtMillis":1}]"""
      }
      assertTrue(RecentProjectsStore(dataStore).recentProjects.first().isEmpty())
    } finally {
      scope.cancel()
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun recordRejectsNonSafUri() = runBlocking {
    val scope = newScope()
    try {
      RecentProjectsStore(newDataStore(File(temporaryFolder.root, "invalid.preferences_pb"), scope))
        .record(entry(accessTreeUri = "file:///tmp/project", rootDocumentId = "root"))
    } finally {
      scope.cancel()
    }
  }

  private fun entry(
    accessTreeUri: String = "content://provider/tree/root",
    rootDocumentId: String,
    projectId: String = "project-$rootDocumentId",
    name: String = rootDocumentId,
    lastOpenedAtMillis: Long = 1,
  ) = RecentProjectEntry(
    accessTreeUri = accessTreeUri,
    rootDocumentId = rootDocumentId,
    projectId = projectId,
    name = name,
    lastOpenedAtMillis = lastOpenedAtMillis,
  )

  private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private fun newDataStore(file: File, scope: CoroutineScope) = PreferenceDataStoreFactory.create(
    scope = scope,
    produceFile = { file },
  )
}
