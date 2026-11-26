package com.example.domain.analytics

import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.Period
import com.example.domain.model.Role
import com.example.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth

data class PeriodSummary(
  val period: Period,
  val incomePaise: Long,
  val expensePaise: Long,
  val differencePaise: Long,
  val activeTransactionCount: Int,
  val expenseCount: Int,
  val incomeCount: Int,
  val voidedExcludedCount: Int,
) {
  val hasAnyActivity: Boolean
    get() = activeTransactionCount > 0
}

data class CategoryBreakdownItem(
  val categoryId: String,
  val categoryName: String,
  val iconKey: String,
  val colorHex: Long,
  val type: TransactionType,
  val amountPaise: Long,
  val transactionCount: Int,
  /** Integer basis points where 10_000 == 100.00%. */
  val shareBps: Int,
)

data class FundingSplitItem(
  val source: FundingSource,
  val amountPaise: Long,
  val transactionCount: Int,
  /** Integer basis points where 10_000 == 100.00%. */
  val shareBps: Int,
)

data class FundingSplitSummary(
  val period: Period,
  val totalExpensePaise: Long,
  val items: List<FundingSplitItem>,
)

data class SalaryExpenseSplitItem(
  val classification: ExpenseClassification,
  val amountPaise: Long,
  val transactionCount: Int,
  /** Integer basis points where 10_000 == 100.00%. */
  val shareBps: Int,
)

data class SalaryExpenseSplitSummary(
  val period: Period,
  val totalExpensePaise: Long,
  val salaryExpensePaise: Long,
  val nonSalaryExpensePaise: Long,
  val salaryCount: Int,
  val nonSalaryCount: Int,
  val items: List<SalaryExpenseSplitItem>,
)

data class MemberSummaryItem(
  val uid: String,
  val displayName: String,
  val role: Role?,
  val paidExpensePaise: Long,
  val receivedIncomePaise: Long,
  val expenseCount: Int,
  val incomeCount: Int,
  /** Integer basis points of period expenses paid by this member. */
  val expenseShareBps: Int,
)

data class CategoryComparisonItem(
  val categoryId: String,
  val categoryName: String,
  val iconKey: String,
  val colorHex: Long,
  val currentPaise: Long,
  val previousPaise: Long,
  val deltaPaise: Long,
)

data class LikeForLikeComparison(
  val currentWindow: Period,
  val previousWindow: Period,
  val windowDescription: String,
  val currentSummary: PeriodSummary,
  val previousSummary: PeriodSummary,
  val expenseDeltaPaise: Long,
  /** Null when previous period expenses are 0. */
  val expenseDeltaBps: Int?,
  val incomeDeltaPaise: Long,
  val differenceDeltaPaise: Long,
  val categoryComparisons: List<CategoryComparisonItem>,
)

data class ForecastDayPoint(
  val dayOfMonth: Int,
  val actualCumulativePaise: Long?,
  val projectedCumulativePaise: Long,
)

data class MonthEndForecast(
  val month: YearMonth,
  val referenceDate: LocalDate,
  val elapsedDays: Int,
  val totalDaysInMonth: Int,
  val remainingDays: Int,
  val spentToDatePaise: Long,
  val incomeToDatePaise: Long,
  val dailyRunRatePaise: Long,
  val projectedMonthEndExpensePaise: Long,
  val projectedRemainingExpensePaise: Long,
  val activeExpenseCount: Int,
  val hasEnoughData: Boolean,
  val isEstimate: Boolean = true,
  val estimateLabel: String,
  val disclaimer: String,
  val trajectoryPoints: List<ForecastDayPoint>,
)

enum class InsightTone {
  GOOD,
  ATTENTION,
  OVER_BUDGET,
  NEUTRAL,
}

data class HighlightedInsight(
  val title: String,
  val body: String,
  val metricValue: String,
  val tone: InsightTone,
  val categoryId: String? = null,
)

data class AnomalyItem(
  val transactionId: String,
  val title: String,
  val categoryId: String,
  val categoryName: String,
  val amountPaise: Long,
  val baselineMedianPaise: Long,
  val multiplierTenths: Int,
  val occurredOn: LocalDate,
  val paidByUid: String,
  val paidByName: String,
  val reason: String,
)

data class AnomalyDetectionResult(
  val period: Period,
  val evaluatedExpenseCount: Int,
  val hasEnoughData: Boolean,
  val anomalies: List<AnomalyItem>,
)

