package com.example.ui.screens

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AppLanguage
import com.example.domain.model.Category
import com.example.domain.model.Family
import com.example.domain.model.Member
import com.example.domain.model.Transaction
import com.example.domain.repository.AdvisorAuthorizedSnapshot
import com.example.domain.repository.AudioTranscriptionRepository
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.LiveAdvisorSessionState
import com.example.domain.repository.LiveVoiceAdvisorRepository
import com.example.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class LiveVoiceAdvisorUiState(
  val sessionState: LiveAdvisorSessionState = LiveAdvisorSessionState.Idle,
  val isRecordingDictation: Boolean = false,
  val isTranscribingDictation: Boolean = false,
  val dictationLevel: Float = 0f,
  val dictationSeconds: Int = 0,
  val voiceError: String? = null,
)

@HiltViewModel
class LiveVoiceAdvisorViewModel @Inject constructor(
  sessionRepository: SessionRepository,
  familyRepository: FamilyRepository,
  ledgerRepository: LedgerRepository,
  private val liveVoiceAdvisorRepository: LiveVoiceAdvisorRepository,
  private val audioTranscriptionRepository: AudioTranscriptionRepository,
) : ViewModel() {

  private val dictationRecording = MutableStateFlow(false)
  private val dictationTranscribing = MutableStateFlow(false)
  private val dictationLevel = MutableStateFlow(0f)
  private val dictationSeconds = MutableStateFlow(0)
  private val voiceError = MutableStateFlow<String?>(null)

  private var latestSnapshot: AdvisorAuthorizedSnapshot? = null

  private val workspaceFlow =
    combine(
      familyRepository.observeFamily(),
      sessionRepository.observeCurrentMember(),
      familyRepository.observeMembers(),
      familyRepository.observeCategories(),
      ledgerRepository.observeTransactions(),
    ) { family: Family?, member: Member?, members: List<Member>, categories: List<Category>, transactions: List<Transaction> ->
      if (member != null) {
        latestSnapshot =
          AdvisorAuthorizedSnapshot(
            familyName = family?.name ?: "Family Ledger",
            currentMember = member,
            members = members,
            categories = categories,
            transactions = transactions,
            referenceDate = LocalDate.now(),
          )
      }
      member != null
    }

  val uiState: StateFlow<LiveVoiceAdvisorUiState> =
    combine(
      workspaceFlow,
      liveVoiceAdvisorRepository.sessionState,
      dictationRecording,
      dictationTranscribing,
      combine(dictationLevel, dictationSeconds, voiceError) { lvl, sec, err -> Triple(lvl, sec, err) },
    ) { _, session, rec, transcribing, extra ->
      LiveVoiceAdvisorUiState(
        sessionState = session,
        isRecordingDictation = rec,
        isTranscribingDictation = transcribing,
        dictationLevel = extra.first,
        dictationSeconds = extra.second,
        voiceError = extra.third,
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = LiveVoiceAdvisorUiState(),
      )

  fun startLiveConversation(language: AppLanguage) {
    val snapshot = latestSnapshot
    if (snapshot == null) {
      voiceError.value =
        if (language == AppLanguage.HINDI) {
          "कार्यक्षेत्र लोड हो रहा है। कृपया कुछ क्षण बाद पुनः प्रयास करें।"
        } else {
          "Workspace is still loading. Try again in a moment."
        }
      return
    }
    voiceError.value = null
    viewModelScope.launch {
      liveVoiceAdvisorRepository.startConversation(snapshot.copy(languageTag = language.tag))
    }
  }

  fun stopLiveConversation() {
    viewModelScope.launch {
      liveVoiceAdvisorRepository.stopConversation()
    }
  }

  fun onDictationProgress(level: Float, seconds: Int) {
    dictationRecording.value = true
    dictationLevel.value = level
    dictationSeconds.value = seconds
  }

  fun onDictationStopped() {
    dictationRecording.value = false
  }

  fun transcribeQuestionAudio(
    wavBytes: ByteArray,
    languageTag: String,
    onTranscript: (String) -> Unit,
  ) {
    viewModelScope.launch {
      dictationRecording.value = false
      dictationTranscribing.value = true
      voiceError.value = null
      audioTranscriptionRepository
        .transcribeForEntry(wavBytes, languageTag)
        .onSuccess { text ->
          dictationTranscribing.value = false
          onTranscript(text)
        }
        .onFailure { err ->
          dictationTranscribing.value = false
          voiceError.value = err.localizedMessage
        }
    }
  }

  fun setVoiceError(message: String) {
    dictationRecording.value = false
    dictationTranscribing.value = false
    voiceError.value = message
  }

  fun clearVoiceError() {
    voiceError.value = null
  }

  override fun onCleared() {
    super.onCleared()
    stopLiveConversation()
  }
}
