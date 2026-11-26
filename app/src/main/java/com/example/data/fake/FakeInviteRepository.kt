package com.example.data.fake

import com.example.domain.model.Invite
import com.example.domain.model.InviteStatus
import com.example.domain.model.InviteValidationResult
import com.example.domain.model.Member
import com.example.domain.model.Role
import com.example.domain.model.UserProfile
import com.example.domain.repository.InviteRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.rules.InviteRules
import com.example.domain.rules.LedgerAction
import com.example.domain.rules.Permissions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class FakeInviteRepository @Inject constructor(
  private val store: FakeWorkspaceStore,
  private val sessionRepository: SessionRepository,
) : InviteRepository {

  override fun observePendingInvites(familyId: String): Flow<List<Invite>> =
    store.invites.map { map ->
      val now = System.currentTimeMillis()
      map.values
        .filter {
          it.familyId == familyId &&
            it.status == InviteStatus.PENDING &&
            it.expiresAtEpochMillis > now
        }
        .sortedByDescending { it.createdAtEpochMillis }
    }

  override suspend fun createInvite(
    familyId: String,
    familyName: String,
    role: Role,
  ): Result<Invite> {
    val uid = store.authUser.value?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
    val member =
      store.membersByFamily.value[familyId].orEmpty().firstOrNull { it.uid == uid }
        ?: return Result.failure(IllegalStateException("Not a member of this family."))
    if (!Permissions.isAllowed(member.role, LedgerAction.CREATE_INVITE)) {
      return Result.failure(IllegalStateException("Only Admins can create invites."))
    }
    if (!InviteRules.isAssignableRole(role)) {
      return Result.failure(IllegalArgumentException("Invites can only grant Member or Viewer roles."))
    }
    val code = InviteRules.generateCode()
    val now = System.currentTimeMillis()
    val invite =
      Invite(
        code = code,
        familyId = familyId,
        familyName = familyName,
        role = role,
        status = InviteStatus.PENDING,
        createdByUid = uid,
        createdAtEpochMillis = now,
        expiresAtEpochMillis = now + InviteRules.EXPIRY_MILLIS,
      )
    store.invites.value = store.invites.value + (code to invite)
    return Result.success(invite)
  }

  override suspend fun revokeInvite(code: String): Result<Unit> {
    val uid = store.authUser.value?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
    val normalized = InviteRules.normalizeCode(code)
    val invite =
      store.invites.value[normalized]
        ?: return Result.failure(IllegalStateException("Invite not found."))
    val member =
      store.membersByFamily.value[invite.familyId].orEmpty().firstOrNull { it.uid == uid }
    if (member == null || !Permissions.isAllowed(member.role, LedgerAction.REVOKE_INVITE)) {
      return Result.failure(IllegalStateException("Only Admins can revoke invites."))
    }
    store.invites.value = store.invites.value + (normalized to invite.copy(status = InviteStatus.REVOKED))
    return Result.success(Unit)
  }

  override suspend fun previewInvite(rawCode: String): Result<InviteValidationResult> {
    if (store.authUser.value == null) {
      return Result.failure(IllegalStateException("Not signed in."))
    }
    val normalized = InviteRules.normalizeCode(rawCode)
    val profile = sessionRepository.observeUserProfile().first()
    val invite = store.invites.value[normalized]
    return Result.success(
      InviteRules.validateForJoin(
        invite = invite,
        normalizedCode = normalized,
        currentUserFamilyId = profile?.familyId,
        nowEpochMillis = System.currentTimeMillis(),
      )
    )
  }

  override suspend fun acceptInvite(code: String): Result<String> {
    val user = store.authUser.value ?: return Result.failure(IllegalStateException("Not signed in."))
    val normalized = InviteRules.normalizeCode(code)
    val profile = sessionRepository.observeUserProfile().first()
    val invite = store.invites.value[normalized]
    val validation =
      InviteRules.validateForJoin(
        invite = invite,
        normalizedCode = normalized,
        currentUserFamilyId = profile?.familyId,
        nowEpochMillis = System.currentTimeMillis(),
      )
    if (validation is InviteValidationResult.Invalid) {
      return Result.failure(IllegalStateException(validation.message))
    }
    val validInvite = (validation as InviteValidationResult.Valid).invite
    val now = System.currentTimeMillis()
    val newMember =
      Member(
        uid = user.uid,
        familyId = validInvite.familyId,
        displayName = user.displayName,
        email = user.email,
        role = validInvite.role,
        joinedAtEpochMillis = now,
        isActive = true,
      )
    val existingMembers = store.membersByFamily.value[validInvite.familyId].orEmpty()
    store.membersByFamily.value =
      store.membersByFamily.value + (validInvite.familyId to (existingMembers + newMember))
    store.invites.value =
      store.invites.value +
        (normalized to validInvite.copy(status = InviteStatus.USED, usedByUid = user.uid))
    store.userProfiles.value =
      store.userProfiles.value +
        (user.uid to
          UserProfile(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            familyId = validInvite.familyId,
          ))
    return Result.success(validInvite.familyId)
  }
}
