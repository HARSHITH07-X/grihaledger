package com.example.data.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.InviteValidationResult
import com.example.domain.model.Role
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyInviteRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private fun newAuthRepo() = FirebaseAuthRepository(auth, firestore)

  private fun newSessionRepo(authRepo: FirebaseAuthRepository) =
    FirebaseSessionRepository(auth, firestore, authRepo, authRepo)

  private fun newFamilyRepo(sessionRepo: FirebaseSessionRepository) =
    FirebaseFamilyRepository(auth, firestore, sessionRepo)

  private fun newInviteRepo(sessionRepo: FirebaseSessionRepository) =
    FirebaseInviteRepository(auth, firestore, sessionRepo)

  private fun randomEmail(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}@test.com"

  @Test
  fun `unauthenticated reads on families and invites fail with PERMISSION_DENIED`() = runBlocking {
    auth.signOut()

    val familyError =
      runCatching {
        withTimeout(DEFAULT_TIMEOUT_MS) {
          firestore.collection("families").document("fam_any").get().await()
        }
      }.exceptionOrNull()
    val fsFamilyError = checkNotNull(familyError?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException for family read, got $familyError"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsFamilyError.code)

    val inviteError =
      runCatching {
        withTimeout(DEFAULT_TIMEOUT_MS) {
          firestore.collection("invites").document("GRH2468KMP").get().await()
        }
      }.exceptionOrNull()
    val fsInviteError = checkNotNull(inviteError?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException for invite read, got $inviteError"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsInviteError.code)
  }

  @Test
  fun `account A creates family atomically and cross-family reads by account B are denied`() =
    runBlocking {
      val adminEmail = randomEmail("admin")
      val outsiderEmail = randomEmail("outsider")

      val adminUid = signInTestUser(adminEmail)
      val authRepoA = newAuthRepo()
      val sessionRepoA = newSessionRepo(authRepoA)
      val familyRepoA = newFamilyRepo(sessionRepoA)

      val familyId =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          familyRepoA.createFamily("Patel Household").getOrThrow()
        }
      assertTrue(familyId.isNotBlank())

      val currentMember =
        withTimeout(FLOW_TIMEOUT_MS) {
          sessionRepoA.observeCurrentMember().first { it?.uid == adminUid }
        }
      assertNotNull(currentMember)
      assertEquals(Role.ADMIN, currentMember?.role)

      val categories =
        withTimeout(FLOW_TIMEOUT_MS) {
          familyRepoA.observeCategories().first { it.size >= 10 }
        }
      assertEquals(10, categories.size)

      // Account B cannot read Account A's family or members
      signInTestUser(outsiderEmail)
      val crossFamilyError =
        runCatching {
          withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("families").document(familyId).get().await()
          }
        }.exceptionOrNull()
      val fsCrossError = checkNotNull(crossFamilyError?.unwrapFirestoreException()) {
        "Expected PERMISSION_DENIED on cross-family read, got $crossFamilyError"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsCrossError.code)
    }

  @Test
  fun `admin creates invite, joiner accepts atomically, code cannot be reused, and non-admin actions fail`() =
    runBlocking {
      val adminEmail = randomEmail("admin")
      val memberEmail = randomEmail("member")
      val secondJoinerEmail = randomEmail("second")

      val adminUid = signInTestUser(adminEmail)
      val authRepoA = newAuthRepo()
      val sessionRepoA = newSessionRepo(authRepoA)
      val familyRepoA = newFamilyRepo(sessionRepoA)
      val inviteRepoA = newInviteRepo(sessionRepoA)

      val familyId =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          familyRepoA.createFamily("Verma Household").getOrThrow()
        }

      val invite =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          inviteRepoA.createInvite(familyId, "Verma Household", Role.MEMBER).getOrThrow()
        }
      assertEquals(10, invite.code.length)

      // Pending invites listener works for admin
      val pending =
        withTimeout(FLOW_TIMEOUT_MS) {
          inviteRepoA.observePendingInvites(familyId).first { list -> list.any { it.code == invite.code } }
        }
      assertTrue(pending.any { it.code == invite.code })

      // Account B previews and joins using the invite code
      val memberUid = signInTestUser(memberEmail)
      val authRepoB = newAuthRepo()
      val sessionRepoB = newSessionRepo(authRepoB)
      val familyRepoB = newFamilyRepo(sessionRepoB)
      val inviteRepoB = newInviteRepo(sessionRepoB)

      val preview = withTimeout(DEFAULT_TIMEOUT_MS) { inviteRepoB.previewInvite(invite.code).getOrThrow() }
      assertTrue(preview is InviteValidationResult.Valid)

      val joinedFamilyId =
        withTimeout(DEFAULT_TIMEOUT_MS) { inviteRepoB.acceptInvite(invite.code).getOrThrow() }
      assertEquals(familyId, joinedFamilyId)

      val joinedMember =
        withTimeout(FLOW_TIMEOUT_MS) {
          sessionRepoB.observeCurrentMember().first { it?.uid == memberUid }
        }
      assertEquals(Role.MEMBER, joinedMember?.role)

      // Non-admin Account B cannot create invites or change Account A's role
      val memberInviteAttempt =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          inviteRepoB.createInvite(familyId, "Verma Household", Role.VIEWER)
        }
      assertTrue(memberInviteAttempt.isFailure)

      val memberRoleChangeAttempt =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          familyRepoB.changeMemberRole(familyId, adminUid, Role.VIEWER)
        }
      assertTrue(memberRoleChangeAttempt.isFailure)

      // Account C cannot reuse the same invite code
      signInTestUser(secondJoinerEmail)
      val authRepoC = newAuthRepo()
      val sessionRepoC = newSessionRepo(authRepoC)
      val inviteRepoC = newInviteRepo(sessionRepoC)
      val reuseAttempt = withTimeout(DEFAULT_TIMEOUT_MS) { inviteRepoC.acceptInvite(invite.code) }
      assertTrue(reuseAttempt.isFailure)

      // Admin A can promote B to Admin, demote B back, remove B, and is protected as last Admin
      signInTestUser(adminEmail)
      assertTrue(
        withTimeout(DEFAULT_TIMEOUT_MS) {
          familyRepoA.changeMemberRole(familyId, memberUid, Role.ADMIN)
        }.isSuccess
      )
      assertTrue(
        withTimeout(DEFAULT_TIMEOUT_MS) {
          familyRepoA.changeMemberRole(familyId, memberUid, Role.VIEWER)
        }.isSuccess
      )
      assertTrue(
        withTimeout(DEFAULT_TIMEOUT_MS) {
          familyRepoA.removeMember(familyId, memberUid)
        }.isSuccess
      )

      // Last admin cannot demote or remove self
      assertTrue(
        withTimeout(DEFAULT_TIMEOUT_MS) {
          familyRepoA.changeMemberRole(familyId, adminUid, Role.MEMBER)
        }.isFailure
      )
      assertTrue(
        withTimeout(DEFAULT_TIMEOUT_MS) {
          familyRepoA.removeMember(familyId, adminUid)
        }.isFailure
      )
    }

  @Test
  fun `revoked invite cannot be used to join`() = runBlocking {
    val adminEmail = randomEmail("admin")
    val joinerEmail = randomEmail("joiner")

    signInTestUser(adminEmail)
    val authRepoA = newAuthRepo()
    val sessionRepoA = newSessionRepo(authRepoA)
    val familyRepoA = newFamilyRepo(sessionRepoA)
    val inviteRepoA = newInviteRepo(sessionRepoA)

    val familyId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        familyRepoA.createFamily("Rao Household").getOrThrow()
      }
    val invite =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        inviteRepoA.createInvite(familyId, "Rao Household", Role.VIEWER).getOrThrow()
      }
    assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { inviteRepoA.revokeInvite(invite.code) }.isSuccess)

    signInTestUser(joinerEmail)
    val authRepoB = newAuthRepo()
    val sessionRepoB = newSessionRepo(authRepoB)
    val inviteRepoB = newInviteRepo(sessionRepoB)

    val joinResult = withTimeout(DEFAULT_TIMEOUT_MS) { inviteRepoB.acceptInvite(invite.code) }
    assertTrue(joinResult.isFailure)
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
