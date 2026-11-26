package com.example.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Invite
import com.example.domain.model.InviteValidationResult
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.InviteRepository
import com.example.domain.rules.InviteRules
import com.example.ui.auth.GoogleCredentialHelper
import com.example.ui.auth.PendingInviteCodeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingMode {
  CREATE,
  JOIN,
}

data class OnboardingUiState(
  val mode: OnboardingMode = OnboardingMode.CREATE,
  val familyNameInput: String = "",
  val inviteCodeInput: String = "",
  val verifiedInvite: Invite? = null,
  val isSubmitting: Boolean = false,
  val errorMessage: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val familyRepository: FamilyRepository,
  private val inviteRepository: InviteRepository,
  private val pendingInviteCodeStore: PendingInviteCodeStore,
) : ViewModel() {

  private val _uiState = MutableStateFlow(OnboardingUiState())
  val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

  init {
    val prefilledCode = pendingInviteCodeStore.consume()
    if (!prefilledCode.isNullOrBlank()) {
      _uiState.update {
        it.copy(
          mode = OnboardingMode.JOIN,
          inviteCodeInput = prefilledCode,
        )
      }
      if (InviteRules.isValidCodeFormat(prefilledCode)) {
        checkInviteCode()
      }
    }
  }

  fun selectMode(mode: OnboardingMode) {
    _uiState.update {
      it.copy(
        mode = mode,
        errorMessage = null,
      )
    }
  }

  fun onFamilyNameChanged(value: String) {
    _uiState.update { it.copy(familyNameInput = value.take(60), errorMessage = null) }
  }

  fun onInviteCodeChanged(value: String) {
    val cleaned = value.uppercase().replace(" ", "").take(InviteRules.CODE_LENGTH + 4)
    _uiState.update {
      it.copy(
        inviteCodeInput = cleaned,
        verifiedInvite = null,
        errorMessage = null,
      )
    }
  }

  fun resetVerifiedInvite() {
    _uiState.update { it.copy(verifiedInvite = null, errorMessage = null) }
  }

  fun createFamily() {
    val name = _uiState.value.familyNameInput.trim()
    if (name.isEmpty()) {
      _uiState.update { it.copy(errorMessage = "Please enter a name for your family.") }
      return
    }
    if (_uiState.value.isSubmitting) return

    viewModelScope.launch {
      _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
      val result = familyRepository.createFamily(name)
      _uiState.update {
        it.copy(
          isSubmitting = false,
          errorMessage = result.exceptionOrNull()?.localizedMessage,
        )
      }
    }
  }

  fun checkInviteCode() {
    val rawCode = _uiState.value.inviteCodeInput
    if (_uiState.value.isSubmitting) return

    viewModelScope.launch {
      _uiState.update { it.copy(isSubmitting = true, errorMessage = null, verifiedInvite = null) }
      val result = inviteRepository.previewInvite(rawCode)
      result.fold(
        onSuccess = { validation ->
          when (validation) {
            is InviteValidationResult.Valid ->
              _uiState.update {
                it.copy(
                  isSubmitting = false,
                  verifiedInvite = validation.invite,
                  errorMessage = null,
                )
              }
            is InviteValidationResult.Invalid ->
              _uiState.update {
                it.copy(
                  isSubmitting = false,
                  verifiedInvite = null,
                  errorMessage = validation.message,
                )
              }
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isSubmitting = false,
              verifiedInvite = null,
              errorMessage = error.localizedMessage ?: "Could not verify invite code.",
            )
          }
        },
      )
    }
  }

  fun confirmJoinFamily() {
    val invite = _uiState.value.verifiedInvite ?: return
    if (_uiState.value.isSubmitting) return

    viewModelScope.launch {
      _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
      val result = inviteRepository.acceptInvite(invite.code)
      _uiState.update {
        it.copy(
          isSubmitting = false,
          verifiedInvite = if (result.isFailure) null else it.verifiedInvite,
          errorMessage = result.exceptionOrNull()?.localizedMessage,
        )
      }
    }
  }

  fun signOut(context: Context) {
    pendingInviteCodeStore.clear()
    viewModelScope.launch {
      GoogleCredentialHelper.clearCredentialState(context)
      authRepository.signOut()
    }
  }
}
