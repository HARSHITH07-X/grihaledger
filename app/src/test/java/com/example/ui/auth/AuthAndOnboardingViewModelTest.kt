package com.example.ui.auth

import com.example.data.fake.FakeAuthRepository
import com.example.data.fake.FakeFamilyRepository
import com.example.data.fake.FakeInviteRepository
import com.example.data.fake.FakeSeedData
import com.example.data.fake.FakeSessionRepository
import com.example.data.fake.FakeWorkspaceStore
import com.example.domain.model.AuthUser
import com.example.domain.model.Role
import com.example.ui.onboarding.OnboardingMode
import com.example.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthAndOnboardingViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private lateinit var store: FakeWorkspaceStore
  private lateinit var authRepo: FakeAuthRepository
  private lateinit var sessionRepo: FakeSessionRepository
  private lateinit var familyRepo: FakeFamilyRepository
  private lateinit var inviteRepo: FakeInviteRepository
  private lateinit var pendingInviteCodeStore: PendingInviteCodeStore

  @Before
  fun setup() {
    Dispatchers.setMain(dispatcher)
    store = FakeWorkspaceStore()
    authRepo = FakeAuthRepository(store)
    sessionRepo = FakeSessionRepository(store)
    familyRepo = FakeFamilyRepository(store, sessionRepo)
    inviteRepo = FakeInviteRepository(store, sessionRepo)
    pendingInviteCodeStore = PendingInviteCodeStore()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `entering invite code on AuthViewModel carries into OnboardingViewModel and joins family`() =
    runTest(dispatcher) {
      authRepo.setSignedInUser(null)
      val authViewModel = AuthViewModel(authRepo, pendingInviteCodeStore)

      authViewModel.toggleJoinInviteExpanded()
      assertTrue(authViewModel.uiState.value.isJoinInviteExpanded)

      authViewModel.onInviteCodeChanged("grh2-468k-mp")
      assertEquals("GRH2468KMP", authViewModel.uiState.value.inviteCodeInput)
      assertEquals("GRH2468KMP", pendingInviteCodeStore.pendingCode.value)

      // Simulate Google Sign-In completing for an unlinked user
      val signedInUser =
        AuthUser(
          uid = "uid_new_joiner",
          displayName = "Rohan Verma",
          email = "rohan@sharma.family",
        )
      authRepo.setSignedInUser(signedInUser, familyId = null)

      val onboardingViewModel =
        OnboardingViewModel(
          authRepository = authRepo,
          familyRepository = familyRepo,
          inviteRepository = inviteRepo,
          pendingInviteCodeStore = pendingInviteCodeStore,
        )
      advanceUntilIdle()

      // Pending code was consumed and pre-validated in JOIN mode
      assertNull(pendingInviteCodeStore.pendingCode.value)
      assertEquals(OnboardingMode.JOIN, onboardingViewModel.uiState.value.mode)
      assertEquals("GRH2468KMP", onboardingViewModel.uiState.value.inviteCodeInput)
      val verified = onboardingViewModel.uiState.value.verifiedInvite
      assertNotNull(verified)
      assertEquals(FakeSeedData.FAMILY_ID, verified?.familyId)
      assertEquals(Role.MEMBER, verified?.role)

      onboardingViewModel.confirmJoinFamily()
      advanceUntilIdle()

      assertNull(onboardingViewModel.uiState.value.errorMessage)
      assertEquals(FakeSeedData.FAMILY_ID, store.userProfiles.value["uid_new_joiner"]?.familyId)
      assertTrue(
        store.membersByFamily.value[FakeSeedData.FAMILY_ID]
          .orEmpty()
          .any { it.uid == "uid_new_joiner" && it.role == Role.MEMBER }
      )
    }

  @Test
  fun `collapsing invite section clears pending invite code and keeps onboarding in default create mode`() =
    runTest(dispatcher) {
      val authViewModel = AuthViewModel(authRepo, pendingInviteCodeStore)
      authViewModel.toggleJoinInviteExpanded()
      authViewModel.onInviteCodeChanged("GRH2468KMP")
      assertEquals("GRH2468KMP", pendingInviteCodeStore.pendingCode.value)

      authViewModel.toggleJoinInviteExpanded()
      assertFalse(authViewModel.uiState.value.isJoinInviteExpanded)
      assertNull(pendingInviteCodeStore.pendingCode.value)
    }
}
