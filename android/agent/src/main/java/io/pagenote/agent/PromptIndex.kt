package io.pagenote.agent

import java.security.MessageDigest

/**
 * 提示词索引（任务 10 / design 3.5）。
 *
 * 对齐桌面 `desktop/prompts.cjs`：
 * - 模块 id：entry/generation/profile/tag-edit/learning/page-tutor/output/learner；
 * - 按场景选模块（routes）：generation / revision / format-fix / generic；
 * - 按 revision 分批读：调用方带 knownRevision，命中时只回 unchanged，不回正文，
 *   不一次全量加载。
 *
 * 安卓侧内置最小索引（id/when/first），模块正文由各 skill/asset 后续提供，
 * 这里只做路由与分批读判定，保持纯内存、可单测。
 */
object PromptIndex {
  data class Module(
    val id: String,
    val whenToUse: String,
  )

  data class Route(
    val scenario: String,
    val modules: List<String>,
    val first: String,
  )

  data class ReadResult(
    val id: String,
    val revision: String,
    val unchanged: Boolean,
    /** revision 命中时为空：调用方复用已缓存正文，不重传。 */
    val text: String?,
  )

  val MODULES = listOf(
    Module("entry", "任务入口、场景选择、数据与工具位置"),
    Module("generation", "初次生成或补讲指定页的执行策略"),
    Module("profile", "生成与修改共享的可编辑偏好和候选记录"),
    Module("tag-edit", "按AI Tag、批注或圈选要求修改已有内容"),
    Module("learning", "解释、教学或重讲文档知识"),
    Module("page-tutor", "逐页教学的知识递进与多模态表达，不规定 Agent 会话调度"),
    Module("output", "生成或修改含公式、代码、图表、图片的笔记"),
    Module("learner", "需要采用用户明确提供的基础、目标和偏好"),
  )

  val ROUTES = listOf(
    Route(
      scenario = "generation",
      modules = listOf("generation", "learning", "page-tutor", "output", "learner", "profile"),
      first = "读取用户范围与当前/相邻源页；不要先读取全部历史讲稿",
    ),
    Route(
      scenario = "revision",
      modules = listOf("tag-edit", "output", "learner", "profile"),
      first = "读取待处理AI批注及目标页已有笔记；需要重讲时再读learning",
    ),
    Route(
      scenario = "format-fix",
      modules = listOf("output"),
      first = "定位用户指定段落并读取现有内容；不自动重生成整章",
    ),
    Route(
      scenario = "generic",
      modules = emptyList(),
      first = "直接依照本次用户要求，按需使用文档工具",
    ),
  )

  /** 按场景选模块，未知场景回 generic 路由。用户当前要求优先：调用方先定场景。 */
  fun routeFor(scenario: String): Route {
    return ROUTES.firstOrNull { it.scenario == scenario } ?: ROUTES.last()
  }

  /**
   * 按 revision 分批读：text 为本批模块正文（调用方按需逐批传入，不一次全量）。
   * knownRevision 命中时只报 unchanged，不回正文。
   */
  fun read(id: String, text: String, knownRevision: String? = null): ReadResult {
    require(MODULES.any { it.id == id }) { "unknown prompt module: $id" }
    val revision = sha256(text)
    val unchanged = knownRevision != null && knownRevision == revision
    return ReadResult(
      id = id,
      revision = revision,
      unchanged = unchanged,
      text = if (unchanged) null else text,
    )
  }

  private fun sha256(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
  }
}
