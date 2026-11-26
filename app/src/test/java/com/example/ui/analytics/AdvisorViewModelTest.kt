package com.example.ui.analytics

import app.cash.turbine.test
import com.example.data.fake.FakeAiAdvisorRepository
import com.example.data.fake.FakeFamilyRepository
import com.example.data.fake.FakeLedgerRepository
import com.example.data.fake.FakeSessionRepository
import com.example.data.fake.FakeWorkspaceStore
import com.example.domain.model.AdvisorFeedback
import com.example.domain.model.AdvisorQuestions
import com.example.ui.ledger.MainDispatcherRule
import com.example.ui.screens.AdvisorInferenceState
import com.example.ui.screens.AdvisorViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdvisorViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private lateinit var store: FakeWorkspaceStore
  private lateinit var sessionRepo: FakeSessionRepository
  private lateinit var familyRepo: FakeFamilyRepository
  private lateinit var ledgerRepo: FakeLedgerRepository
  private lateinit var aiRepo: FakeAiAdvisorRepository
  private lateinit var filterCoordinator: RecordsFilterCoordinator

  @Before
  fun setup() {
    store = FakeWorkspaceStore()
    store.reset()
    sessionRepo = FakeSessionRepository(store)
    familyRepo = FakeFamilyRepository(store, sessionRepo)
    ledgerRepo = FakeLedgerRepository(store, sessionRepo, familyRepo)
    aiRepo = FakeAiAdvisorRepository()
    filterCoordinator = RecordsFilterCoordinator()
  }

  @After
  fun tearDown() {
    store.reset()
  }

  @Test
  fun `AdvisorViewModel handles 6 questions, why disclosure, feedback, dismiss, error recovery, and not enough data without writing to ledger`() =
    runTest {
      val vm =
        AdvisorViewModel(
          sessionRepository = sessionRepo,
          familyRepository = familyRepo,
          ledgerRepository = ledgerRepo,
          aiAdvisorRepository = aiRepo,
          filterCoordinator = filterCoordinator,
        )

      val txSnapshotBefore = store.transactions.value
      val auditCountBefore = store.auditEvents.value.size

      vm.uiState.test {
        val loaded = expectMostRecentItem()
        assertEquals(6, loaded.suggestedQuestions.size)

        // 1. Ask a suggested question
        vm.askSuggestedQuestion(AdvisorQuestions.SUGGESTED.first())
        val answered = expectMostRecentItem()
        assertEquals(AdvisorInferenceState.Idle, answered.inferenceState)
        assertEquals(1, answered.visibleResponses.size)
        val firstResponse = answered.visibleResponses.first()
        assertFalse(firstResponse.isNotEnoughData)

        // 2. Expand "Why am I seeing this?" & record Useful feedback
        vm.toggleWhySeeingThis(firstResponse.id)
        val whyShown = expectMostRecentItem()
        assertTrue(firstResponse.id in whyShown.expandedWhyIds)

        vm.submitFeedback(firstResponse.id, AdvisorFeedback.USEFUL)
        val afterFeedback = expectMostRecentItem()
        assertEquals(AdvisorFeedback.USEFUL, afterFeedback.visibleResponses.first().feedback)

        // 3. Approve proposal navigates read-only without changing ledger store
        var openedInsightsOrRecords = false
        firstResponse.actionProposal?.let { proposal ->
          vm.approveProposal(
            responseId = firstResponse.id,
            proposal = proposal,
            onOpenTransactionDetail = { openedInsightsOrRecords = true },
            onOpenRecords = { openedInsightsOrRecords = true },
            onOpenInsights = { openedInsightsOrRecords = true },
          )
          val approved = expectMostRecentItem()
          assertTrue(approved.visibleResponses.first().proposalApproved)
          assertTrue(openedInsightsOrRecords)
        }
        assertEquals(txSnapshotBefore, store.transactions.value)
        assertEquals(auditCountBefore, store.auditEvents.value.size)

        // 4. Dismiss hides card cleanly
        vm.dismissResponse(firstResponse.id)
        val dismissed = expectMostRecentItem()
        assertTrue(dismissed.visibleResponses.isEmpty())

        // 5. Real error propagates cleanly and retry recovers
        aiRepo.nextFailure = IllegalStateException("App Check attestation unavailable")
        vm.onCustomInputChange("How are groceries trending?")
        vm.submitCustomQuestion()
        val errored = expectMostRecentItem()
        assertTrue(errored.inferenceState is AdvisorInferenceState.Error)
        assertEquals(
          "App Check attestation unavailable",
          (errored.inferenceState as AdvisorInferenceState.Error).message,
        )

        vm.retryLastError()
        val recovered = expectMostRecentItem()
        assertEquals(AdvisorInferenceState.Idle, recovered.inferenceState)
        assertEquals(1, recovered.visibleResponses.size)

        // 6. Empty workspace produces honest "not enough data" card
        store.transactions.value = emptyList()
        expectMostRecentItem()
        vm.askSuggestedQuestion(AdvisorQuestions.SUGGESTED[2])
        val emptyDataState = expectMostRecentItem()
        assertTrue(emptyDataState.visibleResponses.first().isNotEnoughData)

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `AdvisorViewModel streams Hindi response when AppLanguage HINDI is selected`() =
    runTest {
      val vm =
        AdvisorViewModel(
          sessionRepository = sessionRepo,
          familyRepository = familyRepo,
          ledgerRepository = ledgerRepo,
          aiAdvisorRepository = aiRepo,
          filterCoordinator = filterCoordinator,
        )

      vm.uiState.test {
        expectMostRecentItem()

        val hiQuestion = AdvisorQuestions.forLanguage(com.example.domain.model.AppLanguage.HINDI).first()
        vm.askSuggestedQuestion(hiQuestion, com.example.domain.model.AppLanguage.HINDI)
        val answered = expectMostRecentItem()

        assertEquals(AdvisorInferenceState.Idle, answered.inferenceState)
        assertEquals(1, answered.visibleResponses.size)
        val response = answered.visibleResponses.first()
        assertTrue(com.example.domain.model.AppLanguage.containsDevanagari(response.finding))
        assertTrue(response.evidence.isNotEmpty())

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `AdvisorViewModel toggles Google Search grounding and emits web citations`() = runTest {
    val vm =
      AdvisorViewModel(
        sessionRepository = sessionRepo,
        familyRepository = familyRepo,
        ledgerRepository = ledgerRepo,
        aiAdvisorRepository = aiRepo,
        filterCoordinator = filterCoordinator,
      )

    vm.uiState.test {
      val initial = expectMostRecentItem()
      assertFalse(initial.useGoogleSearch)

      vm.toggleGoogleSearch()
      val toggled = expectMostRecentItem()
      assertTrue(toggled.useGoogleSearch)

      vm.onCustomInputChange("Latest LPG cylinder refill price vs our utilities spend")
      vm.submitCustomQuestion()
      val answered = expectMostRecentItem()

      assertEquals(AdvisorInferenceState.Idle, answered.inferenceState)
      val response = answered.visibleResponses.first()
      assertTrue(response.isWebSearchGrounded)
      assertTrue(response.webCitations.isNotEmpty())
      assertTrue("googleSearch" in response.toolsUsed)

      cancelAndIgnoreRemainingEvents()
    }
  }
}
