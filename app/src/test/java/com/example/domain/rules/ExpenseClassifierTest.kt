package com.example.domain.rules

import com.example.data.fake.FakeSeedData
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseClassifierTest {

  private val categories = FakeSeedData.categories

  @Test
  fun `classifies English, Hindi, and Hinglish staff salaries as SALARY`() {
    val salaryInputs =
      listOf(
        "Maid salary" to "",
        "Cook monthly pay" to "Transferred via UPI",
        "Driver tankhwah" to "",
        "Nanny" to "monthly wages",
        "Watchman pagar" to "",
        "कामवाली बाई वेतन" to "",
        "रसोइया पगार" to "",
        "ड्राइवर तनख्वाह" to "",
        "Home tutor salary" to "",
      )

    salaryInputs.forEach { (title, note) ->
      val result = ExpenseClassifier.suggest(title = title, note = note)
      assertNotNull("Expected suggestion for '$title'", result)
      assertEquals(
        "Expected SALARY for '$title'",
        ExpenseClassification.SALARY,
        result?.classification,
      )
    }
  }

  @Test
  fun `classifies everyday household bills, groceries, and school fees as NON_SALARY`() {
    val nonSalaryInputs =
      listOf(
        "BigBasket Groceries" to "" to "cat_groceries",
        "BESCOM Electricity Bill" to "Monthly bill" to "cat_utilities",
        "Flat Rent" to "Monthly apartment rent" to "cat_home",
        "School Tuition Fee" to "Term fee" to "cat_education",
        "Driver petrol reimbursement" to "Fuel" to "cat_transport",
        "रसोई किराना सामान" to "" to "cat_groceries",
      )

    nonSalaryInputs.forEach { (pair, categoryId) ->
      val result =
        ExpenseClassifier.suggest(
          title = pair.first,
          note = pair.second,
          categoryId = categoryId,
        )
      assertNotNull("Expected suggestion for '${pair.first}'", result)
      assertEquals(
        "Expected NON_SALARY for '${pair.first}'",
        ExpenseClassification.NON_SALARY,
        result?.classification,
      )
    }
  }

  @Test
  fun `infers SALARY from prior family ledger history when ambiguous person name is typed`() {
    val pastSalaryTx =
      Transaction(
        id = "tx_hist_sunita",
        familyId = FakeSeedData.FAMILY_ID,
        type = TransactionType.EXPENSE,
        amountPaise = 400_000L,
        title = "Sunita Didi",
        note = "Monthly pay",
        categoryId = "cat_home",
        occurredOn = LocalDate.of(2026, 11, 1),
        paidByUid = FakeSeedData.ADMIN_UID,
        createdByUid = FakeSeedData.ADMIN_UID,
        status = TransactionStatus.ACTIVE,
        createdAtEpochMillis = 1_760_000_000_000L,
        updatedAtEpochMillis = 1_760_000_000_000L,
        expenseClassification = ExpenseClassification.SALARY,
      )

    val result =
      ExpenseClassifier.suggest(
        title = "Sunita",
        note = "",
        categoryId = "cat_home",
        recentTransactions = listOf(pastSalaryTx),
      )
    assertNotNull(result)
    assertEquals(ExpenseClassification.SALARY, result?.classification)
  }

  @Test
  fun `returns null when title is blank and no informative non-ambiguous category exists`() {
    assertNull(ExpenseClassifier.suggest(title = "", note = "", categoryId = "cat_home"))
  }

  @Test
  fun `QuickEntryParser treats staff salary entry as EXPENSE with SALARY classification`() {
    val maid = QuickEntryParser.parse("Maid salary 5000 UPI", categories)
    assertEquals(TransactionType.EXPENSE, maid.type)
    assertEquals(ExpenseClassification.SALARY, maid.expenseClassification)
    assertEquals(500_000L, maid.amountPaise)

    val hindiMaid = QuickEntryParser.parse("कामवाली बाई पगार 4500 नकद", categories)
    assertEquals(TransactionType.EXPENSE, hindiMaid.type)
    assertEquals(ExpenseClassification.SALARY, hindiMaid.expenseClassification)
    assertEquals(450_000L, hindiMaid.amountPaise)

    val personalIncome = QuickEntryParser.parse("Monthly salary credited 85000", categories)
    assertEquals(TransactionType.INCOME, personalIncome.type)
    assertTrue(personalIncome.expenseClassification != ExpenseClassification.SALARY || personalIncome.type == TransactionType.INCOME)
  }
}
