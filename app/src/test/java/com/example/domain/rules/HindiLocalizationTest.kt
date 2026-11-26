package com.example.domain.rules

import com.example.data.fake.FakeSeedData
import com.example.domain.analytics.LedgerAnalytics
import com.example.domain.analytics.PeriodPreset
import com.example.domain.analytics.PeriodResolver
import com.example.domain.model.AppLanguage
import com.example.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HindiLocalizationTest {

  @Test
  fun `AppLanguage detects Devanagari and Hinglish queries cleanly`() {
    assertTrue(AppLanguage.shouldRespondInHindi("इस माह खर्च कितना हुआ?", AppLanguage.ENGLISH))
    assertTrue(AppLanguage.shouldRespondInHindi("Is mahine kharch kitna hua?", AppLanguage.ENGLISH))
    assertTrue(AppLanguage.shouldRespondInHindi("Any question", AppLanguage.HINDI))
    assertFalse(AppLanguage.shouldRespondInHindi("Where did most of our money go?", AppLanguage.ENGLISH))
  }

  @Test
  fun `CategorySuggester matches Hindi Devanagari and Hinglish expense keywords`() {
    assertEquals(
      "cat_groceries",
      CategorySuggester.suggestCategory("ताज़ा दूध और सब्जी", "", TransactionType.EXPENSE, FakeSeedData.categories)?.id,
    )
    assertEquals(
      "cat_utilities",
      CategorySuggester.suggestCategory("बिजली का बिल", "", TransactionType.EXPENSE, FakeSeedData.categories)?.id,
    )
    assertEquals(
      "cat_salary",
      CategorySuggester.suggestCategory("मासिक वेतन", "", TransactionType.INCOME, FakeSeedData.categories)?.id,
    )
    assertEquals(
      "cat_groceries",
      CategorySuggester.suggestCategory("doodh aur sabzi", "", TransactionType.EXPENSE, FakeSeedData.categories)?.id,
    )
  }

  @Test
  fun `LedgerAnalytics and PeriodResolver Hindi mode preserves exact Long paise parity`() {
    val today = LocalDate.of(2025, 5, 18)
    val txs = FakeSeedData.buildTransactions(today)

    val enPeriod = PeriodResolver.resolve(PeriodPreset.THIS_MONTH, today, languageTag = "en")
    val hiPeriod = PeriodResolver.resolve(PeriodPreset.THIS_MONTH, today, languageTag = "hi")
    assertEquals(enPeriod.startDate, hiPeriod.startDate)
    assertEquals(enPeriod.endDate, hiPeriod.endDate)
    assertTrue(AppLanguage.containsDevanagari(hiPeriod.label))

    val enCats = LedgerAnalytics.categoryBreakdown(txs, FakeSeedData.categories, enPeriod, TransactionType.EXPENSE, languageTag = "en")
    val hiCats = LedgerAnalytics.categoryBreakdown(txs, FakeSeedData.categories, hiPeriod, TransactionType.EXPENSE, languageTag = "hi")
    assertEquals(enCats.map { it.categoryId to it.amountPaise }, hiCats.map { it.categoryId to it.amountPaise })
    assertTrue(hiCats.all { AppLanguage.containsDevanagari(it.categoryName) })

    val enCompare = LedgerAnalytics.compareLikeForLike(txs, FakeSeedData.categories, PeriodPreset.THIS_MONTH, today, languageTag = "en")
    val hiCompare = LedgerAnalytics.compareLikeForLike(txs, FakeSeedData.categories, PeriodPreset.THIS_MONTH, today, languageTag = "hi")
    assertEquals(enCompare.expenseDeltaPaise, hiCompare.expenseDeltaPaise)
    assertEquals(enCompare.differenceDeltaPaise, hiCompare.differenceDeltaPaise)

    val enForecast = LedgerAnalytics.forecastMonthEnd(txs, today, "en")
    val hiForecast = LedgerAnalytics.forecastMonthEnd(txs, today, "hi")
    assertEquals(enForecast.projectedMonthEndExpensePaise, hiForecast.projectedMonthEndExpensePaise)
    assertEquals(enForecast.dailyRunRatePaise, hiForecast.dailyRunRatePaise)
    assertTrue(AppLanguage.containsDevanagari(hiForecast.disclaimer))
  }
}
