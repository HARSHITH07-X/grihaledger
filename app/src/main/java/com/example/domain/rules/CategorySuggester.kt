package com.example.domain.rules

import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType

/**
 * Pure keyword-based category suggestion and recency sorting (strictly deterministic, no AI).
 */
object CategorySuggester {

  private val keywordGroups: List<Pair<List<String>, List<String>>> =
    listOf(
      listOf(
        "grocer", "bigbasket", "blinkit", "zepto", "instamart", "dmart", "reliance", "milk", "nandini",
        "dairy", "curd", "vegetable", "sabji", "fruit", "atta", "rice", "dal", "ration", "pantry", "supermarket",
        "doodh", "chawal", "aata", "sabzi", "राशन", "दूध", "दही", "सब्जी", "सब्ज़ी", "फल", "आटा", "चावल", "दाल", "किराना", "सौदा",
      ) to listOf("cat_groceries", "groceries"),
      listOf(
        "electric", "bescom", "power", "bill", "broadband", "wifi", "internet", "fibernet", "act", "jio",
        "airtel", "gas", "indane", "hp gas", "cylinder", "lpg", "water", "recharge", "utility", "utilities",
        "bijli", "paani", "pani", "बिजली", "बिल", "गैस", "सिलेंडर", "पानी", "इंटरनेट", "वाईफाई", "रिचार्ज",
      ) to listOf("cat_utilities", "utilities"),
      listOf(
        "rent", "maintenance", "society", "maid", "cook", "driver", "watchman", "gardener", "mali", "nanny",
        "househelp", "house help", "helper", "staff", "house", "repair", "plumber", "carpenter", "flat", "home",
        "kiraya", "ghar", "kamwali", "kaamwali", "safai", "dhobi", "किराया", "मकान", "घर", "मेंटेनेंस",
        "सोसायटी", "मरम्मत", "कामवाली", "बाई", "रसोइया", "कुक", "ड्राइवर", "माली", "चौकीदार", "सफाई",
      ) to listOf("cat_home", "home"),
      listOf(
        "swiggy", "zomato", "dinner", "lunch", "breakfast", "dosa", "restaurant", "cafe", "coffee", "tea",
        "hotel", "bakery", "eat", "food", "mtr", "a2b", "dining",
        "khana", "nashta", "chai", "mithai", "खाना", "नाश्ता", "चाय", "कॉफी", "मिठाई", "होटल", "रेस्टोरेंट", "बाहर खाना",
      ) to listOf("cat_dining", "dining"),
      listOf(
        "petrol", "diesel", "fuel", "hpcl", "bpcl", "iocl", "shell", "metro", "uber", "ola", "rapido",
        "auto", "cab", "bus", "train", "flight", "toll", "parking", "fastag", "transport",
        "yatra", "kiraya", "रिक्शा", "ऑटो", "पेट्रोल", "डीजल", "मेट्रो", "ट्रेन", "बस", "टोल", "पार्किंग", "यात्रा",
      ) to listOf("cat_transport", "transport"),
      listOf(
        "pharmacy", "apollo", "medplus", "medicine", "tablet", "doctor", "clinic", "hospital", "checkup",
        "lab", "health", "dental", "scan", "bp", "nurse", "caretaker", "attendant",
        "dawai", "dava", "ilaaj", "ilaj", "दवा", "दवाई", "डॉक्टर", "अस्पताल", "जाँच", "जांच", "स्वास्थ्य", "चश्मा", "नर्स",
      ) to listOf("cat_health", "health"),
      listOf(
        "school", "college", "tuition", "tutor", "fee", "fees", "book", "books", "stationery", "exam", "uniform", "education",
        "kitab", "padhai", "स्कूल", "कॉलेज", "ट्यूशन", "ट्यूटर", "फीस", "किताब", "पढ़ाई", "शिक्षा", "कॉपियाँ",
      ) to listOf("cat_education", "education"),
      listOf(
        "amazon", "flipkart", "towel", "linen", "kids", "toy", "clothes", "diwali", "sweets", "gift", "shopping", "household",
        "kapde", "kharidari", "कपड़े", "खरीदारी", "शॉपिंग", "उपहार", "तोहफा", "दिवाली", "सामान",
      ) to listOf("cat_shopping", "household"),
      listOf(
        "salary", "payroll", "stipend", "bonus", "wages",
        "vetan", "tankhah", "pagar", "वेतन", "तनख्वाह", "पगार", "बोनस", "सैलरी",
      ) to listOf("cat_salary", "salary"),
      listOf(
        "dividend", "interest", "refund", "cashback", "freelance", "rent received", "payout", "income",
        "byaj", "kamai", "aamdani", "ब्याज", "रिफंड", "कैशबैक", "कमाई", "आमदनी", "अन्य आय",
      ) to listOf("cat_other_income", "other"),
    )

  fun suggestCategory(
    title: String,
    note: String,
    type: TransactionType,
    categories: List<Category>,
  ): Category? {
    val haystack = "${title.trim()} ${note.trim()}".lowercase()
    if (haystack.isBlank()) return null

    val typeCategories = categories.filter { it.type == type }
    if (typeCategories.isEmpty()) return null

    for ((keywords, targets) in keywordGroups) {
      if (keywords.any { keyword -> haystack.contains(keyword) }) {
        val matched =
          typeCategories.firstOrNull { cat ->
            targets.any { target ->
              cat.id.equals(target, ignoreCase = true) || cat.name.lowercase().contains(target)
            }
          }
        if (matched != null) return matched
      }
    }
    return null
  }

  fun orderCategoriesWithRecentsFirst(
    categories: List<Category>,
    type: TransactionType,
    recentTransactions: List<Transaction>,
  ): List<Category> {
    val typeCategories = categories.filter { it.type == type }.sortedBy { it.sortOrder }
    val recentCategoryOrder =
      recentTransactions
        .asSequence()
        .filter { !it.isVoided && it.type == type }
        .sortedByDescending { it.createdAtEpochMillis }
        .map { it.categoryId }
        .distinct()
        .toList()

    if (recentCategoryOrder.isEmpty()) return typeCategories

    val byId = typeCategories.associateBy { it.id }
    val recentFirst = recentCategoryOrder.mapNotNull { byId[it] }
    val recentIds = recentFirst.map { it.id }.toSet()
    val remaining = typeCategories.filterNot { it.id in recentIds }
    return recentFirst + remaining
  }
}
