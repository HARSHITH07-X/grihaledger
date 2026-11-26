package com.example.data.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiModelFailoverTest {

  @Test
  fun `model chains prioritize fast flash models and include fallbacks`() {
    assertEquals("gemini-3.5-flash-lite", GeminiModelFailover.TOOL_ROUTER_MODELS.first())
    assertTrue(GeminiModelFailover.TOOL_ROUTER_MODELS.size >= 2)

    assertEquals("gemini-3.5-flash-lite", GeminiModelFailover.PROSE_STREAM_MODELS.first())
    assertTrue(GeminiModelFailover.PROSE_STREAM_MODELS.contains("gemini-3.8-flash"))
    assertTrue(GeminiModelFailover.PROSE_STREAM_MODELS.contains("gemini-4-hydrogen"))

    assertEquals("gemini-3.8-flash", GeminiModelFailover.SEARCH_GROUNDED_MODELS.first())
    assertTrue(GeminiModelFailover.SEARCH_GROUNDED_MODELS.contains("gemini-3.5-flash-lite"))
    assertTrue(GeminiModelFailover.SEARCH_GROUNDED_MODELS.contains("gemini-4-hydrogen"))

    assertEquals("gemini-3.5-transcribe", GeminiModelFailover.TRANSCRIBE_MODELS.first())
    assertTrue(GeminiModelFailover.TRANSCRIBE_MODELS.contains("gemini-3.5-flash-lite"))
  }

  @Test
  fun `isRetryableModelFailure recognizes high traffic, quota, and nested causes`() {
    assertTrue(
      GeminiModelFailover.isRetryableModelFailure(
        IllegalStateException("This model is currently experiencing high traffic usage.")
      )
    )
    assertTrue(
      GeminiModelFailover.isRetryableModelFailure(
        RuntimeException("wrapper", IllegalStateException("HTTP 429 RESOURCE_EXHAUSTED"))
      )
    )
    assertTrue(
      GeminiModelFailover.isRetryableModelFailure(
        IllegalStateException("503 Service Unavailable - high demand")
      )
    )
    assertFalse(
      GeminiModelFailover.isRetryableModelFailure(
        IllegalArgumentException("Invalid input format")
      )
    )
  }

  @Test
  fun `executeWithFailover switches to fast backup model on high traffic error`() = runTest {
    val tried = mutableListOf<String>()
    val (usedModel, value) =
      GeminiModelFailover.executeWithFailover(
        modelNames = GeminiModelFailover.PROSE_STREAM_MODELS,
        perModelTimeoutMs = 5_000L,
        modelForName = { it },
      ) { modelName, _ ->
        tried += modelName
        if (modelName == "gemini-3.5-flash-lite") {
          throw IllegalStateException("This model is experiencing high traffic usage.")
        }
        "ok:$modelName"
      }

    assertEquals(listOf("gemini-3.5-flash-lite", "gemini-3.8-flash"), tried)
    assertEquals("gemini-3.8-flash", usedModel)
    assertEquals("ok:gemini-3.8-flash", value)
  }

  @Test
  fun `executeWithFailover falls back when primary model stalls past timeout`() = runTest {
    val tried = mutableListOf<String>()
    val (usedModel, value) =
      GeminiModelFailover.executeWithFailover(
        modelNames = listOf("slow-model", "fast-model"),
        perModelTimeoutMs = 50L,
        modelForName = { it },
      ) { modelName, _ ->
        tried += modelName
        if (modelName == "slow-model") {
          delay(250L)
        }
        "done"
      }

    assertEquals(listOf("slow-model", "fast-model"), tried)
    assertEquals("fast-model", usedModel)
    assertEquals("done", value)
  }

  @Test
  fun `streamWithFailover fails over cleanly before any text chunk is emitted`() = runTest {
    val tried = mutableListOf<String>()
    val chunks = mutableListOf<String>()
    val usedModel =
      GeminiModelFailover.streamWithFailover(
        modelNames = GeminiModelFailover.PROSE_STREAM_MODELS,
        perModelTimeoutMs = 5_000L,
        modelForName = { it },
        streamCall = { modelName ->
          tried += modelName
          flow {
            if (modelName == "gemini-3.5-flash-lite") {
              throw IllegalStateException("503 Model experiencing high demand")
            }
            emit("Hello ")
            emit("family!")
          }
        },
        extractText = { it },
        onChunk = { _, chunk -> chunks += chunk },
      )

    assertEquals(listOf("gemini-3.5-flash-lite", "gemini-3.8-flash"), tried)
    assertEquals("gemini-3.8-flash", usedModel)
    assertEquals(listOf("Hello ", "family!"), chunks)
  }

  @Test
  fun `toFriendlyErrorMessage localizes high traffic error in English and Hindi`() {
    val err = IllegalStateException("429 This model is experiencing high traffic")
    val en = GeminiModelFailover.toFriendlyErrorMessage(err, hindi = false)
    val hi = GeminiModelFailover.toFriendlyErrorMessage(err, hindi = true)
    assertTrue(en.contains("high traffic", ignoreCase = true))
    assertTrue(hi.contains("ट्रैफ़िक"))
  }
}
