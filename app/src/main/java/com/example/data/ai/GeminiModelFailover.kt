package com.example.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Coordinates fast-first Gemini model ordering and transparent failover when any
 * model reports high traffic, quota contention, transient unavailability, or timeout.
 */
object GeminiModelFailover {

  private const val TAG = "GeminiModelFailover"

  /** Ultra-fast tool selection chain prioritizing lowest latency and highest throughput. */
  val TOOL_ROUTER_MODELS: List<String> =
    listOf(
      "gemini-3.5-flash-lite",
      "gemini-3.8-flash",
    )

  /** Fast streaming prose chain: Flash-Lite first for speed, with Flash & Hydrogen fallbacks. */
  val PROSE_STREAM_MODELS: List<String> =
    listOf(
      "gemini-3.5-flash-lite",
      "gemini-3.8-flash",
      "gemini-4-hydrogen",
    )

  /** Live Google Search grounding chain avoiding single-model high-traffic bottlenecks. */
  val SEARCH_GROUNDED_MODELS: List<String> =
    listOf(
      "gemini-3.8-flash",
      "gemini-3.5-flash-lite",
      "gemini-4-hydrogen",
    )

  /** Audio transcription chain: dedicated transcribe model first, then multimodal Flash models. */
  val TRANSCRIBE_MODELS: List<String> =
    listOf(
      "gemini-3.5-transcribe",
      "gemini-3.5-flash-lite",
      "gemini-3.8-flash",
    )

  private val RETRYABLE_SIGNATURES =
    listOf(
      "high traffic",
      "high demand",
      "experiencing high",
      "overloaded",
      "too many requests",
      "rate limit",
      "rate_limit",
      "ratelimit",
      "quota",
      "resource_exhausted",
      "resource exhausted",
      "service unavailable",
      "unavailable",
      "temporarily",
      "try again",
      "capacity",
      "deadline_exceeded",
      "deadline exceeded",
      "429",
      "500",
      "502",
      "503",
      "504",
      "not supported",
      "does not support",
      "model not found",
    )

  fun isRetryableModelFailure(error: Throwable): Boolean {
    if (error is TimeoutCancellationException) return true
    val messages =
      generateSequence(error) { it.cause }
        .mapNotNull { t ->
          val cls = t::class.java.simpleName.lowercase()
          val msg = t.message?.lowercase().orEmpty()
          "$cls $msg"
        }
        .joinToString(" | ")
    return RETRYABLE_SIGNATURES.any { sig -> sig in messages }
  }

  fun toFriendlyErrorMessage(error: Throwable, hindi: Boolean): String {
    if (isRetryableModelFailure(error)) {
      return if (hindi) {
        "सभी AI मॉडल पर अभी अधिक ट्रैफ़िक है या समय सीमा पूरी हो गई। कृपया कुछ सेकंड बाद पुनः प्रयास करें।"
      } else {
        "AI models are currently experiencing high traffic or timed out. Please try again in a few seconds."
      }
    }
    return error.localizedMessage?.takeIf { it.isNotBlank() }
      ?: if (hindi) {
        "AI सलाहकार अभी उत्तर पूरा नहीं कर सका।"
      } else {
        "AI Advisor could not complete your request."
      }
  }

  suspend fun <M, T> executeWithFailover(
    modelNames: List<String>,
    perModelTimeoutMs: Long,
    modelForName: (String) -> M,
    block: suspend (modelName: String, model: M) -> T,
  ): Pair<String, T> {
    var lastError: Throwable? = null
    for ((index, modelName) in modelNames.withIndex()) {
      val hasNext = index < modelNames.lastIndex
      try {
        val result =
          withTimeout(perModelTimeoutMs) {
            block(modelName, modelForName(modelName))
          }
        return modelName to result
      } catch (e: TimeoutCancellationException) {
        lastError = e
        Log.w(TAG, "Model $modelName timed out after ${perModelTimeoutMs}ms; hasFallback=$hasNext", e)
        if (!hasNext) throw e
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        lastError = e
        val retryable = isRetryableModelFailure(e)
        Log.w(TAG, "Model $modelName failed (retryable=$retryable, hasFallback=$hasNext)", e)
        if (!hasNext || !retryable) throw e
      }
    }
    throw lastError ?: IllegalStateException("No AI models available.")
  }

  suspend fun <M, C> streamWithFailover(
    modelNames: List<String>,
    perModelTimeoutMs: Long,
    modelForName: (String) -> M,
    streamCall: (M) -> kotlinx.coroutines.flow.Flow<C>,
    extractText: (C) -> String?,
    onChunk: suspend (modelName: String, chunk: C) -> Unit,
  ): String {
    var lastError: Throwable? = null
    for ((index, modelName) in modelNames.withIndex()) {
      val hasNext = index < modelNames.lastIndex
      var emittedAnyText = false
      try {
        withTimeout(perModelTimeoutMs) {
          streamCall(modelForName(modelName)).collect { chunk ->
            if (!extractText(chunk).isNullOrEmpty()) {
              emittedAnyText = true
            }
            onChunk(modelName, chunk)
          }
        }
        if (emittedAnyText || !hasNext) {
          return modelName
        }
        Log.w(TAG, "Model $modelName returned empty stream; trying fallback.")
      } catch (e: TimeoutCancellationException) {
        lastError = e
        Log.w(
          TAG,
          "Stream from $modelName timed out (emittedText=$emittedAnyText, hasFallback=$hasNext)",
          e,
        )
        if (emittedAnyText || !hasNext) throw e
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        lastError = e
        val retryable = isRetryableModelFailure(e)
        Log.w(
          TAG,
          "Stream from $modelName failed (retryable=$retryable, emittedText=$emittedAnyText, hasFallback=$hasNext)",
          e,
        )
        if (emittedAnyText || !hasNext || !retryable) throw e
      }
    }
    throw lastError ?: IllegalStateException("No AI streaming models available.")
  }
}
