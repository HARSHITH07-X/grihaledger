package com.example.data.fake

import com.example.data.ai.AdvisorGroundingCodec
import com.example.domain.analytics.AdvisorToolComputation
import com.example.domain.analytics.AiGroundingToolbox
import com.example.domain.analytics.LedgerAnalytics
import com.example.domain.model.AdvisorFeedback
import com.example.domain.model.AdvisorResponse
import com.example.domain.model.AdvisorWebCitation
import com.example.domain.model.AppLanguage
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.MoneyFormatter
import com.example.domain.repository.AdvisorAuthorizedSnapshot
import com.example.domain.repository.AdvisorStreamEvent
import com.example.domain.repository.AiAdvisorRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic in-memory [AiAdvisorRepository] for previews and unit tests.
 * Executes the exact same [AiGroundingToolbox] and [LedgerAnalytics] math used by Gemini tool calls,
 * supporting both English and Devanagari Hindi streaming outputs.
 */
@Singleton
class FakeAiAdvisorRepository @Inject constructor() : AiAdvisorRepository {

  var nextFailure: Throwable? = null
  val feedbackLog = mutableMapOf<String, AdvisorFeedback>()

  override suspend fun askQuestion(
    question: String,
    questionId: String?,
    snapshot: AdvisorAuthorizedSnapshot,
  ): Result<AdvisorResponse> {
    var completed: AdvisorResponse? = null
    var failure: Throwable? = null
    askQuestionStream(question, questionId, snapshot).collect { event ->
      when (event) {
        is AdvisorStreamEvent.Completed -> completed = event.response
        is AdvisorStreamEvent.Failed -> failure = event.error
        is AdvisorStreamEvent.ToolsReady,
        is AdvisorStreamEvent.StreamingText -> Unit
      }
    }
    return when {
      completed != null -> Result.success(completed!!)
      failure != null -> Result.failure(failure!!)
      else -> Result.failure(IllegalStateException("Fake advisor failed to complete."))
    }
  }

  override fun askQuestionStream(
    question: String,
    questionId: String?,
    snapshot: AdvisorAuthorizedSnapshot,
  ): Flow<AdvisorStreamEvent> = flow {
    nextFailure?.let { error ->
      nextFailure = null
      emit(AdvisorStreamEvent.Failed(error))
      return@flow
    }

    val safeQuestion = AiGroundingToolbox.sanitizeUserQuestion(question)
    val respondInHindi =
      AppLanguage.shouldRespondInHindi(
        question = safeQuestion,
        selectedLanguage = AppLanguage.fromTag(snapshot.languageTag),
      )
    val effectiveSnapshot =
      if (respondInHindi && !snapshot.languageTag.lowercase().startsWith("hi")) {
        snapshot.copy(languageTag = AppLanguage.HINDI.tag)
      } else {
        snapshot
      }

    if (safeQuestion.isBlank()) {
      emit(
        AdvisorStreamEvent.Failed(
          IllegalArgumentException(
            if (respondInHindi) "कृपया सलाहकार से पूछने के लिए कोई प्रश्न लिखें।"
            else "Please enter a question to ask the advisor."
          )
        )
      )
      return@flow
    }

    val responseId = "adv_${UUID.randomUUID().toString().take(8)}"
    if (effectiveSnapshot.transactions.none { !it.isVoided }) {
      emit(
        AdvisorStreamEvent.Completed(
          AdvisorGroundingCodec.notEnoughDataResponse(
            id = responseId,
            question = safeQuestion,
            questionId = questionId,
            toolsUsed = listOf("getSummary"),
            languageTag = effectiveSnapshot.languageTag,
          )
        )
      )
      return@flow
    }

    val selectedTools = AiGroundingToolbox.selectHeuristicToolsForQuestion(safeQuestion, questionId)
    val computations =
      selectedTools.map { tool ->
        AdvisorGroundingCodec.computeToJson(
          toolName = tool,
          presetArg = null,
          typeArg = null,
          snapshot = effectiveSnapshot,
        ).first
      }

    val primary = computations.firstOrNull()
    val hasEnough =
      when (primary) {
        is AdvisorToolComputation.ForecastResult -> primary.forecast.hasEnoughData
        is AdvisorToolComputation.AnomalyResult -> primary.detection.hasEnoughData
        is AdvisorToolComputation.ComparisonResult ->
          primary.comparison.currentSummary.hasAnyActivity ||
            primary.comparison.previousSummary.hasAnyActivity
        is AdvisorToolComputation.CategoryBreakdownResult -> primary.items.isNotEmpty()
        is AdvisorToolComputation.MemberSummaryResult -> primary.summary.hasAnyActivity
        is AdvisorToolComputation.SummaryResult -> primary.summary.hasAnyActivity
        null -> false
      }

    val toolsUsed = computations.map { it.toolName.wireName }.distinct()
    if (!hasEnough) {
      emit(
        AdvisorStreamEvent.Completed(
          AdvisorGroundingCodec.notEnoughDataResponse(
            id = responseId,
            question = safeQuestion,
            questionId = questionId,
            toolsUsed = toolsUsed,
            languageTag = effectiveSnapshot.languageTag,
          )
        )
      )
      return@flow
    }

    val comparisonWindow =
      when (primary) {
        is AdvisorToolComputation.ComparisonResult -> primary.comparison.windowDescription
        else -> primary?.windowLabel
      }
    val evidence = AdvisorGroundingCodec.collectVerifiedEvidence(computations, effectiveSnapshot)

    // Emit immediate tool evidence before streaming tokens (zero artificial delay for crisp UX & deterministic tests).
    emit(
      AdvisorStreamEvent.ToolsReady(
        comparisonWindow = comparisonWindow,
        evidence = evidence,
        toolsUsed = toolsUsed,
      )
    )

    val finding = buildGroundedFinding(primary, respondInHindi)
    val midpoint = (finding.length / 2).coerceAtLeast(1)
    emit(
      AdvisorStreamEvent.StreamingText(
        partialFinding = finding.substring(0, midpoint),
        comparisonWindow = comparisonWindow,
        evidence = evidence,
        toolsUsed = toolsUsed,
      )
    )
    emit(
      AdvisorStreamEvent.StreamingText(
        partialFinding = finding,
        comparisonWindow = comparisonWindow,
        evidence = evidence,
        toolsUsed = toolsUsed,
      )
    )

    val assumptions =
      AdvisorGroundingCodec.buildAssumptions(
        toolsUsed = toolsUsed,
        computations = computations,
        hindi = respondInHindi,
      )
    val proposal =
      AdvisorGroundingCodec.validateProposal(
        proposed = null,
        snapshot = effectiveSnapshot,
        fallbackComputation = primary,
      )

    emit(
      AdvisorStreamEvent.Completed(
        AdvisorResponse(
          id = responseId,
          questionId = questionId,
          questionText = safeQuestion,
          finding = finding,
          evidence = evidence,
          actionProposal = proposal,
          assumptions = assumptions,
          comparisonWindow = comparisonWindow,
          whySeeingThis =
            AdvisorGroundingCodec.buildWhySeeingThis(
              toolsUsed = toolsUsed,
              familyName = effectiveSnapshot.familyName,
              comparisonWindow = comparisonWindow,
              hindi = respondInHindi,
            ),
          toolsUsed = toolsUsed,
          isNotEnoughData = false,
        )
      )
    )
  }

  private fun buildGroundedFinding(
    primary: AdvisorToolComputation?,
    hindi: Boolean,
  ): String =
    when (primary) {
      is AdvisorToolComputation.ComparisonResult -> {
        val c = primary.comparison
        val curStr =
          MoneyFormatter.formatEnIn(
            c.currentSummary.expensePaise,
            includePaise = c.currentSummary.expensePaise % 100L != 0L,
          )
        val prevStr =
          MoneyFormatter.formatEnIn(
            c.previousSummary.expensePaise,
            includePaise = c.previousSummary.expensePaise % 100L != 0L,
          )
        val deltaStr =
          MoneyFormatter.formatEnIn(
            c.expenseDeltaPaise,
            includePaise = c.expenseDeltaPaise % 100L != 0L,
            showPlusSign = true,
          )
        if (hindi) {
          "${c.currentWindow.label} में कुल खर्च $curStr है, जबकि ${c.previousWindow.label} में यह $prevStr था ($deltaStr बदलाव)।"
        } else {
          "Spending in ${c.currentWindow.label} is $curStr vs $prevStr in ${c.previousWindow.label} ($deltaStr change)."
        }
      }

      is AdvisorToolComputation.CategoryBreakdownResult -> {
        val top = primary.items.first()
        val topStr =
          MoneyFormatter.formatEnIn(top.amountPaise, includePaise = top.amountPaise % 100L != 0L)
        val share = LedgerAnalytics.formatBasisPointsPercent(top.shareBps)
        if (hindi) {
          "${top.categoryName} श्रेणी में सबसे ज्यादा $topStr खर्च हुआ (कुल का $share, ${top.transactionCount} प्रविष्टियाँ)।"
        } else {
          "${top.categoryName} leads expenses at $topStr ($share across ${top.transactionCount} entries)."
        }
      }

      is AdvisorToolComputation.ForecastResult -> {
        val f = primary.forecast
        val estStr =
          MoneyFormatter.formatEnIn(
            f.projectedMonthEndExpensePaise,
            includePaise = f.projectedMonthEndExpensePaise % 100L != 0L,
          )
        val rateStr =
          MoneyFormatter.formatEnIn(
            f.dailyRunRatePaise,
            includePaise = f.dailyRunRatePaise % 100L != 0L,
          )
        if (hindi) {
          "${f.totalDaysInMonth} में से ${f.elapsedDays} दिनों की $rateStr/दिन दर के अनुसार, माह के अंत का अनुमानित खर्च $estStr है।"
        } else {
          "Estimated month-end spend is $estStr based on a $rateStr/day run-rate over ${f.elapsedDays} of ${f.totalDaysInMonth} days."
        }
      }

      is AdvisorToolComputation.AnomalyResult -> {
        val anomalies = primary.detection.anomalies
        if (anomalies.isEmpty()) {
          if (hindi) {
            "जाँची गई ${primary.detection.evaluatedExpenseCount} सक्रिय प्रविष्टियों में कोई भी खर्च सामान्य 2x सीमा से अधिक नहीं मिला।"
          } else {
            "No unusually large expenses crossed the 2x median threshold across ${primary.detection.evaluatedExpenseCount} active entries."
          }
        } else {
          val top = anomalies.first()
          val amt =
            MoneyFormatter.formatEnIn(top.amountPaise, includePaise = top.amountPaise % 100L != 0L)
          val safeTitle = AiGroundingToolbox.sanitizeUntrustedText(top.title, 36)
          if (hindi) {
            "${anomalies.size} असामान्य बड़ा खर्च मिला: \"$safeTitle\" की राशि $amt है (${top.reason})।"
          } else {
            "Found ${anomalies.size} outlier expense(s): \"$safeTitle\" is $amt (${top.reason})."
          }
        }
      }

      is AdvisorToolComputation.MemberSummaryResult -> {
        val top = primary.items.firstOrNull { it.paidExpensePaise > 0L }
        if (top == null) {
          if (hindi) "${primary.windowLabel} में कोई सदस्य खर्च दर्ज नहीं है।"
          else "No member expenses recorded in ${primary.windowLabel}."
        } else {
          val amt =
            MoneyFormatter.formatEnIn(
              top.paidExpensePaise,
              includePaise = top.paidExpensePaise % 100L != 0L,
            )
          val share = LedgerAnalytics.formatBasisPointsPercent(top.expenseShareBps)
          if (hindi) {
            "${top.displayName} ने $amt का भुगतान किया (परिवार के खर्च का $share)।"
          } else {
            "${top.displayName} paid $amt ($share of household spend)."
          }
        }
      }

      is AdvisorToolComputation.SummaryResult, null -> {
        val result = primary as AdvisorToolComputation.SummaryResult
        val s = result.summary
        val split = result.salarySplit
        val diff =
          MoneyFormatter.formatEnIn(
            s.differencePaise,
            includePaise = s.differencePaise % 100L != 0L,
            showPlusSign = true,
          )
        val exp =
          MoneyFormatter.formatEnIn(s.expensePaise, includePaise = s.expensePaise % 100L != 0L)
        if (split.salaryExpensePaise > 0L) {
          val salStr =
            MoneyFormatter.formatEnIn(
              split.salaryExpensePaise,
              includePaise = split.salaryExpensePaise % 100L != 0L,
            )
          val nonSalStr =
            MoneyFormatter.formatEnIn(
              split.nonSalaryExpensePaise,
              includePaise = split.nonSalaryExpensePaise % 100L != 0L,
            )
          val salaryShareBps =
            split.items.firstOrNull { it.classification == ExpenseClassification.SALARY }?.shareBps ?: 0
          val salShare = LedgerAnalytics.formatBasisPointsPercent(salaryShareBps)
          if (hindi) {
            "${s.period.label} में कुल खर्च $exp (अंतर $diff) है — इसमें वेतन खर्च $salStr ($salShare, ${split.salaryCount} प्रविष्टियाँ) और गैर-वेतन खर्च $nonSalStr (${split.nonSalaryCount} प्रविष्टियाँ) शामिल हैं।"
          } else {
            "In ${s.period.label}, household expenses are $exp (Difference $diff): Salary expenses are $salStr ($salShare across ${split.salaryCount} entries) and Non-Salary expenses are $nonSalStr (${split.nonSalaryCount} entries)."
          }
        } else {
          if (hindi) {
            "${s.period.label} में ${s.activeTransactionCount} प्रविष्टियों के साथ कुल खर्च $exp और अंतर (Difference) $diff है।"
          } else {
            "In ${s.period.label}, household expenses are $exp and Difference is $diff across ${s.activeTransactionCount} entries."
          }
        }
      }
    }

  override fun askQuestionWithSearchStream(
    question: String,
    questionId: String?,
    snapshot: AdvisorAuthorizedSnapshot,
  ): Flow<AdvisorStreamEvent> = flow {
    nextFailure?.let { error ->
      nextFailure = null
      emit(AdvisorStreamEvent.Failed(error))
      return@flow
    }

    val safeQuestion = AiGroundingToolbox.sanitizeUserQuestion(question)
    val respondInHindi =
      AppLanguage.shouldRespondInHindi(
        question = safeQuestion,
        selectedLanguage = AppLanguage.fromTag(snapshot.languageTag),
      )
    val effectiveSnapshot =
      if (respondInHindi && !snapshot.languageTag.lowercase().startsWith("hi")) {
        snapshot.copy(languageTag = AppLanguage.HINDI.tag)
      } else {
        snapshot
      }

    val summaryComp = AiGroundingToolbox.executeSummary(effectiveSnapshot)
    val toolsUsed = listOf("getSummary", "googleSearch")
    val evidence = AdvisorGroundingCodec.collectVerifiedEvidence(listOf(summaryComp), effectiveSnapshot)
    emit(
      AdvisorStreamEvent.ToolsReady(
        comparisonWindow = summaryComp.windowLabel,
        evidence = evidence,
        toolsUsed = toolsUsed,
      )
    )

    val expFormatted =
      MoneyFormatter.formatEnIn(
        summaryComp.summary.expensePaise,
        includePaise = summaryComp.summary.expensePaise % 100L != 0L,
      )
    val finding =
      if (respondInHindi) {
        "${summaryComp.windowLabel} में आपका कुल घरेलू खर्च $expFormatted है। ताज़ा बाज़ार संदर्भ के साथ नीचे दिए गए सत्यापित वेब स्रोत देखें।"
      } else {
        "Your household spend in ${summaryComp.windowLabel} is $expFormatted. Live Google Search benchmarks and verified references are attached below."
      }
    emit(
      AdvisorStreamEvent.StreamingText(
        partialFinding = finding,
        comparisonWindow = summaryComp.windowLabel,
        evidence = evidence,
        toolsUsed = toolsUsed,
      )
    )

    emit(
      AdvisorStreamEvent.Completed(
        AdvisorResponse(
          id = "adv_${UUID.randomUUID().toString().take(8)}",
          questionId = questionId,
          questionText = safeQuestion,
          finding = finding,
          evidence = evidence,
          actionProposal =
            AdvisorGroundingCodec.buildDeterministicProposal(summaryComp, effectiveSnapshot.languageTag),
          assumptions =
            AdvisorGroundingCodec.buildAssumptions(
              toolsUsed = listOf("getSummary"),
              computations = listOf(summaryComp),
              hindi = respondInHindi,
            ),
          comparisonWindow = summaryComp.windowLabel,
          whySeeingThis =
            AdvisorGroundingCodec.buildWhySeeingThis(
              toolsUsed = toolsUsed,
              familyName = effectiveSnapshot.familyName,
              comparisonWindow = summaryComp.windowLabel,
              hindi = respondInHindi,
            ),
          toolsUsed = toolsUsed,
          isNotEnoughData = false,
          isWebSearchGrounded = true,
          webSearchQueries = listOf(safeQuestion),
          webCitations =
            listOf(
              AdvisorWebCitation(
                title = "Reserve Bank of India — Financial Benchmarks",
                uri = "https://www.rbi.org.in",
                domain = "rbi.org.in",
              ),
              AdvisorWebCitation(
                title = "Consumer Affairs Price Monitoring Portal",
                uri = "https://fcainfoweb.nic.in",
                domain = "fcainfoweb.nic.in",
              ),
            ),
        )
      )
    )
  }

  override suspend fun recordFeedback(responseId: String, feedback: AdvisorFeedback) {
    feedbackLog[responseId] = feedback
  }
}
