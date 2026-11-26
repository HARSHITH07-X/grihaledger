package com.example.ui.auth

import com.example.domain.rules.InviteRules
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory handoff for a family invite code entered on the login screen before Google Sign-In
 * finishes. Firestore is never queried until authentication completes and OnboardingViewModel runs.
 */
@Singleton
class PendingInviteCodeStore @Inject constructor() {
  private val _pendingCode = MutableStateFlow<String?>(null)
  val pendingCode = _pendingCode.asStateFlow()

  fun save(rawCode: String) {
    val normalized = InviteRules.normalizeCode(rawCode)
    _pendingCode.value = normalized.takeIf { it.isNotEmpty() }
  }

  fun consume(): String? {
    val value = _pendingCode.value
    _pendingCode.value = null
    return value
  }

  fun clear() {
    _pendingCode.value = null
  }
}
