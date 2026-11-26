package com.example.data.fake

import com.example.domain.model.AuditEvent
import com.example.domain.model.AuthUser
import com.example.domain.model.Category
import com.example.domain.model.Family
import com.example.domain.model.Invite
import com.example.domain.model.InviteStatus
import com.example.domain.model.Member
import com.example.domain.model.Role
import com.example.domain.model.Transaction
import com.example.domain.model.UserProfile
import com.example.domain.rules.InviteRules
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

@Singleton
class FakeWorkspaceStore @Inject constructor() {
  val authUser =
    MutableStateFlow<AuthUser?>(
      AuthUser(
        uid = FakeSeedData.ADMIN_UID,
        displayName = "Ananya Sharma",
        email = "ananya@sharma.family",
      )
    )

  val userProfiles =
    MutableStateFlow<Map<String, UserProfile>>(
      FakeSeedData.members.associate { member ->
        member.uid to
          UserProfile(
            uid = member.uid,
            displayName = member.displayName,
            email = member.email,
            familyId = member.familyId,
          )
      }
    )

  val families =
    MutableStateFlow<Map<String, Family>>(
      mapOf(FakeSeedData.FAMILY_ID to FakeSeedData.family)
    )

  val membersByFamily =
    MutableStateFlow<Map<String, List<Member>>>(
      mapOf(FakeSeedData.FAMILY_ID to FakeSeedData.members)
    )

  val categoriesByFamily =
    MutableStateFlow<Map<String, List<Category>>>(
      mapOf(FakeSeedData.FAMILY_ID to FakeSeedData.categories)
    )

  val invites =
    MutableStateFlow<Map<String, Invite>>(
      mapOf(
        "GRH2468KMP" to
          Invite(
            code = "GRH2468KMP",
            familyId = FakeSeedData.FAMILY_ID,
            familyName = FakeSeedData.family.name,
            role = Role.MEMBER,
            status = InviteStatus.PENDING,
            createdByUid = FakeSeedData.ADMIN_UID,
            createdAtEpochMillis = System.currentTimeMillis() - 3_600_000L,
            expiresAtEpochMillis = System.currentTimeMillis() + InviteRules.EXPIRY_MILLIS - 3_600_000L,
          )
      )
    )

  private val initialTransactions = FakeSeedData.buildTransactions()

  val transactions = MutableStateFlow<List<Transaction>>(initialTransactions)

  val auditEvents =
    MutableStateFlow<List<AuditEvent>>(FakeSeedData.buildAuditEvents(initialTransactions))

  val pendingSyncTransactionIds = MutableStateFlow<Set<String>>(emptySet())

  val debugPreviewMemberUid = MutableStateFlow<String?>(null)

  fun reset() {
    debugPreviewMemberUid.value = null
    authUser.value =
      AuthUser(
        uid = FakeSeedData.ADMIN_UID,
        displayName = "Ananya Sharma",
        email = "ananya@sharma.family",
      )
    userProfiles.value =
      FakeSeedData.members.associate { member ->
        member.uid to
          UserProfile(
            uid = member.uid,
            displayName = member.displayName,
            email = member.email,
            familyId = member.familyId,
          )
      }
    families.value = mapOf(FakeSeedData.FAMILY_ID to FakeSeedData.family)
    membersByFamily.value = mapOf(FakeSeedData.FAMILY_ID to FakeSeedData.members)
    categoriesByFamily.value = mapOf(FakeSeedData.FAMILY_ID to FakeSeedData.categories)
    invites.value =
      mapOf(
        "GRH2468KMP" to
          Invite(
            code = "GRH2468KMP",
            familyId = FakeSeedData.FAMILY_ID,
            familyName = FakeSeedData.family.name,
            role = Role.MEMBER,
            status = InviteStatus.PENDING,
            createdByUid = FakeSeedData.ADMIN_UID,
            createdAtEpochMillis = System.currentTimeMillis() - 3_600_000L,
            expiresAtEpochMillis =
              System.currentTimeMillis() + InviteRules.EXPIRY_MILLIS - 3_600_000L,
          )
      )
    val resetTransactions = FakeSeedData.buildTransactions()
    transactions.value = resetTransactions
    auditEvents.value = FakeSeedData.buildAuditEvents(resetTransactions)
    pendingSyncTransactionIds.value = emptySet()
  }
}
