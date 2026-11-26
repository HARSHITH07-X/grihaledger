package com.example.domain.repository

import com.example.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
  fun observeAuthState(): Flow<AuthUser?>

  fun currentUser(): AuthUser?

  suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser>

  suspend fun signOut()
}
