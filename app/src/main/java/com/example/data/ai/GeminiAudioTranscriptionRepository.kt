package com.example.data.ai

import android.util.Log
import com.example.domain.model.AppLanguage
import com.example.domain.repository.AudioTranscriptionRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@Singleton
class GeminiAudioTranscriptionRepository @Inject constructor() : AudioTranscriptionRepository {

  private val cachedModels = ConcurrentHashMap<String, GenerativeModel>()

  private fun getTranscriptionModel(modelName: String): GenerativeModel =
    cachedModels.computeIfAbsent(modelName) { name ->
      Firebase.ai.generativeModel(
        modelName = name,
        generationConfig =
          generationConfig {
            temperature = 0.0f
            maxOutputTokens = 120
          },
      )
    }

  override suspend fun transcribeForEntry(
    wavBytes: ByteArray,
    languageTag: String,
  ): Result<String> {
    val hindi = AppLanguage.fromTag(languageTag) == AppLanguage.HINDI
    if (wavBytes.size <= 44) {
      return Result.failure(
        IllegalArgumentException(
          if (hindi) {
            "ऑडियो रिकॉर्ड नहीं हुआ। कृपया माइक पर टैप करके फिर बोलें।"
          } else {
            "No speech audio captured. Please tap the mic and speak clearly."
          }
        )
      )
    }

    return try {
      val hintLanguage =
        if (hindi) {
          "Speaker may speak Hindi, Hinglish, or English."
        } else {
          "Speaker may speak English, Hinglish, or Hindi."
        }
      val (_, response) =
        withTimeout(TOTAL_TIMEOUT_MS) {
          GeminiModelFailover.executeWithFailover(
            modelNames = GeminiModelFailover.TRANSCRIBE_MODELS,
            perModelTimeoutMs = PER_MODEL_TIMEOUT_MS,
            modelForName = ::getTranscriptionModel,
          ) { _, model ->
            model.generateContent(
              content {
                inlineData(wavBytes, "audio/wav")
                text(
                  "$hintLanguage Transcribe this short Indian household expense/income voice note verbatim. " +
                    "Write spoken numbers cleanly as Western digits (e.g., 180, 1250). " +
                    "Output ONLY the plain transcript with no quotes or explanation."
                )
              }
            )
          }
        }
      val transcript = response.text?.trim().orEmpty()
      if (transcript.isBlank()) {
        Result.failure(
          IllegalStateException(
            if (hindi) {
              "आवाज़ समझ नहीं आई। कृपया दोबारा बोलें।"
            } else {
              "Could not hear a clear entry. Please speak again."
            }
          )
        )
      } else {
        Result.success(transcript)
      }
    } catch (e: TimeoutCancellationException) {
      Log.w(TAG, "Audio transcription timed out", e)
      Result.failure(
        IllegalStateException(GeminiModelFailover.toFriendlyErrorMessage(e, hindi))
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Audio transcription failed", e)
      Result.failure(
        IllegalStateException(GeminiModelFailover.toFriendlyErrorMessage(e, hindi))
      )
    }
  }

  companion object {
    private const val TAG = "GeminiTranscribeRepo"
    private const val PER_MODEL_TIMEOUT_MS = 12_000L
    private const val TOTAL_TIMEOUT_MS = 30_000L
  }
}
