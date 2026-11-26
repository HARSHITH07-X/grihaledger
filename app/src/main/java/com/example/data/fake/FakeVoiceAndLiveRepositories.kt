package com.example.data.fake

import com.example.domain.repository.AdvisorAuthorizedSnapshot
import com.example.domain.repository.AudioTranscriptionRepository
import com.example.domain.repository.LiveAdvisorSessionState
import com.example.domain.repository.LiveTranscriptTurn
import com.example.domain.repository.LiveVoiceAdvisorRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class FakeAudioTranscriptionRepository @Inject constructor() : AudioTranscriptionRepository {

  var nextTranscriptOverride: String? = null
  var nextFailure: Throwable? = null

  override suspend fun transcribeForEntry(
    wavBytes: ByteArray,
    languageTag: String,
  ): Result<String> {
    nextFailure?.let { err ->
      nextFailure = null
      return Result.failure(err)
    }
    nextTranscriptOverride?.let { override ->
      nextTranscriptOverride = null
      return Result.success(override)
    }
    return Result.success(
      if (languageTag.lowercase().startsWith("hi")) {
        "दूध और ब्रेड 180 रुपये यूपीआई आज"
      } else {
        "Milk and bread 180 upi today"
      }
    )
  }
}

@Singleton
class FakeLiveVoiceAdvisorRepository @Inject constructor() : LiveVoiceAdvisorRepository {

  private val _sessionState = MutableStateFlow<LiveAdvisorSessionState>(LiveAdvisorSessionState.Idle)
  override val sessionState: StateFlow<LiveAdvisorSessionState> = _sessionState.asStateFlow()

  override suspend fun startConversation(snapshot: AdvisorAuthorizedSnapshot) {
    val hindi = snapshot.languageTag.lowercase().startsWith("hi")
    _sessionState.value =
      LiveAdvisorSessionState.Active(
        turns =
          listOf(
            LiveTranscriptTurn(
              id = "live_${UUID.randomUUID().toString().take(6)}",
              isUser = false,
              text =
                if (hindi) {
                  "नमस्ते! मैं आपके ${snapshot.familyName} लेजर से जुड़ा हूँ। इस महीने के खर्च या तुलना के बारे में बोलें।"
                } else {
                  "Connected to ${snapshot.familyName}. Speak to ask about this month's spending, categories, or forecast."
                },
            )
          ),
        toolsUsed = listOf("getSummary"),
      )
  }

  override suspend fun stopConversation() {
    _sessionState.value = LiveAdvisorSessionState.Idle
  }
}
