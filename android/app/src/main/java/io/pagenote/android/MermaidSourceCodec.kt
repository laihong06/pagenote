package io.pagenote.android

import java.nio.charset.StandardCharsets
import java.util.Base64

internal object MermaidSourceCodec {
  const val SCHEME = "pagenote-mermaid"
  const val MAX_SOURCE_CHARS = 100_000
  private const val MAX_ENCODED_CHARS = MAX_SOURCE_CHARS * 4

  fun encode(source: String): String {
    require(source.length <= MAX_SOURCE_CHARS) { "Mermaid source exceeds the supported size" }
    val payload = Base64.getUrlEncoder().withoutPadding()
      .encodeToString(source.toByteArray(StandardCharsets.UTF_8))
    return "$SCHEME:$payload"
  }

  fun decode(uri: String): String {
    require(uri.startsWith("$SCHEME:")) { "Unsupported Mermaid source URI" }
    val payload = uri.substringAfter(':')
    require(payload.length <= MAX_ENCODED_CHARS) { "Mermaid source exceeds the supported size" }
    val source = String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8)
    require(source.length <= MAX_SOURCE_CHARS) { "Mermaid source exceeds the supported size" }
    return source
  }
}
