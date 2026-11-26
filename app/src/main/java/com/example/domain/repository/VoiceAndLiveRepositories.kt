package com.example.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Transcribes short recorded household expense/income speech into verbatim text using Gemini.
 */
interface AudioTranscriptionRepository {
  suspend fun transcribeForEntry(
    wavBytes: ByteArray,
    languageTag: String,
  ): Result<String>
}

data class LiveTranscriptTurn(
  val id: String,
  val isUser: Boolean,
  val text: String,
)

sealed interface LiveAdvisorSessionState {
  data object Idle : LiveAdvisorSessionState

  data object Connecting : LiveAdvisorSessionState

  data class Active(
    val userPartialTranscript: String = "",
    val advisorPartialTranscript: String = "",
    val turns: List<LiveTranscriptTurn> = emptyList(),
    val toolsUsed: List<String> = emptyList(),
  ) : LiveAdvisorSessionState

  data class Error(val message: String) : LiveAdvisorSessionState
}

/**
 * Manages a bidirectional Gemini Live audio advisor session grounded strictly in
 * [AiGroundingToolbox] analytics tool calls on the user's authorized snapshot.
 */
interface LiveVoiceAdvisorRepository {
  val sessionState: StateFlow<LiveAdvisorSessionState>

  suspend fun startConversation(snapshot: AdvisorAuthorizedSnapshot)

  suspend fun stopConversation()
}
