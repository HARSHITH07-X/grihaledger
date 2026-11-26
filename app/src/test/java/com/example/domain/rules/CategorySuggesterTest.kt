package com.example.domain.rules

import com.example.data.fake.FakeSeedData
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategorySuggesterTest {

  private val categories = FakeSeedData.categories

  @Test
  fun `suggests deterministic categories from merchant and expense keywords`() {
    assertEquals(
      "cat_groceries",
      CategorySuggester.suggestCategory("BigBasket Milk", "", TransactionType.EXPENSE, categories)?.id,
    )
    assertEquals(
      "cat_utilities",
      CategorySuggester.suggestCategory("BESCOM", "power bill", TransactionType.EXPENSE, categories)?.id,
    )
    assertEquals(
      "cat_health",
      CategorySuggester.suggestCategory("Apollo Pharmacy", "", TransactionType.EXPENSE, categories)?.id,
    )
    assertEquals(
      "cat_salary",
      CategorySuggester.suggestCategory("October Salary", "", TransactionType.INCOME, categories)?.id,
    )
  }

  @Test
  fun `respects transaction type when suggesting category`() {
    assertNull(
      CategorySuggester.suggestCategory("Monthly Salary", "", TransactionType.EXPENSE, categories)
    )
    assertNull(
      CategorySuggester.suggestCategory("BigBasket groceries", "", TransactionType.INCOME, categories)
    )
  }

  @Test
  fun `orders recently used active categories first while ignoring voided entries`() {
    val recent =
      listOf(
        Transaction(
          id = "tx_v",
          familyId = FakeSeedData.FAMILY_ID,
          type = TransactionType.EXPENSE,
          amountPaise = 10_000L,
          title = "Voided Cafe",
          categoryId = "cat_dining",
          occurredOn = LocalDate.now(),
          paidByUid = FakeSeedData.ADMIN_UID,
          createdByUid = FakeSeedData.ADMIN_UID,
          status = TransactionStatus.VOIDED,
          createdAtEpochMillis = 900L,
          updatedAtEpochMillis = 900L,
        ),
        Transaction(
          id = "tx_health",
          familyId = FakeSeedData.FAMILY_ID,
          type = TransactionType.EXPENSE,
          amountPaise = 25_000L,
          title = "Clinic",
          categoryId = "cat_health",
          occurredOn = LocalDate.now(),
          paidByUid = FakeSeedData.ADMIN_UID,
          createdByUid = FakeSeedData.ADMIN_UID,
          createdAtEpochMillis = 800L,
          updatedAtEpochMillis = 800L,
        ),
        Transaction(
          id = "tx_transport",
          familyId = FakeSeedData.FAMILY_ID,
          type = TransactionType.EXPENSE,
          amountPaise = 15_000L,
          title = "Metro",
          categoryId = "cat_transport",
          occurredOn = LocalDate.now(),
          paidByUid = FakeSeedData.ADMIN_UID,
          createdByUid = FakeSeedData.ADMIN_UID,
          createdAtEpochMillis = 700L,
          updatedAtEpochMillis = 700L,
        ),
      )

    val ordered =
      CategorySuggester.orderCategoriesWithRecentsFirst(
        categories = categories,
        type = TransactionType.EXPENSE,
        recentTransactions = recent,
      )

    assertEquals("cat_health", ordered[0].id)
    assertEquals("cat_transport", ordered[1].id)
    // Remaining categories keep sortOrder (cat_groceries has sortOrder 1)
    assertEquals("cat_groceries", ordered[2].id)
  }
}
