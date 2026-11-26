package com.example.ui.ledger

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.data.fake.FakeAuthRepository
import com.example.data.fake.FakeFamilyRepository
import com.example.data.fake.FakeLedgerRepository
import com.example.data.fake.FakeSeedData
import com.example.data.fake.FakeSessionRepository
import com.example.data.fake.FakeWorkspaceStore
import com.example.domain.model.AuditAction
import com.example.domain.model.FundingSource
import com.example.domain.model.PaymentMethod
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.ui.screens.RecordsFilter
import com.example.ui.screens.RecordsViewModel
import com.example.ui.screens.TransactionDetailViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
  private val dispatcher = UnconfinedTestDispatcher()

  override fun starting(description: Description) {
    Dispatchers.setMain(dispatcher)
  }

  override fun finished(description: Description) {
    Dispatchers.resetMain()
  }
}

class LedgerViewModelsTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private lateinit var store: FakeWorkspaceStore
  private lateinit var authRepo: FakeAuthRepository
  private lateinit var sessionRepo: FakeSessionRepository
  private lateinit var familyRepo: FakeFamilyRepository
  private lateinit var ledgerRepo: FakeLedgerRepository

  @Before
  fun setup() {
    store = FakeWorkspaceStore()
    store.reset()
    authRepo = FakeAuthRepository(store)
    sessionRepo = FakeSessionRepository(store)
    familyRepo = FakeFamilyRepository(store, sessionRepo)
    ledgerRepo = FakeLedgerRepository(store, sessionRepo, familyRepo)
  }

  @After
  fun tearDown() {
    store.reset()
  }

  @Test
  fun `AddTransactionViewModel handles keypad, keyword suggestion, duplicate warning, and save with audit`() =
    runTest {
      val vm = AddTransactionViewModel(sessionRepo, familyRepo, ledgerRepo)
      vm.uiState.test {
        // Initial / loaded workspace emission
        val initial = expectMostRecentItem()
        assertEquals(FakeSeedData.ADMIN_UID, initial.paidByUid)

        vm.onKeypadKey("4")
        vm.onKeypadKey("5")
        vm.onKeypadKey("0")
        vm.onKeypadKey(".")
        vm.onKeypadKey("5")
        vm.onKeypadKey("0")
        val afterAmount = expectMostRecentItem()
        assertEquals(45_050L, afterAmount.amountPaise)
        assertEquals("₹450.50", afterAmount.formattedAmountPreview)

        vm.onTitleChanged("BigBasket Milk")
        val afterTitle = expectMostRecentItem()
        assertEquals("cat_groceries", afterTitle.suggestedCategoryId)
        assertEquals("cat_groceries", afterTitle.selectedCategoryId)

        // Manual category override should stick even if title changes later
        vm.onSelectCategory("cat_dining")
        vm.onTitleChanged("BigBasket BESCOM snacks")
        val afterManualPick = expectMostRecentItem()
        assertEquals("cat_dining", afterManualPick.selectedCategoryId)

        vm.onSelectCategory("cat_utilities")
        vm.onSelectFundingSource(FundingSource.FAMILY_CARD)
        vm.onSelectPaymentMethod(PaymentMethod.CARD)
        vm.onSelectPaidBy(FakeSeedData.MEMBER_UID)
        vm.onSelectDate(LocalDate.now())
        val ready = expectMostRecentItem()
        assertTrue(ready.canSave)

        var savedId: String? = null
        vm.save { saved, wasEdit ->
          savedId = saved.id
          assertFalse(wasEdit)
        }
        val afterSave = expectMostRecentItem()
        assertFalse(afterSave.isSaving)
        assertNotNull(savedId)
        assertTrue(savedId!!.startsWith("tx_"))

        val persisted = ledgerRepo.observeTransactions().first().first { it.id == savedId }
        assertEquals(45_050L, persisted.amountPaise)
        assertEquals(FakeSeedData.MEMBER_UID, persisted.paidByUid)
        assertEquals(FakeSeedData.ADMIN_UID, persisted.createdByUid)
        assertEquals(FundingSource.FAMILY_CARD, persisted.fundingSource)
        assertEquals(PaymentMethod.CARD, persisted.paymentMethod)

        val events = ledgerRepo.observeAuditEventsForTransaction(savedId!!).first()
        assertEquals(1, events.size)
        assertEquals(AuditAction.CREATED, events.first().action)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `AddTransactionViewModel surfaces non-blocking duplicate warning without blocking save`() =
    runTest {
      val existing = ledgerRepo.observeTransactions().first().first { !it.isVoided && it.type == TransactionType.EXPENSE }
      val vm = AddTransactionViewModel(sessionRepo, familyRepo, ledgerRepo)

      vm.uiState.test {
        expectMostRecentItem()
        val rupees = existing.amountPaise / 100L
        rupees.toString().forEach { vm.onKeypadKey(it.toString()) }
        vm.onTitleChanged(existing.title)
        vm.onSelectCategory(existing.categoryId)
        vm.onSelectDate(existing.occurredOn)

        val warned = expectMostRecentItem()
        assertNotNull(warned.duplicateWarning)
        assertTrue(warned.canSave)

        var savedAnyway = false
        vm.save { _, _ -> savedAnyway = true }
        expectMostRecentItem()
        assertTrue(savedAnyway)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `RecordsViewModel supports search, filter chips, paging, and pending sync indicator`() =
    runTest {
      val vm = RecordsViewModel(sessionRepo, familyRepo, ledgerRepo, com.example.ui.analytics.RecordsFilterCoordinator())
      vm.uiState.test {
        val page1 = expectMostRecentItem()
        assertEquals(15, page1.items.size)
        assertEquals(30, page1.totalMatchingCount)
        assertTrue(page1.hasMore)
        assertFalse(page1.hasPendingSync)

        // Pending sync flows through from repository
        store.pendingSyncTransactionIds.value = setOf(page1.items.first().transaction.id)
        val pendingState = expectMostRecentItem()
        assertTrue(pendingState.hasPendingSync)
        assertTrue(pendingState.items.first().transaction.hasPendingSync)

        // Paging loads next 15
        vm.loadMore()
        val page2 = expectMostRecentItem()
        assertEquals(30, page2.items.size)
        assertFalse(page2.hasMore)

        // Voided filter chip shows only voided records
        vm.onSelectFilter(RecordsFilter.VOIDED)
        val voidedOnly = expectMostRecentItem()
        assertEquals(2, voidedOnly.items.size)
        assertTrue(voidedOnly.items.all { it.transaction.isVoided })

        // Search query filters by title/category/payer/note
        vm.onSelectFilter(RecordsFilter.ALL)
        vm.onSearchQueryChange("BESCOM")
        val searchResult = expectMostRecentItem()
        assertTrue(searchResult.items.isNotEmpty())
        assertTrue(searchResult.items.all { it.transaction.title.contains("BESCOM", ignoreCase = true) })
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `TransactionDetailViewModel enforces role permissions and creates audit events on void`() =
    runTest {
      // Seeded tx_04: createdByUid = ADMIN_UID, paidByUid = MEMBER_UID
      val vm =
        TransactionDetailViewModel(
          savedStateHandle = SavedStateHandle(mapOf("transactionId" to "tx_04")),
          sessionRepository = sessionRepo,
          familyRepository = familyRepo,
          ledgerRepository = ledgerRepo,
        )

      vm.uiState.test {
        val adminView = expectMostRecentItem()
        assertEquals("Ananya Sharma", adminView.createdByName)
        assertEquals("Rohan Sharma", adminView.paidByName)
        assertTrue(adminView.canEdit)
        assertTrue(adminView.canVoid)

        // Switching to Member Rohan: cannot edit/void tx_04 even though paidByUid == Rohan
        sessionRepo.switchDebugMember(FakeSeedData.MEMBER_UID)
        val memberViewOtherRecord = expectMostRecentItem()
        assertFalse(memberViewOtherRecord.canEdit)
        assertFalse(memberViewOtherRecord.canVoid)

        // Switching back to Admin then to Viewer Dadi: read-only
        sessionRepo.switchDebugMember(FakeSeedData.ADMIN_UID)
        val backToAdmin = expectMostRecentItem()
        assertTrue(backToAdmin.canEdit)
        sessionRepo.switchDebugMember(FakeSeedData.VIEWER_UID)
        val viewerView = expectMostRecentItem()
        assertFalse(viewerView.canEdit)
        assertFalse(viewerView.canVoid)

        // Back to Admin: void requires non-blank reason
        sessionRepo.switchDebugMember(FakeSeedData.ADMIN_UID)
        expectMostRecentItem()
        vm.voidTransaction("   ") {}
        val invalidVoid = expectMostRecentItem()
        assertNotNull(invalidVoid.actionError)

        var voidCompleted = false
        vm.voidTransaction("Duplicate receipt") { voidCompleted = true }
        val voidedState = expectMostRecentItem()
        assertTrue(voidCompleted)
        assertNull(voidedState.actionError)
        assertEquals(TransactionStatus.VOIDED, voidedState.transaction?.status)
        assertFalse(voidedState.canEdit)
        assertFalse(voidedState.canVoid)
        assertEquals(AuditAction.VOIDED, voidedState.auditEvents.first().event.action)
        cancelAndIgnoreRemainingEvents()
      }
    }
}
