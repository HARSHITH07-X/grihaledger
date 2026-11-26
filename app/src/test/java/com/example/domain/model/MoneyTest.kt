package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

  @Test
  fun `parseRupeesToPaise converts integers and decimals without floating point error`() {
    assertEquals(0L, MoneyFormatter.parseRupeesToPaise("0"))
    assertEquals(10_000L, MoneyFormatter.parseRupeesToPaise("100"))
    assertEquals(10_050L, MoneyFormatter.parseRupeesToPaise("100.5"))
    assertEquals(123_456L, MoneyFormatter.parseRupeesToPaise("₹1,234.56"))
    assertEquals(-4_525L, MoneyFormatter.parseRupeesToPaise("-45.25"))
    // Classic binary float failure case: 19.99 * 100
    assertEquals(1_999L, MoneyFormatter.parseRupeesToPaise("19.99"))
  }

  @Test
  fun `parseRupeesToPaise applies HALF_UP rounding on sub-paise decimals`() {
    assertEquals(10_000L, MoneyFormatter.parseRupeesToPaise("99.995"))
    assertEquals(9_999L, MoneyFormatter.parseRupeesToPaise("99.994"))
    assertEquals(124L, MoneyFormatter.parseRupeesToPaise("1.235"))
    assertEquals(123L, MoneyFormatter.parseRupeesToPaise("1.234"))
    assertEquals(-124L, MoneyFormatter.parseRupeesToPaise("-1.235"))
  }

  @Test
  fun `parseRupeesToPaise rejects blank and malformed inputs`() {
    assertNull(MoneyFormatter.parseRupeesToPaise(""))
    assertNull(MoneyFormatter.parseRupeesToPaise("   "))
    assertNull(MoneyFormatter.parseRupeesToPaise("abc"))
    assertNull(MoneyFormatter.parseRupeesToPaise("12.34.56"))
    assertNull(MoneyFormatter.parseRupeesToPaise("NaN"))
    assertNull(MoneyFormatter.parseRupeesToPaise("Infinity"))
  }

  @Test
  fun `formatEnIn applies Indian numbering system grouping`() {
    assertEquals("₹0.00", MoneyFormatter.formatEnIn(0L))
    assertEquals("₹0.50", MoneyFormatter.formatEnIn(50L))
    assertEquals("₹999.00", MoneyFormatter.formatEnIn(99_900L))
    assertEquals("₹1,000.00", MoneyFormatter.formatEnIn(100_000L))
    assertEquals("₹10,000.00", MoneyFormatter.formatEnIn(1_000_000L))
    assertEquals("₹1,23,456.78", MoneyFormatter.formatEnIn(12_345_678L))
    assertEquals("₹10,00,000.00", MoneyFormatter.formatEnIn(100_000_000L))
    assertEquals("-₹1,23,456.78", MoneyFormatter.formatEnIn(-12_345_678L))
    assertEquals("+₹2,500.00", MoneyFormatter.formatEnIn(250_000L, showPlusSign = true))
  }

  @Test
  fun `formatEnIn compact omits trailing zero paise when requested`() {
    assertEquals("₹1,500", MoneyFormatter.formatEnIn(150_000L, includePaise = false))
    assertEquals("₹1,500.25", MoneyFormatter.formatEnIn(150_025L, includePaise = false))
  }

  @Test
  fun `money arithmetic operates strictly on Long paise`() {
    val a = Money.fromPaise(150_075L)
    val b = Money.fromPaise(49_925L)
    assertEquals(200_000L, (a + b).paise)
    assertEquals(100_150L, (a - b).paise)
    assertEquals(-150_075L, (-a).paise)
    assertTrue(a > b)
  }

  @Test
  fun `money model and formatter never expose Double or Float types`() {
    val moneyFields = Money::class.java.declaredFields
    assertFalse(moneyFields.any { it.type == Double::class.java || it.type == Float::class.java })
    val moneyMethods = Money::class.java.declaredMethods
    assertFalse(
      moneyMethods.any { m ->
        m.returnType == Double::class.java ||
          m.returnType == Float::class.java ||
          m.parameterTypes.any { it == Double::class.java || it == Float::class.java }
      }
    )

    val formatterMethods = MoneyFormatter::class.java.declaredMethods
    assertFalse(
      formatterMethods.any { m ->
        m.returnType == Double::class.java ||
          m.returnType == Float::class.java ||
          m.parameterTypes.any { it == Double::class.java || it == Float::class.java }
      }
    )
  }
}
