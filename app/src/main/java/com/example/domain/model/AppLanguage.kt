package com.example.domain.model

enum class AppLanguage(
  val tag: String,
  val nativeLabel: String,
  val shortChipLabel: String,
) {
  ENGLISH(tag = "en", nativeLabel = "English", shortChipLabel = "EN"),
  HINDI(tag = "hi", nativeLabel = "हिन्दी", shortChipLabel = "हिं");

  companion object {
    fun fromTag(tag: String?): AppLanguage =
      if (tag?.lowercase()?.startsWith("hi") == true) HINDI else ENGLISH

    fun containsDevanagari(text: String): Boolean =
      text.any { it in '\u0900'..'\u097F' }

    fun looksLikeHinglishQuery(text: String): Boolean {
      val lower = text.lowercase()
      return HINGLISH_HINTS.any { hint ->
        Regex("\\b${Regex.escape(hint)}\\b").containsMatchIn(lower)
      }
    }

    fun shouldRespondInHindi(question: String, selectedLanguage: AppLanguage): Boolean =
      selectedLanguage == HINDI ||
        containsDevanagari(question) ||
        looksLikeHinglishQuery(question)

    private val HINGLISH_HINTS =
      listOf(
        "kharch",
        "kharcha",
        "kitna",
        "mahina",
        "mahine",
        "tulna",
        "anuman",
        "batao",
        "bataiye",
        "kaun",
        "kahan",
        "bachat",
        "hisab",
        "paisa",
        "paise",
        "rupaye",
        "parivar",
        "ghar",
        "zyada",
        "sabse",
        "aamdani",
        "kamai",
        "antar",
      )
  }
}
