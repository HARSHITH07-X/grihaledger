package com.example.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure Kotlin monetary value backed strictly by integer paise (1 INR = 100 paise).
 * Floating-point types (Double/Float) are never used for money storage or arithmetic.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {

  val isZero: Boolean
    get() = paise == 0L

  val isPositive: Boolean
    get() = paise > 0L

  val isNegative: Boolean
    get() = paise < 0L

  operator fun plus(other: Money): Money = Money(Math.addExact(paise, other.paise))

  operator fun minus(other: Money): Money = Money(Math.subtractExact(paise, other.paise))

  operator fun unaryMinus(): Money = Money(Math.negateExact(paise))

  fun abs(): Money = if (paise < 0L) Money(Math.negateExact(paise)) else this

  override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

  fun formatEnIn(includePaise: Boolean = true, showPlusSign: Boolean = false): String =
    MoneyFormatter.formatEnIn(
      paise = paise,
      includePaise = includePaise,
      showPlusSign = showPlusSign,
    )

  companion object {
    val ZERO = Money(0L)

    fun fromPaise(paise: Long): Money = Money(paise)

    fun parseRupeesOrNull(rawInput: String): Money? =
      MoneyFormatter.parseRupeesToPaise(rawInput)?.let(::Money)
  }
}

object MoneyFormatter {
  private val validInputRegex = Regex("""^[+-]?\d+(\.\d+)?$""")
  private val maxSupportedRupees = BigDecimal("90000000000000.00")

  /**
   * Parses a user-entered rupee string (e.g. "1,234.50", "₹99.995") into integer paise
   * using decimal HALF_UP rounding, never Double/Float. Returns null for invalid input.
   */
  fun parseRupeesToPaise(rawInput: String): Long? {
    val sanitized =
      rawInput
        .trim()
        .replace("₹", "")
        .replace(",", "")
        .replace(" ", "")

    if (sanitized.isEmpty() || !validInputRegex.matches(sanitized)) {
      return null
    }

    val decimal = sanitized.toBigDecimalOrNull() ?: return null
    if (decimal.abs() > maxSupportedRupees) {
      return null
    }

    val roundedPaise =
      decimal
        .setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2)

    return runCatching { roundedPaise.longValueExact() }.getOrNull()
  }

  /**
   * Formats integer paise into an editable rupee string without grouping commas (e.g. "1250.50").
   */
  fun paiseToEditableRupees(paise: Long): String {
    val sign = if (paise < 0L) "-" else ""
    val absPaise = kotlin.math.abs(paise)
    val rupees = absPaise / 100L
    val remPaise = absPaise % 100L
    return if (remPaise == 0L) {
      "$sign$rupees"
    } else {
      "$sign$rupees.${remPaise.toString().padStart(2, '0')}"
    }
  }

  /**
   * Formats integer paise with Indian numbering system grouping (en-IN: ##,##,###.pp).
   */
  fun formatEnIn(
    paise: Long,
    includePaise: Boolean = true,
    showPlusSign: Boolean = false,
  ): String {
    val sign =
      when {
        paise < 0L -> "-"
        paise > 0L && showPlusSign -> "+"
        else -> ""
      }
    val absPaise = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paise)
    val rupees = absPaise / 100L
    val remPaise = absPaise % 100L

    val groupedRupees = formatIndianIntegerGrouping(rupees)
    val showDecimals = includePaise || remPaise != 0L
    return if (showDecimals) {
      "${sign}₹$groupedRupees.${remPaise.toString().padStart(2, '0')}"
    } else {
      "${sign}₹$groupedRupees"
    }
  }

  private fun formatIndianIntegerGrouping(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits

    val lastThree = digits.takeLast(3)
    val remaining = digits.dropLast(3)
    val pairs = mutableListOf<String>()
    var index = remaining.length
    while (index > 0) {
      val start = (index - 2).coerceAtLeast(0)
      pairs.add(remaining.substring(start, index))
      index = start
    }
    val prefix = pairs.asReversed().joinToString(",")
    return "$prefix,$lastThree"
  }
}
