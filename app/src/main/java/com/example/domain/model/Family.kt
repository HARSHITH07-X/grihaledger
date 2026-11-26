package com.example.domain.model

data class Family(
  val id: String,
  val name: String,
  val currencyCode: String = "INR",
  val createdAtEpochMillis: Long,
  val ledgerEpoch: Int = 0,
)

data class Member(
  val uid: String,
  val familyId: String,
  val displayName: String,
  val email: String,
  val role: Role,
  val joinedAtEpochMillis: Long,
  val isActive: Boolean = true,
)
