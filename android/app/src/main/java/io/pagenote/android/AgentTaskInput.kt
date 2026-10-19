package io.pagenote.android

import io.pagenote.agent.AgentTask
import io.pagenote.agent.PageRequest

data class AgentTaskInput(
  val task: AgentTask,
  val messages: List<Map<String, String>>,
) {
  companion object {
    fun build(
      documentId: String,
      page: Int,
      totalPages: Int,
      instruction: String,
      noteMarkdown: String,
      noteRevision: String,
      sourceText: String,
      neighbors: List<Pair<String, Int>> = emptyList(),
      sourceWarnings: List<String> = emptyList(),
      maxSteps: Int = 8,
    ): AgentTaskInput {
      require(documentId.isNotBlank()) { "document ID is blank" }
      require(page >= 1) { "bad page" }
      require(totalPages >= 1 && page <= totalPages) { "page out of range" }
      require(instruction.isNotBlank()) { "empty instruction" }
      require(noteRevision.isNotBlank()) { "note revision is blank" }
      require(sourceText.isNotBlank()) { "source text is blank" }
      val task = AgentTask(
        documentId = documentId,
        page = page,
        instruction = instruction,
        expectedRevision = noteRevision,
        maxSteps = maxSteps,
      )
      val currentText = buildString {
        append("source:\n")
        append(sourceText)
        val warnings = sourceWarnings.filter(String::isNotBlank).distinct()
        if (warnings.isNotEmpty()) {
          append("\n\nsource warnings (verify OCR content against the original document):\n")
          warnings.forEach { append("- ").append(it).append('\n') }
        }
        append("\n\nnote:\n")
        append(noteMarkdown)
      }
      val materials = neighbors.map { (text, neighborPage) ->
        PageRequest.PageMaterial(page = neighborPage, text = text)
      }
      val messages = PageRequest.build(
        task = task,
        current = PageRequest.PageMaterial(page = page, text = currentText, revision = noteRevision),
        neighbors = materials,
        totalPages = totalPages,
      )
      return AgentTaskInput(task = task, messages = messages)
    }
  }
}
