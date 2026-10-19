package io.pagenote.agent

/**
 * 端侧 Agent 任务循环执行器（任务 10）。
 *
 * 桌面真相映射：
 * - 逐页独立请求 + 相邻源页材料：调用方按页组装 messages，本执行器不跨页拼全章；
 * - 按 revision 分批读 / 保存带 expectedRevision：落在各工具实现里，执行器只透传结果，
 *   revision 冲突视为工具失败并继续循环，不静默覆盖；
 * - 限流 / 超步：停止并报告实际完成范围，不伪称完成。
 *
 * 模型输出协议（极简行协议，保持可测、不绑死某一模型）：
 * - "DONE"：任务完成；
 * - "TOOL <name> [k=v ...]"：调用本地工具；
 * 其他输出视为需要继续的中间回复（记一步，不分发工具）。
 */
class AgentRunner(
  private val chat: ChatClient,
  private val tools: ToolDispatcher = LocalToolDispatcher(),
) {
  suspend fun run(
    task: AgentTask,
    endpoint: ModelEndpoint,
    messages: List<Map<String, String>>,
    dispatcher: ToolDispatcher? = null,
  ): AgentEvent {
    AgentLoop.validate(task)
    // 缺 key / 缺模型 / 缺地址：直接抛 MissingModelConfigException，不 fallback。
    if (endpoint.baseUrl.isBlank() || endpoint.apiKey.isBlank() || endpoint.model.isBlank()) {
      throw MissingModelConfigException()
    }
    val completedPages = LinkedHashSet<Int>()
    val updatedPages = LinkedHashSet<Int>()

    val history = messages.toMutableList()
    var step = 0
    while (true) {
      if (step >= task.maxSteps) {
        return AgentEvent.Failed("step limit exceeded after $step steps")
      }
      step++
      val reply = try {
        chat.complete(endpoint, history.toList())
      } catch (e: MissingModelConfigException) {
        throw e
      } catch (e: IllegalStateException) {
        // 限流 / 空响应 / HTTP 错误：报实际完成范围，不伪称完成。
        if (isRateLimit(e)) {
          return AgentEvent.Failed("rate limited at step $step")
        }
        return AgentEvent.Failed(e.message ?: "model call failed at step $step")
      }
      history.add(mapOf("role" to "assistant", "content" to reply))
      val decision = parse(reply)
      when (decision) {
        is StepDecision.Done -> {
          return AgentEvent.Done(
            pages = completedPages.toList(),
            updated = updatedPages.toList(),
          )
        }
        is StepDecision.Waiting -> {
          history.add(mapOf("role" to "user", "content" to "continue"))
        }
        is StepDecision.UseTool -> {
          val result = (dispatcher ?: tools).dispatch(decision.request)
          val unknownTool = !result.ok && result.detail.startsWith("unknown tool:")
          if (!unknownTool) {
            val touched = decision.touchedPage ?: result.touchedPage
            if (touched != null) completedPages.add(touched)
            if (result.updatedPage != null) updatedPages.add(result.updatedPage)
          }
          if (!result.ok) {
            // 未知工具、参数拒绝与 revision 冲突都反馈给模型继续循环，不静默覆盖。
            history.add(mapOf("role" to "user", "content" to "tool failed: ${result.detail}"))
          } else {
            history.add(mapOf("role" to "user", "content" to "tool ok: ${result.detail}"))
          }
        }
      }
    }
  }

  private fun isRateLimit(e: IllegalStateException): Boolean {
    val message = e.message.orEmpty().lowercase()
    return message.contains("429") || message.contains("rate") || message.contains("limit")
  }

  private fun parse(reply: String): StepDecision {
    val line = reply.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (line.equals("DONE", ignoreCase = true)) return StepDecision.Done
    if (line.startsWith("TOOL ", ignoreCase = true)) {
      val parts = line.substring(5).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
      if (parts.isEmpty()) return StepDecision.Waiting
      val name = parts.first()
      val args = parts.drop(1).mapNotNull { token ->
        val index = token.indexOf('=')
        if (index <= 0) null else token.substring(0, index) to token.substring(index + 1)
      }.toMap()
      val touched = args["page"]?.toIntOrNull()
      return StepDecision.UseTool(ToolRequest(name, args), touched)
    }
    return StepDecision.Waiting
  }

  private sealed interface StepDecision {
    data object Done : StepDecision
    data object Waiting : StepDecision
    data class UseTool(val request: ToolRequest, val touchedPage: Int?) : StepDecision
  }
}
