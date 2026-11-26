package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.MainActivity
import com.example.data.fake.FakeWorkspaceStore
import com.example.domain.analytics.LedgerAnalytics
import com.example.domain.analytics.PeriodPreset
import com.example.domain.analytics.PeriodResolver
import com.example.domain.model.Money
import com.example.domain.model.TransactionStatus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalDate
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HiltTestApplication::class)
class CoreJourneyUiTest {

  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var fakeStore: FakeWorkspaceStore

  @Before
  fun setup() {
    hiltRule.inject()
    fakeStore.reset()
  }

  @Test
  fun `core journey add edit and void transaction updates ledger audit trail and home totals`() {
    composeTestRule.waitForIdle()
    val thisMonthPeriod = PeriodResolver.resolve(PeriodPreset.THIS_MONTH, LocalDate.now())
    val initialSummary =
      LedgerAnalytics.summarizePeriod(fakeStore.transactions.value, thisMonthPeriod)
    val initialExpenseFormatted =
      Money(initialSummary.expensePaise)
        .formatEnIn(includePaise = initialSummary.expensePaise % 100L != 0L)

    composeTestRule.onNodeWithTag("screen_home").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_summary_card"))
    composeTestRule.onNodeWithTag("home_expense_value").assertTextEquals(initialExpenseFormatted)

    // 1. Add ₹750 expense via Quick Add sheet
    composeTestRule.onNodeWithTag("add_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("add_transaction_sheet").assertIsDisplayed()

    composeTestRule.onNodeWithTag("keypad_7").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("keypad_5").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("keypad_0").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("amount_display_text").assertTextEquals("₹750")

    composeTestRule.onNodeWithTag("tx_title_input").performScrollTo().performTextInput("Zepto Fruits")
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("save_transaction_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("add_sheet_error_banner").assertDoesNotExist()

    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      fakeStore.transactions.value.any { it.title == "Zepto Fruits" }
    }
    val createdTx = fakeStore.transactions.value.first { it.title == "Zepto Fruits" }
    assertEquals(75_000L, createdTx.amountPaise)
    assertEquals(TransactionStatus.ACTIVE, createdTx.status)

    // Verify Home expense total increased by ₹750
    val afterAddSummary =
      LedgerAnalytics.summarizePeriod(fakeStore.transactions.value, thisMonthPeriod)
    assertEquals(initialSummary.expensePaise + 75_000L, afterAddSummary.expensePaise)

    // 2. Open Records, tap the newly created record, verify Audit History (1), then Edit to ₹900
    composeTestRule.onNodeWithTag("nav_records").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("records_list").performScrollToIndex(3)
    composeTestRule.onNodeWithTag("records_list")
      .performScrollToNode(hasTestTag("record_item_${createdTx.id}"))
    composeTestRule.onNodeWithTag("record_item_${createdTx.id}").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("screen_transaction_detail").assertIsDisplayed()
    composeTestRule.onNodeWithTag("detail_hero_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("detail_amount_text").performScrollTo().assertTextEquals("₹750")
    composeTestRule.onNodeWithTag("detail_list")
      .performScrollToNode(hasTestTag("audit_history_section"))
    composeTestRule.onNodeWithTag("audit_history_section").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Audit history (1)", substring = true).performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("detail_list")
      .performScrollToNode(hasTestTag("detail_edit_button"))
    composeTestRule.onNodeWithTag("detail_edit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("keypad_clear").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("keypad_9").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("keypad_0").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("keypad_0").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("amount_display_text").assertTextEquals("₹900")

    composeTestRule.onNodeWithTag("save_transaction_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      fakeStore.transactions.value.first { it.id == createdTx.id }.amountPaise == 90_000L
    }

    composeTestRule.onNodeWithTag("detail_list").performScrollToNode(hasTestTag("detail_hero_card"))
    composeTestRule.onNodeWithTag("detail_amount_text").performScrollTo().assertTextEquals("₹900")
    composeTestRule.onNodeWithTag("detail_list")
      .performScrollToNode(hasTestTag("audit_history_section"))
    composeTestRule.onNodeWithText("Audit history (2)", substring = true).performScrollTo().assertIsDisplayed()

    // 3. Void with reason and verify status badge, audit trail (3), and Home expense restoration
    composeTestRule.onNodeWithTag("detail_list")
      .performScrollToNode(hasTestTag("detail_void_button"))
    composeTestRule.onNodeWithTag("detail_void_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("void_reason_input").performTextInput("Duplicate entry")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("confirm_void_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      fakeStore.transactions.value.first { it.id == createdTx.id }.isVoided
    }
    composeTestRule.onNodeWithTag("detail_list").performScrollToNode(hasTestTag("detail_hero_card"))
    composeTestRule.onNodeWithTag("detail_status_badge").performScrollTo().assertIsDisplayed()
    assertTrue(
      composeTestRule.onAllNodesWithText("Voided").fetchSemanticsNodes().isNotEmpty()
    )
    composeTestRule.onNodeWithTag("detail_void_reason_box").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("detail_list")
      .performScrollToNode(hasTestTag("audit_history_section"))
    composeTestRule.onNodeWithText("Audit history (3)", substring = true).performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("detail_list").performScrollToIndex(0)
    composeTestRule.onNodeWithTag("detail_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_home").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_summary_card"))
    composeTestRule.onNodeWithTag("home_expense_value").performScrollTo().assertTextEquals(initialExpenseFormatted)
  }
}
