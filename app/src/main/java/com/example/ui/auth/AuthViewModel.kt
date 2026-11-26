package com.example.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.repository.AuthRepository
import com.example.domain.rules.InviteRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
  val isLoading: Boolean = false,
  val isJoinInviteExpanded: Boolean = false,
  val inviteCodeInput: String = "",
  val errorMessage: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val pendingInviteCodeStore: PendingInviteCodeStore,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  private var autoSignInAttempted = false

  fun toggleJoinInviteExpanded() {
    _uiState.update { state ->
      val nextExpanded = !state.isJoinInviteExpanded
      if (!nextExpanded) {
        pendingInviteCodeStore.clear()
      }
      state.copy(
        isJoinInviteExpanded = nextExpanded,
        errorMessage = null,
      )
    }
  }

  fun onInviteCodeChanged(value: String) {
    val cleaned = InviteRules.normalizeCode(value).take(InviteRules.CODE_LENGTH + 4)
    _uiState.update { it.copy(inviteCodeInput = cleaned, errorMessage = null) }
    if (cleaned.isEmpty()) {
      pendingInviteCodeStore.clear()
    } else {
      pendingInviteCodeStore.save(cleaned)
    }
  }

  fun attemptAutoSignInOnce(context: Context) {
    if (autoSignInAttempted || authRepository.currentUser() != null) return
    autoSignInAttempted = true
    viewModelScope.launch {
      val idToken = GoogleCredentialHelper.attemptSilentAutoSignIn(context) ?: return@launch
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val result = authRepository.signInWithGoogleIdToken(idToken)
      _uiState.update {
        it.copy(
          isLoading = false,
          errorMessage = result.exceptionOrNull()?.localizedMessage,
        )
      }
    }
  }

  fun onSignInWithGoogleClick(context: Context) {
    if (_uiState.value.isLoading) return
    val normalizedCode = InviteRules.normalizeCode(_uiState.value.inviteCodeInput)
    if (normalizedCode.isNotEmpty() && !InviteRules.isValidCodeFormat(normalizedCode)) {
      _uiState.update {
        it.copy(
          errorMessage = "Invite codes have 10 letters & numbers (or clear the field to choose after sign-in)."
        )
      }
      return
    }
    if (normalizedCode.isNotEmpty()) {
      pendingInviteCodeStore.save(normalizedCode)
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val tokenResult = GoogleCredentialHelper.requestInteractiveGoogleSignIn(context)
      tokenResult.fold(
        onSuccess = { idToken ->
          val signInResult = authRepository.signInWithGoogleIdToken(idToken)
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = signInResult.exceptionOrNull()?.localizedMessage
                ?: if (signInResult.isFailure) "Could not complete Firebase sign-in." else null,
            )
          }
        },
        onFailure = { error ->
          val message =
            if (error is SignInCancelledException) null
            else error.localizedMessage ?: "Google Sign-In failed."
          _uiState.update { it.copy(isLoading = false, errorMessage = message) }
        },
      )
    }
  }

  fun dismissError() {
    _uiState.update { it.copy(errorMessage = null) }
  }
}
