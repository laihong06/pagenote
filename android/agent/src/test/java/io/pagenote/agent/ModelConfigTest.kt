package io.pagenote.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelConfigTest {
  @Test
  fun customEndpointPassesThrough() {
    val endpoint = ModelConfig.resolve(
      ModelConfig.Input(baseUrl = "https://example.com/v1", apiKey = "k", model = "m"),
    )
    assertEquals("https://example.com/v1", endpoint.baseUrl)
    assertEquals("k", endpoint.apiKey)
    assertEquals("m", endpoint.model)
  }

  @Test
  fun defaultsPointToLocalRelay() {
    val endpoint = ModelConfig.resolve(ModelConfig.Input(apiKey = "k"))
    assertEquals(OpenAiCompatibleDefaults.LOCAL_RELAY_BASE_URL, endpoint.baseUrl)
    assertEquals(OpenAiCompatibleDefaults.LOCAL_RELAY_MODEL, endpoint.model)
  }

  @Test(expected = MissingModelConfigException::class)
  fun missingApiKeyRejected() {
    ModelConfig.resolve(ModelConfig.Input(baseUrl = "https://example.com/v1", model = "m"))
  }

  @Test(expected = MissingModelConfigException::class)
  fun blankApiKeyRejected() {
    ModelConfig.resolve(ModelConfig.Input(apiKey = "  "))
  }

  @Test(expected = MissingModelConfigException::class)
  fun clearedConfigRejected() {
    ModelConfig.resolve(ModelConfig.cleared())
  }
}
