package com.example.domain.repository

import com.example.domain.model.Member
import com.example.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
  fun observeUserProfile(): Flow<UserProfile?>

  fun observeCurrentMember(): Flow<Member?>

  fun observeAvailableDebugMembers(): Flow<List<Member>>

  suspend fun switchDebugMember(uid: String)
}
