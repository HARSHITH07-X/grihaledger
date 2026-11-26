package com.example.ui.ledger

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.theme.GrihaTheme
import com.example.ui.voice.MicWavRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
  editingTransaction: Transaction?,
  onDismiss: () -> Unit,
  onSaved: (Transaction, Boolean) -> Unit,
  autoStartVoice: Boolean = false,
  viewModel: AddTransactionViewModel = hiltViewModel(),
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val haptic = LocalHapticFeedback.current
  val context = LocalContext.current
  val language = LocalAppLanguage.current
  val scope = rememberCoroutineScope()

  val recorder = remember(context) { MicWavRecorder(context.applicationContext) }
  val recordJobState = remember { mutableListOf<Job>() }

  fun launchVoiceCapture() {
    if (uiState.voiceState is VoiceEntryState.Recording) {
      recorder.requestStop()
      return
    }
    recordJobState.forEach { it.cancel() }
    recordJobState.clear()

    val meterJob =
      scope.launch {
        recorder.meter.collect { meter ->
          if (meter.isRecording) {
            viewModel.onVoiceRecordingProgress(meter.normalizedLevel, meter.elapsedSeconds)
          }
        }
      }
    val captureJob =
      scope.launch {
        try {
          recorder
            .recordUntilStopped()
            .onSuccess { wavBytes ->
              viewModel.processVoiceAudio(wavBytes, language.tag)
            }
            .onFailure { err ->
              if (err !is CancellationException) {
                viewModel.onVoiceError(err.localizedMessage ?: "Recording stopped.")
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
      if (granted) {
        launchVoiceCapture()
      } else {
        viewModel.onVoicePermissionDenied(language.tag)
      }
    }

  fun onMicTapped() {
    if (uiState.voiceState is VoiceEntryState.Recording) {
      recorder.requestStop()
    } else if (recorder.hasAudioPermission()) {
      launchVoiceCapture()
    } else {
      permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      recorder.cancel()
      recordJobState.forEach { it.cancel() }
      recordJobState.clear()
    }
  }

  LaunchedEffect(editingTransaction?.id, autoStartVoice) {
    if (editingTransaction != null) {
      viewModel.startEdit(editingTransaction)
    } else {
      viewModel.startCreate()
      if (autoStartVoice) {
        if (recorder.hasAudioPermission()) {
          launchVoiceCapture()
        } else {
          permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = {
      recorder.cancel()
      onDismiss()
    },
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    modifier = Modifier.testTag("add_transaction_sheet"),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .imePadding(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            stringResource(
              if (uiState.isEditMode) R.string.add_sheet_title_edit
              else R.string.add_sheet_title_new
            ),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.semantics { heading() },
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (!uiState.isEditMode) {
            val isRecordingHeader = uiState.voiceState is VoiceEntryState.Recording
            FilledTonalIconButton(
              onClick = ::onMicTapped,
              enabled = uiState.voiceState !is VoiceEntryState.Transcribing,
              colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                  containerColor =
                    if (isRecordingHeader) GrihaTheme.colors.overBudgetContainer
                    else GrihaTheme.colors.goodContainer,
                  contentColor =
                    if (isRecordingHeader) GrihaTheme.colors.onOverBudgetContainer
                    else GrihaTheme.colors.onGoodContainer,
                ),
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("add_sheet_header_voice_button"),
            ) {
              Icon(
                imageVector = if (isRecordingHeader) Icons.Outlined.Stop else Icons.Outlined.Mic,
                contentDescription = stringResource(R.string.voice_entry_fab_cd),
              )
            }
          }
          IconButton(
            onClick = {
              recorder.cancel()
              onDismiss()
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("add_sheet_close_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = stringResource(R.string.action_close),
            )
          }
        }
      }

      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        val voiceCardAtTop = !uiState.isEditMode && uiState.voiceState !is VoiceEntryState.Idle
        if (voiceCardAtTop) {
          VoiceAndQuickEntryCard(
            quickText = uiState.quickTextInput,
            voiceState = uiState.voiceState,
            onQuickTextChanged = viewModel::onQuickTextChanged,
            onApplyQuickText = { viewModel.applyQuickText() },
            onToggleVoiceRecording = ::onMicTapped,
            onDismissVoiceBanner = viewModel::clearVoiceBanner,
          )
        }

        TransactionTypeToggle(
          selectedType = uiState.type,
          onSelectType = viewModel::onTypeSelected,
        )

        AmountDisplayAndKeypad(
          formattedAmount = uiState.formattedAmountPreview,
          type = uiState.type,
          onDigitOrDot = viewModel::onKeypadKey,
          onBackspace = viewModel::onKeypadBackspace,
          onClear = viewModel::onKeypadClear,
          onAddPresetAmount = viewModel::onAddPresetAmount,
        )

        if (!uiState.isEditMode) {
          if (!voiceCardAtTop) {
            VoiceAndQuickEntryCard(
              quickText = uiState.quickTextInput,
              voiceState = uiState.voiceState,
              onQuickTextChanged = viewModel::onQuickTextChanged,
              onApplyQuickText = { viewModel.applyQuickText() },
              onToggleVoiceRecording = ::onMicTapped,
              onDismissVoiceBanner = viewModel::clearVoiceBanner,
            )
          }

          QuickRepeatTemplatesRow(
            templates = uiState.quickTemplates,
            onApplyTemplate = viewModel::onApplyTemplate,
          )
        }

        OutlinedTextField(
          value = uiState.title,
          onValueChange = viewModel::onTitleChanged,
          singleLine = true,
          label = { Text(stringResource(R.string.tx_title_label)) },
          placeholder = { Text(stringResource(R.string.tx_title_placeholder)) },
          modifier = Modifier.fillMaxWidth().testTag("tx_title_input"),
        )

        CategoryIconGrid(
          categories = uiState.categories,
          selectedCategoryId = uiState.selectedCategoryId,
          suggestedCategoryId = uiState.suggestedCategoryId,
          onSelectCategory = viewModel::onSelectCategory,
        )

        if (uiState.type == TransactionType.EXPENSE) {
          ExpenseClassificationSelector(
            selectedClassification = uiState.expenseClassification,
            suggestedClassification = uiState.suggestedClassification,
            onSelectClassification = viewModel::onSelectExpenseClassification,
          )

          FundingSourceSelector(
            selectedSource = uiState.fundingSource,
            onSelectSource = viewModel::onSelectFundingSource,
          )
        }

        if (uiState.members.isNotEmpty()) {
          PaidByMemberSelector(
            isIncome = uiState.type == TransactionType.INCOME,
            members = uiState.members,
            selectedUid = uiState.paidByUid,
            onSelectUid = viewModel::onSelectPaidBy,
          )
        }

        QuickDateSelector(
          selectedDate = uiState.occurredOn,
          onSelectDate = viewModel::onSelectDate,
        )

        MoreDetailsSection(
          expanded = uiState.showMoreDetails,
          paymentMethod = uiState.paymentMethod,
          note = uiState.note,
          onToggleExpanded = viewModel::onToggleMoreDetails,
          onSelectPaymentMethod = viewModel::onSelectPaymentMethod,
          onNoteChanged = viewModel::onNoteChanged,
        )

        uiState.duplicateWarning?.let { warning ->
          DuplicateWarningBanner(warning = warning)
        }

        uiState.errorMessage?.let { error ->
          Surface(
            shape = MaterialTheme.shapes.small,
            color = GrihaTheme.colors.overBudgetContainer,
            modifier = Modifier.fillMaxWidth().testTag("add_sheet_error_banner"),
          ) {
            Text(
              text = error,
              style = MaterialTheme.typography.bodyMedium,
              color = GrihaTheme.colors.onOverBudgetContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Button(
          onClick = {
            recorder.cancel()
            viewModel.save { savedTx, wasEdit ->
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              onSaved(savedTx, wasEdit)
            }
          },
          enabled = !uiState.isSaving,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = GrihaTheme.colors.good,
              contentColor = GrihaTheme.colors.onGood,
            ),
          shape = MaterialTheme.shapes.small,
          modifier =
            Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("save_transaction_button"),
        ) {
          if (uiState.isSaving) {
            CircularProgressIndicator(
              color = GrihaTheme.colors.onGood,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp),
            )
          } else {
            Text(
              text =
                stringResource(
                  if (uiState.isEditMode) R.string.action_update_transaction
                  else R.string.action_save_transaction
                ),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }
}
