package com.example.ui.ledger

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.fake.FakeAudioTranscriptionRepository
import com.example.domain.model.Category
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.Member
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.AudioTranscriptionRepository
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.rules.CategorySuggester
import com.example.domain.rules.DuplicateDetector
import com.example.domain.rules.DuplicateWarning
import com.example.domain.rules.ExpenseClassifier
import com.example.domain.rules.QuickEntryParser
import com.example.domain.rules.QuickEntryTemplate
import com.example.domain.rules.TransactionDraft
import com.example.domain.rules.TransactionValidation
import com.example.domain.rules.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface VoiceEntryState {
  data object Idle : VoiceEntryState

  data class Recording(
    val level: Float = 0.1f,
    val elapsedSeconds: Int = 0,
  ) : VoiceEntryState

  data object Transcribing : VoiceEntryState

  data class TranscriptApplied(val transcript: String) : VoiceEntryState

  data class Error(val message: String) : VoiceEntryState
}

private data class FormDraftState(
  val isEditMode: Boolean = false,
  val transactionId: String = newClientTransactionId(),
  val type: TransactionType = TransactionType.EXPENSE,
  val amountInput: String = "",
  val title: String = "",
  val note: String = "",
  val quickTextInput: String = "",
  val selectedCategoryId: String? = null,
  val userManuallySelectedCategory: Boolean = false,
  val expenseClassification: ExpenseClassification = ExpenseClassification.NON_SALARY,
  val userManuallySelectedClassification: Boolean = false,
  val fundingSource: FundingSource = FundingSource.HOUSEHOLD,
  val paymentMethod: PaymentMethod = PaymentMethod.UPI,
  val paidByUid: String? = null,
  val occurredOn: LocalDate = LocalDate.now(),
  val showMoreDetails: Boolean = false,
  val isSaving: Boolean = false,
  val errorMessage: String? = null,
)

private fun newClientTransactionId(): String =
  "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"

@Immutable
data class AddTransactionUiState(
  val isEditMode: Boolean = false,
  val transactionId: String = "",
  val type: TransactionType = TransactionType.EXPENSE,
  val amountInput: String = "",
  val formattedAmountPreview: String = "₹0",
  val amountPaise: Long = 0L,
  val title: String = "",
  val note: String = "",
  val quickTextInput: String = "",
  val quickTemplates: List<QuickEntryTemplate> = emptyList(),
  val voiceState: VoiceEntryState = VoiceEntryState.Idle,
  val categories: List<Category> = emptyList(),
  val selectedCategoryId: String? = null,
  val suggestedCategoryId: String? = null,
  val expenseClassification: ExpenseClassification = ExpenseClassification.NON_SALARY,
  val suggestedClassification: ExpenseClassification? = null,
  val fundingSource: FundingSource = FundingSource.HOUSEHOLD,
  val paymentMethod: PaymentMethod = PaymentMethod.UPI,
  val members: List<Member> = emptyList(),
  val currentMemberUid: String = "",
  val paidByUid: String = "",
  val occurredOn: LocalDate = LocalDate.now(),
  val showMoreDetails: Boolean = false,
  val duplicateWarning: DuplicateWarning? = null,
  val isSaving: Boolean = false,
  val errorMessage: String? = null,
  val canSave: Boolean = false,
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
  private val sessionRepository: SessionRepository,
  private val familyRepository: FamilyRepository,
  private val ledgerRepository: LedgerRepository,
  private val audioTranscriptionRepository: AudioTranscriptionRepository,
) : ViewModel() {

  /** Secondary convenience constructor preserving compatibility for unit tests. */
  constructor(
    sessionRepository: SessionRepository,
    familyRepository: FamilyRepository,
    ledgerRepository: LedgerRepository,
  ) : this(
    sessionRepository = sessionRepository,
    familyRepository = familyRepository,
    ledgerRepository = ledgerRepository,
    audioTranscriptionRepository = FakeAudioTranscriptionRepository(),
  )

  private val formState = MutableStateFlow(FormDraftState())
  private val voiceState = MutableStateFlow<VoiceEntryState>(VoiceEntryState.Idle)
  private var latestAllCategories: List<Category> = emptyList()

  private val workspaceDataFlow =
    combine(
      sessionRepository.observeCurrentMember(),
      familyRepository.observeMembers(),
      familyRepository.observeCategories(),
      ledgerRepository.observeTransactions(),
    ) { currentMember, members, categories, transactions ->
      latestAllCategories = categories
      WorkspaceSnapshot(currentMember, members, categories, transactions)
    }

  val uiState: StateFlow<AddTransactionUiState> =
    combine(formState, workspaceDataFlow, voiceState) { form, workspace, voice ->
      val currentMember = workspace.currentMember
      val activeMembers = workspace.members.filter { it.isActive }
      val orderedCategories =
        CategorySuggester.orderCategoriesWithRecentsFirst(
          categories = workspace.categories,
          type = form.type,
          recentTransactions = workspace.transactions,
        )
      val suggestedCategory =
        if (form.isEditMode) {
          null
        } else {
          CategorySuggester.suggestCategory(
            title = form.title,
            note = form.note,
            type = form.type,
            categories = orderedCategories,
          )
        }

      val resolvedCategoryId =
        when {
          form.userManuallySelectedCategory &&
            form.selectedCategoryId != null &&
            orderedCategories.any { it.id == form.selectedCategoryId } -> form.selectedCategoryId
          suggestedCategory != null -> suggestedCategory.id
          else -> orderedCategories.firstOrNull()?.id
        }

      val classificationSuggestion =
        if (form.type == TransactionType.EXPENSE && !form.isEditMode) {
          ExpenseClassifier.suggest(
            title = form.title,
            note = form.note,
            categoryId = resolvedCategoryId,
            recentTransactions = workspace.transactions,
          )
        } else {
          null
        }
      val resolvedClassification =
        when {
          form.type != TransactionType.EXPENSE -> ExpenseClassification.NON_SALARY
          form.userManuallySelectedClassification -> form.expenseClassification
          classificationSuggestion != null -> classificationSuggestion.classification
          else -> ExpenseClassification.NON_SALARY
        }

      val resolvedPaidByUid =
        when {
          form.paidByUid != null && activeMembers.any { it.uid == form.paidByUid } ->
            form.paidByUid
          currentMember != null -> currentMember.uid
          else -> activeMembers.firstOrNull()?.uid.orEmpty()
        }

      val amountPaise = MoneyFormatter.parseRupeesToPaise(form.amountInput) ?: 0L
      val familyId = currentMember?.familyId.orEmpty()
      val draft =
        if (currentMember != null && resolvedCategoryId != null && resolvedPaidByUid.isNotBlank()) {
          TransactionDraft(
            familyId = familyId,
            type = form.type,
            amountPaise = amountPaise,
            title = form.title,
            note = form.note,
            categoryId = resolvedCategoryId,
            occurredOn = form.occurredOn,
            paidByUid = resolvedPaidByUid,
            createdByUid = currentMember.uid,
            fundingSource =
              if (form.type == TransactionType.EXPENSE) form.fundingSource else FundingSource.HOUSEHOLD,
            paymentMethod = form.paymentMethod,
            expenseClassification = resolvedClassification,
          )
        } else {
          null
        }

      val duplicateWarning =
        if (draft != null && amountPaise > 0L) {
          DuplicateDetector.detectPotentialDuplicates(
            draft = draft,
            existingTransactions = workspace.transactions,
            excludeTransactionId = if (form.isEditMode) form.transactionId else null,
          )
        } else {
          null
        }

      val isValidDraft =
        draft != null &&
          TransactionValidation.validateDraft(draft, workspace.categories, activeMembers) is
            ValidationResult.Valid

      val quickTemplates =
        if (form.isEditMode) emptyList()
        else QuickEntryParser.buildQuickTemplates(workspace.transactions, limit = 6)

      AddTransactionUiState(
        isEditMode = form.isEditMode,
        transactionId = form.transactionId,
        type = form.type,
        amountInput = form.amountInput,
        formattedAmountPreview = formatDisplayAmount(form.amountInput),
        amountPaise = amountPaise,
        title = form.title,
        note = form.note,
        quickTextInput = form.quickTextInput,
        quickTemplates = quickTemplates,
        voiceState = voice,
        categories = orderedCategories,
        selectedCategoryId = resolvedCategoryId,
        suggestedCategoryId = suggestedCategory?.id,
        expenseClassification = resolvedClassification,
        suggestedClassification = classificationSuggestion?.classification,
        fundingSource = form.fundingSource,
        paymentMethod = form.paymentMethod,
        members = activeMembers,
        currentMemberUid = currentMember?.uid.orEmpty(),
        paidByUid = resolvedPaidByUid,
        occurredOn = form.occurredOn,
        showMoreDetails = form.showMoreDetails,
        duplicateWarning = duplicateWarning,
        isSaving = form.isSaving,
        errorMessage = form.errorMessage,
        canSave = !form.isSaving && isValidDraft,
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AddTransactionUiState(),
      )

  fun startCreate() {
    formState.value = FormDraftState(transactionId = newClientTransactionId())
    voiceState.value = VoiceEntryState.Idle
  }

  fun startEdit(transaction: Transaction) {
    voiceState.value = VoiceEntryState.Idle
    formState.value =
      FormDraftState(
        isEditMode = true,
        transactionId = transaction.id,
        type = transaction.type,
        amountInput = MoneyFormatter.paiseToEditableRupees(transaction.amountPaise),
        title = transaction.title,
        note = transaction.note,
        selectedCategoryId = transaction.categoryId,
        userManuallySelectedCategory = true,
        expenseClassification = transaction.expenseClassification,
        userManuallySelectedClassification = true,
        fundingSource = transaction.fundingSource,
        paymentMethod = transaction.paymentMethod,
        paidByUid = transaction.paidByUid,
        occurredOn = transaction.occurredOn,
        showMoreDetails = transaction.note.isNotBlank() || transaction.paymentMethod != PaymentMethod.UPI,
      )
  }

  fun onTypeSelected(type: TransactionType) {
    formState.update { current ->
      if (current.type == type) current
      else {
        current.copy(
          type = type,
          selectedCategoryId = null,
          userManuallySelectedCategory = false,
          expenseClassification = ExpenseClassification.NON_SALARY,
          userManuallySelectedClassification = false,
          errorMessage = null,
        )
      }
    }
  }

  fun onKeypadKey(key: String) {
    formState.update { current ->
      val nextAmount = applyKeypadKey(current.amountInput, key)
      current.copy(amountInput = nextAmount, errorMessage = null)
    }
  }

  fun onAddPresetAmount(deltaRupees: Long) {
    if (deltaRupees <= 0L) return
    formState.update { current ->
      val currentPaise = MoneyFormatter.parseRupeesToPaise(current.amountInput) ?: 0L
      val updatedPaise = (currentPaise + (deltaRupees * 100L)).coerceAtMost(99_999_999_00L)
      current.copy(
        amountInput = MoneyFormatter.paiseToEditableRupees(updatedPaise),
        errorMessage = null,
      )
    }
  }

  fun onKeypadBackspace() {
    formState.update { current ->
      if (current.amountInput.isEmpty()) current
      else current.copy(amountInput = current.amountInput.dropLast(1), errorMessage = null)
    }
  }

  fun onKeypadClear() {
    formState.update { it.copy(amountInput = "", errorMessage = null) }
  }

  fun onTitleChanged(newTitle: String) {
    val clipped = newTitle.take(TransactionValidation.MAX_TITLE_LENGTH)
    formState.update { current ->
      current.copy(title = clipped, errorMessage = null)
    }
  }

  fun onNoteChanged(newNote: String) {
    val clipped = newNote.take(TransactionValidation.MAX_NOTE_LENGTH)
    formState.update { it.copy(note = clipped, errorMessage = null) }
  }

  fun onQuickTextChanged(text: String) {
    formState.update { it.copy(quickTextInput = text.take(120), errorMessage = null) }
  }

  fun applyQuickText(rawText: String = formState.value.quickTextInput) {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return
    applyParsedTextToForm(trimmed, clearQuickInput = true)
  }

  fun onApplyTemplate(template: QuickEntryTemplate) {
    formState.update { current ->
      current.copy(
        type = template.type,
        amountInput = MoneyFormatter.paiseToEditableRupees(template.amountPaise),
        title = template.title,
        selectedCategoryId = template.categoryId,
        userManuallySelectedCategory = true,
        expenseClassification = template.expenseClassification,
        userManuallySelectedClassification = true,
        fundingSource = template.fundingSource,
        paymentMethod = template.paymentMethod,
        errorMessage = null,
      )
    }
  }

  fun onVoiceRecordingProgress(level: Float, elapsedSeconds: Int) {
    voiceState.value = VoiceEntryState.Recording(level = level, elapsedSeconds = elapsedSeconds)
  }

  fun onVoicePermissionDenied(languageTag: String) {
    voiceState.value =
      VoiceEntryState.Error(
        if (languageTag.lowercase().startsWith("hi")) {
          "बोलकर प्रविष्टि जोड़ने के लिए माइक्रोफ़ोन की अनुमति आवश्यक है।"
        } else {
          "Microphone permission is needed for voice entry."
        }
      )
  }

  fun onVoiceError(message: String) {
    voiceState.value = VoiceEntryState.Error(message)
  }

  fun clearVoiceBanner() {
    voiceState.value = VoiceEntryState.Idle
  }

  fun processVoiceAudio(wavBytes: ByteArray, languageTag: String) {
    viewModelScope.launch {
      voiceState.value = VoiceEntryState.Transcribing
      audioTranscriptionRepository
        .transcribeForEntry(wavBytes, languageTag)
        .onSuccess { transcript ->
          applyParsedTextToForm(transcript, clearQuickInput = false)
          voiceState.value = VoiceEntryState.TranscriptApplied(transcript)
        }
        .onFailure { err ->
          voiceState.value =
            VoiceEntryState.Error(
              err.localizedMessage
                ?: if (languageTag.lowercase().startsWith("hi")) {
                  "आवाज़ को टेक्स्ट में बदलने में समस्या आई।"
                } else {
                  "Could not transcribe audio. Try again or type below."
                }
            )
        }
    }
  }

  private fun applyParsedTextToForm(rawText: String, clearQuickInput: Boolean) {
    val allCategories = latestAllCategories.ifEmpty { uiState.value.categories }
    val parsed = QuickEntryParser.parse(rawText, allCategories)
    formState.update { current ->
      val nextType = parsed.type ?: current.type
      val nextAmount = parsed.editableRupees ?: current.amountInput
      val nextTitle = parsed.title.ifBlank { current.title }
      val nextCatId = parsed.suggestedCategoryId ?: current.selectedCategoryId
      val nextFunding = parsed.fundingSource ?: current.fundingSource
      val nextMethod = parsed.paymentMethod ?: current.paymentMethod
      val nextDate = parsed.occurredOn ?: current.occurredOn
      val expandDetails =
        current.showMoreDetails || (parsed.paymentMethod != null && parsed.paymentMethod != PaymentMethod.UPI)

      val nextClassification =
        if (nextType == TransactionType.EXPENSE) {
          parsed.expenseClassification ?: current.expenseClassification
        } else {
          ExpenseClassification.NON_SALARY
        }
      val liveFromTitleSuggestion =
        if (nextType == TransactionType.EXPENSE) {
          ExpenseClassifier.suggest(
            title = nextTitle,
            note = current.note,
            categoryId = nextCatId,
          )?.classification
        } else {
          null
        }
      val lockParsedClassification =
        nextType == TransactionType.EXPENSE &&
          parsed.expenseClassification != null &&
          liveFromTitleSuggestion != parsed.expenseClassification

      current.copy(
        type = nextType,
        amountInput = nextAmount,
        title = nextTitle,
        quickTextInput = if (clearQuickInput) "" else current.quickTextInput,
        selectedCategoryId = nextCatId,
        userManuallySelectedCategory = parsed.suggestedCategoryId != null || current.userManuallySelectedCategory,
        expenseClassification = nextClassification,
        userManuallySelectedClassification =
          when {
            nextType != TransactionType.EXPENSE -> false
            parsed.expenseClassification != null -> lockParsedClassification
            else -> current.userManuallySelectedClassification
          },
        fundingSource = nextFunding,
        paymentMethod = nextMethod,
        occurredOn = nextDate,
        showMoreDetails = expandDetails,
        errorMessage = null,
      )
    }
  }

  fun onSelectCategory(categoryId: String) {
    formState.update {
      it.copy(
        selectedCategoryId = categoryId,
        userManuallySelectedCategory = true,
        errorMessage = null,
      )
    }
  }

  fun onSelectExpenseClassification(classification: ExpenseClassification) {
    formState.update {
      it.copy(
        expenseClassification = classification,
        userManuallySelectedClassification = true,
        errorMessage = null,
      )
    }
  }

  fun onSelectFundingSource(source: FundingSource) {
    formState.update { it.copy(fundingSource = source, errorMessage = null) }
  }

  fun onSelectPaymentMethod(method: PaymentMethod) {
    formState.update { it.copy(paymentMethod = method, errorMessage = null) }
  }

  fun onSelectPaidBy(uid: String) {
    formState.update { it.copy(paidByUid = uid, errorMessage = null) }
  }

  fun onSelectDate(date: LocalDate) {
    formState.update { it.copy(occurredOn = date, errorMessage = null) }
  }

  fun onToggleMoreDetails() {
    formState.update { it.copy(showMoreDetails = !it.showMoreDetails) }
  }

  fun save(onSuccess: (Transaction, Boolean) -> Unit) {
    val snapshot = uiState.value
    if (snapshot.isSaving) return

    val categoryId = snapshot.selectedCategoryId
    if (categoryId == null) {
      formState.update { it.copy(errorMessage = "Select a category first.") }
      return
    }
    if (snapshot.amountPaise <= 0L) {
      formState.update { it.copy(errorMessage = "Enter an amount greater than ₹0.") }
      return
    }
    if (snapshot.title.trim().isEmpty()) {
      formState.update { it.copy(errorMessage = "Add a short title so family recognizes it.") }
      return
    }

    viewModelScope.launch {
      formState.update { it.copy(isSaving = true, errorMessage = null) }
      val anyMember =
        snapshot.members.firstOrNull { it.uid == snapshot.paidByUid } ?: snapshot.members.firstOrNull()
      val familyId = anyMember?.familyId.orEmpty()
      val creatorUid = snapshot.currentMemberUid.ifBlank { snapshot.paidByUid }
      val draft =
        TransactionDraft(
          familyId = familyId,
          type = snapshot.type,
          amountPaise = snapshot.amountPaise,
          title = snapshot.title.trim(),
          note = snapshot.note.trim(),
          categoryId = categoryId,
          occurredOn = snapshot.occurredOn,
          paidByUid = snapshot.paidByUid,
          createdByUid = creatorUid,
          fundingSource =
            if (snapshot.type == TransactionType.EXPENSE) snapshot.fundingSource else FundingSource.HOUSEHOLD,
          paymentMethod = snapshot.paymentMethod,
          expenseClassification =
            if (snapshot.type == TransactionType.EXPENSE) snapshot.expenseClassification
            else ExpenseClassification.NON_SALARY,
        )

      val result =
        if (snapshot.isEditMode) {
          ledgerRepository.editTransaction(snapshot.transactionId, draft)
        } else {
          ledgerRepository.createTransaction(snapshot.transactionId, draft)
        }

      result
        .onSuccess { savedTx ->
          formState.update { it.copy(isSaving = false, errorMessage = null) }
          onSuccess(savedTx, snapshot.isEditMode)
        }
        .onFailure { error ->
          formState.update {
            it.copy(
              isSaving = false,
              errorMessage = error.message ?: "Could not save transaction.",
            )
          }
        }
    }
  }

  private fun applyKeypadKey(current: String, key: String): String {
    if (key == ".") {
      if (current.contains('.')) return current
      return if (current.isEmpty()) "0." else "$current."
    }
    if (key.length != 1 || !key[0].isDigit()) return current

    val dotIndex = current.indexOf('.')
    if (dotIndex >= 0) {
      val decimalPart = current.substring(dotIndex + 1)
      if (decimalPart.length >= 2) return current
      return "$current$key"
    }
    if (current == "0") {
      return key
    }
    if (current.length >= 8) return current
    return "$current$key"
  }

  private fun formatDisplayAmount(raw: String): String {
    if (raw.isEmpty()) return "₹0"
    val parts = raw.split('.')
    val intPart = parts[0].toLongOrNull() ?: 0L
    val grouped = MoneyFormatter.formatEnIn(paise = intPart * 100L, includePaise = false)
    return if (parts.size == 2) "$grouped.${parts[1]}" else grouped
  }

  private data class WorkspaceSnapshot(
    val currentMember: Member?,
    val members: List<Member>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
  )
}
