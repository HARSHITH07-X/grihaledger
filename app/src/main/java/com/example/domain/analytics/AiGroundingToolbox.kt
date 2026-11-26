package com.example.domain.analytics

import com.example.domain.model.AdvisorEvidenceItem
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.Period
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.AdvisorAuthorizedSnapshot
import java.time.LocalDate

enum class AdvisorToolName(val wireName: String) {
  GET_SUMMARY("getSummary"),
  COMPARE_PERIODS("comparePeriods"),
  GET_CATEGORY_BREAKDOWN("getCategoryBreakdown"),
  GET_MEMBER_SUMMARY("getMemberSummary"),
  FORECAST_MONTH_END("forecastMonthEnd"),
  DETECT_ANOMALIES("detectAnomalies");

  companion object {
    fun fromWireName(name: String): AdvisorToolName? = entries.firstOrNull { it.wireName == name }
  }
}

sealed interface AdvisorToolComputation {
  val toolName: AdvisorToolName
  val windowLabel: String
  val evidenceRefs: List<AdvisorEvidenceItem>

  data class SummaryResult(
    override val windowLabel: String,
    val summary: PeriodSummary,
    override val evidenceRefs: List<AdvisorEvidenceItem>,
    val salarySplit: SalaryExpenseSplitSummary =
      LedgerAnalytics.salaryExpenseSplit(emptyList(), summary.period),
  ) : AdvisorToolComputation {
    override val toolName: AdvisorToolName = AdvisorToolName.GET_SUMMARY
  }

  data class ComparisonResult(
    override val windowLabel: String,
    val comparison: LikeForLikeComparison,
    override val evidenceRefs: List<AdvisorEvidenceItem>,
  ) : AdvisorToolComputation {
    override val toolName: AdvisorToolName = AdvisorToolName.COMPARE_PERIODS
  }

  data class CategoryBreakdownResult(
    override val windowLabel: String,
    val type: TransactionType,
    val summary: PeriodSummary,
    val items: List<CategoryBreakdownItem>,
    override val evidenceRefs: List<AdvisorEvidenceItem>,
  ) : AdvisorToolComputation {
    override val toolName: AdvisorToolName = AdvisorToolName.GET_CATEGORY_BREAKDOWN
  }

  data class MemberSummaryResult(
    override val windowLabel: String,
    val summary: PeriodSummary,
    val fundingSplit: FundingSplitSummary,
    val items: List<MemberSummaryItem>,
    override val evidenceRefs: List<AdvisorEvidenceItem>,
  ) : AdvisorToolComputation {
    override val toolName: AdvisorToolName = AdvisorToolName.GET_MEMBER_SUMMARY
  }

  data class ForecastResult(
    override val windowLabel: String,
    val forecast: MonthEndForecast,
    override val evidenceRefs: List<AdvisorEvidenceItem>,
  ) : AdvisorToolComputation {
    override val toolName: AdvisorToolName = AdvisorToolName.FORECAST_MONTH_END
  }

  data class AnomalyResult(
    override val windowLabel: String,
    val detection: AnomalyDetectionResult,
    override val evidenceRefs: List<AdvisorEvidenceItem>,
  ) : AdvisorToolComputation {
    override val toolName: AdvisorToolName = AdvisorToolName.DETECT_ANOMALIES
  }
}

/**
 * Pure Kotlin grounding toolbox that delegates every calculation exclusively to
 * [LedgerAnalytics] over the user's already-loaded, already-authorized snapshot.
 * - Sanitizes all untrusted transaction titles/notes to prevent prompt injection.
 * - Caps returned evidence/category slices so raw ledger dumps are never sent to the model.
 */
object AiGroundingToolbox {

  const val MAX_CATEGORIES_SENT = 6
  const val MAX_EVIDENCE_REFS_PER_CALL = 4
  const val MAX_UNTRUSTED_LABEL_LEN = 48
  const val MAX_QUESTION_LEN = 240

  private val CONTROL_OR_INJECTION_REGEX = Regex("[\\p{Cntrl}<>`{}\\[\\]\\\\]")
  private val WHITESPACE_REGEX = Regex("\\s+")

  fun sanitizeUntrustedText(raw: String, maxLength: Int = MAX_UNTRUSTED_LABEL_LEN): String {
    val cleaned =
      raw
        .replace(CONTROL_OR_INJECTION_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()
    if (cleaned.isEmpty()) return "(unlabelled)"
    return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength - 1).trimEnd() + "…"
  }

  fun sanitizeUserQuestion(raw: String): String {
    val cleaned =
      raw
        .replace(CONTROL_OR_INJECTION_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()
    return if (cleaned.length <= MAX_QUESTION_LEN) cleaned else cleaned.take(MAX_QUESTION_LEN)
  }

  fun parsePreset(raw: String?): PeriodPreset {
    val normalized = raw?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_')
    return when (normalized) {
      "LAST_MONTH" -> PeriodPreset.LAST_MONTH
      "LAST_3_MONTHS", "LAST_3" -> PeriodPreset.LAST_3_MONTHS
      "LAST_6_MONTHS", "LAST_6" -> PeriodPreset.LAST_6_MONTHS
      "CALENDAR_YEAR" -> PeriodPreset.CALENDAR_YEAR
      "FINANCIAL_YEAR", "FY" -> PeriodPreset.FINANCIAL_YEAR
      else -> PeriodPreset.THIS_MONTH
    }
  }

  fun executeSummary(
    snapshot: AdvisorAuthorizedSnapshot,
    preset: PeriodPreset = PeriodPreset.THIS_MONTH,
  ): AdvisorToolComputation.SummaryResult {
    val period = PeriodResolver.resolve(preset, snapshot.referenceDate, languageTag = snapshot.languageTag)
    val summary = LedgerAnalytics.summarizePeriod(snapshot.transactions, period)
    val salarySplit = LedgerAnalytics.salaryExpenseSplit(snapshot.transactions, period)
    val refs = buildTopEvidenceRefs(snapshot, period, typeFilter = null, limit = MAX_EVIDENCE_REFS_PER_CALL)
    return AdvisorToolComputation.SummaryResult(
      windowLabel = period.label,
      summary = summary,
      evidenceRefs = refs,
      salarySplit = salarySplit,
    )
  }

  fun executeComparePeriods(
    snapshot: AdvisorAuthorizedSnapshot,
    preset: PeriodPreset = PeriodPreset.THIS_MONTH,
  ): AdvisorToolComputation.ComparisonResult {
    val comparison =
      LedgerAnalytics.compareLikeForLike(
        transactions = snapshot.transactions,
        categories = snapshot.categories,
        preset = preset,
        referenceDate = snapshot.referenceDate,
        languageTag = snapshot.languageTag,
      )
    val refs =
      buildTopEvidenceRefs(
        snapshot = snapshot,
        period = comparison.currentWindow,
        typeFilter = TransactionType.EXPENSE,
        limit = MAX_EVIDENCE_REFS_PER_CALL,
      )
    return AdvisorToolComputation.ComparisonResult(
      windowLabel = comparison.windowDescription,
      comparison = comparison,
      evidenceRefs = refs,
    )
  }

  fun executeCategoryBreakdown(
    snapshot: AdvisorAuthorizedSnapshot,
    preset: PeriodPreset = PeriodPreset.THIS_MONTH,
    type: TransactionType = TransactionType.EXPENSE,
  ): AdvisorToolComputation.CategoryBreakdownResult {
    val period = PeriodResolver.resolve(preset, snapshot.referenceDate, languageTag = snapshot.languageTag)
    val summary = LedgerAnalytics.summarizePeriod(snapshot.transactions, period)
    val items =
      LedgerAnalytics.categoryBreakdown(
          transactions = snapshot.transactions,
          categories = snapshot.categories,
          period = period,
          type = type,
          languageTag = snapshot.languageTag,
        )
        .take(MAX_CATEGORIES_SENT)
    val topCatId = items.firstOrNull()?.categoryId
    val refs =
      buildTopEvidenceRefs(
        snapshot = snapshot,
        period = period,
        typeFilter = type,
        categoryIdFilter = topCatId,
        limit = MAX_EVIDENCE_REFS_PER_CALL,
      )
    return AdvisorToolComputation.CategoryBreakdownResult(
      windowLabel = period.label,
      type = type,
      summary = summary,
      items = items,
      evidenceRefs = refs,
    )
  }

  fun executeMemberSummary(
    snapshot: AdvisorAuthorizedSnapshot,
    preset: PeriodPreset = PeriodPreset.THIS_MONTH,
  ): AdvisorToolComputation.MemberSummaryResult {
    val period = PeriodResolver.resolve(preset, snapshot.referenceDate, languageTag = snapshot.languageTag)
    val summary = LedgerAnalytics.summarizePeriod(snapshot.transactions, period)
    val funding = LedgerAnalytics.fundingSplit(snapshot.transactions, period)
    val items =
      LedgerAnalytics.memberSummary(
        transactions = snapshot.transactions,
        members = snapshot.members,
        period = period,
        languageTag = snapshot.languageTag,
      )
    val refs =
      buildTopEvidenceRefs(
        snapshot = snapshot,
        period = period,
        typeFilter = TransactionType.EXPENSE,
        limit = 3,
      )
    return AdvisorToolComputation.MemberSummaryResult(
      windowLabel = period.label,
      summary = summary,
      fundingSplit = funding,
      items = items,
      evidenceRefs = refs,
    )
  }

  fun executeForecastMonthEnd(
    snapshot: AdvisorAuthorizedSnapshot,
  ): AdvisorToolComputation.ForecastResult {
    val forecast =
      LedgerAnalytics.forecastMonthEnd(
        transactions = snapshot.transactions,
        referenceDate = snapshot.referenceDate,
        languageTag = snapshot.languageTag,
      )
    val mtdPeriod = Period.monthToDate(snapshot.referenceDate, snapshot.languageTag)
    val refs =
      buildTopEvidenceRefs(
        snapshot = snapshot,
        period = mtdPeriod,
        typeFilter = TransactionType.EXPENSE,
        limit = 3,
      )
    return AdvisorToolComputation.ForecastResult(
      windowLabel = mtdPeriod.label,
      forecast = forecast,
      evidenceRefs = refs,
    )
  }

  fun executeDetectAnomalies(
    snapshot: AdvisorAuthorizedSnapshot,
    preset: PeriodPreset = PeriodPreset.THIS_MONTH,
  ): AdvisorToolComputation.AnomalyResult {
    val period = PeriodResolver.resolve(preset, snapshot.referenceDate, languageTag = snapshot.languageTag)
    val detection =
      LedgerAnalytics.detectAnomalies(
        transactions = snapshot.transactions,
        categories = snapshot.categories,
        members = snapshot.members,
        period = period,
        languageTag = snapshot.languageTag,
      )
    val refs =
      detection.anomalies.take(MAX_EVIDENCE_REFS_PER_CALL).map { anomaly ->
        val safeTitle = sanitizeUntrustedText(anomaly.title)
        AdvisorEvidenceItem(
          label = "${sanitizeUntrustedText(anomaly.categoryName, 24)} · $safeTitle",
          formattedValue =
            MoneyFormatter.formatEnIn(
              anomaly.amountPaise,
              includePaise = anomaly.amountPaise % 100L != 0L,
            ),
          rawPaise = anomaly.amountPaise,
          transactionId = anomaly.transactionId,
          transactionTitle = safeTitle,
          categoryId = anomaly.categoryId,
        )
      }
    return AdvisorToolComputation.AnomalyResult(
      windowLabel = period.label,
      detection = detection,
      evidenceRefs = refs,
    )
  }

  fun selectHeuristicToolsForQuestion(question: String, questionId: String?): List<AdvisorToolName> {
    return when (questionId) {
      "q_month_change" -> listOf(AdvisorToolName.COMPARE_PERIODS, AdvisorToolName.DETECT_ANOMALIES)
      "q_top_categories" -> listOf(AdvisorToolName.GET_CATEGORY_BREAKDOWN, AdvisorToolName.GET_SUMMARY)
      "q_month_forecast" -> listOf(AdvisorToolName.FORECAST_MONTH_END, AdvisorToolName.GET_SUMMARY)
      "q_unusual_expenses" -> listOf(AdvisorToolName.DETECT_ANOMALIES, AdvisorToolName.GET_CATEGORY_BREAKDOWN)
      "q_member_spending" -> listOf(AdvisorToolName.GET_MEMBER_SUMMARY, AdvisorToolName.GET_SUMMARY)
      "q_difference_health" -> listOf(AdvisorToolName.GET_SUMMARY, AdvisorToolName.GET_MEMBER_SUMMARY)
      else -> {
        val lower = question.lowercase()
        when {
          "forecast" in lower ||
            "month end" in lower ||
            "run-rate" in lower ||
            "estimate" in lower ||
            "अनुमान" in lower ||
            "भविष्य" in lower ||
            "anuman" in lower ->
            listOf(AdvisorToolName.FORECAST_MONTH_END, AdvisorToolName.GET_SUMMARY)

          "unusual" in lower ||
            "anomal" in lower ||
            "spike" in lower ||
            "big" in lower ||
            "outlier" in lower ||
            "असामान्य" in lower ||
            "बड़ा" in lower ||
            "bada" in lower ->
            listOf(AdvisorToolName.DETECT_ANOMALIES, AdvisorToolName.GET_CATEGORY_BREAKDOWN)

          "compare" in lower ||
            "last month" in lower ||
            "change" in lower ||
            "why" in lower ||
            "तुलना" in lower ||
            "बदल" in lower ||
            "क्यों" in lower ||
            "पिछल" in lower ||
            "tulna" in lower ->
            listOf(AdvisorToolName.COMPARE_PERIODS, AdvisorToolName.GET_CATEGORY_BREAKDOWN)

          "who" in lower ||
            "member" in lower ||
            "paid" in lower ||
            "किसने" in lower ||
            "सदस्य" in lower ||
            "किसका" in lower ||
            "kaun" in lower ||
            "kisne" in lower ->
            listOf(AdvisorToolName.GET_MEMBER_SUMMARY, AdvisorToolName.GET_SUMMARY)

          "category" in lower ||
            "categories" in lower ||
            "where" in lower ||
            "कहाँ" in lower ||
            "श्रेणी" in lower ||
            "kahan" in lower ->
            listOf(AdvisorToolName.GET_CATEGORY_BREAKDOWN, AdvisorToolName.GET_SUMMARY)

          "salary" in lower ||
            "non-salary" in lower ||
            "non salary" in lower ||
            "वेतन" in lower ||
            "पगार" in lower ||
            "तनख्वाह" in lower ->
            listOf(AdvisorToolName.GET_SUMMARY, AdvisorToolName.GET_CATEGORY_BREAKDOWN)

          else -> listOf(AdvisorToolName.GET_SUMMARY, AdvisorToolName.COMPARE_PERIODS)
        }
      }
    }
  }

  private fun buildTopEvidenceRefs(
    snapshot: AdvisorAuthorizedSnapshot,
    period: Period,
    typeFilter: TransactionType?,
    categoryIdFilter: String? = null,
    limit: Int,
  ): List<AdvisorEvidenceItem> {
    val active =
      LedgerAnalytics.filterActiveInPeriod(snapshot.transactions, period)
        .filter { tx ->
          (typeFilter == null || tx.type == typeFilter) &&
            (categoryIdFilter == null || tx.categoryId == categoryIdFilter)
        }
        .sortedByDescending { it.amountPaise }
        .take(limit)

    val categoriesById = snapshot.categories.associateBy { it.id }
    val uncategorizedFallback =
      if (snapshot.languageTag.lowercase().startsWith("hi")) "अन्य" else "Other"
    return active.map { tx ->
      val safeTitle = sanitizeUntrustedText(tx.title)
      val localizedCat =
        LedgerAnalytics.localizedCategoryName(
          categoryId = tx.categoryId,
          fallbackName = categoriesById[tx.categoryId]?.name ?: uncategorizedFallback,
          languageTag = snapshot.languageTag,
        )
      val safeCat = sanitizeUntrustedText(localizedCat, 24)
      AdvisorEvidenceItem(
        label = "$safeTitle ($safeCat)",
        formattedValue =
          MoneyFormatter.formatEnIn(
            tx.amountPaise,
            includePaise = tx.amountPaise % 100L != 0L,
            showPlusSign = tx.type == TransactionType.INCOME,
          ),
        rawPaise = tx.amountPaise,
        transactionId = tx.id,
        transactionTitle = safeTitle,
        categoryId = tx.categoryId,
      )
    }
  }
}
