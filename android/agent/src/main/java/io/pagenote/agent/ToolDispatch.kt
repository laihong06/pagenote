package io.pagenote.agent

/** 单步工具调用请求：模型输出的 tool call，经解析后落到这里。 */
data class ToolRequest(
  val name: String,
  val args: Map<String, String> = emptyMap(),
)

/** 工具执行结果：ok 为真表示工具成功执行，detail 给模型/报告用。 */
data class ToolResult(
  val ok: Boolean,
  val detail: String = "",
  val touchedPage: Int? = null,
  val updatedPage: Int? = null,
)

/**
 * 本地工具分发接口。7 个已接线 LOCAL_TOOLS 由各模块接入实现；
 * 未知工具名必须拒绝，不能静默通过。
 */
interface ToolDispatcher {
  suspend fun dispatch(tool: ToolRequest): ToolResult
}

/** 未注册到 LOCAL_TOOLS 的工具一律拒绝。 */
class LocalToolDispatcher(
  private val handlers: Map<String, suspend (ToolRequest) -> ToolResult> = emptyMap(),
) : ToolDispatcher {
  override suspend fun dispatch(tool: ToolRequest): ToolResult {
    when (val check = ToolSchemas.check(tool)) {
      ToolSchemas.Check.Ok -> Unit
      is ToolSchemas.Check.Rejected -> return ToolResult(ok = false, detail = check.reason)
    }
    val handler = handlers[tool.name]
      ?: return ToolResult(ok = false, detail = "unknown tool: ${tool.name}")
    return handler(tool)
  }
}
