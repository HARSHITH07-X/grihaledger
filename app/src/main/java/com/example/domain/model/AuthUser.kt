package com.example.domain.model

data class AuthUser(
  val uid: String,
  val displayName: String,
  val email: String,
)

data class UserProfile(
  val uid: String,
  val displayName: String,
  val email: String,
  val familyId: String? = null,
)
