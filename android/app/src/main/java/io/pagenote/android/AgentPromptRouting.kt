package io.pagenote.android

import java.util.Locale

internal object AgentPromptRouting {
  fun forPageTutor(): List<String> = listOf("system", "learning", "page-tutor", "output")

  fun forAgentTask(instruction: String): List<String> {
    val normalized = instruction.lowercase(Locale.ROOT)
    val tagEdit = TAG_WORD.containsMatchIn(normalized) || ANNOTATION_WORD.containsMatchIn(normalized) ||
      "标签" in normalized || "批注" in normalized
    val learning = listOf("解释", "讲解", "教学", "重讲", "explain", "teach", "tutor").any(normalized::contains)
    val learner = listOf("我的基础", "我的学习背景", "我的偏好", "my learning background", "my level").any(normalized::contains)
    return buildList {
      add("system")
      if (learning) {
        add("learning")
        add("page-tutor")
      }
      if (tagEdit) add("tag-edit")
      add("output")
      if (learner) add("learner")
    }
  }

  private val TAG_WORD = Regex("\\b(?:ai\\s+)?tags?\\b")
  private val ANNOTATION_WORD = Regex("\\bannotations?\\b")
}

internal object AgentPromptComposition {
  fun append(
    messages: List<Map<String, String>>,
    modules: List<AgentPromptText>,
  ): List<Map<String, String>> {
    if (modules.isEmpty()) return messages
    val systemIndex = messages.indexOfFirst { it["role"] == "system" }
    require(systemIndex >= 0) { "request is missing its system safety prompt" }
    require(modules.map(AgentPromptText::id).distinct().size == modules.size) { "duplicate prompt module" }
    modules.forEach { module -> AgentPromptCatalog.validate(module.id, module.text) }
    return messages.mapIndexed { index, message ->
      if (index != systemIndex) message else {
        val extra = modules.joinToString("\n\n") { "[${it.id}]\n${it.text}" }
        message + ("content" to listOf(message["content"].orEmpty(), extra).filter(String::isNotBlank).joinToString("\n\n"))
      }
    }
  }
}
