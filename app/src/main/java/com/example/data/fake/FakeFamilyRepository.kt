package com.example.data.fake

import com.example.domain.model.Category
import com.example.domain.model.Family
import com.example.domain.model.Member
import com.example.domain.model.Role
import com.example.domain.model.UserProfile
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.rules.Permissions
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class FakeFamilyRepository @Inject constructor(
  private val store: FakeWorkspaceStore,
  private val sessionRepository: SessionRepository,
) : FamilyRepository {

  override fun observeFamily(): Flow<Family?> =
    combine(sessionRepository.observeUserProfile(), store.families) { profile, families ->
      val familyId = profile?.familyId ?: return@combine null
      families[familyId]
    }

  override fun observeMembers(): Flow<List<Member>> =
    combine(sessionRepository.observeUserProfile(), store.membersByFamily) { profile, map ->
      val familyId = profile?.familyId ?: return@combine emptyList()
      map[familyId].orEmpty().sortedWith(compareBy<Member> { it.role.ordinal }.thenBy { it.displayName })
    }

  override fun observeCategories(): Flow<List<Category>> =
    combine(sessionRepository.observeUserProfile(), store.categoriesByFamily) { profile, map ->
      val familyId = profile?.familyId ?: return@combine emptyList()
      map[familyId].orEmpty().sortedBy { it.sortOrder }
    }

  override suspend fun createFamily(familyName: String): Result<String> {
    val trimmedName = familyName.trim()
    if (trimmedName.isEmpty() || trimmedName.length > 60) {
      return Result.failure(IllegalArgumentException("Enter a family name between 1 and 60 characters."))
    }
    val user = store.authUser.value ?: return Result.failure(IllegalStateException("Not signed in."))
    val familyId = "fam_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val now = System.currentTimeMillis()
    val family = Family(id = familyId, name = trimmedName, currencyCode = "INR", createdAtEpochMillis = now)
    val adminMember =
      Member(
        uid = user.uid,
        familyId = familyId,
        displayName = user.displayName,
        email = user.email,
        role = Role.ADMIN,
        joinedAtEpochMillis = now,
        isActive = true,
      )
    val seededCategories = FakeSeedData.categories.map { it.copy(familyId = familyId) }

    store.families.value = store.families.value + (familyId to family)
    store.membersByFamily.value = store.membersByFamily.value + (familyId to listOf(adminMember))
    store.categoriesByFamily.value = store.categoriesByFamily.value + (familyId to seededCategories)
    store.userProfiles.value =
      store.userProfiles.value +
        (user.uid to
          UserProfile(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            familyId = familyId,
          ))
    return Result.success(familyId)
  }

  override suspend fun changeMemberRole(
    familyId: String,
    targetUid: String,
    newRole: Role,
  ): Result<Unit> {
    val actorUid = store.authUser.value?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
    val currentMembers = store.membersByFamily.value[familyId].orEmpty()
    val actor = currentMembers.firstOrNull { it.uid == actorUid }
      ?: return Result.failure(IllegalStateException("Your member record was not found."))
    val target = currentMembers.firstOrNull { it.uid == targetUid }
      ?: return Result.failure(IllegalStateException("Member not found."))
    val activeAdmins = currentMembers.count { it.isActive && it.role == Role.ADMIN }
    if (!Permissions.canChangeMemberRole(actor, target, newRole, activeAdmins)) {
      return Result.failure(IllegalStateException("Cannot change role: unauthorized or last admin protected."))
    }
    val updated = currentMembers.map { if (it.uid == targetUid) it.copy(role = newRole) else it }
    store.membersByFamily.value = store.membersByFamily.value + (familyId to updated)
    return Result.success(Unit)
  }

  override suspend fun removeMember(familyId: String, targetUid: String): Result<Unit> {
    val actorUid = store.authUser.value?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
    val currentMembers = store.membersByFamily.value[familyId].orEmpty()
    val actor = currentMembers.firstOrNull { it.uid == actorUid }
      ?: return Result.failure(IllegalStateException("Your member record was not found."))
    val target = currentMembers.firstOrNull { it.uid == targetUid }
      ?: return Result.failure(IllegalStateException("Member not found."))
    val activeAdmins = currentMembers.count { it.isActive && it.role == Role.ADMIN }
    if (!Permissions.canRemoveMember(actor, target, activeAdmins)) {
      return Result.failure(IllegalStateException("Cannot remove member: unauthorized or last admin protected."))
    }
    val updated = currentMembers.filterNot { it.uid == targetUid }
    store.membersByFamily.value = store.membersByFamily.value + (familyId to updated)
    return Result.success(Unit)
  }

  override suspend fun clearRemovedFamilyLink(): Result<Unit> {
    val user = store.authUser.value ?: return Result.failure(IllegalStateException("Not signed in."))
    val currentProfile =
      store.userProfiles.value[user.uid]
        ?: UserProfile(uid = user.uid, displayName = user.displayName, email = user.email)
    store.userProfiles.value =
      store.userProfiles.value + (user.uid to currentProfile.copy(familyId = null))
    return Result.success(Unit)
  }
}
