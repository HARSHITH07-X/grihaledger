package com.example.domain.rules

import com.example.domain.model.ExpenseClassification
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType

enum class ClassificationReason {
  EXPLICIT_NON_SALARY_CUE,
  SALARY_OR_STAFF_KEYWORD,
  NON_SALARY_EXPENSE_KEYWORD,
  PAST_ENTRY_HISTORY,
}

data class ExpenseClassificationSuggestion(
  val classification: ExpenseClassification,
  val reason: ClassificationReason,
  val matchedCue: String? = null,
)

/**
 * Pure deterministic, zero-latency expense classifier (English, Hindi Devanagari, Hinglish).
 * Distinguishes Salary Expenses (wages/salaries paid to household staff, helpers, tutors, drivers, etc.)
 * from Non-Salary Expenses (bills, groceries, rent, shopping, dining, transport, medical, etc.).
 */
object ExpenseClassifier {

  private val EXPLICIT_NON_SALARY_CUES =
    listOf(
      "non-salary",
      "non salary",
      "nonsalary",
      "not salary",
      "गैर-वेतन",
      "गैर वेतन",
      "बिना वेतन",
    )

  /**
   * Household staff roles and payee cues indicating an outgoing wage/salary expense
   * (rather than household member income).
   */
  private val STAFF_OR_PAYEE_CUES =
    listOf(
      "maid",
      "cook",
      "driver",
      "nanny",
      "babysitter",
      "ayah",
      "aaya",
      "watchman",
      "security guard",
      "guard",
      "gardener",
      "mali",
      "caretaker",
      "nurse",
      "attendant",
      "helper",
      "househelp",
      "house help",
      "domestic help",
      "cleaning lady",
      "car cleaner",
      "car wash",
      "home tutor",
      "private tutor",
      "teacher salary",
      "tuition teacher",
      "kamwali",
      "kaamwali",
      "safaiwala",
      "safaiwali",
      "dhobi",
      "laundryman",
      "naukar",
      "staff",
      "employee",
      "worker",
      "बाई",
      "कामवाली",
      "रसोइया",
      "कुक",
      "ड्राइवर",
      "माली",
      "चौकीदार",
      "आया",
      "दाई",
      "नर्स",
      "स्टाफ",
      "घरेलू सहायक",
      "सफाई कर्मचारी",
      "धोबी",
      "नौकर",
      "ट्यूटर",
    )

  private val SALARY_PAYMENT_CUES =
    listOf(
      "salary",
      "salaries",
      "wages",
      "wage",
      "payroll",
      "stipend",
      "honorarium",
      "pagaar",
      "pagar",
      "tankhah",
      "tankhwa",
      "vetan",
      "majdoori",
      "mazdoori",
      "वेतन",
      "तनख्वाह",
      "पगार",
      "सैलरी",
      "मजदूरी",
      "मानदेय",
    )

  private val NON_SALARY_CUES =
    listOf(
      "grocery",
      "groceries",
      "bigbasket",
      "blinkit",
      "zepto",
      "instamart",
      "dmart",
      "milk",
      "vegetable",
      "sabji",
      "sabzi",
      "rent",
      "flat rent",
      "house rent",
      "electricity",
      "bescom",
      "broadband",
      "wifi",
      "internet",
      "gas cylinder",
      "lpg",
      "water bill",
      "recharge",
      "petrol",
      "diesel",
      "fuel",
      "reimbursement",
      "toll",
      "parking",
      "servicing",
      "repair",
      "metro",
      "uber",
      "ola",
      "swiggy",
      "zomato",
      "restaurant",
      "dinner",
      "lunch",
      "breakfast",
      "pharmacy",
      "medicine",
      "hospital",
      "doctor",
      "school",
      "tuition",
      "school fee",
      "tuition fee",
      "college fee",
      "fee",
      "fees",
      "bill",
      "bills",
      "amazon",
      "flipkart",
      "shopping",
      "kirana",
      "राशन",
      "किराना",
      "दूध",
      "सब्जी",
      "सब्ज़ी",
      "किराया",
      "बिजली",
      "गैस",
      "पानी",
      "पेट्रोल",
      "डीजल",
      "दवा",
      "दवाई",
      "स्कूल",
      "फ़ीस",
      "फीस",
      "खरीदारी",
      "खाना",
      "होटल",
    )

  private val WORD_CHAR_REGEX = Regex("[\\p{L}\\p{M}\\p{N}_]")

  fun hasStaffOrPayeeExpenseCue(rawText: String): Boolean {
    val lower = rawText.lowercase()
    return STAFF_OR_PAYEE_CUES.any { containsCue(lower, it) }
  }

  fun suggest(
    title: String,
    note: String = "",
    categoryId: String? = null,
    recentTransactions: List<Transaction> = emptyList(),
  ): ExpenseClassificationSuggestion? {
    val combined = "${title.trim()} ${note.trim()}".lowercase().trim()
    if (combined.isNotEmpty()) {
      EXPLICIT_NON_SALARY_CUES.firstOrNull { containsCue(combined, it) }?.let { cue ->
        return ExpenseClassificationSuggestion(
          classification = ExpenseClassification.NON_SALARY,
          reason = ClassificationReason.EXPLICIT_NON_SALARY_CUE,
          matchedCue = cue,
        )
      }

      val salaryCue = SALARY_PAYMENT_CUES.firstOrNull { containsCue(combined, it) }
      if (salaryCue != null) {
        return ExpenseClassificationSuggestion(
          classification = ExpenseClassification.SALARY,
          reason = ClassificationReason.SALARY_OR_STAFF_KEYWORD,
          matchedCue = salaryCue,
        )
      }

      val nonSalaryCue = NON_SALARY_CUES.firstOrNull { containsCue(combined, it) }
      if (nonSalaryCue != null) {
        return ExpenseClassificationSuggestion(
          classification = ExpenseClassification.NON_SALARY,
          reason = ClassificationReason.NON_SALARY_EXPENSE_KEYWORD,
          matchedCue = nonSalaryCue,
        )
      }

      val staffCue = STAFF_OR_PAYEE_CUES.firstOrNull { containsCue(combined, it) }
      if (staffCue != null) {
        return ExpenseClassificationSuggestion(
          classification = ExpenseClassification.SALARY,
          reason = ClassificationReason.SALARY_OR_STAFF_KEYWORD,
          matchedCue = staffCue,
        )
      }
    }

    val normalizedTitle = title.trim().lowercase()
    if (normalizedTitle.length >= 2 && recentTransactions.isNotEmpty()) {
      val matchingHistorical =
        recentTransactions
          .asSequence()
          .filter { !it.isVoided && it.type == TransactionType.EXPENSE }
          .sortedByDescending { it.createdAtEpochMillis }
          .firstOrNull { tx ->
            val prevTitle = tx.title.trim().lowercase()
            prevTitle == normalizedTitle ||
              (normalizedTitle.length >= 4 &&
                (prevTitle.contains(normalizedTitle) || normalizedTitle.contains(prevTitle)))
          }
      if (matchingHistorical != null) {
        return ExpenseClassificationSuggestion(
          classification = matchingHistorical.expenseClassification,
          reason = ClassificationReason.PAST_ENTRY_HISTORY,
          matchedCue = matchingHistorical.title,
        )
      }
    }

    // Fallback category-based gentle non-salary signal when category is clearly non-payroll
    if (categoryId in setOf("cat_groceries", "cat_utilities", "cat_dining", "cat_shopping")) {
      return ExpenseClassificationSuggestion(
        classification = ExpenseClassification.NON_SALARY,
        reason = ClassificationReason.NON_SALARY_EXPENSE_KEYWORD,
        matchedCue = categoryId,
      )
    }

    return null
  }

  private fun containsCue(haystack: String, cue: String): Boolean {
    if (cue.contains(' ') || cue.contains('-')) {
      return haystack.contains(cue)
    }
    var searchFrom = 0
    while (searchFrom < haystack.length) {
      val idx = haystack.indexOf(cue, startIndex = searchFrom)
      if (idx < 0) return false
      val beforeOk =
        idx == 0 || !WORD_CHAR_REGEX.matches(haystack[idx - 1].toString())
      val endIdx = idx + cue.length
      val afterOk =
        endIdx >= haystack.length || !WORD_CHAR_REGEX.matches(haystack[endIdx].toString())
      if (beforeOk && afterOk) return true
      searchFrom = idx + 1
    }
    return false
  }
}
