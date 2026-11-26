package com.example.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.example.MainActivity
import com.example.data.fake.FakeWorkspaceStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], fontScale = 2.0f, application = HiltTestApplication::class)
class AccessibilityFontScaleTest {

  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var fakeStore: FakeWorkspaceStore

  @Before
  fun setup() {
    hiltRule.inject()
    fakeStore.reset()
  }

  @Test
  fun `font scale 200 preserves touch targets, talkback headings, and renders all routes cleanly`() {
    composeTestRule.waitForIdle()

    // Verify TalkBack heading semantics exist on Home
    val homeHeadings =
      composeTestRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        .fetchSemanticsNodes()
    assertTrue("Expected at least one TalkBack heading on Home", homeHeadings.isNotEmpty())

    // Verify FAB meets 48dp minimum touch target & has click semantics
    val fabNode = composeTestRule.onNodeWithTag("add_fab").fetchSemanticsNode()
    val minTouchPx = with(composeTestRule.density) { 48.dp.toPx() }
    assertTrue(
      "FAB width ${fabNode.size.width} should be >= $minTouchPx",
      fabNode.size.width >= minTouchPx - 1f,
    )
    assertTrue(
      "FAB height ${fabNode.size.height} should be >= $minTouchPx",
      fabNode.size.height >= minTouchPx - 1f,
    )
    assertTrue(fabNode.config.contains(SemanticsActions.OnClick))

    // Home scrolls cleanly at 200% font scale
    composeTestRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_summary_card"))
    composeTestRule.onNodeWithTag("home_summary_card").assertIsDisplayed()

    // Quick Add sheet opens and keypad remains usable at 200% font scale
    composeTestRule.onNodeWithTag("add_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("add_transaction_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("keypad_5").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("save_transaction_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("add_sheet_close_button").performClick()
    composeTestRule.waitForIdle()

    // Records screen + heading
    composeTestRule.onNodeWithTag("nav_records").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_records").assertIsDisplayed()
    composeTestRule.onNodeWithTag("records_list")
      .performScrollToNode(hasTestTag("records_count_label"))
    assertTrue(
      composeTestRule.onNodeWithTag("records_count_label")
        .fetchSemanticsNode()
        .config
        .contains(SemanticsProperties.Heading)
    )

    // Insights screen
    composeTestRule.onNodeWithTag("nav_insights").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_insights").assertIsDisplayed()

    // Advisor screen
    composeTestRule.onNodeWithTag("nav_advisor").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_advisor").assertIsDisplayed()
    composeTestRule.onNodeWithTag("advisor_list")
      .performScrollToNode(hasTestTag("advisor_suggested_questions"))
    composeTestRule.onNodeWithTag("advisor_suggested_questions").assertIsDisplayed()

    // Family screen
    composeTestRule.onNodeWithTag("nav_family").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screen_family").assertIsDisplayed()
    composeTestRule.onNodeWithTag("family_list")
      .performScrollToNode(hasTestTag("family_sign_out_button"))
    composeTestRule.onNodeWithTag("family_sign_out_button").assertIsDisplayed()
  }
}
