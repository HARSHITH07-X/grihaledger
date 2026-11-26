package com.example.domain.analytics

import com.example.domain.model.Category
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.Member
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.Period
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure Kotlin household ledger analytics engine.
 * - Excludes voided transactions from all totals and projections.
 * - Uses Long paise exclusively (zero Double/Float).
 * - Uses basis points (10_000 = 100.00%) for integer shares and percent deltas.
 */
object LedgerAnalytics {

  fun filterActiveInPeriod(
    transactions: List<Transaction>,
    period: Period,
  ): List<Transaction> =
    transactions.filter { tx -> !tx.isVoided && tx.occurredOn in period }

  fun summarizePeriod(
    transactions: List<Transaction>,
    period: Period,
  ): PeriodSummary {
    var incomePaise = 0L
    var expensePaise = 0L
    var expenseCount = 0
    var incomeCount = 0
    var voidedExcludedCount = 0

    for (tx in transactions) {
      if (tx.occurredOn !in period) continue
      if (tx.isVoided) {
        voidedExcludedCount++
        continue
      }
      when (tx.type) {
        TransactionType.INCOME -> {
          incomePaise += tx.amountPaise
          incomeCount++
        }
        TransactionType.EXPENSE -> {
          expensePaise += tx.amountPaise
          expenseCount++
        }
      }
    }

    return PeriodSummary(
      period = period,
      incomePaise = incomePaise,
      expensePaise = expensePaise,
      differencePaise = incomePaise - expensePaise,
      activeTransactionCount = expenseCount + incomeCount,
      expenseCount = expenseCount,
      incomeCount = incomeCount,
      voidedExcludedCount = voidedExcludedCount,
    )
  }

  private fun isHindi(languageTag: String): Boolean = languageTag.lowercase().startsWith("hi")

  fun localizedCategoryName(
    categoryId: String,
    fallbackName: String,
    languageTag: String = "en",
  ): String {
    if (!isHindi(languageTag)) return fallbackName
    return when (categoryId) {
      "cat_groceries" -> "राशन"
      "cat_utilities" -> "बिल और उपयोगिता"
      "cat_home" -> "किराया व घर"
      "cat_dining" -> "बाहर खाना"
      "cat_transport" -> "यात्रा"
      "cat_health" -> "स्वास्थ्य"
      "cat_education" -> "शिक्षा"
      "cat_shopping" -> "खरीदारी"
      "cat_salary" -> "वेतन"
      "cat_other_income" -> "अन्य आय"
      else -> fallbackName
    }
  }

  fun categoryBreakdown(
    transactions: List<Transaction>,
    categories: List<Category>,
    period: Period,
    type: TransactionType = TransactionType.EXPENSE,
    languageTag: String = "en",
  ): List<CategoryBreakdownItem> {
    val hi = isHindi(languageTag)
    val activeOfType =
      transactions.filter { tx ->
        !tx.isVoided && tx.occurredOn in period && tx.type == type
      }
    val totalPaise = activeOfType.sumOf { it.amountPaise }
    if (totalPaise == 0L || activeOfType.isEmpty()) return emptyList()

    val categoriesById = categories.associateBy { it.id }
    val grouped = activeOfType.groupBy { it.categoryId }

    return grouped.entries
      .map { (categoryId, items) ->
        val cat = categoriesById[categoryId]
        val sum = items.sumOf { it.amountPaise }
        val fallback = cat?.name ?: if (hi) "अन्य" else "Other"
        CategoryBreakdownItem(
          categoryId = categoryId,
          categoryName = localizedCategoryName(categoryId, fallback, languageTag),
          iconKey = cat?.iconKey ?: "category",
          colorHex = cat?.colorHex ?: 0xFF2D6A4FL,
          type = type,
          amountPaise = sum,
          transactionCount = items.size,
          shareBps = computeBasisPoints(sum, totalPaise),
        )
      }
      .sortedWith(
        compareByDescending<CategoryBreakdownItem> { it.amountPaise }
          .thenBy { categoriesById[it.categoryId]?.sortOrder ?: Int.MAX_VALUE }
          .thenBy { it.categoryName }
      )
  }

  fun fundingSplit(
    transactions: List<Transaction>,
    period: Period,
  ): FundingSplitSummary {
    val activeExpenses =
      transactions.filter { tx ->
        !tx.isVoided && tx.occurredOn in period && tx.type == TransactionType.EXPENSE
      }
    val totalExpensePaise = activeExpenses.sumOf { it.amountPaise }
    val grouped = activeExpenses.groupBy { it.fundingSource }

    val items =
      FundingSource.entries.map { source ->
        val group = grouped[source].orEmpty()
        val sum = group.sumOf { it.amountPaise }
        FundingSplitItem(
          source = source,
          amountPaise = sum,
          transactionCount = group.size,
          shareBps = if (totalExpensePaise > 0L) computeBasisPoints(sum, totalExpensePaise) else 0,
        )
      }

    return FundingSplitSummary(
      period = period,
      totalExpensePaise = totalExpensePaise,
      items = items,
    )
  }

  fun salaryExpenseSplit(
    transactions: List<Transaction>,
    period: Period,
  ): SalaryExpenseSplitSummary {
    val activeExpenses =
      transactions.filter { tx ->
        !tx.isVoided && tx.occurredOn in period && tx.type == TransactionType.EXPENSE
      }
    var salaryPaise = 0L
    var nonSalaryPaise = 0L
    var salaryCount = 0
    var nonSalaryCount = 0

    for (tx in activeExpenses) {
      when (tx.expenseClassification) {
        ExpenseClassification.SALARY -> {
          salaryPaise += tx.amountPaise
          salaryCount++
        }
        ExpenseClassification.NON_SALARY -> {
          nonSalaryPaise += tx.amountPaise
          nonSalaryCount++
        }
      }
    }

    val totalExpensePaise = salaryPaise + nonSalaryPaise
    val items =
      listOf(
        SalaryExpenseSplitItem(
          classification = ExpenseClassification.SALARY,
          amountPaise = salaryPaise,
          transactionCount = salaryCount,
          shareBps = if (totalExpensePaise > 0L) computeBasisPoints(salaryPaise, totalExpensePaise) else 0,
        ),
        SalaryExpenseSplitItem(
          classification = ExpenseClassification.NON_SALARY,
          amountPaise = nonSalaryPaise,
          transactionCount = nonSalaryCount,
          shareBps =
            if (totalExpensePaise > 0L) computeBasisPoints(nonSalaryPaise, totalExpensePaise) else 0,
        ),
      )

    return SalaryExpenseSplitSummary(
      period = period,
      totalExpensePaise = totalExpensePaise,
      salaryExpensePaise = salaryPaise,
      nonSalaryExpensePaise = nonSalaryPaise,
      salaryCount = salaryCount,
      nonSalaryCount = nonSalaryCount,
      items = items,
    )
  }

  fun memberSummary(
    transactions: List<Transaction>,
    members: List<Member>,
    period: Period,
    languageTag: String = "en",
  ): List<MemberSummaryItem> {
    val hi = isHindi(languageTag)
    val active = filterActiveInPeriod(transactions, period)
    val totalExpenses =
      active.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }
    val membersByUid = members.associateBy { it.uid }
    val txsByPayer = active.groupBy { it.paidByUid }
    val allUids = (members.map { it.uid } + txsByPayer.keys).distinct()

    return allUids
      .mapNotNull { uid ->
        val member = membersByUid[uid]
        val memberTxs = txsByPayer[uid].orEmpty()
        if (member == null && memberTxs.isEmpty()) return@mapNotNull null

        var paidExpense = 0L
        var receivedIncome = 0L
        var expenseCount = 0
        var incomeCount = 0
        for (tx in memberTxs) {
          when (tx.type) {
            TransactionType.EXPENSE -> {
              paidExpense += tx.amountPaise
              expenseCount++
            }
            TransactionType.INCOME -> {
              receivedIncome += tx.amountPaise
              incomeCount++
            }
          }
        }

        MemberSummaryItem(
          uid = uid,
          displayName = member?.displayName ?: if (hi) "सदस्य" else "Member",
          role = member?.role,
          paidExpensePaise = paidExpense,
          receivedIncomePaise = receivedIncome,
          expenseCount = expenseCount,
          incomeCount = incomeCount,
          expenseShareBps =
            if (totalExpenses > 0L) computeBasisPoints(paidExpense, totalExpenses) else 0,
        )
      }
      .sortedWith(
        compareByDescending<MemberSummaryItem> { it.paidExpensePaise }
          .thenByDescending { it.receivedIncomePaise }
          .thenBy { it.displayName }
      )
  }

  fun compareLikeForLike(
    transactions: List<Transaction>,
    categories: List<Category>,
    preset: PeriodPreset,
    referenceDate: LocalDate,
    customStart: LocalDate? = null,
    customEnd: LocalDate? = null,
    languageTag: String = "en",
  ): LikeForLikeComparison {
    val hi = isHindi(languageTag)
    val (currentWindow, previousWindow) =
      PeriodResolver.comparisonWindows(
        preset = preset,
        referenceDate = referenceDate,
        customStart = customStart,
        customEnd = customEnd,
        languageTag = languageTag,
      )
    val currentSummary = summarizePeriod(transactions, currentWindow)
    val previousSummary = summarizePeriod(transactions, previousWindow)

    val currentCats =
      categoryBreakdown(transactions, categories, currentWindow, TransactionType.EXPENSE, languageTag)
        .associateBy { it.categoryId }
    val previousCats =
      categoryBreakdown(transactions, categories, previousWindow, TransactionType.EXPENSE, languageTag)
        .associateBy { it.categoryId }
    val categoriesById = categories.associateBy { it.id }

    val categoryIds = (currentCats.keys + previousCats.keys).distinct()
    val categoryComparisons =
      categoryIds
        .map { categoryId ->
          val cur = currentCats[categoryId]?.amountPaise ?: 0L
          val prev = previousCats[categoryId]?.amountPaise ?: 0L
          val cat = categoriesById[categoryId]
          val rawName =
            cat?.name ?: currentCats[categoryId]?.categoryName
              ?: previousCats[categoryId]?.categoryName ?: if (hi) "अन्य" else "Other"
          CategoryComparisonItem(
            categoryId = categoryId,
            categoryName = localizedCategoryName(categoryId, rawName, languageTag),
            iconKey = cat?.iconKey ?: "category",
            colorHex = cat?.colorHex ?: 0xFF2D6A4FL,
            currentPaise = cur,
            previousPaise = prev,
            deltaPaise = cur - prev,
          )
        }
        .sortedByDescending { kotlin.math.abs(it.deltaPaise).coerceAtLeast(it.currentPaise) }

    val expenseDelta = currentSummary.expensePaise - previousSummary.expensePaise
    val expenseDeltaBps =
      if (previousSummary.expensePaise > 0L) {
        computeSignedBasisPoints(expenseDelta, previousSummary.expensePaise)
      } else {
        null
      }

    val vsWord = if (hi) "बनाम" else "vs"
    return LikeForLikeComparison(
      currentWindow = currentWindow,
      previousWindow = previousWindow,
      windowDescription = "${currentWindow.label} $vsWord ${previousWindow.label}",
      currentSummary = currentSummary,
      previousSummary = previousSummary,
      expenseDeltaPaise = expenseDelta,
      expenseDeltaBps = expenseDeltaBps,
      incomeDeltaPaise = currentSummary.incomePaise - previousSummary.incomePaise,
      differenceDeltaPaise = currentSummary.differencePaise - previousSummary.differencePaise,
      categoryComparisons = categoryComparisons,
    )
  }

  fun forecastMonthEnd(
    transactions: List<Transaction>,
    referenceDate: LocalDate,
    languageTag: String = "en",
  ): MonthEndForecast {
    val hi = isHindi(languageTag)
    val month = YearMonth.from(referenceDate)
    val mtdPeriod = Period.monthToDate(referenceDate, languageTag)
    val totalDaysInMonth = month.lengthOfMonth()
    val elapsedDays = referenceDate.dayOfMonth.coerceAtLeast(1)
    val remainingDays = (totalDaysInMonth - elapsedDays).coerceAtLeast(0)

    val activeMtd = filterActiveInPeriod(transactions, mtdPeriod)
    val mtdExpenses = activeMtd.filter { it.type == TransactionType.EXPENSE }
    val mtdIncomes = activeMtd.filter { it.type == TransactionType.INCOME }

    val spentToDatePaise = mtdExpenses.sumOf { it.amountPaise }
    val incomeToDatePaise = mtdIncomes.sumOf { it.amountPaise }
    val dailyRunRatePaise =
      if (spentToDatePaise == 0L) 0L else (spentToDatePaise + (elapsedDays / 2L)) / elapsedDays
    val projectedMonthEndExpensePaise =
      if (spentToDatePaise == 0L) 0L
      else ((spentToDatePaise * totalDaysInMonth) + (elapsedDays / 2L)) / elapsedDays
    val projectedRemainingExpensePaise =
      (projectedMonthEndExpensePaise - spentToDatePaise).coerceAtLeast(0L)

    val dailyExpenseSums = LongArray(totalDaysInMonth + 1)
    for (tx in mtdExpenses) {
      val day = tx.occurredOn.dayOfMonth
      if (day in 1..elapsedDays) {
        dailyExpenseSums[day] += tx.amountPaise
      }
    }

    var runningActual = 0L
    val trajectoryPoints =
      (1..totalDaysInMonth).map { day ->
        if (day <= elapsedDays) {
          runningActual += dailyExpenseSums[day]
        }
        val projectedAtDay =
          if (spentToDatePaise == 0L) 0L
          else ((spentToDatePaise * day) + (elapsedDays / 2L)) / elapsedDays
        ForecastDayPoint(
          dayOfMonth = day,
          actualCumulativePaise = if (day <= elapsedDays) runningActual else null,
          projectedCumulativePaise = projectedAtDay,
        )
      }

    val monthLocale = if (hi) Locale.forLanguageTag("hi-IN") else Locale.ENGLISH
    val monthName = month.month.getDisplayName(TextStyle.FULL, monthLocale)
    return MonthEndForecast(
      month = month,
      referenceDate = referenceDate,
      elapsedDays = elapsedDays,
      totalDaysInMonth = totalDaysInMonth,
      remainingDays = remainingDays,
      spentToDatePaise = spentToDatePaise,
      incomeToDatePaise = incomeToDatePaise,
      dailyRunRatePaise = dailyRunRatePaise,
      projectedMonthEndExpensePaise = projectedMonthEndExpensePaise,
      projectedRemainingExpensePaise = projectedRemainingExpensePaise,
      activeExpenseCount = mtdExpenses.size,
      hasEnoughData = mtdExpenses.isNotEmpty(),
      isEstimate = true,
      estimateLabel =
        if (hi) "माह के अंत का अनुमानित खर्च (दैनिक दर)"
        else "Estimated month-end spend (run-rate)",
      disclaimer =
        if (hi) {
          "$monthName के $totalDaysInMonth में से $elapsedDays दिनों के आधार पर दैनिक दर का अनुमान (रद्द रिकॉर्ड शामिल नहीं)।"
        } else {
          "Run-rate estimate for $monthName based on $elapsedDays of $totalDaysInMonth days (excludes voided records)."
        },
      trajectoryPoints = trajectoryPoints,
    )
  }

  fun highlightInsight(
    transactions: List<Transaction>,
    categories: List<Category>,
    preset: PeriodPreset,
    referenceDate: LocalDate,
    customStart: LocalDate? = null,
    customEnd: LocalDate? = null,
    languageTag: String = "en",
  ): HighlightedInsight {
    val hi = isHindi(languageTag)
    val period = PeriodResolver.resolve(preset, referenceDate, customStart, customEnd, languageTag)
    val summary = summarizePeriod(transactions, period)
    if (!summary.hasAnyActivity) {
      return HighlightedInsight(
        title = if (hi) "इस अवधि में कोई सक्रिय प्रविष्टि नहीं" else "No active entries in this period",
        body =
          if (hi) "खर्च की मुख्य अंतर्दृष्टि देखने के लिए परिवार का कोई खर्च या आय जोड़ें।"
          else "Add a household expense or income entry to unlock spending highlights.",
        metricValue = "₹0",
        tone = InsightTone.NEUTRAL,
      )
    }

    val breakdown = categoryBreakdown(transactions, categories, period, TransactionType.EXPENSE, languageTag)
    if (breakdown.isNotEmpty()) {
      val top = breakdown.first()
      val shareLabel = formatBasisPointsPercent(top.shareBps)
      return HighlightedInsight(
        title =
          if (hi) "घरेलू खर्च में ${top.categoryName} सबसे आगे है"
          else "${top.categoryName} leads household spending",
        body =
          if (hi) {
            "${period.label} के कुल खर्च का $shareLabel ${top.categoryName} पर गया (${top.transactionCount} प्रविष्टियाँ)।"
          } else {
            "$shareLabel of expenses in ${period.label} went to ${top.categoryName} (${top.transactionCount} entries)."
          },
        metricValue = MoneyFormatter.formatEnIn(top.amountPaise, includePaise = top.amountPaise % 100L != 0L),
        tone = if (summary.differencePaise >= 0L) InsightTone.GOOD else InsightTone.ATTENTION,
        categoryId = top.categoryId,
      )
    }

    return HighlightedInsight(
      title = if (hi) "बिना किसी खर्च के आय दर्ज की गई" else "Income recorded with zero expenses",
      body =
        if (hi) "${period.label} में ${summary.incomeCount} आय प्रविष्टि दर्ज हुई।"
        else "${summary.incomeCount} income entry logged in ${period.label}.",
      metricValue =
        MoneyFormatter.formatEnIn(
          summary.incomePaise,
          includePaise = summary.incomePaise % 100L != 0L,
          showPlusSign = true,
        ),
      tone = InsightTone.GOOD,
    )
  }

  fun detectAnomalies(
    transactions: List<Transaction>,
    categories: List<Category>,
    members: List<Member>,
    period: Period,
    languageTag: String = "en",
  ): AnomalyDetectionResult {
    val hi = isHindi(languageTag)
    val activePeriodExpenses =
      transactions.filter { tx ->
        !tx.isVoided && tx.occurredOn in period && tx.type == TransactionType.EXPENSE
      }
    val allActiveExpenses =
      transactions.filter { tx -> !tx.isVoided && tx.type == TransactionType.EXPENSE }

    if (activePeriodExpenses.isEmpty() || allActiveExpenses.size < 4) {
      return AnomalyDetectionResult(
        period = period,
        evaluatedExpenseCount = activePeriodExpenses.size,
        hasEnoughData = false,
        anomalies = emptyList(),
      )
    }

    val categoriesById = categories.associateBy { it.id }
    val membersByUid = members.associateBy { it.uid }
    val overallMedian = integerMedian(allActiveExpenses.map { it.amountPaise })
    val categoryMedians =
      allActiveExpenses
        .groupBy { it.categoryId }
        .filterValues { it.size >= 3 }
        .mapValues { (_, list) -> integerMedian(list.map { it.amountPaise }) }

    val anomalies =
      activePeriodExpenses
        .mapNotNull { tx ->
          val categoryBaseline = categoryMedians[tx.categoryId]
          val baseline = categoryBaseline ?: overallMedian
          if (baseline <= 0L) return@mapNotNull null

          val excess = tx.amountPaise - baseline
          // Flag expenses >= 2x baseline AND at least ₹500 above baseline
          if (tx.amountPaise < baseline * 2L || excess < 50_000L) return@mapNotNull null

          val catName =
            localizedCategoryName(
              tx.categoryId,
              categoriesById[tx.categoryId]?.name ?: if (hi) "अन्य" else "Other",
              languageTag,
            )
          val payerName = membersByUid[tx.paidByUid]?.displayName ?: if (hi) "सदस्य" else "Member"
          val multTenths = (((tx.amountPaise * 10L) + (baseline / 2L)) / baseline).toInt()
          val multWhole = multTenths / 10
          val multDec = multTenths % 10
          val multLabel = if (multDec == 0) "${multWhole}x" else "${multWhole}.${multDec}x"
          val baselineScope =
            if (hi) {
              if (categoryBaseline != null) "$catName के औसत" else "घरेलू औसत"
            } else {
              if (categoryBaseline != null) "$catName median" else "household median"
            }
          val baselineFormatted =
            MoneyFormatter.formatEnIn(baseline, includePaise = baseline % 100L != 0L)
          val reason =
            if (hi) "$baselineScope ($baselineFormatted) से $multLabel अधिक"
            else "$multLabel above $baselineScope ($baselineFormatted)"

          AnomalyItem(
            transactionId = tx.id,
            title = tx.title,
            categoryId = tx.categoryId,
            categoryName = catName,
            amountPaise = tx.amountPaise,
            baselineMedianPaise = baseline,
            multiplierTenths = multTenths,
            occurredOn = tx.occurredOn,
            paidByUid = tx.paidByUid,
            paidByName = payerName,
            reason = reason,
          )
        }
        .sortedByDescending { it.amountPaise - it.baselineMedianPaise }
        .take(5)

    return AnomalyDetectionResult(
      period = period,
      evaluatedExpenseCount = activePeriodExpenses.size,
      hasEnoughData = true,
      anomalies = anomalies,
    )
  }

  fun integerMedian(values: List<Long>): Long {
    if (values.isEmpty()) return 0L
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) {
      sorted[mid]
    } else {
      (sorted[mid - 1] + sorted[mid]) / 2L
    }
  }

  fun computeBasisPoints(part: Long, total: Long): Int {
    if (total <= 0L || part <= 0L) return 0
    return (((part * 10_000L) + (total / 2L)) / total).toInt().coerceIn(0, 10_000)
  }

  fun computeSignedBasisPoints(delta: Long, base: Long): Int {
    if (base <= 0L || delta == 0L) return 0
    val absBps = (((kotlin.math.abs(delta) * 10_000L) + (base / 2L)) / base).toInt()
    return if (delta < 0L) -absBps else absBps
  }

  fun formatBasisPointsPercent(bps: Int): String {
    val sign = if (bps < 0) "-" else ""
    val abs = kotlin.math.abs(bps)
    val tenths = (abs + 5) / 10
    val whole = tenths / 10
    val decimal = tenths % 10
    return if (decimal == 0) "$sign$whole%" else "$sign$whole.$decimal%"
  }
}
