package com.example.ui

import app.cash.turbine.test
import com.example.R
import com.example.data.fake.FakeAuthRepository
import com.example.data.fake.FakeFamilyRepository
import com.example.data.fake.FakeInviteRepository
import com.example.data.fake.FakeLedgerRepository
import com.example.data.fake.FakeSeedData
import com.example.data.fake.FakeSessionRepository
import com.example.data.fake.FakeWorkspaceStore
import com.example.ui.ledger.MainDispatcherRule
import com.example.ui.screens.FamilyViewModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FamilySettingsViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private lateinit var store: FakeWorkspaceStore
  private lateinit var authRepo: FakeAuthRepository
  private lateinit var sessionRepo: FakeSessionRepository
  private lateinit var familyRepo: FakeFamilyRepository
  private lateinit var inviteRepo: FakeInviteRepository
  private lateinit var ledgerRepo: FakeLedgerRepository

  @Before
  fun setup() {
    store = FakeWorkspaceStore()
    store.reset()
    authRepo = FakeAuthRepository(store)
    sessionRepo = FakeSessionRepository(store)
    familyRepo = FakeFamilyRepository(store, sessionRepo)
    inviteRepo = FakeInviteRepository(store, sessionRepo)
    ledgerRepo = FakeLedgerRepository(store, sessionRepo, familyRepo)
  }

  @After
  fun tearDown() {
    store.reset()
  }

  @Test
  fun `only admin exposes canClearAllData and clears ledger transactions and audit events`() =
    runTest {
      val vm = FamilyViewModel(authRepo, sessionRepo, familyRepo, inviteRepo, ledgerRepo)

      vm.uiState.test {
        var adminState = awaitItem()
        while (adminState.currentMember == null) {
          adminState = awaitItem()
        }
        assertTrue(adminState.canClearAllData)
        assertTrue(store.transactions.value.isNotEmpty())
        assertTrue(store.auditEvents.value.isNotEmpty())
        val initialEpoch = store.families.value.getValue(FakeSeedData.FAMILY_ID).ledgerEpoch

        // Switch preview role to Member -> blocked
        sessionRepo.switchDebugMember(FakeSeedData.MEMBER_UID)
        val memberState = expectMostRecentItem()
        assertFalse(memberState.canClearAllData)
        vm.clearAllData()
        val memberAfterAttempt = expectMostRecentItem()
        assertTrue(memberAfterAttempt.isErrorFeedback)
        assertEquals(R.string.settings_clear_data_error, memberAfterAttempt.feedbackMessageRes)
        assertTrue(store.transactions.value.isNotEmpty())
        assertEquals(initialEpoch, store.families.value.getValue(FakeSeedData.FAMILY_ID).ledgerEpoch)

        vm.dismissFeedback()
        expectMostRecentItem()

        // Switch preview role to Viewer -> blocked
        sessionRepo.switchDebugMember(FakeSeedData.VIEWER_UID)
        val viewerState = expectMostRecentItem()
        assertFalse(viewerState.canClearAllData)
        vm.clearAllData()
        val viewerAfterAttempt = expectMostRecentItem()
        assertTrue(viewerAfterAttempt.isErrorFeedback)
        assertEquals(R.string.settings_clear_data_error, viewerAfterAttempt.feedbackMessageRes)
        assertTrue(store.transactions.value.isNotEmpty())

        // Switch back to Admin -> clear succeeds
        sessionRepo.switchDebugMember(FakeSeedData.ADMIN_UID)
        val backToAdmin = expectMostRecentItem()
        assertTrue(backToAdmin.canClearAllData)

        vm.clearAllData()
        val clearedState = expectMostRecentItem()
        assertFalse(clearedState.isErrorFeedback)
        assertEquals(R.string.settings_clear_data_success, clearedState.feedbackMessageRes)
        assertTrue(store.transactions.value.isEmpty())
        assertTrue(store.auditEvents.value.isEmpty())
        assertEquals(initialEpoch + 1, store.families.value.getValue(FakeSeedData.FAMILY_ID).ledgerEpoch)

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `FakeLedgerRepository clearAllData rejects member and viewer directly`() = runTest {
    val txBefore = store.transactions.value.size

    sessionRepo.switchDebugMember(FakeSeedData.MEMBER_UID)
    assertTrue(ledgerRepo.clearAllData().isFailure)
    assertEquals(txBefore, store.transactions.value.size)

    sessionRepo.switchDebugMember(FakeSeedData.VIEWER_UID)
    assertTrue(ledgerRepo.clearAllData().isFailure)
    assertEquals(txBefore, store.transactions.value.size)
  }
}
