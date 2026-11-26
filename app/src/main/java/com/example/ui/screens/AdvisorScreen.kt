package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.model.AdvisorActionProposal
import com.example.domain.model.AdvisorEvidenceItem
import com.example.domain.model.AdvisorFeedback
import com.example.domain.model.AdvisorProposalKind
import com.example.domain.model.AdvisorQuestions
import com.example.domain.model.AdvisorResponse
import com.example.domain.model.AppLanguage
import com.example.domain.model.Category
import com.example.domain.model.Family
import com.example.domain.model.Member
import com.example.domain.model.SuggestedAdvisorQuestion
import com.example.domain.model.Transaction
import com.example.domain.repository.AdvisorAuthorizedSnapshot
import com.example.domain.repository.AdvisorStreamEvent
import com.example.domain.repository.AiAdvisorRepository
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.LiveAdvisorSessionState
import com.example.domain.repository.SessionRepository
import com.example.ui.analytics.RecordsDrilldownFilter
import com.example.ui.analytics.RecordsFilterCoordinator
import com.example.ui.components.EmptyStateCard
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.theme.GrihaTheme
import com.example.ui.voice.MicWavRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AdvisorInferenceState {
  data object Idle : AdvisorInferenceState

  data class Loading(val questionText: String) : AdvisorInferenceState

  data class Streaming(
    val questionText: String,
    val partialFinding: String,
    val comparisonWindow: String?,
    val evidence: List<AdvisorEvidenceItem>,
    val toolsUsed: List<String>,
  ) : AdvisorInferenceState

  data class Error(
    val questionText: String,
    val questionId: String?,
    val message: String,
  ) : AdvisorInferenceState
}

@Immutable
data class AdvisorUiState(
  val currentMember: Member? = null,
  val familyName: String = "",
  val activeTransactionCount: Int = 0,
  val customInput: String = "",
  val suggestedQuestions: List<SuggestedAdvisorQuestion> = AdvisorQuestions.SUGGESTED,
  val inferenceState: AdvisorInferenceState = AdvisorInferenceState.Idle,
  val responses: List<AdvisorResponse> = emptyList(),
  val expandedWhyIds: Set<String> = emptySet(),
  val useGoogleSearch: Boolean = false,
) {
  val visibleResponses: List<AdvisorResponse>
    get() = responses.filterNot { it.isDismissed }
}

@HiltViewModel
class AdvisorViewModel @Inject constructor(
  sessionRepository: SessionRepository,
  familyRepository: FamilyRepository,
  ledgerRepository: LedgerRepository,
  private val aiAdvisorRepository: AiAdvisorRepository,
  private val filterCoordinator: RecordsFilterCoordinator,
) : ViewModel() {

  private val customInput = MutableStateFlow("")
  private val inferenceState = MutableStateFlow<AdvisorInferenceState>(AdvisorInferenceState.Idle)
  private val responses = MutableStateFlow<List<AdvisorResponse>>(emptyList())
  private val expandedWhyIds = MutableStateFlow<Set<String>>(emptySet())
  private val useGoogleSearch = MutableStateFlow(false)

  private var latestSnapshot: AdvisorAuthorizedSnapshot? = null
  private var lastObservedLedgerEpoch: Int? = null
  private var lastLanguage: AppLanguage = AppLanguage.ENGLISH
  private var activeJob: Job? = null

  private val workspaceFlow =
    combine(
      familyRepository.observeFamily(),
      sessionRepository.observeCurrentMember(),
      familyRepository.observeMembers(),
      familyRepository.observeCategories(),
      ledgerRepository.observeTransactions(),
    ) { family: Family?, member: Member?, members: List<Member>, categories: List<Category>, transactions: List<Transaction> ->
      AdvisorWorkspace(family, member, members, categories, transactions)
    }

  val uiState: StateFlow<AdvisorUiState> =
    combine(
      workspaceFlow,
      customInput,
      inferenceState,
      responses,
      combine(expandedWhyIds, useGoogleSearch) { why, search -> why to search },
    ) { workspace, input, inference, items, extra ->
      val currentEpoch = workspace.family?.ledgerEpoch
      if (currentEpoch != null) {
        val previousEpoch = lastObservedLedgerEpoch
        if (previousEpoch != null && previousEpoch != currentEpoch) {
          activeJob?.cancel()
          responses.value = emptyList()
          expandedWhyIds.value = emptySet()
          inferenceState.value = AdvisorInferenceState.Idle
        }
        lastObservedLedgerEpoch = currentEpoch
      }
      if (workspace.currentMember != null) {
        latestSnapshot =
          AdvisorAuthorizedSnapshot(
            familyName = workspace.family?.name ?: "Family Ledger",
            currentMember = workspace.currentMember,
            members = workspace.members,
            categories = workspace.categories,
            transactions = workspace.transactions,
            referenceDate = LocalDate.now(),
          )
      }
      AdvisorUiState(
        currentMember = workspace.currentMember,
        familyName = workspace.family?.name ?: "Family Ledger",
        activeTransactionCount = workspace.transactions.count { !it.isVoided },
        customInput = input,
        inferenceState = inference,
        responses = items,
        expandedWhyIds = extra.first,
        useGoogleSearch = extra.second,
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AdvisorUiState(),
      )

  fun onCustomInputChange(value: String) {
    customInput.value = value
  }

  fun toggleGoogleSearch() {
    useGoogleSearch.update { !it }
  }

  fun askSuggestedQuestion(
    question: SuggestedAdvisorQuestion,
    language: AppLanguage = AppLanguage.ENGLISH,
  ) {
    ask(question.prompt, question.id, language)
  }

  fun submitCustomQuestion(language: AppLanguage = AppLanguage.ENGLISH) {
    val prompt = customInput.value.trim()
    if (prompt.isEmpty()) return
    customInput.value = ""
    ask(prompt, null, language)
  }

  fun retryLastError() {
    val err = inferenceState.value as? AdvisorInferenceState.Error ?: return
    ask(err.questionText, err.questionId, lastLanguage)
  }

  private fun ask(questionText: String, questionId: String?, language: AppLanguage) {
    lastLanguage = language
    val snapshot = latestSnapshot
    if (snapshot == null) {
      inferenceState.value =
        AdvisorInferenceState.Error(
          questionText = questionText,
          questionId = questionId,
          message =
            if (language == AppLanguage.HINDI) {
              "कार्यक्षेत्र अभी लोड हो रहा है। कृपया कुछ क्षण बाद पुनः प्रयास करें।"
            } else {
              "Workspace is still loading. Please try again in a moment."
            },
        )
      return
    }

    val localizedSnapshot = snapshot.copy(languageTag = language.tag)
    val stream =
      if (useGoogleSearch.value) {
        aiAdvisorRepository.askQuestionWithSearchStream(
          question = questionText,
          questionId = questionId,
          snapshot = localizedSnapshot,
        )
      } else {
        aiAdvisorRepository.askQuestionStream(
          question = questionText,
          questionId = questionId,
          snapshot = localizedSnapshot,
        )
      }
    activeJob?.cancel()
    activeJob =
      viewModelScope.launch {
        inferenceState.value = AdvisorInferenceState.Loading(questionText)
        try {
          stream.collect { event ->
              when (event) {
                is AdvisorStreamEvent.ToolsReady -> {
                  inferenceState.value =
                    AdvisorInferenceState.Streaming(
                      questionText = questionText,
                      partialFinding = "",
                      comparisonWindow = event.comparisonWindow,
                      evidence = event.evidence,
                      toolsUsed = event.toolsUsed,
                    )
                }

                is AdvisorStreamEvent.StreamingText -> {
                  inferenceState.value =
                    AdvisorInferenceState.Streaming(
                      questionText = questionText,
                      partialFinding = event.partialFinding,
                      comparisonWindow = event.comparisonWindow,
                      evidence = event.evidence,
                      toolsUsed = event.toolsUsed,
                    )
                }

                is AdvisorStreamEvent.Completed -> {
                  responses.update { list -> listOf(event.response) + list }
                  inferenceState.value = AdvisorInferenceState.Idle
                }

                is AdvisorStreamEvent.Failed -> {
                  inferenceState.value =
                    AdvisorInferenceState.Error(
                      questionText = questionText,
                      questionId = questionId,
                      message =
                        event.error.localizedMessage
                          ?: if (language == AppLanguage.HINDI) {
                            "सलाहकार अभी जवाब नहीं दे सका।"
                          } else {
                            "Advisor could not answer right now."
                          },
                    )
                }
              }
            }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          inferenceState.value =
            AdvisorInferenceState.Error(
              questionText = questionText,
              questionId = questionId,
              message =
                e.localizedMessage
                  ?: if (language == AppLanguage.HINDI) {
                    "सलाहकार में अनपेक्षित त्रुटि हुई।"
                  } else {
                    "Unexpected advisor error."
                  },
            )
        }
      }
  }

  fun toggleWhySeeingThis(responseId: String) {
    expandedWhyIds.update { current ->
      if (responseId in current) current - responseId else current + responseId
    }
  }

  fun dismissResponse(responseId: String) {
    responses.update { list ->
      list.map { if (it.id == responseId) it.copy(isDismissed = true) else it }
    }
  }

  fun submitFeedback(responseId: String, feedback: AdvisorFeedback) {
    responses.update { list ->
      list.map { if (it.id == responseId) it.copy(feedback = feedback) else it }
    }
    viewModelScope.launch {
      aiAdvisorRepository.recordFeedback(responseId, feedback)
    }
  }

  fun approveProposal(
    responseId: String,
    proposal: AdvisorActionProposal,
    onOpenTransactionDetail: (String) -> Unit,
    onOpenRecords: () -> Unit,
    onOpenInsights: () -> Unit,
  ) {
    responses.update { list ->
      list.map { if (it.id == responseId) it.copy(proposalApproved = true) else it }
    }
    when (proposal.kind) {
      AdvisorProposalKind.REVIEW_TRANSACTION -> {
        val txId = proposal.targetTransactionId
        if (!txId.isNullOrBlank()) {
          onOpenTransactionDetail(txId)
        } else {
          filterCoordinator.apply(
            RecordsDrilldownFilter(
              baseFilter = RecordsFilter.EXPENSE,
              categoryId = proposal.targetCategoryId,
              categoryName = proposal.targetCategoryName,
            )
          )
          onOpenRecords()
        }
      }

      AdvisorProposalKind.REVIEW_RECORDS -> {
        filterCoordinator.apply(
          RecordsDrilldownFilter(
            baseFilter = RecordsFilter.EXPENSE,
            categoryId = proposal.targetCategoryId,
            categoryName = proposal.targetCategoryName,
          )
        )
        onOpenRecords()
      }

      AdvisorProposalKind.INSPECT_INSIGHTS_COMPARISON,
      AdvisorProposalKind.INSPECT_INSIGHTS_FORECAST -> onOpenInsights()
    }
  }

  private data class AdvisorWorkspace(
    val family: Family?,
    val currentMember: Member?,
    val members: List<Member>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
  )
}

private enum class PendingAudioAction {
  DICTATE_QUESTION,
  LIVE_CONVERSATION,
}

@Composable
fun AdvisorRouteScreen(
  onOpenTransactionDetail: (String) -> Unit = {},
  onOpenRecords: () -> Unit = {},
  onOpenInsights: () -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: AdvisorViewModel = hiltViewModel(),
  liveVoiceViewModel: LiveVoiceAdvisorViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val liveState by liveVoiceViewModel.uiState.collectAsStateWithLifecycle()
  val language = LocalAppLanguage.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val recorder = remember(context) { MicWavRecorder(context.applicationContext) }
  val recordJobState = remember { mutableListOf<Job>() }
  var pendingAudioAction by remember { mutableStateOf<PendingAudioAction?>(null) }

  fun launchQuestionDictation() {
    if (liveState.isRecordingDictation) {
      recorder.requestStop()
      return
    }
    recordJobState.forEach { it.cancel() }
    recordJobState.clear()

    val meterJob =
      scope.launch {
        recorder.meter.collect { meter ->
          if (meter.isRecording) {
            liveVoiceViewModel.onDictationProgress(meter.normalizedLevel, meter.elapsedSeconds)
          }
        }
      }
    val captureJob =
      scope.launch {
        try {
          recorder
            .recordUntilStopped()
            .onSuccess { wavBytes ->
              liveVoiceViewModel.transcribeQuestionAudio(wavBytes, language.tag) { transcript ->
                if (transcript.isNotBlank()) {
                  viewModel.onCustomInputChange(transcript)
                }
              }
            }
            .onFailure { err ->
              liveVoiceViewModel.onDictationStopped()
              if (err !is CancellationException) {
                liveVoiceViewModel.setVoiceError(err.localizedMessage ?: "Recording stopped.")
              }
            }
        } finally {
          meterJob.cancel()
        }
      }
    recordJobState.add(meterJob)
    recordJobState.add(captureJob)
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      val action = pendingAudioAction
      pendingAudioAction = null
      if (granted) {
        when (action) {
          PendingAudioAction.DICTATE_QUESTION -> launchQuestionDictation()
          PendingAudioAction.LIVE_CONVERSATION -> liveVoiceViewModel.startLiveConversation(language)
          null -> Unit
        }
      } else {
        liveVoiceViewModel.setVoiceError(
          if (language == AppLanguage.HINDI) {
            "वॉयस सलाहकार के लिए माइक्रोफ़ोन अनुमति आवश्यक है।"
          } else {
            "Microphone permission is required for voice advisor features."
          }
        )
      }
    }

  DisposableEffect(Unit) {
    onDispose {
      recorder.cancel()
      recordJobState.forEach { it.cancel() }
      recordJobState.clear()
      liveVoiceViewModel.stopLiveConversation()
    }
  }

  AdvisorScreen(
    state = state,
    liveVoiceState = liveState,
    onCustomInputChange = viewModel::onCustomInputChange,
    onSuggestedClick = { question -> viewModel.askSuggestedQuestion(question, language) },
    onSubmitQuestion = { viewModel.submitCustomQuestion(language) },
    onToggleGoogleSearch = viewModel::toggleGoogleSearch,
    onDictateQuestionClick = {
      if (liveState.isRecordingDictation) {
        recorder.requestStop()
      } else if (recorder.hasAudioPermission()) {
        launchQuestionDictation()
      } else {
        pendingAudioAction = PendingAudioAction.DICTATE_QUESTION
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      }
    },
    onStartLiveVoiceClick = {
      if (recorder.hasAudioPermission()) {
        liveVoiceViewModel.startLiveConversation(language)
      } else {
        pendingAudioAction = PendingAudioAction.LIVE_CONVERSATION
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      }
    },
    onStopLiveVoiceClick = liveVoiceViewModel::stopLiveConversation,
    onClearVoiceError = liveVoiceViewModel::clearVoiceError,
    onRetry = viewModel::retryLastError,
    onToggleWhy = viewModel::toggleWhySeeingThis,
    onDismiss = viewModel::dismissResponse,
    onFeedback = viewModel::submitFeedback,
    onApproveProposal = { responseId, proposal ->
      viewModel.approveProposal(
        responseId = responseId,
        proposal = proposal,
        onOpenTransactionDetail = onOpenTransactionDetail,
        onOpenRecords = onOpenRecords,
        onOpenInsights = onOpenInsights,
      )
    },
    onEvidenceTransactionClick = onOpenTransactionDetail,
    modifier = modifier,
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvisorScreen(
  state: AdvisorUiState,
  liveVoiceState: LiveVoiceAdvisorUiState = LiveVoiceAdvisorUiState(),
  onCustomInputChange: (String) -> Unit = {},
  onSuggestedClick: (SuggestedAdvisorQuestion) -> Unit = {},
  onSubmitQuestion: () -> Unit = {},
  onToggleGoogleSearch: () -> Unit = {},
  onDictateQuestionClick: () -> Unit = {},
  onStartLiveVoiceClick: () -> Unit = {},
  onStopLiveVoiceClick: () -> Unit = {},
  onClearVoiceError: () -> Unit = {},
  onRetry: () -> Unit = {},
  onToggleWhy: (String) -> Unit = {},
  onDismiss: (String) -> Unit = {},
  onFeedback: (String, AdvisorFeedback) -> Unit = { _, _ -> },
  onApproveProposal: (String, AdvisorActionProposal) -> Unit = { _, _ -> },
  onEvidenceTransactionClick: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  val displayQuestions =
    if (state.suggestedQuestions == AdvisorQuestions.SUGGESTED) {
      AdvisorQuestions.forLanguage(language)
    } else {
      state.suggestedQuestions
    }
  val isBusy =
    state.inferenceState is AdvisorInferenceState.Loading ||
      state.inferenceState is AdvisorInferenceState.Streaming

  Box(
    modifier = modifier.fillMaxSize().testTag("screen_advisor"),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth().testTag("advisor_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item(key = "header_banner") {
        Surface(
          shape = MaterialTheme.shapes.large,
          color = GrihaTheme.colors.brandForest,
          modifier = Modifier.fillMaxWidth().testTag("advisor_header_card"),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = GrihaTheme.colors.onBrandForest,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = stringResource(R.string.advisor_header_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GrihaTheme.colors.onBrandForest,
                modifier = Modifier.semantics { heading() },
              )
            }
            Text(
              text = stringResource(R.string.advisor_header_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = GrihaTheme.colors.onBrandForest.copy(alpha = 0.86f),
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = GrihaTheme.colors.onBrandForest.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = stringResource(R.string.advisor_security_line, state.activeTransactionCount),
                style = MaterialTheme.typography.labelSmall,
                color = GrihaTheme.colors.onBrandForest.copy(alpha = 0.8f),
              )
            }
            FlowRow(
              modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilledTonalButton(
                onClick = onStartLiveVoiceClick,
                enabled = !isBusy,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("advisor_live_voice_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.RecordVoiceOver,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                  text = stringResource(R.string.advisor_live_voice_cta),
                  fontWeight = FontWeight.SemiBold,
                )
              }
              FilterChip(
                selected = state.useGoogleSearch,
                onClick = onToggleGoogleSearch,
                label = { Text(stringResource(R.string.advisor_google_search_toggle)) },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Outlined.Public,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                colors =
                  FilterChipDefaults.filterChipColors(
                    containerColor = GrihaTheme.colors.onBrandForest.copy(alpha = 0.14f),
                    labelColor = GrihaTheme.colors.onBrandForest,
                    iconColor = GrihaTheme.colors.onBrandForest,
                    selectedContainerColor = GrihaTheme.colors.goodContainer,
                    selectedLabelColor = GrihaTheme.colors.onGoodContainer,
                    selectedLeadingIconColor = GrihaTheme.colors.onGoodContainer,
                  ),
                border =
                  FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = state.useGoogleSearch,
                    borderColor = GrihaTheme.colors.onBrandForest.copy(alpha = 0.35f),
                  ),
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("advisor_google_search_toggle"),
              )
            }
          }
        }
      }

      if (state.useGoogleSearch) {
        item(key = "google_search_banner") {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = GrihaTheme.colors.goodContainer,
            modifier = Modifier.fillMaxWidth().testTag("advisor_search_mode_banner"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.Public,
                contentDescription = null,
                tint = GrihaTheme.colors.onGoodContainer,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.advisor_google_search_hint),
                style = MaterialTheme.typography.bodySmall,
                color = GrihaTheme.colors.onGoodContainer,
              )
            }
          }
        }
      }

      liveVoiceState.voiceError?.let { errorMsg ->
        item(key = "voice_error_banner") {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = GrihaTheme.colors.overBudgetContainer,
            modifier = Modifier.fillMaxWidth().testTag("advisor_voice_error"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = errorMsg,
                style = MaterialTheme.typography.bodySmall,
                color = GrihaTheme.colors.onOverBudgetContainer,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = onClearVoiceError,
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = stringResource(R.string.action_dismiss),
                  tint = GrihaTheme.colors.onOverBudgetContainer,
                )
              }
            }
          }
        }
      }

      item(key = "suggested_questions") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = stringResource(R.string.advisor_suggested_heading),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("advisor_suggested_questions"),
          ) {
            displayQuestions.forEach { q ->
              Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier =
                  Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = !isBusy) { onSuggestedClick(q) }
                    .minimumInteractiveComponentSize()
                    .testTag("advisor_suggested_q_${q.id}"),
              ) {
                Column(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Text(
                    text = q.prompt,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = q.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }

      item(key = "question_input") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = state.customInput,
            onValueChange = onCustomInputChange,
            enabled = !isBusy,
            singleLine = true,
            placeholder = {
              Text(
                when {
                  liveVoiceState.isRecordingDictation ->
                    stringResource(R.string.voice_entry_listening, liveVoiceState.dictationSeconds)
                  liveVoiceState.isTranscribingDictation ->
                    stringResource(R.string.voice_entry_transcribing)
                  else -> stringResource(R.string.advisor_input_placeholder)
                }
              )
            },
            modifier = Modifier.weight(1f).testTag("advisor_question_input"),
          )
          FilledTonalIconButton(
            onClick = onDictateQuestionClick,
            enabled = !isBusy && !liveVoiceState.isTranscribingDictation,
            colors =
              IconButtonDefaults.filledTonalIconButtonColors(
                containerColor =
                  if (liveVoiceState.isRecordingDictation) GrihaTheme.colors.overBudgetContainer
                  else MaterialTheme.colorScheme.secondaryContainer,
                contentColor =
                  if (liveVoiceState.isRecordingDictation) GrihaTheme.colors.onOverBudgetContainer
                  else MaterialTheme.colorScheme.onSecondaryContainer,
              ),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("advisor_dictate_button"),
          ) {
            if (liveVoiceState.isTranscribingDictation) {
              CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
              )
            } else {
              Icon(
                imageVector =
                  if (liveVoiceState.isRecordingDictation) Icons.Outlined.Stop else Icons.Outlined.Mic,
                contentDescription = stringResource(R.string.advisor_dictate_cd),
              )
            }
          }
          Button(
            onClick = onSubmitQuestion,
            enabled = !isBusy && state.customInput.isNotBlank(),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = GrihaTheme.colors.good,
                contentColor = GrihaTheme.colors.onGood,
              ),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("advisor_ask_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.Send,
              contentDescription = stringResource(R.string.advisor_ask_cd),
            )
          }
        }
      }

      when (val inf = state.inferenceState) {
        is AdvisorInferenceState.Loading -> {
          item(key = "loading_card") {
            Surface(
              shape = MaterialTheme.shapes.large,
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth().testTag("advisor_loading_card"),
            ) {
              Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                CircularProgressIndicator(
                  color = GrihaTheme.colors.good,
                  modifier = Modifier.size(24.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(
                    text = stringResource(R.string.advisor_loading_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = inf.questionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }

        is AdvisorInferenceState.Streaming -> {
          item(key = "streaming_card") {
            AdvisorStreamingCard(
              streaming = inf,
              onEvidenceTxClick = onEvidenceTransactionClick,
            )
          }
        }

        is AdvisorInferenceState.Error -> {
          item(key = "error_card") {
            Surface(
              shape = MaterialTheme.shapes.large,
              color = GrihaTheme.colors.overBudgetContainer,
              modifier = Modifier.fillMaxWidth().testTag("advisor_error_card"),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = GrihaTheme.colors.onOverBudgetContainer,
                  )
                  Text(
                    text = stringResource(R.string.advisor_error_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = GrihaTheme.colors.onOverBudgetContainer,
                  )
                }
                Text(
                  text = inf.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = GrihaTheme.colors.onOverBudgetContainer,
                )
                OutlinedButton(
                  onClick = onRetry,
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("advisor_retry_button"),
                ) {
                  Text(stringResource(R.string.action_try_again))
                }
              }
            }
          }
        }

        AdvisorInferenceState.Idle -> Unit
      }

      if (state.visibleResponses.isEmpty() && state.inferenceState is AdvisorInferenceState.Idle) {
        item(key = "ready_guide") {
          EmptyStateCard(
            icon = Icons.Outlined.AutoAwesome,
            badgeText = stringResource(R.string.advisor_empty_badge),
            title = stringResource(R.string.advisor_empty_title),
            description = stringResource(R.string.advisor_empty_desc),
            tip = stringResource(R.string.advisor_empty_tip),
            testTag = "advisor_empty_state",
          )
        }
      } else {
        items(state.visibleResponses, key = { it.id }) { response ->
          if (response.isNotEnoughData) {
            AdvisorNotEnoughDataCard(
              response = response,
              onDismiss = { onDismiss(response.id) },
            )
          } else {
            AdvisorAnswerCard(
              response = response,
              isWhyExpanded = response.id in state.expandedWhyIds,
              onToggleWhy = { onToggleWhy(response.id) },
              onDismiss = { onDismiss(response.id) },
              onFeedback = { fb -> onFeedback(response.id, fb) },
              onApproveProposal = { prop -> onApproveProposal(response.id, prop) },
              onEvidenceTxClick = onEvidenceTransactionClick,
            )
          }
        }
      }

      item(key = "bottom_spacer") {
        Spacer(modifier = Modifier.size(72.dp))
      }
    }

    if (liveVoiceState.sessionState !is LiveAdvisorSessionState.Idle) {
      LiveVoiceAdvisorSheet(
        sessionState = liveVoiceState.sessionState,
        onDismissAndEnd = onStopLiveVoiceClick,
      )
    }
  }
}

@Composable
private fun AdvisorStreamingCard(
  streaming: AdvisorInferenceState.Streaming,
  onEvidenceTxClick: (String) -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, GrihaTheme.colors.good.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().animateContentSize().testTag("advisor_streaming_card"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CircularProgressIndicator(
          color = GrihaTheme.colors.good,
          strokeWidth = 2.5.dp,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = stringResource(R.string.advisor_streaming_title),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = GrihaTheme.colors.good,
        )
      }
      Text(
        text = streaming.questionText,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (streaming.partialFinding.isNotBlank()) {
        Text(
          text = streaming.partialFinding,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.testTag("advisor_streaming_text"),
        )
      }
      streaming.comparisonWindow?.let { window ->
        Surface(
          shape = MaterialTheme.shapes.small,
          color = GrihaTheme.colors.goodContainer,
        ) {
          Text(
            text = stringResource(R.string.advisor_window_checked, window),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = GrihaTheme.colors.onGoodContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }
      if (streaming.evidence.isNotEmpty()) {
        Text(
          text = stringResource(R.string.advisor_section_evidence),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          streaming.evidence.forEach { ev ->
            AdvisorEvidenceRow(
              item = ev,
              onEvidenceTxClick = onEvidenceTxClick,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AdvisorEvidenceRow(
  item: AdvisorEvidenceItem,
  onEvidenceTxClick: (String) -> Unit,
) {
  val txId = item.transactionId
  if (txId != null) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier =
        Modifier
          .fillMaxWidth()
          .clip(MaterialTheme.shapes.small)
          .clickable { onEvidenceTxClick(txId) }
          .minimumInteractiveComponentSize()
          .testTag("advisor_tx_link_$txId"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = item.label,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = item.formattedValue,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
          )
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = stringResource(R.string.advisor_open_tx_cd),
            modifier = Modifier.size(15.dp),
            tint = GrihaTheme.colors.good,
          )
        }
      }
    }
  } else {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = item.label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = item.formattedValue,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
private fun AdvisorNotEnoughDataCard(
  response: AdvisorResponse,
  onDismiss: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = GrihaTheme.colors.attentionContainer,
    modifier = Modifier.fillMaxWidth().testTag("advisor_not_enough_data_card"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.advisor_not_enough_data_title),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = GrihaTheme.colors.onAttentionContainer,
        )
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("advisor_dismiss_${response.id}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.action_dismiss),
            tint = GrihaTheme.colors.onAttentionContainer,
          )
        }
      }
      Text(
        text = response.finding,
        style = MaterialTheme.typography.bodyMedium,
        color = GrihaTheme.colors.onAttentionContainer,
      )
      Text(
        text = response.whySeeingThis,
        style = MaterialTheme.typography.bodySmall,
        color = GrihaTheme.colors.onAttentionContainer.copy(alpha = 0.85f),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvisorAnswerCard(
  response: AdvisorResponse,
  isWhyExpanded: Boolean,
  onToggleWhy: () -> Unit,
  onDismiss: () -> Unit,
  onFeedback: (AdvisorFeedback) -> Unit,
  onApproveProposal: (AdvisorActionProposal) -> Unit,
  onEvidenceTxClick: (String) -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    modifier = Modifier.fillMaxWidth().testTag("advisor_answer_${response.id}"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = response.questionText,
          style = MaterialTheme.typography.labelLarge,
          color = GrihaTheme.colors.good,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("advisor_dismiss_${response.id}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.advisor_dismiss_cd),
          )
        }
      }

      // 1. Finding
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = stringResource(R.string.advisor_section_finding),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = response.finding,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.testTag("advisor_finding_${response.id}"),
        )
      }

      // Window + Assumptions
      response.comparisonWindow?.let { window ->
        Surface(
          shape = MaterialTheme.shapes.small,
          color = GrihaTheme.colors.goodContainer,
          modifier = Modifier.testTag("advisor_window_${response.id}"),
        ) {
          Text(
            text = stringResource(R.string.advisor_window_checked, window),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = GrihaTheme.colors.onGoodContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      if (response.assumptions.isNotEmpty()) {
        Column(
          modifier = Modifier.testTag("advisor_assumptions_${response.id}"),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          response.assumptions.forEach { assumption ->
            Text(
              text = "• $assumption",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // 2. Evidence
      if (response.evidence.isNotEmpty()) {
        Column(
          modifier = Modifier.testTag("advisor_evidence_${response.id}"),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = stringResource(R.string.advisor_section_evidence),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          response.evidence.forEach { ev ->
            AdvisorEvidenceRow(
              item = ev,
              onEvidenceTxClick = onEvidenceTxClick,
            )
          }
        }
      }

      if (response.isWebSearchGrounded || response.webCitations.isNotEmpty() || response.webSearchQueries.isNotEmpty()) {
        AdvisorWebCitationsSection(
          citations = response.webCitations,
          searchQueries = response.webSearchQueries,
        )
      }

      // 3. Read-only Action Proposal (requires user approval)
      response.actionProposal?.let { proposal ->
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          border = BorderStroke(1.dp, GrihaTheme.colors.good.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth().testTag("advisor_proposal_${response.id}"),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = stringResource(R.string.advisor_section_proposal),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = GrihaTheme.colors.good,
            )
            Text(
              text = proposal.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = proposal.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (response.proposalApproved) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Outlined.CheckCircle,
                  contentDescription = null,
                  tint = GrihaTheme.colors.good,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = stringResource(R.string.advisor_proposal_approved),
                  style = MaterialTheme.typography.labelMedium,
                  color = GrihaTheme.colors.good,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            } else {
              Button(
                onClick = { onApproveProposal(proposal) },
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = GrihaTheme.colors.good,
                    contentColor = GrihaTheme.colors.onGood,
                  ),
                modifier =
                  Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("advisor_approve_proposal_${response.id}"),
              ) {
                Text(stringResource(R.string.advisor_proposal_approve_cta))
              }
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

      // Why am I seeing this? + Feedback (wraps cleanly at 200% font scale)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        TextButton(
          onClick = onToggleWhy,
          modifier =
            Modifier
              .align(Alignment.CenterVertically)
              .minimumInteractiveComponentSize()
              .testTag("advisor_why_button_${response.id}"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.size(4.dp))
          Text(stringResource(R.string.advisor_why_button))
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          FilterChip(
            selected = response.feedback == AdvisorFeedback.USEFUL,
            onClick = { onFeedback(AdvisorFeedback.USEFUL) },
            label = { Text(stringResource(R.string.advisor_feedback_useful)) },
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.ThumbUp,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = GrihaTheme.colors.goodContainer,
                selectedLabelColor = GrihaTheme.colors.onGoodContainer,
              ),
            modifier =
              Modifier
                .minimumInteractiveComponentSize()
                .testTag("advisor_feedback_useful_${response.id}"),
          )
          FilterChip(
            selected = response.feedback == AdvisorFeedback.NOT_RELEVANT,
            onClick = { onFeedback(AdvisorFeedback.NOT_RELEVANT) },
            label = { Text(stringResource(R.string.advisor_feedback_not_relevant)) },
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.ThumbDown,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = GrihaTheme.colors.attentionContainer,
                selectedLabelColor = GrihaTheme.colors.onAttentionContainer,
              ),
            modifier =
              Modifier
                .minimumInteractiveComponentSize()
                .testTag("advisor_feedback_not_relevant_${response.id}"),
          )
        }
      }

      if (isWhyExpanded) {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth().testTag("advisor_why_panel_${response.id}"),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = response.whySeeingThis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = stringResource(R.string.advisor_tools_run, response.toolsUsed.joinToString(", ")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
