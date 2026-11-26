package com.example.domain.model

enum class AdvisorFeedback {
  USEFUL,
  NOT_RELEVANT,
}

enum class AdvisorProposalKind {
  REVIEW_RECORDS,
  REVIEW_TRANSACTION,
  INSPECT_INSIGHTS_COMPARISON,
  INSPECT_INSIGHTS_FORECAST,
}

data class SuggestedAdvisorQuestion(
  val id: String,
  val prompt: String,
  val subtitle: String,
)

data class AdvisorEvidenceItem(
  val label: String,
  val formattedValue: String,
  val rawPaise: Long? = null,
  val transactionId: String? = null,
  val transactionTitle: String? = null,
  val categoryId: String? = null,
)

data class AdvisorActionProposal(
  val title: String,
  val description: String,
  val kind: AdvisorProposalKind,
  val targetCategoryId: String? = null,
  val targetCategoryName: String? = null,
  val targetTransactionId: String? = null,
  val requiresUserApproval: Boolean = true,
)

data class AdvisorWebCitation(
  val title: String,
  val uri: String,
  val domain: String? = null,
)

data class AdvisorResponse(
  val id: String,
  val questionId: String?,
  val questionText: String,
  val finding: String,
  val evidence: List<AdvisorEvidenceItem>,
  val actionProposal: AdvisorActionProposal?,
  val assumptions: List<String>,
  val comparisonWindow: String?,
  val whySeeingThis: String,
  val toolsUsed: List<String>,
  val isNotEnoughData: Boolean = false,
  val feedback: AdvisorFeedback? = null,
  val isDismissed: Boolean = false,
  val proposalApproved: Boolean = false,
  val isWebSearchGrounded: Boolean = false,
  val webSearchQueries: List<String> = emptyList(),
  val webCitations: List<AdvisorWebCitation> = emptyList(),
)

object AdvisorQuestions {
  val SUGGESTED: List<SuggestedAdvisorQuestion> =
    listOf(
      SuggestedAdvisorQuestion(
        id = "q_month_change",
        prompt = "Why did our spending change this month?",
        subtitle = "Like-for-like comparison vs last month",
      ),
      SuggestedAdvisorQuestion(
        id = "q_top_categories",
        prompt = "Where did most of our money go this month?",
        subtitle = "Category breakdown and top expenses",
      ),
      SuggestedAdvisorQuestion(
        id = "q_month_forecast",
        prompt = "What is our estimated spend by month end?",
        subtitle = "Run-rate estimate from elapsed days",
      ),
      SuggestedAdvisorQuestion(
        id = "q_unusual_expenses",
        prompt = "Are there any unusually large expenses?",
        subtitle = "Outliers above category or household median",
      ),
      SuggestedAdvisorQuestion(
        id = "q_member_spending",
        prompt = "Who paid for expenses this month?",
        subtitle = "Breakdown strictly by Paid by",
      ),
      SuggestedAdvisorQuestion(
        id = "q_difference_health",
        prompt = "How is our monthly Difference looking?",
        subtitle = "Income minus Expenses and funding split",
      ),
    )

  val SUGGESTED_HI: List<SuggestedAdvisorQuestion> =
    listOf(
      SuggestedAdvisorQuestion(
        id = "q_month_change",
        prompt = "इस महीने हमारा खर्च क्यों बदला?",
        subtitle = "पिछले महीने की समान अवधि से तुलना",
      ),
      SuggestedAdvisorQuestion(
        id = "q_top_categories",
        prompt = "इस महीने सबसे ज्यादा पैसा कहाँ खर्च हुआ?",
        subtitle = "श्रेणीवार खर्च और मुख्य प्रविष्टियाँ",
      ),
      SuggestedAdvisorQuestion(
        id = "q_month_forecast",
        prompt = "महीने के अंत तक कुल अनुमानित खर्च कितना होगा?",
        subtitle = "बीते दिनों की दैनिक दर पर आधारित अनुमान",
      ),
      SuggestedAdvisorQuestion(
        id = "q_unusual_expenses",
        prompt = "क्या कोई असामान्य रूप से बड़ा खर्च हुआ है?",
        subtitle = "औसत से कहीं अधिक खर्चों की पहचान",
      ),
      SuggestedAdvisorQuestion(
        id = "q_member_spending",
        prompt = "इस महीने किस सदस्य ने कितना भुगतान किया?",
        subtitle = "\"किसने भुगतान किया\" के आधार पर विवरण",
      ),
      SuggestedAdvisorQuestion(
        id = "q_difference_health",
        prompt = "इस महीने हमारा अंतर (Difference) कैसा है?",
        subtitle = "आय में से खर्च घटाकर शेष स्थिति",
      ),
    )

  fun forLanguage(language: AppLanguage): List<SuggestedAdvisorQuestion> =
    if (language == AppLanguage.HINDI) SUGGESTED_HI else SUGGESTED
}
