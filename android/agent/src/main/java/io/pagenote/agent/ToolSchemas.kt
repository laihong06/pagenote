package io.pagenote.agent

/**
 * 本地工具参数校验（任务 10 / 本地工具收敛集）。
 *
 * 约定：
 * - 7 个已接线 LOCAL_TOOLS 每个声明必填参数；缺参/空参/非法页码在分发前拒绝，
 *   不打进各模块实现；
 * - 写类工具（write_note/patch_note/write_review_status/write_handoff）要求
 *   携带 revision 语义参数，缺失即拒绝，不静默覆盖；
 * - 纯内存校验，不调网络，可单测。
 */
object ToolSchemas {
  data class Schema(
    val required: List<String> = emptyList(),
    val requirePage: Boolean = false,
    /** 写类工具必须携带的 revision 语义参数名。 */
    val revisionParam: String? = null,
  )

  val SCHEMAS: Map<String, Schema> = mapOf(
    "read_page" to Schema(requirePage = true),
    "write_note" to Schema(requirePage = true, revisionParam = "expectedRevision"),
    "patch_note" to Schema(requirePage = true, revisionParam = "expectedRevision"),
    "read_review" to Schema(),
    "write_review_status" to Schema(required = listOf("id", "status")),
    "read_handoff" to Schema(),
    "write_handoff" to Schema(requirePage = true, required = listOf("summary", "sourceRevision"), revisionParam = "expectedRevision"),
  )

  sealed interface Check {
    data object Ok : Check
    data class Rejected(val reason: String) : Check
  }

  fun check(tool: ToolRequest): Check {
    val schema = SCHEMAS[tool.name] ?: return Check.Rejected("unknown tool: ${tool.name}")
    if (!AgentLoop.LOCAL_TOOLS.contains(tool.name)) {
      return Check.Rejected("unknown tool: ${tool.name}")
    }
    for (param in schema.required) {
      if (tool.args[param].isNullOrBlank()) {
        return Check.Rejected("missing param: $param for ${tool.name}")
      }
    }
    val page = tool.args["page"]
    if (schema.requirePage) {
      val number = page?.toIntOrNull()
      if (number == null || number < 1) {
        return Check.Rejected("bad page for ${tool.name}")
      }
    }
    schema.revisionParam?.let { param ->
      if (tool.args[param].isNullOrBlank()) {
        return Check.Rejected("missing param: $param for ${tool.name}")
      }
    }
    return Check.Ok
  }
}
