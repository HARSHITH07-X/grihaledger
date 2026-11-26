package com.example.domain.model

enum class InviteStatus {
  PENDING,
  USED,
  REVOKED,
}

data class Invite(
  val code: String,
  val familyId: String,
  val familyName: String,
  val role: Role,
  val status: InviteStatus,
  val createdByUid: String,
  val createdAtEpochMillis: Long,
  val expiresAtEpochMillis: Long,
  val usedByUid: String? = null,
)

enum class InviteErrorReason {
  INVALID_FORMAT,
  NOT_FOUND,
  EXPIRED,
  ALREADY_USED,
  REVOKED,
  ALREADY_IN_FAMILY,
}

sealed interface InviteValidationResult {
  data class Valid(val invite: Invite) : InviteValidationResult

  data class Invalid(
    val reason: InviteErrorReason,
    val message: String,
  ) : InviteValidationResult
}
