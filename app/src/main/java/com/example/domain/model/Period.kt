package com.example.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class Period(
  val startDate: LocalDate,
  val endDate: LocalDate,
  val label: String,
  val isLikeForLike: Boolean = false,
) {
  init {
    require(!endDate.isBefore(startDate)) {
      "Period endDate ($endDate) cannot be before startDate ($startDate)"
    }
  }

  operator fun contains(date: LocalDate): Boolean =
    !date.isBefore(startDate) && !date.isAfter(endDate)

  companion object {
    private fun localeFor(languageTag: String): Locale =
      if (languageTag.lowercase().startsWith("hi")) Locale.forLanguageTag("hi-IN") else Locale.ENGLISH

    fun monthToDate(referenceDate: LocalDate, languageTag: String = "en"): Period {
      val start = referenceDate.withDayOfMonth(1)
      val monthShort = referenceDate.month.getDisplayName(TextStyle.SHORT, localeFor(languageTag))
      val label = "$monthShort 1–${referenceDate.dayOfMonth}"
      return Period(
        startDate = start,
        endDate = referenceDate,
        label = label,
        isLikeForLike = true,
      )
    }

    fun likeForLikePreviousMonth(referenceDate: LocalDate, languageTag: String = "en"): Period {
      val isHindi = languageTag.lowercase().startsWith("hi")
      val previousMonth = YearMonth.from(referenceDate).minusMonths(1)
      val start = previousMonth.atDay(1)
      val clampedDay = minOf(referenceDate.dayOfMonth, previousMonth.lengthOfMonth())
      val end = previousMonth.atDay(clampedDay)
      val monthShort = previousMonth.month.getDisplayName(TextStyle.SHORT, localeFor(languageTag))
      val suffix = if (isHindi) "समान अवधि" else "like-for-like"
      val label = "$monthShort 1–$clampedDay ($suffix)"
      return Period(
        startDate = start,
        endDate = end,
        label = label,
        isLikeForLike = true,
      )
    }

    fun fullMonth(yearMonth: YearMonth, languageTag: String = "en"): Period {
      val monthShort = yearMonth.month.getDisplayName(TextStyle.SHORT, localeFor(languageTag))
      return Period(
        startDate = yearMonth.atDay(1),
        endDate = yearMonth.atEndOfMonth(),
        label = "$monthShort ${yearMonth.year}",
        isLikeForLike = false,
      )
    }
  }
}
