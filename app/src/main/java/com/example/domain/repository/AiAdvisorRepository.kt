package com.example.domain.repository

import com.example.domain.model.AdvisorEvidenceItem
import com.example.domain.model.AdvisorFeedback
import com.example.domain.model.AdvisorResponse
import com.example.domain.model.Category
import com.example.domain.model.Member
import com.example.domain.model.Transaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class AdvisorAuthorizedSnapshot(
  val familyName: String,
  val currentMember: Member,
  val members: List<Member>,
  val categories: List<Category>,
  val transactions: List<Transaction>,
  val referenceDate: LocalDate = LocalDate.now(),
  val languageTag: String = "en",
)

sealed interface AdvisorStreamEvent {
  data class ToolsReady(
    val comparisonWindow: String?,
    val evidence: List<AdvisorEvidenceItem>,
    val toolsUsed: List<String>,
  ) : AdvisorStreamEvent

  data class StreamingText(
    val partialFinding: String,
    val comparisonWindow: String?,
    val evidence: List<AdvisorEvidenceItem>,
    val toolsUsed: List<String>,
  ) : AdvisorStreamEvent

  data class Completed(val response: AdvisorResponse) : AdvisorStreamEvent

  data class Failed(val error: Throwable) : AdvisorStreamEvent
}

interface AiAdvisorRepository {
  suspend fun askQuestion(
    question: String,
    questionId: String?,
    snapshot: AdvisorAuthorizedSnapshot,
  ): Result<AdvisorResponse>

  fun askQuestionStream(
    question: String,
    questionId: String?,
    snapshot: AdvisorAuthorizedSnapshot,
  ): Flow<AdvisorStreamEvent> = flow {
    askQuestion(question, questionId, snapshot)
      .onSuccess { emit(AdvisorStreamEvent.Completed(it)) }
      .onFailure { emit(AdvisorStreamEvent.Failed(it)) }
  }

  fun askQuestionWithSearchStream(
    question: String,
    questionId: String?,
    snapshot: AdvisorAuthorizedSnapshot,
  ): Flow<AdvisorStreamEvent> = askQuestionStream(question, questionId, snapshot)

  suspend fun recordFeedback(
    responseId: String,
    feedback: AdvisorFeedback,
  )
}
