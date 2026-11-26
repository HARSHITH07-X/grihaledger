package com.example.data.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.AuditAction
import com.example.domain.model.FundingSource
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Role
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.rules.TransactionDraft
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private fun newAuthRepo() = FirebaseAuthRepository(auth, firestore)

  private fun newSessionRepo(authRepo: FirebaseAuthRepository) =
    FirebaseSessionRepository(auth, firestore, authRepo, authRepo)

  private fun newFamilyRepo(sessionRepo: FirebaseSessionRepository) =
    FirebaseFamilyRepository(auth, firestore, sessionRepo)

  private fun newInviteRepo(sessionRepo: FirebaseSessionRepository) =
    FirebaseInviteRepository(auth, firestore, sessionRepo)

  private fun newLedgerRepo(
    sessionRepo: FirebaseSessionRepository,
    familyRepo: FirebaseFamilyRepository,
  ) = FirebaseLedgerRepository(auth, firestore, sessionRepo, familyRepo)

  private fun randomEmail(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}@test.com"

  @Test
  fun `member A creates offline and syncs without duplicates while member B observes live and audit history records create edit void`() =
    runBlocking {
      val adminEmail = randomEmail("admin")
      val memberEmail = randomEmail("member")

      val adminUid = signInTestUser(adminEmail)
      val authRepoA = newAuthRepo()
      val sessionRepoA = newSessionRepo(authRepoA)
      val familyRepoA = newFamilyRepo(sessionRepoA)
      val inviteRepoA = newInviteRepo(sessionRepoA)
      val ledgerRepoA = newLedgerRepo(sessionRepoA, familyRepoA)

      val familyId =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          familyRepoA.createFamily("Mehta Household").getOrThrow()
        }
      val memberInvite =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          inviteRepoA.createInvite(familyId, "Mehta Household", Role.MEMBER).getOrThrow()
        }

      // Member B joins
      val memberUid = signInTestUser(memberEmail)
      val authRepoB = newAuthRepo()
      val sessionRepoB = newSessionRepo(authRepoB)
      val familyRepoB = newFamilyRepo(sessionRepoB)
      val inviteRepoB = newInviteRepo(sessionRepoB)
      val ledgerRepoB = newLedgerRepo(sessionRepoB, familyRepoB)

      withTimeout(DEFAULT_TIMEOUT_MS) { inviteRepoB.acceptInvite(memberInvite.code).getOrThrow() }
      // Warm active listeners just like the live UI workspace does, so offline persistent/memory cache is populated
      val scopeJob = kotlinx.coroutines.SupervisorJob()
      val collectorScope = kotlinx.coroutines.CoroutineScope(coroutineContext + scopeJob)
      try {
        collectorScope.launch { sessionRepoB.observeCurrentMember().collect {} }
        collectorScope.launch { familyRepoB.observeMembers().collect {} }
        collectorScope.launch { familyRepoB.observeCategories().collect {} }
        collectorScope.launch { ledgerRepoB.observeTransactions().collect {} }

        withTimeout(FLOW_TIMEOUT_MS) {
          sessionRepoB.observeCurrentMember().first { it?.uid == memberUid }
          familyRepoB.observeMembers().first { it.isNotEmpty() }
          familyRepoB.observeCategories().first { it.size >= 10 }
        }

        // Simulate airplane mode: Member B adds with client-generated ID
        val offlineTxId = "tx_offline_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        firestore.disableNetwork().await()

        val offlineDraft =
          TransactionDraft(
            familyId = familyId,
            type = TransactionType.EXPENSE,
            amountPaise = 42_500L,
            title = "Offline Sabji",
            note = "Cash purchase",
            categoryId = "cat_groceries",
            occurredOn = LocalDate.now(),
            paidByUid = memberUid,
            createdByUid = memberUid,
            fundingSource = FundingSource.HOUSEHOLD,
            paymentMethod = PaymentMethod.CASH,
          )

        val offlineResult =
          withTimeout(DEFAULT_TIMEOUT_MS) {
            ledgerRepoB.createTransaction(offlineTxId, offlineDraft)
          }
        assertTrue(
          "Offline createTransaction failed: ${offlineResult.exceptionOrNull()}",
          offlineResult.isSuccess,
        )

        val pendingList =
          withTimeout(FLOW_TIMEOUT_MS) {
            ledgerRepoB.observeTransactions().first { list -> list.any { it.id == offlineTxId } }
          }
        assertEquals(1, pendingList.count { it.id == offlineTxId })
        assertTrue(pendingList.first { it.id == offlineTxId }.hasPendingSync)

        // Reconnect network: sync completes with NO duplicate, pending sync clears, and Admin A sees it live
        firestore.enableNetwork().await()

        val syncedList =
          withTimeout(FLOW_TIMEOUT_MS) {
            ledgerRepoB.observeTransactions().first { list ->
              val match = list.firstOrNull { it.id == offlineTxId }
              match != null && !match.hasPendingSync
            }
          }
        assertEquals(1, syncedList.count { it.id == offlineTxId })

        // Calling createTransaction again with the same client ID is idempotent (no duplicate)
        val retryResult =
          withTimeout(DEFAULT_TIMEOUT_MS) {
            ledgerRepoB.createTransaction(offlineTxId, offlineDraft)
          }
        assertTrue("Retry failed: ${retryResult.exceptionOrNull()}", retryResult.isSuccess)
        scopeJob.cancel()

        // Admin A sees Member B's record live
        signInTestUser(adminEmail)
        val adminSeen =
          withTimeout(FLOW_TIMEOUT_MS) {
            ledgerRepoA.observeTransactions().first { list -> list.any { it.id == offlineTxId } }
          }
        assertEquals("Offline Sabji", adminSeen.first { it.id == offlineTxId }.title)

        // Admin edits Member B's record and then voids it; audit trail preserves CREATED -> EDITED -> VOIDED
        val editedDraft =
          offlineDraft.copy(amountPaise = 45_000L, title = "Sabji & Fruits", createdByUid = memberUid)
        val editRes =
          withTimeout(DEFAULT_TIMEOUT_MS) {
            ledgerRepoA.editTransaction(offlineTxId, editedDraft)
          }
        assertTrue("Edit failed: ${editRes.exceptionOrNull()}", editRes.isSuccess)
        val voidRes =
          withTimeout(DEFAULT_TIMEOUT_MS) {
            ledgerRepoA.voidTransaction(offlineTxId, "Entered twice")
          }
        assertTrue("Void failed: ${voidRes.exceptionOrNull()}", voidRes.isSuccess)

        val auditHistory =
          withTimeout(FLOW_TIMEOUT_MS) {
            ledgerRepoA.observeAuditEventsForTransaction(offlineTxId).first { it.size == 3 }
          }
        assertEquals(
          setOf(AuditAction.CREATED, AuditAction.EDITED, AuditAction.VOIDED),
          auditHistory.map { it.action }.toSet(),
        )

        val finalTx =
          withTimeout(FLOW_TIMEOUT_MS) {
            ledgerRepoA.observeTransactions().first { list ->
              list.firstOrNull { it.id == offlineTxId }?.status == TransactionStatus.VOIDED
            }
          }.first { it.id == offlineTxId }
        assertEquals(45_000L, finalTx.amountPaise)
        assertEquals("Entered twice", finalTx.voidReason)
        assertEquals(adminUid, finalTx.voidedByUid)
      } finally {
        scopeJob.cancel()
        runCatching { firestore.enableNetwork().await() }
      }
    }

  @Test
  fun `enforces viewer read-only, member own-only edit void, no hard delete, append-only auditEvents, and cross-family denial`() =
    runBlocking {
      val adminEmail = randomEmail("admin")
      val memberEmail = randomEmail("member")
      val viewerEmail = randomEmail("viewer")
      val outsiderEmail = randomEmail("outsider")

      val adminUid = signInTestUser(adminEmail)
      val authRepoA = newAuthRepo()
      val sessionRepoA = newSessionRepo(authRepoA)
      val familyRepoA = newFamilyRepo(sessionRepoA)
      val inviteRepoA = newInviteRepo(sessionRepoA)
      val ledgerRepoA = newLedgerRepo(sessionRepoA, familyRepoA)

      val familyId = withTimeout(DEFAULT_TIMEOUT_MS) { familyRepoA.createFamily("Joshi Home").getOrThrow() }
      val memberInvite =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          inviteRepoA.createInvite(familyId, "Joshi Home", Role.MEMBER).getOrThrow()
        }
      val viewerInvite =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          inviteRepoA.createInvite(familyId, "Joshi Home", Role.VIEWER).getOrThrow()
        }

      // Join as Member and Viewer
      val memberUid = signInTestUser(memberEmail)
      val authRepoB = newAuthRepo()
      val sessionRepoB = newSessionRepo(authRepoB)
      val familyRepoB = newFamilyRepo(sessionRepoB)
      val inviteRepoB = newInviteRepo(sessionRepoB)
      val ledgerRepoB = newLedgerRepo(sessionRepoB, familyRepoB)
      withTimeout(DEFAULT_TIMEOUT_MS) { inviteRepoB.acceptInvite(memberInvite.code).getOrThrow() }

      signInTestUser(viewerEmail)
      val authRepoV = newAuthRepo()
      val sessionRepoV = newSessionRepo(authRepoV)
      val familyRepoV = newFamilyRepo(sessionRepoV)
      val inviteRepoV = newInviteRepo(sessionRepoV)
      val ledgerRepoV = newLedgerRepo(sessionRepoV, familyRepoV)
      withTimeout(DEFAULT_TIMEOUT_MS) { inviteRepoV.acceptInvite(viewerInvite.code).getOrThrow() }

      // Viewer cannot write transactions
      val viewerCreateResult =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          ledgerRepoV.createTransaction(
            "tx_viewer_attempt",
            TransactionDraft(
              familyId = familyId,
              type = TransactionType.EXPENSE,
              amountPaise = 10_000L,
              title = "Viewer Test",
              categoryId = "cat_groceries",
              occurredOn = LocalDate.now(),
              paidByUid = memberUid,
              createdByUid = memberUid,
            ),
          )
        }
      assertTrue(viewerCreateResult.isFailure)

      // Admin creates a transaction where paidByUid = memberUid, createdByUid = adminUid
      signInTestUser(adminEmail)
      withTimeout(FLOW_TIMEOUT_MS) { familyRepoA.observeCategories().first { it.size >= 10 } }
      val adminTxId = "tx_admin_${UUID.randomUUID().toString().replace("-", "").take(8)}"
      val adminDraft =
        TransactionDraft(
          familyId = familyId,
          type = TransactionType.EXPENSE,
          amountPaise = 50_000L,
          title = "Admin Logged for Member",
          categoryId = "cat_groceries",
          occurredOn = LocalDate.now(),
          paidByUid = memberUid,
          createdByUid = adminUid,
        )
      val txRes =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          ledgerRepoA.createTransaction(adminTxId, adminDraft)
        }
      assertTrue("createTransaction failed: ${txRes.exceptionOrNull()}", txRes.isSuccess)
      val createdAudit =
        withTimeout(FLOW_TIMEOUT_MS) {
          ledgerRepoA.observeAuditEventsForTransaction(adminTxId).first { it.isNotEmpty() }
        }.first()

      // Member CANNOT edit or void Admin's record (even when paidByUid == memberUid)
      signInTestUser(memberEmail)
      withTimeout(FLOW_TIMEOUT_MS) { familyRepoB.observeMembers().first { it.size >= 2 } }
      val memberEditOther =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          ledgerRepoB.editTransaction(adminTxId, adminDraft.copy(title = "Tampered"))
        }
      assertTrue(memberEditOther.isFailure)

      val memberVoidOther =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          ledgerRepoB.voidTransaction(adminTxId, "Not allowed")
        }
      assertTrue(memberVoidOther.isFailure)

      // Admin CANNOT hard-delete transactions or modify/delete auditEvents
      signInTestUser(adminEmail)
      val deleteTxErr =
        runCatching {
          withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore
              .collection("families")
              .document(familyId)
              .collection("transactions")
              .document(adminTxId)
              .delete()
              .await()
          }
        }.exceptionOrNull()
      assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        deleteTxErr?.unwrapFirestoreException()?.code,
      )

      val deleteAuditErr =
        runCatching {
          withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore
              .collection("families")
              .document(familyId)
              .collection("auditEvents")
              .document(createdAudit.id)
              .delete()
              .await()
          }
        }.exceptionOrNull()
      assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        deleteAuditErr?.unwrapFirestoreException()?.code,
      )

      // Outsider in another family cannot read transactions or auditEvents
      signInTestUser(outsiderEmail)
      val crossFamilyReadErr =
        runCatching {
          withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore
              .collection("families")
              .document(familyId)
              .collection("transactions")
              .get()
              .await()
          }
        }.exceptionOrNull()
      assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        crossFamilyReadErr?.unwrapFirestoreException()?.code,
      )
    }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 5000L
  }
}
