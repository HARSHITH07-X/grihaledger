package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.MainActivity
import com.example.data.fake.FakeAuthRepository
import com.example.data.fake.FakeWorkspaceStore
import com.example.ui.auth.PendingInviteCodeStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HiltTestApplication::class)
class AuthLoginFlowRobolectricTest {

  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var fakeStore: FakeWorkspaceStore
  @Inject lateinit var fakeAuthRepository: FakeAuthRepository
  @Inject lateinit var pendingInviteCodeStore: PendingInviteCodeStore

  @Before
  fun setup() {
    hiltRule.inject()
    fakeStore.reset()
    pendingInviteCodeStore.clear()
  }

  @Test
  fun `signed out user sees Google Sign-In screen and pre-entered invite code carries into join confirmation`() {
    fakeAuthRepository.setSignedInUser(null)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("screen_auth").assertIsDisplayed()
    composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("google_sign_in_button")
      .assertTextContains("Sign in with Google", substring = true)

    // Expand invite code input on login screen and enter valid seeded code
    composeTestRule.onNodeWithTag("auth_invite_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("auth_invite_code_input").performScrollTo().performTextInput("grh2468kmp")
    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithTag("google_sign_in_button")
      .performScrollTo()
      .assertTextContains("Sign in with Google & check code")

    // Complete Google Sign-In via AuthRepository (representing Credential Manager ID token exchange)
    runBlocking { fakeAuthRepository.signInWithGoogleIdToken("tok_priya_123") }
    composeTestRule.waitForIdle()

    // Onboarding automatically opens in Join mode and shows confirmation for Sharma Household
    composeTestRule.onNodeWithTag("screen_onboarding").assertIsDisplayed()
    composeTestRule.onNodeWithTag("join_confirmation_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Join Sharma Household as Member?").assertIsDisplayed()

    composeTestRule.onNodeWithTag("confirm_join_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("screen_home").assertIsDisplayed()
  }
}
