package io.pagenote.android.search

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update

/**
 * 全文检索文档表（移植批次 7）。主键 [id] 为 rowid 别名（外部内容 FTS4 的
 * JOIN 依赖），(projectId, page, kind) 唯一索引承载业务身份。
 */
@Entity(
  tableName = "search_docs",
  indices = [androidx.room.Index(value = ["projectId", "page", "kind"], unique = true)],
)
data class SearchDocEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val projectId: String,
  val page: Int,
  val kind: String,
  val content: String,
  val revision: String,
  val hasRect: Boolean,
  val rectLeft: Int,
  val rectTop: Int,
  val rectRight: Int,
  val rectBottom: Int,
  val updatedAt: Long,
)

/** 外部内容 FTS4 表：跟随 search_docs 的触发器由 Room 生成，无需手工同步。 */
@Fts4(contentEntity = SearchDocEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "search_docs_fts")
data class SearchDocFtsEntity(val content: String)

@Dao
interface SearchDocDao {
  @Query(
    "SELECT search_docs.* FROM search_docs " +
      "JOIN search_docs_fts ON search_docs.rowid = search_docs_fts.rowid " +
      "WHERE search_docs_fts MATCH :match " +
      "AND search_docs.projectId = :projectId AND search_docs.kind IN (:kinds) " +
      "ORDER BY search_docs.page, search_docs.kind LIMIT :limit",
  )
  suspend fun searchFullText(projectId: String, kinds: List<String>, match: String, limit: Int): List<SearchDocEntity>

  /** 纯 CJK 查询的 LIKE 扫描（unicode61 分词不做汉字子串切分）。 */
  @Query(
    "SELECT * FROM search_docs " +
      "WHERE projectId = :projectId AND kind IN (:kinds) " +
      "AND content LIKE :pattern ESCAPE '\\' " +
      "ORDER BY page, kind LIMIT :limit",
  )
  suspend fun searchContains(projectId: String, kinds: List<String>, pattern: String, limit: Int): List<SearchDocEntity>

  @Query("SELECT * FROM search_docs WHERE projectId = :projectId AND page = :page AND kind = :kind")
  suspend fun get(projectId: String, page: Int, kind: String): SearchDocEntity?

  @Query("SELECT revision FROM search_docs WHERE projectId = :projectId AND page = :page AND kind = :kind")
  suspend fun revisionOf(projectId: String, page: Int, kind: String): String?

  @Query("SELECT COUNT(*) FROM search_docs WHERE projectId = :projectId")
  suspend fun countForProject(projectId: String): Int

  @Query("DELETE FROM search_docs WHERE projectId = :projectId AND page = :page AND kind IN (:kinds)")
  suspend fun deletePage(projectId: String, page: Int, kinds: List<String>)

  @Query("DELETE FROM search_docs WHERE projectId = :projectId AND kind IN (:kinds)")
  suspend fun deleteKindsForProject(projectId: String, kinds: List<String>)

  @Query("DELETE FROM search_docs WHERE projectId = :projectId")
  suspend fun deleteProject(projectId: String)

  /** 业务键 upsert：保持 rowid 稳定（更新而非重建行）。 */
  @Transaction
  suspend fun upsert(doc: SearchDocEntity) {
    val existing = get(doc.projectId, doc.page, doc.kind)
    if (existing == null) {
      insert(doc)
    } else {
      update(doc.copy(id = existing.id))
    }
  }

  @Insert
  suspend fun insert(doc: SearchDocEntity): Long

  @Update
  suspend fun update(doc: SearchDocEntity)
}

@Database(
  entities = [SearchDocEntity::class, SearchDocFtsEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class SearchIndexDatabase : RoomDatabase() {
  abstract fun searchDocDao(): SearchDocDao

  companion object {
    private const val DB_NAME = "search-index.db"

    @Volatile
    private var instance: SearchIndexDatabase? = null

    fun get(context: Context): SearchIndexDatabase = instance ?: synchronized(this) {
      instance ?: Room.databaseBuilder(
        context.applicationContext,
        SearchIndexDatabase::class.java,
        DB_NAME,
      ).build().also { instance = it }
    }
  }
}
