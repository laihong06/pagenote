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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.pagenote.android.flashcard.FlashcardDao
import io.pagenote.android.flashcard.FlashcardEntity
import io.pagenote.android.flashcard.FlashcardReviewEntity
import io.pagenote.android.flashcard.FlashcardTagCrossRef
import io.pagenote.android.flashcard.TagEntity

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
  entities = [
    SearchDocEntity::class,
    SearchDocFtsEntity::class,
    // 批次 11（差距清单 #20）：闪卡（卡片+标签+复习记录）复用本数据库。
    FlashcardEntity::class,
    TagEntity::class,
    FlashcardTagCrossRef::class,
    FlashcardReviewEntity::class,
  ],
  version = 2,
  exportSchema = false,
)
abstract class SearchIndexDatabase : RoomDatabase() {
  abstract fun searchDocDao(): SearchDocDao

  abstract fun flashcardDao(): FlashcardDao

  companion object {
    private const val DB_NAME = "search-index.db"

    /**
     * 批次 11 迁移：新增闪卡四表。列定义与 Room 生成的 schema 一致
     * （Room 打开时会按实体校验 table_info）。
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `flashcards` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`projectId` TEXT NOT NULL, `page` INTEGER NOT NULL, `sourceKind` TEXT NOT NULL, " +
            "`front` TEXT NOT NULL, `back` TEXT NOT NULL, `sourceQuote` TEXT NOT NULL, " +
            "`anchorStart` INTEGER NOT NULL, `anchorEnd` INTEGER NOT NULL, `revision` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `dueAt` INTEGER NOT NULL, `intervalDays` INTEGER NOT NULL, " +
            "`easeX1000` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `lapses` INTEGER NOT NULL)",
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_flashcards_projectId_dueAt` ON `flashcards` (`projectId`, `dueAt`)",
        )
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `tags` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`projectId` TEXT NOT NULL, `name` TEXT NOT NULL)",
        )
        db.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_projectId_name` ON `tags` (`projectId`, `name`)",
        )
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `flashcard_tags` (" +
            "`cardId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, " +
            "PRIMARY KEY(`cardId`, `tagId`), " +
            "FOREIGN KEY(`cardId`) REFERENCES `flashcards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
            "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_flashcard_tags_tagId` ON `flashcard_tags` (`tagId`)",
        )
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `flashcard_reviews` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`cardId` INTEGER NOT NULL, `projectId` TEXT NOT NULL, `reviewedAt` INTEGER NOT NULL, " +
            "`outcome` TEXT NOT NULL, `intervalAfter` INTEGER NOT NULL, " +
            "FOREIGN KEY(`cardId`) REFERENCES `flashcards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_flashcard_reviews_cardId` ON `flashcard_reviews` (`cardId`)",
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_flashcard_reviews_projectId_reviewedAt` " +
            "ON `flashcard_reviews` (`projectId`, `reviewedAt`)",
        )
      }
    }

    @Volatile
    private var instance: SearchIndexDatabase? = null

    fun get(context: Context): SearchIndexDatabase = instance ?: synchronized(this) {
      instance ?: Room.databaseBuilder(
        context.applicationContext,
        SearchIndexDatabase::class.java,
        DB_NAME,
      ).addMigrations(MIGRATION_1_2).build().also { instance = it }
    }
  }
}
