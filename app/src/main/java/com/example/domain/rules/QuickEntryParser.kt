package com.example.domain.rules

import com.example.domain.model.Category
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import java.time.LocalDate

data class ParsedQuickEntry(
  val rawText: String,
  val title: String,
  val amountPaise: Long?,
  val editableRupees: String?,
  val type: TransactionType?,
  val suggestedCategoryId: String?,
  val fundingSource: FundingSource?,
  val paymentMethod: PaymentMethod?,
  val occurredOn: LocalDate?,
  val expenseClassification: ExpenseClassification? = null,
)

data class QuickEntryTemplate(
  val id: String,
  val title: String,
  val amountPaise: Long,
  val type: TransactionType,
  val categoryId: String,
  val fundingSource: FundingSource,
  val paymentMethod: PaymentMethod,
  val expenseClassification: ExpenseClassification = ExpenseClassification.NON_SALARY,
  val frequencyCount: Int,
) {
  val formattedAmount: String
    get() =
      MoneyFormatter.formatEnIn(
        paise = amountPaise,
        includePaise = amountPaise % 100L != 0L,
      )
}

/**
 * Deterministic parser for quick typed entries and Gemini audio transcripts (English, Hindi Devanagari, Hinglish).
 * Strictly pure Kotlin with Long paise arithmetic.
 */
object QuickEntryParser {

  private val DEVANAGARI_DIGITS =
    mapOf(
      '०' to '0',
      '१' to '1',
      '२' to '2',
      '३' to '3',
      '४' to '4',
      '५' to '5',
      '६' to '6',
      '७' to '7',
      '८' to '8',
      '९' to '9',
    )

  private val CURRENCY_NUMBER_REGEX =
    Regex(
      "(?:₹|rs\\.?|inr|rupees?|रुपये|रुपए|रु\\.?)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
      RegexOption.IGNORE_CASE,
    )

  private val TRAILING_CURRENCY_NUMBER_REGEX =
    Regex(
      "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:₹|rs\\.?|inr|rupees?|रुपये|रुपए|रु\\.?)",
      RegexOption.IGNORE_CASE,
    )

  private val BARE_NUMBER_REGEX = Regex("\\b([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\b")

  private val WORD_NUMBERS: Map<String, Long> =
    mapOf(
      "one" to 1L,
      "two" to 2L,
      "three" to 3L,
      "four" to 4L,
      "five" to 5L,
      "six" to 6L,
      "seven" to 7L,
      "eight" to 8L,
      "nine" to 9L,
      "ten" to 10L,
      "fifteen" to 15L,
      "twenty" to 20L,
      "twentyfive" to 25L,
      "thirty" to 30L,
      "forty" to 40L,
      "fifty" to 50L,
      "sixty" to 60L,
      "seventy" to 70L,
      "eighty" to 80L,
      "ninety" to 90L,
      "hundred" to 100L,
      "thousand" to 1_000L,
      "ek" to 1L,
      "एक" to 1L,
      "do" to 2L,
      "दो" to 2L,
      "teen" to 3L,
      "तीन" to 3L,
      "chaar" to 4L,
      "चार" to 4L,
      "paanch" to 5L,
      "panch" to 5L,
      "पांच" to 5L,
      "पाँच" to 5L,
      "chhe" to 6L,
      "छह" to 6L,
      "saat" to 7L,
      "सात" to 7L,
      "aath" to 8L,
      "आठ" to 8L,
      "nau" to 9L,
      "नौ" to 9L,
      "das" to 10L,
      "दस" to 10L,
      "bees" to 20L,
      "बीस" to 20L,
      "pachchees" to 25L,
      "पच्चीस" to 25L,
      "tees" to 30L,
      "तीस" to 30L,
      "chaalees" to 40L,
      "चालीस" to 40L,
      "pachas" to 50L,
      "pachaas" to 50L,
      "पचास" to 50L,
      "saath" to 60L,
      "साठ" to 60L,
      "sattar" to 70L,
      "सत्तर" to 70L,
      "assi" to 80L,
      "अस्सी" to 80L,
      "nabbe" to 90L,
      "नब्बे" to 90L,
      "sau" to 100L,
      "सौ" to 100L,
      "hazaar" to 1_000L,
      "hajar" to 1_000L,
      "हजार" to 1_000L,
      "हज़ार" to 1_000L,
    )

  private val INCOME_CUES =
    listOf(
      "salary",
      "payroll",
      "stipend",
      "bonus",
      "refund",
      "cashback",
      "dividend",
      "interest received",
      "income",
      "received",
      "credited",
      "वेतन",
      "सैलरी",
      "तनख्वाह",
      "पगार",
      "आय",
      "रिफंड",
      "कैशबैक",
      "ब्याज",
      "मिला",
      "मिले",
      "आया",
      "vetan",
      "tankhah",
      "aamdani",
    )

  private val EXPENSE_CUES =
    listOf(
      "spent",
      "paid",
      "bought",
      "खर्च",
      "दिए",
      "दिया",
      "भरा",
      "भरे",
      "खरीदा",
      "खरीदी",
    )

  private val NOISE_WORDS =
    setOf(
      "spent",
      "paid",
      "for",
      "on",
      "at",
      "via",
      "by",
      "through",
      "from",
      "using",
      "with",
      "rs",
      "inr",
      "rupee",
      "rupees",
      "today",
      "yesterday",
      "upi",
      "gpay",
      "phonepe",
      "paytm",
      "bhim",
      "cash",
      "card",
      "debit",
      "credit",
      "bank",
      "neft",
      "imps",
      "transfer",
      "personal",
      "household",
      "family",
      "आज",
      "कल",
      "रुपये",
      "रुपए",
      "रु",
      "में",
      "को",
      "से",
      "के",
      "लिए",
      "का",
      "की",
      "पर",
      "और",
      "खर्च",
      "दिए",
      "दिया",
      "भरे",
      "भरा",
      "मिला",
      "मिले",
      "नकद",
      "कैश",
      "यूपीआई",
      "जीपे",
      "फोनपे",
      "पेटीएम",
      "कार्ड",
      "बैंक",
      "नेफ्ट",
      "निजी",
      "घरेलू",
    )

  fun normalizeDigits(input: String): String {
    val sb = StringBuilder(input.length)
    for (ch in input) {
      sb.append(DEVANAGARI_DIGITS[ch] ?: ch)
    }
    return sb.toString()
  }

  fun parse(
    rawText: String,
    categories: List<Category>,
    referenceDate: LocalDate = LocalDate.now(),
  ): ParsedQuickEntry {
    val normalized = normalizeDigits(rawText).replace(Regex("\\s+"), " ").trim()
    val lower = normalized.lowercase()

    // 1. Extract amount (currency-anchored digits first, then bare digits, then Hindi/English spoken words)
    var matchedAmountText: String? = null
    var amountPaise: Long? = null

    val curMatch = CURRENCY_NUMBER_REGEX.find(normalized) ?: TRAILING_CURRENCY_NUMBER_REGEX.find(normalized)
    if (curMatch != null) {
      val digits = curMatch.groupValues[1].replace(",", "")
      val parsed = MoneyFormatter.parseRupeesToPaise(digits)
      if (parsed != null && parsed > 0L) {
        amountPaise = parsed
        matchedAmountText = curMatch.value
      }
    }

    if (amountPaise == null) {
      val bareMatch = BARE_NUMBER_REGEX.find(normalized)
      if (bareMatch != null) {
        val digits = bareMatch.groupValues[1].replace(",", "")
        val parsed = MoneyFormatter.parseRupeesToPaise(digits)
        if (parsed != null && parsed > 0L) {
          amountPaise = parsed
          matchedAmountText = bareMatch.value
        }
      }
    }

    var consumedSpokenTokens = emptySet<String>()
    if (amountPaise == null) {
      val spoken = parseSpokenRupees(lower)
      if (spoken != null && spoken.first > 0L) {
        amountPaise = spoken.first * 100L
        consumedSpokenTokens = spoken.second
      }
    }

    // 2. Type (distinguish outgoing staff/payee salary expenses from incoming personal salary)
    val hasStaffPayeeCue = ExpenseClassifier.hasStaffOrPayeeExpenseCue(lower)
    val hasExplicitNonSalaryCue =
      listOf("non-salary", "non salary", "nonsalary", "गैर-वेतन", "गैर वेतन").any { it in lower }
    val inferredType =
      when {
        hasStaffPayeeCue || hasExplicitNonSalaryCue || EXPENSE_CUES.any { it in lower } ->
          TransactionType.EXPENSE
        INCOME_CUES.any { it in lower } -> TransactionType.INCOME
        else -> null
      }

    // 3. Payment Method
    val paymentMethod =
      when {
        listOf("cash", "नकद", "कैश", "nakad").any { it in lower } -> PaymentMethod.CASH
        listOf("upi", "gpay", "google pay", "phonepe", "paytm", "bhim", "यूपीआई", "जीपे", "फोनपे", "पेटीएम")
          .any { it in lower } -> PaymentMethod.UPI
        listOf("neft", "imps", "bank transfer", "netbanking", "bank", "बैंक", "नेफ्ट").any { it in lower } ->
          PaymentMethod.BANK_TRANSFER
        listOf("credit card", "debit card", "card", "कार्ड", "क्रेडिट कार्ड", "डेबिट कार्ड").any { it in lower } ->
          PaymentMethod.CARD
        else -> null
      }

    // 4. Funding Source
    val fundingSource =
      when {
        listOf("personal", "my money", "self", "निजी", "खुद").any { it in lower } ->
          FundingSource.PERSONAL
        listOf("family card", "joint card", "फैमिली कार्ड", "परिवार कार्ड").any { it in lower } ->
          FundingSource.FAMILY_CARD
        listOf("household", "home budget", "family", "घरेलू", "घर").any { it in lower } ->
          FundingSource.HOUSEHOLD
        else -> null
      }

    // 5. Occurred On date
    val occurredOn =
      when {
        listOf("day before yesterday", "परसों", "parson").any { it in lower } ->
          referenceDate.minusDays(2)
        listOf("yesterday", "बीते कल", "kal", "कल").any { it in lower } ->
          referenceDate.minusDays(1)
        listOf("today", "आज", "aaj").any { it in lower } -> referenceDate
        else -> null
      }

    // 6. Clean Title
    val withoutAmount =
      if (matchedAmountText != null) normalized.replaceFirst(matchedAmountText, " ")
      else normalized
    val titleTokens =
      withoutAmount
        .replace(Regex("[₹,.;!?\"']"), " ")
        .split(Regex("\\s+"))
        .filter { token ->
          if (token.isBlank()) return@filter false
          val tokLower = token.lowercase()
          tokLower !in NOISE_WORDS && tokLower !in consumedSpokenTokens
        }
    val cleanedTitle =
      titleTokens
        .joinToString(" ")
        .trim()
        .take(TransactionValidation.MAX_TITLE_LENGTH)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    val targetType = inferredType ?: TransactionType.EXPENSE
    val suggestedCat =
      CategorySuggester.suggestCategory(
        title = cleanedTitle,
        note = rawText,
        type = targetType,
        categories = categories,
      )
    val inferredClassification =
      if (targetType == TransactionType.EXPENSE) {
        ExpenseClassifier.suggest(
          title = cleanedTitle,
          note = rawText,
          categoryId = suggestedCat?.id,
        )?.classification
      } else {
        null
      }

    return ParsedQuickEntry(
      rawText = rawText,
      title = cleanedTitle,
      amountPaise = amountPaise,
      editableRupees = amountPaise?.let { MoneyFormatter.paiseToEditableRupees(it) },
      type = inferredType,
      suggestedCategoryId = suggestedCat?.id,
      fundingSource = fundingSource,
      paymentMethod = paymentMethod,
      occurredOn = occurredOn,
      expenseClassification = inferredClassification,
    )
  }

  private fun parseSpokenRupees(lowerText: String): Pair<Long, Set<String>>? {
    val tokens =
      lowerText
        .replace(Regex("[₹,.;!?\"']"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    if ("डेढ़ सौ" in lowerText || "dedh sau" in lowerText) {
      return 150L to setOf("डेढ़", "सौ", "dedh", "sau")
    }
    if ("ढाई सौ" in lowerText || "dhai sau" in lowerText) {
      return 250L to setOf("ढाई", "सौ", "dhai", "sau")
    }

    var total = 0L
    var current = 0L
    val usedTokens = mutableSetOf<String>()
    for (token in tokens) {
      val value = WORD_NUMBERS[token]
      if (value != null) {
        usedTokens += token
        when (value) {
          1_000L -> {
            current = if (current == 0L) 1L else current
            total += current * 1_000L
            current = 0L
          }
          100L -> {
            current = if (current == 0L) 1L else current
            current *= 100L
          }
          else -> current += value
        }
      } else if (total > 0L || current > 0L) {
        // Stop accumulating on first non-number token after a number sequence started
        break
      }
    }
    val combined = total + current
    return if (combined > 0L) combined to usedTokens else null
  }

  /**
   * Builds 1-tap repeat templates from the household's active transactions ordered by frequency then recency.
   */
  fun buildQuickTemplates(
    transactions: List<Transaction>,
    limit: Int = 6,
  ): List<QuickEntryTemplate> {
    val active = transactions.filter { !it.isVoided && it.title.isNotBlank() && it.amountPaise > 0L }
    if (active.isEmpty()) return emptyList()

    val grouped =
      active.groupBy { tx ->
        "${tx.title.trim().lowercase()}|${tx.categoryId}|${tx.type.name}"
      }

    return grouped.values
      .mapNotNull { group ->
        val latest = group.maxByOrNull { it.createdAtEpochMillis } ?: return@mapNotNull null
        QuickEntryTemplate(
          id = "tpl_${latest.id}",
          title = latest.title.trim(),
          amountPaise = latest.amountPaise,
          type = latest.type,
          categoryId = latest.categoryId,
          fundingSource = latest.fundingSource,
          paymentMethod = latest.paymentMethod,
          expenseClassification = latest.expenseClassification,
          frequencyCount = group.size,
        )
      }
      .sortedWith(
        compareByDescending<QuickEntryTemplate> { it.frequencyCount }
          .thenBy { it.title.lowercase() }
      )
      .take(limit)
  }
}
