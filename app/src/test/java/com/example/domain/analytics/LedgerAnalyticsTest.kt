package com.example.domain.analytics

import com.example.data.fake.FakeSeedData
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.Period
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerAnalyticsTest {

  private val refDate = LocalDate.of(2026, 11, 21)

  @Test
  fun `period presets resolve calendar boundaries including Apr-Mar financial year`() {
    val thisMonth = PeriodResolver.resolve(PeriodPreset.THIS_MONTH, refDate)
    assertEquals(LocalDate.of(2026, 11, 1), thisMonth.startDate)
    assertEquals(LocalDate.of(2026, 11, 30), thisMonth.endDate)

    val lastMonth = PeriodResolver.resolve(PeriodPreset.LAST_MONTH, refDate)
    assertEquals(LocalDate.of(2026, 10, 1), lastMonth.startDate)
    assertEquals(LocalDate.of(2026, 10, 31), lastMonth.endDate)

    val last3 = PeriodResolver.resolve(PeriodPreset.LAST_3_MONTHS, refDate)
    assertEquals(LocalDate.of(2026, 9, 1), last3.startDate)
    assertEquals(LocalDate.of(2026, 11, 30), last3.endDate)

    val last6 = PeriodResolver.resolve(PeriodPreset.LAST_6_MONTHS, refDate)
    assertEquals(LocalDate.of(2026, 6, 1), last6.startDate)
    assertEquals(LocalDate.of(2026, 11, 30), last6.endDate)

    val calYear = PeriodResolver.resolve(PeriodPreset.CALENDAR_YEAR, refDate)
    assertEquals(LocalDate.of(2026, 1, 1), calYear.startDate)
    assertEquals(LocalDate.of(2026, 12, 31), calYear.endDate)

    // Nov 2026 falls in FY 2026-27 (Apr 1, 2026 -> Mar 31, 2027)
    val fyNov = PeriodResolver.resolve(PeriodPreset.FINANCIAL_YEAR, refDate)
    assertEquals(LocalDate.of(2026, 4, 1), fyNov.startDate)
    assertEquals(LocalDate.of(2027, 3, 31), fyNov.endDate)
    assertTrue(fyNov.label.contains("Apr–Mar"))

    // Feb 2026 falls in FY 2025-26 (Apr 1, 2025 -> Mar 31, 2026)
    val fyFeb = PeriodResolver.resolve(PeriodPreset.FINANCIAL_YEAR, LocalDate.of(2026, 2, 15))
    assertEquals(LocalDate.of(2025, 4, 1), fyFeb.startDate)
    assertEquals(LocalDate.of(2026, 3, 31), fyFeb.endDate)

    val custom =
      PeriodResolver.resolve(
        PeriodPreset.CUSTOM,
        refDate,
        customStart = LocalDate.of(2026, 10, 5),
        customEnd = LocalDate.of(2026, 11, 10),
      )
    assertEquals(LocalDate.of(2026, 10, 5), custom.startDate)
    assertEquals(LocalDate.of(2026, 11, 10), custom.endDate)
  }

  @Test
  fun `reconciliation - every analytics total matches exact sum of visible active transactions`() {
    val transactions = FakeSeedData.buildTransactions(refDate)
    val categories = FakeSeedData.categories
    val members = FakeSeedData.members

    PeriodPreset.entries.forEach { preset ->
      val period =
        PeriodResolver.resolve(
          preset = preset,
          referenceDate = refDate,
          customStart = refDate.minusDays(25),
          customEnd = refDate,
        )
      val visibleActive = LedgerAnalytics.filterActiveInPeriod(transactions, period)
      val expectedIncome =
        visibleActive.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
      val expectedExpense =
        visibleActive.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }

      val summary = LedgerAnalytics.summarizePeriod(transactions, period)
      assertEquals(expectedIncome, summary.incomePaise)
      assertEquals(expectedExpense, summary.expensePaise)
      assertEquals(expectedIncome - expectedExpense, summary.differencePaise)
      assertEquals(visibleActive.size, summary.activeTransactionCount)

      val expenseCategories =
        LedgerAnalytics.categoryBreakdown(transactions, categories, period, TransactionType.EXPENSE)
      assertEquals(expectedExpense, expenseCategories.sumOf { it.amountPaise })

      val incomeCategories =
        LedgerAnalytics.categoryBreakdown(transactions, categories, period, TransactionType.INCOME)
      assertEquals(expectedIncome, incomeCategories.sumOf { it.amountPaise })

      val funding = LedgerAnalytics.fundingSplit(transactions, period)
      assertEquals(expectedExpense, funding.totalExpensePaise)
      assertEquals(expectedExpense, funding.items.sumOf { it.amountPaise })

      val salarySplit = LedgerAnalytics.salaryExpenseSplit(transactions, period)
      assertEquals(expectedExpense, salarySplit.totalExpensePaise)
      assertEquals(
        expectedExpense,
        salarySplit.salaryExpensePaise + salarySplit.nonSalaryExpensePaise,
      )
      assertEquals(expectedExpense, salarySplit.items.sumOf { it.amountPaise })

      val memberTotals = LedgerAnalytics.memberSummary(transactions, members, period)
      assertEquals(expectedExpense, memberTotals.sumOf { it.paidExpensePaise })
      assertEquals(expectedIncome, memberTotals.sumOf { it.receivedIncomePaise })
    }
  }

  @Test
  fun `voided records never change summary, category, funding, member, comparison, or forecast totals`() {
    val base = FakeSeedData.buildTransactions(refDate)
    val extraVoided =
      Transaction(
        id = "tx_void_huge",
        familyId = FakeSeedData.FAMILY_ID,
        type = TransactionType.EXPENSE,
        amountPaise = 99_999_900L,
        title = "Voided Huge Purchase",
        categoryId = "cat_groceries",
        occurredOn = refDate.withDayOfMonth(5),
        paidByUid = FakeSeedData.MEMBER_UID,
        createdByUid = FakeSeedData.ADMIN_UID,
        status = TransactionStatus.VOIDED,
        voidReason = "Accidental entry",
        voidedByUid = FakeSeedData.ADMIN_UID,
        createdAtEpochMillis = 1_763_000_000_000L,
        updatedAtEpochMillis = 1_763_000_100_000L,
        fundingSource = FundingSource.FAMILY_CARD,
      )
    val withVoided = base + extraVoided
    val period = PeriodResolver.resolve(PeriodPreset.THIS_MONTH, refDate)

    val s1 = LedgerAnalytics.summarizePeriod(base, period)
    val s2 = LedgerAnalytics.summarizePeriod(withVoided, period)
    assertEquals(s1.incomePaise, s2.incomePaise)
    assertEquals(s1.expensePaise, s2.expensePaise)
    assertEquals(s1.differencePaise, s2.differencePaise)
    assertEquals(s1.voidedExcludedCount + 1, s2.voidedExcludedCount)

    assertEquals(
      LedgerAnalytics.categoryBreakdown(base, FakeSeedData.categories, period),
      LedgerAnalytics.categoryBreakdown(withVoided, FakeSeedData.categories, period),
    )
    assertEquals(
      LedgerAnalytics.fundingSplit(base, period),
      LedgerAnalytics.fundingSplit(withVoided, period),
    )
    assertEquals(
      LedgerAnalytics.salaryExpenseSplit(base, period),
      LedgerAnalytics.salaryExpenseSplit(withVoided, period),
    )
    assertEquals(
      LedgerAnalytics.memberSummary(base, FakeSeedData.members, period),
      LedgerAnalytics.memberSummary(withVoided, FakeSeedData.members, period),
    )
    val c1 =
      LedgerAnalytics.compareLikeForLike(
        base,
        FakeSeedData.categories,
        PeriodPreset.THIS_MONTH,
        refDate,
      )
    val c2 =
      LedgerAnalytics.compareLikeForLike(
        withVoided,
        FakeSeedData.categories,
        PeriodPreset.THIS_MONTH,
        refDate,
      )
    assertEquals(c1.currentSummary.incomePaise, c2.currentSummary.incomePaise)
    assertEquals(c1.currentSummary.expensePaise, c2.currentSummary.expensePaise)
    assertEquals(c1.currentSummary.differencePaise, c2.currentSummary.differencePaise)
    assertEquals(c1.currentSummary.voidedExcludedCount + 1, c2.currentSummary.voidedExcludedCount)
    assertEquals(c1.expenseDeltaPaise, c2.expenseDeltaPaise)
    assertEquals(c1.expenseDeltaBps, c2.expenseDeltaBps)
    assertEquals(c1.categoryComparisons, c2.categoryComparisons)

    assertEquals(
      LedgerAnalytics.forecastMonthEnd(base, refDate),
      LedgerAnalytics.forecastMonthEnd(withVoided, refDate),
    )
  }

  @Test
  fun `memberSummary aggregates by paidByUid separately from createdByUid`() {
    val txPaidByMemberCreatedByAdmin =
      Transaction(
        id = "tx_payer_split",
        familyId = FakeSeedData.FAMILY_ID,
        type = TransactionType.EXPENSE,
        amountPaise = 150_000L,
        title = "Paid by Rohan, logged by Ananya",
        categoryId = "cat_groceries",
        occurredOn = refDate,
        paidByUid = FakeSeedData.MEMBER_UID,
        createdByUid = FakeSeedData.ADMIN_UID,
        createdAtEpochMillis = 1_763_000_000_000L,
        updatedAtEpochMillis = 1_763_000_000_000L,
        fundingSource = FundingSource.PERSONAL,
      )
    val period = Period.fullMonth(java.time.YearMonth.from(refDate))
    val summary =
      LedgerAnalytics.memberSummary(
        listOf(txPaidByMemberCreatedByAdmin),
        FakeSeedData.members,
        period,
      )

    val memberRow = summary.first { it.uid == FakeSeedData.MEMBER_UID }
    val adminRow = summary.first { it.uid == FakeSeedData.ADMIN_UID }
    assertEquals(150_000L, memberRow.paidExpensePaise)
    assertEquals(1, memberRow.expenseCount)
    assertEquals(0L, adminRow.paidExpensePaise)
    assertEquals(0, adminRow.expenseCount)
  }

  @Test
  fun `compareLikeForLike matches partial month window against same days of previous month and states window`() {
    val midMonth = LocalDate.of(2026, 11, 15)
    val txCurrent =
      Transaction(
        id = "tx_cur",
        familyId = FakeSeedData.FAMILY_ID,
        type = TransactionType.EXPENSE,
        amountPaise = 200_000L,
        title = "Nov 10 Grocery",
        categoryId = "cat_groceries",
        occurredOn = LocalDate.of(2026, 11, 10),
        paidByUid = FakeSeedData.ADMIN_UID,
        createdByUid = FakeSeedData.ADMIN_UID,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
      )
    val txPrevIncluded =
      Transaction(
        id = "tx_prev_in",
        familyId = FakeSeedData.FAMILY_ID,
        type = TransactionType.EXPENSE,
        amountPaise = 100_000L,
        title = "Oct 10 Grocery",
        categoryId = "cat_groceries",
        occurredOn = LocalDate.of(2026, 10, 10),
        paidByUid = FakeSeedData.ADMIN_UID,
        createdByUid = FakeSeedData.ADMIN_UID,
        createdAtEpochMillis = 2L,
        updatedAtEpochMillis = 2L,
      )
    // After the 15th of October -> MUST be excluded from like-for-like Oct 1-15 comparison
    val txPrevAfterCutoff =
      Transaction(
        id = "tx_prev_out",
        familyId = FakeSeedData.FAMILY_ID,
        type = TransactionType.EXPENSE,
        amountPaise = 500_000L,
        title = "Oct 25 Grocery",
        categoryId = "cat_groceries",
        occurredOn = LocalDate.of(2026, 10, 25),
        paidByUid = FakeSeedData.ADMIN_UID,
        createdByUid = FakeSeedData.ADMIN_UID,
        createdAtEpochMillis = 3L,
        updatedAtEpochMillis = 3L,
      )

    val comparison =
      LedgerAnalytics.compareLikeForLike(
        transactions = listOf(txCurrent, txPrevIncluded, txPrevAfterCutoff),
        categories = FakeSeedData.categories,
        preset = PeriodPreset.THIS_MONTH,
        referenceDate = midMonth,
      )

    assertEquals(LocalDate.of(2026, 11, 1), comparison.currentWindow.startDate)
    assertEquals(LocalDate.of(2026, 11, 15), comparison.currentWindow.endDate)
    assertEquals(LocalDate.of(2026, 10, 1), comparison.previousWindow.startDate)
    assertEquals(LocalDate.of(2026, 10, 15), comparison.previousWindow.endDate)
    assertTrue(comparison.windowDescription.contains("like-for-like"))
    assertEquals(200_000L, comparison.currentSummary.expensePaise)
    assertEquals(100_000L, comparison.previousSummary.expensePaise)
    assertEquals(100_000L, comparison.expenseDeltaPaise)
    assertEquals(10_000, comparison.expenseDeltaBps) // +100.00%

    val zeroPrevComparison =
      LedgerAnalytics.compareLikeForLike(
        transactions = listOf(txCurrent),
        categories = FakeSeedData.categories,
        preset = PeriodPreset.THIS_MONTH,
        referenceDate = midMonth,
      )
    assertNull(zeroPrevComparison.expenseDeltaBps)
  }

  @Test
  fun `forecastMonthEnd computes run-rate in Long paise and labels itself an estimate`() {
    val tenth = LocalDate.of(2026, 11, 10) // 30 days in Nov -> factor 3x
    val tx1 =
      Transaction(
        id = "tx_f1",
        familyId = FakeSeedData.FAMILY_ID,
        type = TransactionType.EXPENSE,
        amountPaise = 300_000L,
        title = "Groceries",
        categoryId = "cat_groceries",
        occurredOn = LocalDate.of(2026, 11, 4),
        paidByUid = FakeSeedData.ADMIN_UID,
        createdByUid = FakeSeedData.ADMIN_UID,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
      )
    val tx2 =
      tx1.copy(
        id = "tx_f2",
        amountPaise = 200_000L,
        occurredOn = LocalDate.of(2026, 11, 10),
      )

    val forecast = LedgerAnalytics.forecastMonthEnd(listOf(tx1, tx2), tenth)
    assertTrue(forecast.isEstimate)
    assertTrue(forecast.hasEnoughData)
    assertTrue(forecast.estimateLabel.contains("estimate", ignoreCase = true))
    assertEquals(10, forecast.elapsedDays)
    assertEquals(30, forecast.totalDaysInMonth)
    assertEquals(20, forecast.remainingDays)
    assertEquals(500_000L, forecast.spentToDatePaise)
    assertEquals(50_000L, forecast.dailyRunRatePaise)
    assertEquals(1_500_000L, forecast.projectedMonthEndExpensePaise)
    assertEquals(1_000_000L, forecast.projectedRemainingExpensePaise)
    assertEquals(30, forecast.trajectoryPoints.size)
    assertEquals(300_000L, forecast.trajectoryPoints[3].actualCumulativePaise)
    assertEquals(500_000L, forecast.trajectoryPoints[9].actualCumulativePaise)
    assertNull(forecast.trajectoryPoints[10].actualCumulativePaise)

    val emptyForecast = LedgerAnalytics.forecastMonthEnd(emptyList(), tenth)
    assertFalse(emptyForecast.hasEnoughData)
    assertTrue(emptyForecast.isEstimate)
    assertEquals(0L, emptyForecast.projectedMonthEndExpensePaise)
  }

  @Test
  fun `salaryExpenseSplit separates salary and non-salary expenses accurately in seed data`() {
    val transactions = FakeSeedData.buildTransactions(refDate)
    val period = PeriodResolver.resolve(PeriodPreset.THIS_MONTH, refDate)
    val split = LedgerAnalytics.salaryExpenseSplit(transactions, period)

    assertEquals(550_000L, split.salaryExpensePaise)
    assertEquals(1, split.salaryCount)
    assertTrue(split.nonSalaryExpensePaise > 0L)
    assertEquals(split.totalExpensePaise, split.salaryExpensePaise + split.nonSalaryExpensePaise)
    assertEquals(2, split.items.size)
    assertEquals(ExpenseClassification.SALARY, split.items.first().classification)
  }
}
