package com.example.domain.analytics

import com.example.domain.model.Period
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class PeriodPreset(val shortLabel: String) {
  THIS_MONTH("This month"),
  LAST_MONTH("Last month"),
  LAST_3_MONTHS("Last 3M"),
  LAST_6_MONTHS("Last 6M"),
  CALENDAR_YEAR("This year"),
  FINANCIAL_YEAR("FY Apr–Mar"),
  CUSTOM("Custom"),
}

object PeriodResolver {

  private val HI_IN_LOCALE: Locale = Locale.forLanguageTag("hi-IN")
  private val dayMonthFormatterEn: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
  private val dayMonthYearFormatterEn: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
  private val dayMonthFormatterHi: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", HI_IN_LOCALE)
  private val dayMonthYearFormatterHi: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", HI_IN_LOCALE)

  private fun isHindi(languageTag: String): Boolean = languageTag.lowercase().startsWith("hi")

  private fun localeFor(languageTag: String): Locale =
    if (isHindi(languageTag)) HI_IN_LOCALE else Locale.ENGLISH

  fun resolve(
    preset: PeriodPreset,
    referenceDate: LocalDate,
    customStart: LocalDate? = null,
    customEnd: LocalDate? = null,
    languageTag: String = "en",
  ): Period {
    val currentMonth = YearMonth.from(referenceDate)
    val hi = isHindi(languageTag)
    val locale = localeFor(languageTag)
    return when (preset) {
      PeriodPreset.THIS_MONTH -> {
        val monthShort = currentMonth.month.getDisplayName(TextStyle.SHORT, locale)
        val prefix = if (hi) "इस माह" else "This month"
        Period(
          startDate = currentMonth.atDay(1),
          endDate = currentMonth.atEndOfMonth(),
          label = "$prefix · $monthShort ${currentMonth.year}",
          isLikeForLike = false,
        )
      }

      PeriodPreset.LAST_MONTH -> {
        val lastMonth = currentMonth.minusMonths(1)
        val monthShort = lastMonth.month.getDisplayName(TextStyle.SHORT, locale)
        val prefix = if (hi) "पिछला माह" else "Last month"
        Period(
          startDate = lastMonth.atDay(1),
          endDate = lastMonth.atEndOfMonth(),
          label = "$prefix · $monthShort ${lastMonth.year}",
          isLikeForLike = false,
        )
      }

      PeriodPreset.LAST_3_MONTHS -> {
        val startMonth = currentMonth.minusMonths(2)
        val prefix = if (hi) "पिछले 3 माह" else "Last 3M"
        Period(
          startDate = startMonth.atDay(1),
          endDate = currentMonth.atEndOfMonth(),
          label =
            "$prefix · ${startMonth.month.getDisplayName(TextStyle.SHORT, locale)}–${currentMonth.month.getDisplayName(TextStyle.SHORT, locale)}",
          isLikeForLike = false,
        )
      }

      PeriodPreset.LAST_6_MONTHS -> {
        val startMonth = currentMonth.minusMonths(5)
        val prefix = if (hi) "पिछले 6 माह" else "Last 6M"
        Period(
          startDate = startMonth.atDay(1),
          endDate = currentMonth.atEndOfMonth(),
          label =
            "$prefix · ${startMonth.month.getDisplayName(TextStyle.SHORT, locale)}–${currentMonth.month.getDisplayName(TextStyle.SHORT, locale)}",
          isLikeForLike = false,
        )
      }

      PeriodPreset.CALENDAR_YEAR -> {
        Period(
          startDate = LocalDate.of(referenceDate.year, Month.JANUARY, 1),
          endDate = LocalDate.of(referenceDate.year, Month.DECEMBER, 31),
          label = if (hi) "वर्ष ${referenceDate.year}" else "Year ${referenceDate.year}",
          isLikeForLike = false,
        )
      }

      PeriodPreset.FINANCIAL_YEAR -> {
        val fyStartYear =
          if (referenceDate.monthValue >= 4) referenceDate.year else referenceDate.year - 1
        val fyEndShort = ((fyStartYear + 1) % 100).toString().padStart(2, '0')
        Period(
          startDate = LocalDate.of(fyStartYear, Month.APRIL, 1),
          endDate = LocalDate.of(fyStartYear + 1, Month.MARCH, 31),
          label =
            if (hi) "वित्त वर्ष $fyStartYear–$fyEndShort (अप्रैल–मार्च)"
            else "FY $fyStartYear–$fyEndShort (Apr–Mar)",
          isLikeForLike = false,
        )
      }

      PeriodPreset.CUSTOM -> {
        val start = customStart ?: currentMonth.atDay(1)
        val rawEnd = customEnd ?: referenceDate
        val end = if (rawEnd.isBefore(start)) start else rawEnd
        Period(
          startDate = start,
          endDate = end,
          label = formatRangeLabel(start, end, languageTag = languageTag),
          isLikeForLike = false,
        )
      }
    }
  }

  /**
   * Resolves paired (currentWindow, previousWindow) periods of matched calendar span so
   * comparisons never pit a partial period against a full prior period.
   */
  fun comparisonWindows(
    preset: PeriodPreset,
    referenceDate: LocalDate,
    customStart: LocalDate? = null,
    customEnd: LocalDate? = null,
    languageTag: String = "en",
  ): Pair<Period, Period> {
    val hi = isHindi(languageTag)
    val likeForLikeSuffix = if (hi) "समान अवधि" else "like-for-like"
    return when (preset) {
      PeriodPreset.THIS_MONTH -> {
        Period.monthToDate(referenceDate, languageTag) to
          Period.likeForLikePreviousMonth(referenceDate, languageTag)
      }

      PeriodPreset.LAST_MONTH -> {
        val lastMonth = YearMonth.from(referenceDate).minusMonths(1)
        val priorMonth = lastMonth.minusMonths(1)
        val matchedDays = minOf(lastMonth.lengthOfMonth(), priorMonth.lengthOfMonth())
        val currentWindow =
          Period(
            startDate = lastMonth.atDay(1),
            endDate = lastMonth.atDay(matchedDays),
            label = formatShortDayRange(lastMonth.atDay(1), lastMonth.atDay(matchedDays), languageTag),
            isLikeForLike = true,
          )
        val previousWindow =
          Period(
            startDate = priorMonth.atDay(1),
            endDate = priorMonth.atDay(matchedDays),
            label = "${formatShortDayRange(priorMonth.atDay(1), priorMonth.atDay(matchedDays), languageTag)} ($likeForLikeSuffix)",
            isLikeForLike = true,
          )
        currentWindow to previousWindow
      }

      PeriodPreset.LAST_3_MONTHS -> {
        val currentMonth = YearMonth.from(referenceDate)
        val start = currentMonth.minusMonths(2).atDay(1)
        val currentEnd = minOf(referenceDate, currentMonth.atEndOfMonth())
        val prevStartMonth = currentMonth.minusMonths(5)
        val prevEndMonth = currentMonth.minusMonths(3)
        val prevEndDay = minOf(currentEnd.dayOfMonth, prevEndMonth.lengthOfMonth())
        val prevStart = prevStartMonth.atDay(1)
        val prevEnd = prevEndMonth.atDay(prevEndDay)
        val currentWindow =
          Period(
            startDate = start,
            endDate = currentEnd,
            label = formatRangeLabel(start, currentEnd, languageTag = languageTag),
            isLikeForLike = true,
          )
        val previousWindow =
          Period(
            startDate = prevStart,
            endDate = prevEnd,
            label = "${formatRangeLabel(prevStart, prevEnd, languageTag = languageTag)} ($likeForLikeSuffix)",
            isLikeForLike = true,
          )
        currentWindow to previousWindow
      }

      PeriodPreset.LAST_6_MONTHS -> {
        val currentMonth = YearMonth.from(referenceDate)
        val start = currentMonth.minusMonths(5).atDay(1)
        val currentEnd = minOf(referenceDate, currentMonth.atEndOfMonth())
        val prevStartMonth = currentMonth.minusMonths(11)
        val prevEndMonth = currentMonth.minusMonths(6)
        val prevEndDay = minOf(currentEnd.dayOfMonth, prevEndMonth.lengthOfMonth())
        val prevStart = prevStartMonth.atDay(1)
        val prevEnd = prevEndMonth.atDay(prevEndDay)
        val currentWindow =
          Period(
            startDate = start,
            endDate = currentEnd,
            label = formatRangeLabel(start, currentEnd, languageTag = languageTag),
            isLikeForLike = true,
          )
        val previousWindow =
          Period(
            startDate = prevStart,
            endDate = prevEnd,
            label = "${formatRangeLabel(prevStart, prevEnd, languageTag = languageTag)} ($likeForLikeSuffix)",
            isLikeForLike = true,
          )
        currentWindow to previousWindow
      }

      PeriodPreset.CALENDAR_YEAR -> {
        val resolved = resolve(preset, referenceDate, languageTag = languageTag)
        val currentEnd = minOf(referenceDate, resolved.endDate)
        val prevStart = resolved.startDate.minusYears(1)
        val prevEnd =
          YearMonth.of(prevStart.year, currentEnd.month).let { ym ->
            ym.atDay(minOf(currentEnd.dayOfMonth, ym.lengthOfMonth()))
          }
        val currentWindow =
          Period(
            startDate = resolved.startDate,
            endDate = currentEnd,
            label = formatRangeLabel(resolved.startDate, currentEnd, includeYear = true, languageTag = languageTag),
            isLikeForLike = true,
          )
        val previousWindow =
          Period(
            startDate = prevStart,
            endDate = prevEnd,
            label = "${formatRangeLabel(prevStart, prevEnd, includeYear = true, languageTag = languageTag)} ($likeForLikeSuffix)",
            isLikeForLike = true,
          )
        currentWindow to previousWindow
      }

      PeriodPreset.FINANCIAL_YEAR -> {
        val resolved = resolve(preset, referenceDate, languageTag = languageTag)
        val currentEnd = minOf(referenceDate, resolved.endDate)
        val prevStart = resolved.startDate.minusYears(1)
        val prevEnd =
          YearMonth.of(currentEnd.year - 1, currentEnd.month).let { ym ->
            ym.atDay(minOf(currentEnd.dayOfMonth, ym.lengthOfMonth()))
          }
        val currentWindow =
          Period(
            startDate = resolved.startDate,
            endDate = currentEnd,
            label = formatRangeLabel(resolved.startDate, currentEnd, includeYear = true, languageTag = languageTag),
            isLikeForLike = true,
          )
        val previousWindow =
          Period(
            startDate = prevStart,
            endDate = prevEnd,
            label = "${formatRangeLabel(prevStart, prevEnd, includeYear = true, languageTag = languageTag)} ($likeForLikeSuffix)",
            isLikeForLike = true,
          )
        currentWindow to previousWindow
      }

      PeriodPreset.CUSTOM -> {
        val currentWindow =
          resolve(preset, referenceDate, customStart, customEnd, languageTag = languageTag).copy(isLikeForLike = true)
        val days =
          ChronoUnit.DAYS.between(currentWindow.startDate, currentWindow.endDate).coerceAtLeast(0L)
        val prevEnd = currentWindow.startDate.minusDays(1)
        val prevStart = prevEnd.minusDays(days)
        val priorSuffix = if (hi) "पिछले ${days + 1} दिन" else "prior ${days + 1}d"
        val previousWindow =
          Period(
            startDate = prevStart,
            endDate = prevEnd,
            label = "${formatRangeLabel(prevStart, prevEnd, languageTag = languageTag)} ($priorSuffix)",
            isLikeForLike = true,
          )
        currentWindow to previousWindow
      }
    }
  }

  fun formatRangeLabel(
    start: LocalDate,
    end: LocalDate,
    includeYear: Boolean = start.year != end.year,
    languageTag: String = "en",
  ): String {
    val hi = isHindi(languageTag)
    val dayMonthFmt = if (hi) dayMonthFormatterHi else dayMonthFormatterEn
    val dayMonthYearFmt = if (hi) dayMonthYearFormatterHi else dayMonthYearFormatterEn
    val fmt = if (includeYear) dayMonthYearFmt else dayMonthFmt
    return if (start == end) {
      start.format(dayMonthYearFmt)
    } else {
      "${start.format(fmt)} – ${end.format(fmt)}"
    }
  }

  private fun formatShortDayRange(start: LocalDate, end: LocalDate, languageTag: String = "en"): String {
    val monthShort = start.month.getDisplayName(TextStyle.SHORT, localeFor(languageTag))
    return "$monthShort ${start.dayOfMonth}–${end.dayOfMonth}"
  }
}
