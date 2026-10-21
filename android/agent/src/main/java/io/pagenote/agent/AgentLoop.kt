package io.pagenote.agent

data class AgentTask(
  val documentId: String,
  val page: Int,
  val instruction: String,
  val expectedRevision: String?,
  val maxSteps: Int = 8,
)

sealed interface AgentEvent {
  data class ToolCall(val name: String, val args: Map<String, String>) : AgentEvent
  data class Done(val pages: List<Int>, val updated: List<Int>) : AgentEvent
  data class Failed(val reason: String) : AgentEvent
}

object AgentLoop {
  val LOCAL_TOOLS = listOf(
    "read_page",
    "write_note",
    "patch_note",
    "read_review",
    "write_review_status",
    "read_handoff",
    "write_handoff",
  )

  fun validate(task: AgentTask) {
    require(task.page >= 1) { "bad page" }
    require(task.instruction.isNotBlank()) { "empty instruction" }
    require(task.maxSteps in 1..16) { "maxSteps out of range" }
  }
}
