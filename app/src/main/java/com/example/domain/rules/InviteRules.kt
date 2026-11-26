package com.example.domain.rules

import com.example.domain.model.Invite
import com.example.domain.model.InviteErrorReason
import com.example.domain.model.InviteStatus
import com.example.domain.model.InviteValidationResult
import com.example.domain.model.Role
import java.security.SecureRandom

object InviteRules {
  // 32-character unambiguous alphabet: excludes 0, O, 1, I
  const val ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
  const val CODE_LENGTH: Int = 10
  const val EXPIRY_HOURS: Long = 48L
  const val EXPIRY_MILLIS: Long = EXPIRY_HOURS * 60L * 60L * 1000L

  private val validCodeRegex = Regex("^[$ALPHABET]{$CODE_LENGTH}$")
  private val secureRandom = SecureRandom()

  fun generateCode(): String =
    buildString(CODE_LENGTH) {
      repeat(CODE_LENGTH) {
        append(ALPHABET[secureRandom.nextInt(ALPHABET.length)])
      }
    }

  fun normalizeCode(rawInput: String): String =
    rawInput
      .trim()
      .uppercase()
      .replace(" ", "")
      .replace("-", "")

  fun isValidCodeFormat(normalizedCode: String): Boolean =
    validCodeRegex.matches(normalizedCode)

  fun isAssignableRole(role: Role): Boolean =
    role == Role.MEMBER || role == Role.VIEWER

  fun validateForJoin(
    invite: Invite?,
    normalizedCode: String,
    currentUserFamilyId: String?,
    nowEpochMillis: Long,
  ): InviteValidationResult {
    if (!currentUserFamilyId.isNullOrBlank()) {
      return InviteValidationResult.Invalid(
        reason = InviteErrorReason.ALREADY_IN_FAMILY,
        message = "You already belong to a family workspace.",
      )
    }

    if (!isValidCodeFormat(normalizedCode)) {
      return InviteValidationResult.Invalid(
        reason = InviteErrorReason.INVALID_FORMAT,
        message = "Enter a valid 10-character invite code.",
      )
    }

    if (invite == null) {
      return InviteValidationResult.Invalid(
        reason = InviteErrorReason.NOT_FOUND,
        message = "We couldn't find an invite with that code. Double-check and try again.",
      )
    }

    if (!isAssignableRole(invite.role)) {
      return InviteValidationResult.Invalid(
        reason = InviteErrorReason.NOT_FOUND,
        message = "This invite has an unsupported role and cannot be redeemed.",
      )
    }

    return when (invite.status) {
      InviteStatus.USED ->
        InviteValidationResult.Invalid(
          reason = InviteErrorReason.ALREADY_USED,
          message = "This invite code has already been used.",
        )
      InviteStatus.REVOKED ->
        InviteValidationResult.Invalid(
          reason = InviteErrorReason.REVOKED,
          message = "This invite was revoked by a family admin.",
        )
      InviteStatus.PENDING ->
        if (invite.expiresAtEpochMillis <= nowEpochMillis) {
          InviteValidationResult.Invalid(
            reason = InviteErrorReason.EXPIRED,
            message = "This invite code expired after 48 hours. Ask your family admin for a fresh code.",
          )
        } else {
          InviteValidationResult.Valid(invite)
        }
    }
  }
}
