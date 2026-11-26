package com.example.ui.analytics

import app.cash.turbine.test
import com.example.data.fake.FakeFamilyRepository
import com.example.data.fake.FakeLedgerRepository
import com.example.data.fake.FakeSeedData
import com.example.data.fake.FakeSessionRepository
import com.example.data.fake.FakeWorkspaceStore
import com.example.domain.analytics.LedgerAnalytics
import com.example.domain.analytics.PeriodPreset
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.ui.ledger.MainDispatcherRule
import com.example.ui.screens.HomeViewModel
import com.example.ui.screens.InsightsTab
import com.example.ui.screens.InsightsViewModel
import com.example.ui.screens.RecordsViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelsTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private lateinit var store: FakeWorkspaceStore
  private lateinit var sessionRepo: FakeSessionRepository
  private lateinit var familyRepo: FakeFamilyRepository
  private lateinit var ledgerRepo: FakeLedgerRepository
  private lateinit var periodStore: SelectedPeriodStore
  private lateinit var filterCoordinator: RecordsFilterCoordinator

  @Before
  fun setup() {
    store = FakeWorkspaceStore()
    store.reset()
    sessionRepo = FakeSessionRepository(store)
    familyRepo = FakeFamilyRepository(store, sessionRepo)
    ledgerRepo = FakeLedgerRepository(store, sessionRepo, familyRepo)
    periodStore = SelectedPeriodStore()
    filterCoordinator = RecordsFilterCoordinator()
  }

  @After
  fun tearDown() {
    store.reset()
  }

  @Test
  fun `HomeViewModel totals reconcile to active transactions and ignore voided records`() = runTest {
    val homeVm =
      HomeViewModel(
        sessionRepository = sessionRepo,
        familyRepository = familyRepo,
        ledgerRepository = ledgerRepo,
        periodStore = periodStore,
        filterCoordinator = filterCoordinator,
      )
    val recordsVm =
      RecordsViewModel(
        sessionRepository = sessionRepo,
        familyRepository = familyRepo,
        ledgerRepository = ledgerRepo,
        filterCoordinator = filterCoordinator,
      )

    homeVm.uiState.test {
      var latestState = awaitLoaded(this)
      for (preset in listOf(
        PeriodPreset.THIS_MONTH,
        PeriodPreset.LAST_MONTH,
        PeriodPreset.LAST_3_MONTHS,
        PeriodPreset.FINANCIAL_YEAR,
      )) {
        val state =
          if (preset == latestState.selectedPreset) {
            latestState
          } else {
            homeVm.selectPreset(preset)
            expectMostRecentItem()
          }
        latestState = state
        val allTxs = ledgerRepo.observeTransactions().first()
        val activeInPeriod = allTxs.filter { !it.isVoided && it.occurredOn in state.resolvedPeriod }
        val expectedIncome =
          activeInPeriod.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
        val expectedExpense =
          activeInPeriod.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }
        assertEquals(expectedIncome, state.summary.incomePaise)
        assertEquals(expectedExpense, state.summary.expensePaise)
        assertEquals(expectedIncome - expectedExpense, state.summary.differencePaise)
        assertEquals(activeInPeriod.size, state.summary.activeTransactionCount)
        assertTrue(state.recentActivity.none { it.transaction.isVoided })
      }

      // Adding a voided transaction in the active period must never change totals
      val beforeState = latestState
      store.transactions.value =
        store.transactions.value +
          Transaction(
            id = "tx_voided_probe",
            familyId = FakeSeedData.FAMILY_ID,
            title = "Should be excluded",
            amountPaise = 999_900L,
            type = TransactionType.EXPENSE,
            categoryId = "cat_groceries",
            fundingSource = FundingSource.HOUSEHOLD,
            paidByUid = FakeSeedData.ADMIN_UID,
            createdByUid = FakeSeedData.ADMIN_UID,
            occurredOn = beforeState.resolvedPeriod.endDate,
            status = TransactionStatus.VOIDED,
            voidReason = "Test exclusion",
            createdAtEpochMillis = 1_700_000_000_000L,
            updatedAtEpochMillis = 1_700_000_000_000L,
          )
      val afterVoidAdd = expectMostRecentItem()
      assertEquals(beforeState.summary.expensePaise, afterVoidAdd.summary.expensePaise)
      assertEquals(beforeState.summary.differencePaise, afterVoidAdd.summary.differencePaise)

      // Opening Records from a top category sets drilldown and filters RecordsViewModel
      val topCategory = afterVoidAdd.topCategories.first()
      homeVm.openCategoryRecords(topCategory)
      recordsVm.uiState.test {
        val recordsState = expectMostRecentItem()
        assertNotNull(recordsState.activeDrilldown)
        assertEquals(topCategory.categoryId, recordsState.activeDrilldown?.categoryId)
        assertTrue(recordsState.items.all { it.transaction.categoryId == topCategory.categoryId })
        assertTrue(recordsState.items.none { it.transaction.isVoided })
        recordsVm.clearDrilldown()
        val cleared = expectMostRecentItem()
        assertNull(cleared.activeDrilldown)
        cancelAndIgnoreRemainingEvents()
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `InsightsViewModel reconciles tabs, states comparison window, labels estimate, and drills into Records`() =
    runTest {
      val insightsVm =
        InsightsViewModel(
          familyRepository = familyRepo,
          ledgerRepository = ledgerRepo,
          sessionRepository = sessionRepo,
          periodStore = periodStore,
          filterCoordinator = filterCoordinator,
        )
      val recordsVm =
        RecordsViewModel(
          sessionRepository = sessionRepo,
          familyRepository = familyRepo,
          ledgerRepository = ledgerRepo,
          filterCoordinator = filterCoordinator,
        )

      insightsVm.uiState.test {
        val state = expectMostRecentItem()
        // Category breakdown sums equal total period expenses
        assertEquals(
          state.summary.expensePaise,
          state.expenseCategories.sumOf { it.amountPaise },
        )
        // Funding split sums equal total period expenses
        assertEquals(
          state.summary.expensePaise,
          state.fundingSplit.items.sumOf { it.amountPaise },
        )
        // Salary vs Non-Salary split reconciles to total period expenses
        assertEquals(
          state.summary.expensePaise,
          state.salaryExpenseSplit.salaryExpensePaise + state.salaryExpenseSplit.nonSalaryExpensePaise,
        )
        // Member summary paid sums equal total period expenses
        assertEquals(
          state.summary.expensePaise,
          state.memberSummaries.sumOf { it.paidExpensePaise },
        )

        // Comparison tab states explicit like-for-like window
        insightsVm.selectTab(InsightsTab.COMPARISON)
        val comparisonState = expectMostRecentItem()
        assertEquals(InsightsTab.COMPARISON, comparisonState.selectedTab)
        assertTrue(comparisonState.comparison.windowDescription.contains("vs"))
        val curDays =
          ChronoUnit.DAYS.between(
            comparisonState.comparison.currentWindow.startDate,
            comparisonState.comparison.currentWindow.endDate,
          )
        val prevDays =
          ChronoUnit.DAYS.between(
            comparisonState.comparison.previousWindow.startDate,
            comparisonState.comparison.previousWindow.endDate,
          )
        assertEquals(curDays, prevDays)

        // Forecast tab is explicitly flagged as a run-rate estimate
        insightsVm.selectTab(InsightsTab.FORECAST)
        val forecastState = expectMostRecentItem()
        assertTrue(forecastState.forecast.isEstimate)
        assertTrue(forecastState.forecast.disclaimer.contains("estimate", ignoreCase = true))

        // Tapping a funding source filters Records
        insightsVm.openFundingSourceRecords(FundingSource.HOUSEHOLD)
        recordsVm.uiState.test {
          val filtered = expectMostRecentItem()
          assertEquals(FundingSource.HOUSEHOLD, filtered.activeDrilldown?.fundingSource)
          assertTrue(filtered.items.all { it.transaction.fundingSource == FundingSource.HOUSEHOLD })
          cancelAndIgnoreRemainingEvents()
        }

        // Tapping Salary Expenses group filters Records to active salary expenses
        insightsVm.openSalaryClassificationRecords(ExpenseClassification.SALARY)
        recordsVm.uiState.test {
          val salaryFiltered = expectMostRecentItem()
          assertEquals(
            ExpenseClassification.SALARY,
            salaryFiltered.activeDrilldown?.expenseClassification,
          )
          assertTrue(salaryFiltered.items.isNotEmpty())
          assertTrue(
            salaryFiltered.items.all {
              it.transaction.type == TransactionType.EXPENSE &&
                it.transaction.expenseClassification == ExpenseClassification.SALARY &&
                !it.transaction.isVoided
            }
          )
          cancelAndIgnoreRemainingEvents()
        }

        // Tapping a member filters Records strictly by paidByUid
        val targetMember = forecastState.memberSummaries.first()
        insightsVm.openMemberRecords(targetMember)
        recordsVm.uiState.test {
          val byMember = expectMostRecentItem()
          assertEquals(targetMember.uid, byMember.activeDrilldown?.paidByUid)
          assertTrue(byMember.items.all { it.transaction.paidByUid == targetMember.uid })
          cancelAndIgnoreRemainingEvents()
        }

        cancelAndIgnoreRemainingEvents()
      }
    }

  private suspend fun awaitLoaded(
    turbine: app.cash.turbine.ReceiveTurbine<com.example.ui.screens.HomeUiState>,
  ): com.example.ui.screens.HomeUiState {
    var item = turbine.expectMostRecentItem()
    while (item.isLoading) {
      item = turbine.awaitItem()
    }
    return item
  }
}
