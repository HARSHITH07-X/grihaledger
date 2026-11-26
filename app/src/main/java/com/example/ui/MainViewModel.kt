package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AppLanguage
import com.example.domain.model.AuthUser
import com.example.domain.model.Family
import com.example.domain.model.Member
import com.example.domain.model.Transaction
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.rules.Permissions
import com.example.ui.auth.GoogleCredentialHelper
import com.example.ui.i18n.AppLanguageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionGate {
  data object SignedOut : SessionGate

  data class Loading(val authUser: AuthUser?) : SessionGate

  data class NeedsOnboarding(val authUser: AuthUser) : SessionGate

  data class WorkspaceUnavailable(val authUser: AuthUser, val familyId: String) : SessionGate

  data class WorkspaceReady(
    val authUser: AuthUser,
    val family: Family?,
    val currentMember: Member,
  ) : SessionGate
}

data class MainUiState(
  val sessionGate: SessionGate = SessionGate.Loading(null),
  val family: Family? = null,
  val currentMember: Member? = null,
  val debugMembers: List<Member> = emptyList(),
  val canCreateTransaction: Boolean = false,
  val darkThemeOverride: Boolean? = null,
  val isAddSheetOpen: Boolean = false,
  val autoStartVoiceOnAdd: Boolean = false,
  val editingTransaction: Transaction? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val sessionRepository: SessionRepository,
  private val familyRepository: FamilyRepository,
  private val ledgerRepository: LedgerRepository,
  private val languageStore: AppLanguageStore,
) : ViewModel() {

  val appLanguage: StateFlow<AppLanguage> = languageStore.language

  private val darkThemeOverride = MutableStateFlow<Boolean?>(null)
  private val isAddSheetOpen = MutableStateFlow(false)
  private val autoStartVoiceOnAdd = MutableStateFlow(false)
  private val editingTransaction = MutableStateFlow<Transaction?>(null)

  private val memberGraceExpiredFlow =
    combine(
        sessionRepository.observeUserProfile(),
        sessionRepository.observeCurrentMember(),
      ) { profile, member ->
        (profile?.familyId != null && member == null) to profile?.familyId
      }
      .distinctUntilChanged()
      .flatMapLatest { (waitingForMember, _) ->
        if (!waitingForMember) {
          flowOf(false)
        } else {
          flow {
            emit(false)
            delay(2_500L)
            emit(true)
          }
        }
      }

  private val sessionFlow =
    combine(
      authRepository.observeAuthState(),
      sessionRepository.observeUserProfile(),
      sessionRepository.observeCurrentMember(),
      familyRepository.observeFamily(),
      memberGraceExpiredFlow,
    ) { authUser, profile, currentMember, family, graceExpired ->
      when {
        authUser == null -> SessionGate.SignedOut
        profile == null -> SessionGate.Loading(authUser)
        profile.familyId.isNullOrBlank() -> SessionGate.NeedsOnboarding(authUser)
        currentMember != null ->
          SessionGate.WorkspaceReady(
            authUser = authUser,
            family = family,
            currentMember = currentMember,
          )
        graceExpired -> SessionGate.WorkspaceUnavailable(authUser, profile.familyId)
        else -> SessionGate.Loading(authUser)
      }
    }

  private val uiFlagsFlow =
    combine(darkThemeOverride, isAddSheetOpen, autoStartVoiceOnAdd, editingTransaction) {
      theme,
      sheetOpen,
      autoVoice,
      editing ->
      UiFlags(theme, sheetOpen, autoVoice, editing)
    }

  val uiState: StateFlow<MainUiState> =
    combine(
        sessionFlow,
        familyRepository.observeFamily(),
        sessionRepository.observeCurrentMember(),
        sessionRepository.observeAvailableDebugMembers(),
        uiFlagsFlow,
      ) { gate, family, currentMember, debugMembers, uiFlags ->
        MainUiState(
          sessionGate = gate,
          family = family,
          currentMember = currentMember,
          debugMembers = debugMembers,
          canCreateTransaction = currentMember?.let { Permissions.canCreateTransaction(it) } ?: false,
          darkThemeOverride = uiFlags.darkThemeOverride,
          isAddSheetOpen = uiFlags.isAddSheetOpen,
          autoStartVoiceOnAdd = uiFlags.autoStartVoiceOnAdd,
          editingTransaction = uiFlags.editingTransaction,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState(),
      )

  fun switchDebugMember(uid: String) {
    viewModelScope.launch { sessionRepository.switchDebugMember(uid) }
  }

  fun toggleDarkTheme(currentlyDark: Boolean) {
    darkThemeOverride.value = !currentlyDark
  }

  fun toggleLanguage() {
    languageStore.toggleLanguage()
  }

  fun setLanguage(language: AppLanguage) {
    languageStore.setLanguage(language)
  }

  fun openAddSheet() {
    editingTransaction.value = null
    autoStartVoiceOnAdd.value = false
    isAddSheetOpen.value = true
  }

  fun openVoiceAddSheet() {
    editingTransaction.value = null
    autoStartVoiceOnAdd.value = true
    isAddSheetOpen.value = true
  }

  fun openEditSheet(transaction: Transaction) {
    editingTransaction.value = transaction
    autoStartVoiceOnAdd.value = false
    isAddSheetOpen.value = true
  }

  fun dismissAddSheet() {
    isAddSheetOpen.value = false
    autoStartVoiceOnAdd.value = false
    editingTransaction.value = null
  }

  fun undoCreatedTransaction(
    transactionId: String,
    successMessage: String = "Entry voided cleanly (kept in audit history).",
    onResultMessage: (String) -> Unit,
  ) {
    viewModelScope.launch {
      ledgerRepository
        .voidTransaction(transactionId, "Undone after saving")
        .onSuccess { onResultMessage(successMessage) }
        .onFailure { err -> onResultMessage(err.message ?: "Could not undo transaction.") }
    }
  }

  fun returnToOnboarding() {
    viewModelScope.launch { familyRepository.clearRemovedFamilyLink() }
  }

  fun signOut(context: Context) {
    viewModelScope.launch {
      GoogleCredentialHelper.clearCredentialState(context)
      authRepository.signOut()
    }
  }

  private data class UiFlags(
    val darkThemeOverride: Boolean?,
    val isAddSheetOpen: Boolean,
    val autoStartVoiceOnAdd: Boolean,
    val editingTransaction: Transaction?,
  )
}
