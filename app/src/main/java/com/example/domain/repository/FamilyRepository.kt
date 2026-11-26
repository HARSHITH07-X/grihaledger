package com.example.domain.repository

import com.example.domain.model.Category
import com.example.domain.model.Family
import com.example.domain.model.Member
import com.example.domain.model.Role
import kotlinx.coroutines.flow.Flow

interface FamilyRepository {
  fun observeFamily(): Flow<Family?>

  fun observeMembers(): Flow<List<Member>>

  fun observeCategories(): Flow<List<Category>>

  suspend fun createFamily(familyName: String): Result<String>

  suspend fun changeMemberRole(familyId: String, targetUid: String, newRole: Role): Result<Unit>

  suspend fun removeMember(familyId: String, targetUid: String): Result<Unit>

  suspend fun clearRemovedFamilyLink(): Result<Unit>
}
