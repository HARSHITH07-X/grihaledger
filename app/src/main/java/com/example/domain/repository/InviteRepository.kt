package com.example.domain.repository

import com.example.domain.model.Invite
import com.example.domain.model.InviteValidationResult
import com.example.domain.model.Role
import kotlinx.coroutines.flow.Flow

interface InviteRepository {
  fun observePendingInvites(familyId: String): Flow<List<Invite>>

  suspend fun createInvite(familyId: String, familyName: String, role: Role): Result<Invite>

  suspend fun revokeInvite(code: String): Result<Unit>

  suspend fun previewInvite(rawCode: String): Result<InviteValidationResult>

  suspend fun acceptInvite(code: String): Result<String>
}
