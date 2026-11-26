package com.example.ui.ledger

import app.cash.turbine.test
import com.example.data.fake.FakeAudioTranscriptionRepository
import com.example.data.fake.FakeFamilyRepository
import com.example.data.fake.FakeLedgerRepository
import com.example.data.fake.FakeSessionRepository
import com.example.data.fake.FakeWorkspaceStore
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.PaymentMethod
import com.example.domain.model.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EasyAndVoiceEntryTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private lateinit var store: FakeWorkspaceStore
  private lateinit var sessionRepo: FakeSessionRepository
  private lateinit var familyRepo: FakeFamilyRepository
  private lateinit var ledgerRepo: FakeLedgerRepository
  private lateinit var audioRepo: FakeAudioTranscriptionRepository

  @Before
  fun setup() {
    store = FakeWorkspaceStore()
    store.reset()
    sessionRepo = FakeSessionRepository(store)
    familyRepo = FakeFamilyRepository(store, sessionRepo)
    ledgerRepo = FakeLedgerRepository(store, sessionRepo, familyRepo)
    audioRepo = FakeAudioTranscriptionRepository()
  }

  @After
  fun tearDown() {
    store.reset()
  }

  @Test
  fun `preset rupee chips, 1-tap templates, and quick natural text auto-fill form cleanly`() = runTest {
    val vm = AddTransactionViewModel(sessionRepo, familyRepo, ledgerRepo, audioRepo)
    vm.uiState.test {
      val initial = expectMostRecentItem()
      assertTrue(initial.quickTemplates.isNotEmpty())

      // +₹100 and +₹50 preset chips add cumulatively in paise
      vm.onAddPresetAmount(100L)
      vm.onAddPresetAmount(50L)
      val afterPresets = expectMostRecentItem()
      assertEquals(15_000L, afterPresets.amountPaise)
      assertEquals("150", afterPresets.amountInput)

      // 1-tap template fills amount, title, category, method
      val firstTpl = afterPresets.quickTemplates.first()
      vm.onApplyTemplate(firstTpl)
      val afterTemplate = expectMostRecentItem()
      assertEquals(firstTpl.amountPaise, afterTemplate.amountPaise)
      assertEquals(firstTpl.title, afterTemplate.title)
      assertEquals(firstTpl.categoryId, afterTemplate.selectedCategoryId)

      // Quick natural text bar clears quick input and updates parsed fields
      vm.onQuickTextChanged("Metro recharge 250 cash yesterday")
      vm.applyQuickText()
      val afterQuick = expectMostRecentItem()
      assertEquals(25_000L, afterQuick.amountPaise)
      assertEquals(PaymentMethod.CASH, afterQuick.paymentMethod)
      assertEquals(LocalDate.now().minusDays(1), afterQuick.occurredOn)
      assertEquals("", afterQuick.quickTextInput)
      assertTrue(afterQuick.showMoreDetails)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `processVoiceAudio transcribes English and Hindi audio and populates transaction draft`() = runTest {
    val vm = AddTransactionViewModel(sessionRepo, familyRepo, ledgerRepo, audioRepo)
    vm.uiState.test {
      expectMostRecentItem()

      // Hindi voice transcription
      vm.processVoiceAudio(byteArrayOf(1, 2, 3, 4), "hi")
      val afterHindiVoice = expectMostRecentItem()
      assertTrue(afterHindiVoice.voiceState is VoiceEntryState.TranscriptApplied)
      assertEquals(18_000L, afterHindiVoice.amountPaise)
      assertEquals(PaymentMethod.UPI, afterHindiVoice.paymentMethod)
      assertTrue(afterHindiVoice.title.contains("दूध"))

      // English voice transcription error recovery
      audioRepo.nextFailure = IllegalStateException("Mic stream interrupted")
      vm.processVoiceAudio(byteArrayOf(1, 2, 3, 4), "en")
      val errored = expectMostRecentItem()
      assertTrue(errored.voiceState is VoiceEntryState.Error)
      assertEquals("Mic stream interrupted", (errored.voiceState as VoiceEntryState.Error).message)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `smart expense classifier updates responsively on title edits and respects manual override`() =
    runTest {
      val vm = AddTransactionViewModel(sessionRepo, familyRepo, ledgerRepo, audioRepo)
      vm.uiState.test {
        val initial = expectMostRecentItem()
        assertEquals(ExpenseClassification.NON_SALARY, initial.expenseClassification)

        // Typing a staff salary title immediately flips to SALARY with suggested badge
        vm.onTitleChanged("Cook monthly salary")
        val salaryState = expectMostRecentItem()
        assertEquals(ExpenseClassification.SALARY, salaryState.expenseClassification)
        assertEquals(ExpenseClassification.SALARY, salaryState.suggestedClassification)

        // Switching title back to groceries responsively updates back to NON_SALARY
        vm.onTitleChanged("BigBasket groceries")
        val groceryState = expectMostRecentItem()
        assertEquals(ExpenseClassification.NON_SALARY, groceryState.expenseClassification)
        assertEquals(ExpenseClassification.NON_SALARY, groceryState.suggestedClassification)

        // Manual override sticks even when title changes
        vm.onSelectExpenseClassification(ExpenseClassification.SALARY)
        vm.onTitleChanged("Milk and bread")
        val manualSticky = expectMostRecentItem()
        assertEquals(ExpenseClassification.SALARY, manualSticky.expenseClassification)

        // Quick entry for staff salary chooses EXPENSE + SALARY classification
        vm.onTypeSelected(TransactionType.EXPENSE)
        vm.onQuickTextChanged("Maid salary 5000 UPI")
        vm.applyQuickText()
        val quickSalary = expectMostRecentItem()
        assertEquals(TransactionType.EXPENSE, quickSalary.type)
        assertEquals(ExpenseClassification.SALARY, quickSalary.expenseClassification)
        assertEquals(500_000L, quickSalary.amountPaise)

        cancelAndIgnoreRemainingEvents()
      }
    }
}
