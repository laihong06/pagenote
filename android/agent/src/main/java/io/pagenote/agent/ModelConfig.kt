package io.pagenote.agent

/**
 * 模型配置解析（任务 10 / EARS Agent 与模型）。
 *
 * 约定：
 * - BaseURL / Key / 模型名全部可配；任一缺失即报缺配置，不 fallback 到内置模型；
 * - 默认可配本地中转 `http://127.0.0.1:3457/v1` 与 `cline-free/mimo-v2.6-flash`，
 *   仅作默认配置项；key 永不硬编码进代码；
 * - 密钥不落项目目录：本解析器只做内存组装，不写任何文件（由调用方决定存哪，
 *   且不得存入项目目录）。
 *
 * 纯内存解析，不调网络，可单测。
 */
object ModelConfig {
  data class Input(
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val model: String? = null,
  )

  /** 缺配置时的明确提示（直接抛 MissingModelConfigException，由调用方展示）。 */
  fun resolve(input: Input = Input()): ModelEndpoint {
    val baseUrl = input.baseUrl?.trim().orEmpty()
      .ifEmpty { OpenAiCompatibleDefaults.LOCAL_RELAY_BASE_URL }
    val model = input.model?.trim().orEmpty()
      .ifEmpty { OpenAiCompatibleDefaults.LOCAL_RELAY_MODEL }
    val apiKey = input.apiKey?.trim().orEmpty()
    if (baseUrl.isBlank() || model.isBlank() || apiKey.isBlank()) {
      throw MissingModelConfigException()
    }
    return ModelEndpoint(baseUrl = baseUrl, apiKey = apiKey, model = model)
  }

  /** 显式清空配置：用户清掉 Key/地址/模型后，下次 resolve 必抛缺配置。 */
  fun cleared(): Input = Input(baseUrl = "", apiKey = "", model = "")
}
