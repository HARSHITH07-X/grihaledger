package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.example.MainActivity
import com.example.data.fake.FakeAuthRepository
import com.example.data.fake.FakeWorkspaceStore
import com.example.domain.model.AuthUser
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.TransactionStatus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
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
class GrihaShellRobolectricTest {

  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var fakeStore: FakeWorkspaceStore
  @Inject lateinit var fakeAuthRepository: FakeAuthRepository

  @Before
  fun setup() {
    hiltRule.inject()
    fakeStore.reset()
  }

  @Test
  fun `phone width renders bottom nav, switches all five routes, and opens quick add sheet`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("bottom_navigation_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("screen_home").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_records").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_records").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_insights").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_insights").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_advisor").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_advisor").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_family").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_family").assertIsDisplayed()

    composeTestRule.onNodeWithTag("add_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("add_transaction_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("keypad_4").assertIsDisplayed()

    composeTestRule.onNodeWithTag("add_sheet_close_button").performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun `quick add saves via keypad and keyword suggest, then snackbar undo voids cleanly`() {
    composeTestRule.waitForIdle()
    val initialSize = fakeStore.transactions.value.size

    composeTestRule.onNodeWithTag("add_fab").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("keypad_4").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("keypad_2").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("keypad_5").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("amount_display_text").assertTextEquals("₹425")
    composeTestRule.onNodeWithTag("tx_title_input").performScrollTo().performTextInput("BigBasket fresh milk")
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("suggested_category_badge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("save_transaction_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("save_transaction_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("add_sheet_error_banner").assertDoesNotExist()

    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      fakeStore.transactions.value.size == initialSize + 1
    }
    val added = fakeStore.transactions.value.first()
    assertEquals("BigBasket fresh milk", added.title)
    assertEquals(42_500L, added.amountPaise)
    assertEquals("cat_groceries", added.categoryId)
    assertEquals(TransactionStatus.ACTIVE, added.status)

    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      composeTestRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Undo").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      fakeStore.transactions.value.first { it.id == added.id }.isVoided
    }
    assertTrue(fakeStore.transactions.value.first { it.id == added.id }.isVoided)
  }

  @Test
  fun `records search, detail audit history, edit, void, and viewer fab hiding work end to end`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_records").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("records_search_input").performTextInput("BESCOM")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("record_item_tx_05").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("screen_transaction_detail").assertIsDisplayed()
    composeTestRule.onNodeWithTag("detail_paid_by_row").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("detail_added_by_row").performScrollTo().assertIsDisplayed()

    // Edit tx_05 title
    composeTestRule.onNodeWithTag("detail_list").performScrollToNode(hasTestTag("detail_edit_button"))
    composeTestRule.onNodeWithTag("detail_edit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("tx_title_input").performScrollTo().performTextReplacement("BESCOM Power Updated")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("save_transaction_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("save_transaction_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("add_sheet_error_banner").assertDoesNotExist()
    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      fakeStore.transactions.value.first { it.id == "tx_05" }.title == "BESCOM Power Updated"
    }

    // Void tx_05 with reason
    composeTestRule.mainClock.advanceTimeBy(5_000L)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("detail_list").performScrollToNode(hasTestTag("audit_history_section"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("detail_void_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("void_reason_input").performTextInput("Paid via auto-debit instead")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("confirm_void_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("detail_error_banner").assertDoesNotExist()
    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      fakeStore.transactions.value.first { it.id == "tx_05" }.isVoided
    }

    // Viewer role hides Add FAB
    composeTestRule.onNodeWithTag("debug_user_chip_viewer").performClick()
    composeTestRule.waitForIdle()
    assertTrue(
      composeTestRule.onAllNodes(hasTestTag("add_fab")).fetchSemanticsNodes().isEmpty()
    )
  }

  @Test
  fun `debug role switcher updates what family screen shows`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_family").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("family_admin_controls").assertIsDisplayed()
    composeTestRule.onNodeWithTag("family_list")
      .performScrollToNode(hasTestTag("pending_invites_section"))
    composeTestRule.onNodeWithTag("pending_invites_section").assertIsDisplayed()
    composeTestRule.onNodeWithTag("family_list")
      .performScrollToNode(hasTestTag("family_role_banner"))

    composeTestRule.onNodeWithTag("debug_user_chip_member").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("family_member_notice").assertIsDisplayed()

    composeTestRule.onNodeWithTag("debug_user_chip_viewer").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("family_viewer_notice").assertIsDisplayed()
  }

  @Test
  fun `signed out user sees branded sign in screen only`() {
    composeTestRule.runOnIdle {
      fakeAuthRepository.setSignedInUser(null)
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_auth").assertIsDisplayed()
    composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `new user without family sees onboarding and can create a family`() {
    composeTestRule.runOnIdle {
      fakeAuthRepository.setSignedInUser(
        AuthUser(uid = "uid_new_user", displayName = "Meera Nair", email = "meera@nair.family"),
        familyId = null,
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_onboarding").assertIsDisplayed()
    composeTestRule.onNodeWithTag("create_family_name_input").performScrollTo().performTextInput("Nair Home")
    composeTestRule.onNodeWithTag("create_family_submit_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      composeTestRule.onAllNodes(hasTestTag("screen_home")).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("screen_home").assertIsDisplayed()
  }

  @Test
  fun `new user can validate invite code and join existing family`() {
    composeTestRule.runOnIdle {
      fakeAuthRepository.setSignedInUser(
        AuthUser(uid = "uid_joiner", displayName = "Vikram", email = "vikram@sharma.family"),
        familyId = null,
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_mode_join").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("join_invite_code_input").performScrollTo().performTextInput("GRH2468KMP")
    composeTestRule.onNodeWithTag("check_invite_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("join_confirmation_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Join Sharma Household as Member?").assertIsDisplayed()
    composeTestRule.onNodeWithTag("confirm_join_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      composeTestRule.onAllNodes(hasTestTag("screen_home")).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("screen_home").assertIsDisplayed()
  }

  @Test
  fun `theme toggle switches between light and dark without crashing`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_home").assertIsDisplayed()
    composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_home").assertIsDisplayed()
  }

  @Config(sdk = [36], qualifiers = "w800dp-h1200dp")
  @Test
  fun `tablet width renders navigation rail and navigates all routes`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("navigation_rail").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_family").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_family").assertIsDisplayed()
  }

  @Test
  fun `home analytics period switch and insights drilldown open matching records filter`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_summary_card"))
    composeTestRule.onNodeWithTag("home_summary_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_difference_value").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_list")
      .performScrollToNode(hasTestTag("home_highlighted_insight_card"))
    composeTestRule.onNodeWithTag("home_highlighted_insight_card").performScrollTo().assertIsDisplayed()

    // Switch period to Last month on Home
    composeTestRule.onNodeWithTag("home_list")
      .performScrollToNode(hasTestTag("period_chip_last_month"))
    composeTestRule.onNodeWithTag("period_chip_last_month").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_summary_card"))
    composeTestRule.onNodeWithTag("home_summary_card").performScrollTo().assertIsDisplayed()

    // Navigate to Insights, verify Comparison window and Forecast estimate badge, then drill into Overview funding -> Records
    composeTestRule.onNodeWithTag("nav_insights").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("insights_list").performScrollToNode(hasTestTag("insights_tab_row"))
    composeTestRule.onNodeWithTag("insights_tab_comparison").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("insights_list")
      .performScrollToNode(hasTestTag("comparison_window_banner"))
    composeTestRule.onNodeWithTag("comparison_window_banner").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("insights_list").performScrollToNode(hasTestTag("insights_tab_row"))
    composeTestRule.onNodeWithTag("insights_tab_forecast").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("insights_list")
      .performScrollToNode(hasTestTag("insights_forecast_badge"))
    composeTestRule.onNodeWithTag("insights_forecast_badge").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("insights_list").performScrollToNode(hasTestTag("insights_tab_row"))
    composeTestRule.onNodeWithTag("insights_tab_overview").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("insights_list")
      .performScrollToNode(hasTestTag("funding_row_household"))
    composeTestRule.onNodeWithTag("funding_row_household").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("screen_records").assertIsDisplayed()
    composeTestRule.onNodeWithTag("records_drilldown_banner").assertIsDisplayed()
  }

  @Test
  fun `advisor renders 6 suggested questions, computes grounded answer, expands why, records feedback, and dismisses`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_advisor").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("advisor_list")
      .performScrollToNode(hasTestTag("advisor_suggested_questions"))
    composeTestRule.onNodeWithTag("advisor_suggested_questions").assertIsDisplayed()
    composeTestRule.onNodeWithTag("advisor_suggested_q_q_month_change").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      composeTestRule.onAllNodes(hasTestTag("advisor_empty_state")).fetchSemanticsNodes().isEmpty() &&
        composeTestRule.onAllNodes(hasTestTag("advisor_loading_card")).fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag("advisor_list").performScrollToIndex(3)
    composeTestRule.waitForIdle()

    val answerNodes =
      composeTestRule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("advisor_answer") {
        it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.TestTag) &&
          it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].startsWith("advisor_answer_")
      }).fetchSemanticsNodes()
    assertTrue(answerNodes.isNotEmpty())
    val answerTag =
      answerNodes.first().config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]
    val responseId = answerTag.removePrefix("advisor_answer_")

    composeTestRule.onNodeWithTag("advisor_list")
      .performScrollToNode(hasTestTag("advisor_answer_$responseId"))
    composeTestRule.onNodeWithTag("advisor_finding_$responseId").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("advisor_why_button_$responseId").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("advisor_why_panel_$responseId").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("advisor_feedback_useful_$responseId").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("advisor_dismiss_$responseId").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("advisor_list")
      .performScrollToNode(hasTestTag("advisor_empty_state"))
    composeTestRule.onNodeWithTag("advisor_empty_state").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `smart entry classifies cook salary and insights salary split drills into matching records`() {
    composeTestRule.waitForIdle()
    val initialSize = fakeStore.transactions.value.size

    composeTestRule.onNodeWithTag("add_fab").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("keypad_5").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("keypad_0").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("keypad_0").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("tx_title_input").performScrollTo().performTextInput("Driver salary")
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("suggested_classification_badge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("expense_classification_salary").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("save_transaction_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      fakeStore.transactions.value.size == initialSize + 1
    }
    val added = fakeStore.transactions.value.first()
    assertEquals("Driver salary", added.title)
    assertEquals(ExpenseClassification.SALARY, added.expenseClassification)

    // Insights -> Overview shows Salary vs Non-Salary card and drills down on tap
    composeTestRule.mainClock.advanceTimeBy(5_000L)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_insights").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("insights_list")
      .performScrollToNode(hasTestTag("salary_split_row_non_salary"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("insights_salary_split_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("salary_split_row_salary")
      .assertIsDisplayed()
      .performTouchInput {
        click(androidx.compose.ui.geometry.Offset(width * 0.15f, centerY))
      }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("screen_records").assertIsDisplayed()
    composeTestRule.onNodeWithTag("records_drilldown_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("records_list")
      .performScrollToNode(hasTestTag("record_item_${added.id}"))
    composeTestRule.onNodeWithTag("record_item_${added.id}").assertIsDisplayed()
    composeTestRule.onNodeWithTag("record_salary_badge_${added.id}", useUnmergedTree = true)
      .assertIsDisplayed()
  }

  @Test
  fun `clear all data setting is only visible to admin and wipes transactions after confirmation`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_family").performClick()
    composeTestRule.waitForIdle()

    // Member cannot see admin settings card or clear button
    composeTestRule.onNodeWithTag("debug_user_chip_member").performClick()
    composeTestRule.waitForIdle()
    assertTrue(
      composeTestRule.onAllNodes(hasTestTag("family_admin_settings_card")).fetchSemanticsNodes().isEmpty()
    )
    assertTrue(
      composeTestRule.onAllNodes(hasTestTag("clear_all_data_button")).fetchSemanticsNodes().isEmpty()
    )

    // Viewer cannot see admin settings card or clear button
    composeTestRule.onNodeWithTag("debug_user_chip_viewer").performClick()
    composeTestRule.waitForIdle()
    assertTrue(
      composeTestRule.onAllNodes(hasTestTag("family_admin_settings_card")).fetchSemanticsNodes().isEmpty()
    )
    assertTrue(
      composeTestRule.onAllNodes(hasTestTag("clear_all_data_button")).fetchSemanticsNodes().isEmpty()
    )

    // Admin sees admin settings card, opens dialog, and confirms clear
    composeTestRule.onNodeWithTag("debug_user_chip_admin").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("family_list")
      .performScrollToNode(hasTestTag("clear_all_data_button"))
    composeTestRule.onNodeWithTag("family_list")
      .performScrollToNode(hasTestTag("family_sign_out_button"))
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("family_admin_settings_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("clear_all_data_button").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("confirm_clear_data_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("confirm_clear_all_data_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.waitUntil(timeoutMillis = 3_000L) {
      fakeStore.transactions.value.isEmpty() && fakeStore.auditEvents.value.isEmpty()
    }
    assertTrue(fakeStore.transactions.value.isEmpty())
    assertTrue(fakeStore.auditEvents.value.isEmpty())
  }
}
