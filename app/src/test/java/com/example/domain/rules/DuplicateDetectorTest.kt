package com.example.domain.rules

import com.example.domain.model.Transaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateDetectorTest {

  private fun existingTx(
    id: String = "tx_1",
    amountPaise: Long = 150_000L,
    categoryId: String = "cat_groceries",
    type: TransactionType = TransactionType.EXPENSE,
    occurredOn: LocalDate = LocalDate.of(2026, 11, 10),
    status: TransactionStatus = TransactionStatus.ACTIVE,
  ) =
    Transaction(
      id = id,
      familyId = "fam1",
      type = type,
      amountPaise = amountPaise,
      title = "BigBasket",
      categoryId = categoryId,
      occurredOn = occurredOn,
      paidByUid = "u1",
      createdByUid = "u1",
      status = status,
      createdAtEpochMillis = 10_000L,
      updatedAtEpochMillis = 10_000L,
    )

  private fun draft(
    amountPaise: Long = 150_000L,
    categoryId: String = "cat_groceries",
    type: TransactionType = TransactionType.EXPENSE,
    occurredOn: LocalDate = LocalDate.of(2026, 11, 10),
  ) =
    TransactionDraft(
      familyId = "fam1",
      type = type,
      amountPaise = amountPaise,
      title = "BigBasket Order",
      categoryId = categoryId,
      occurredOn = occurredOn,
      paidByUid = "u2",
      createdByUid = "u2",
    )

  @Test
  fun `warns on matching amount category type within one day`() {
    val sameDay =
      DuplicateDetector.detectPotentialDuplicates(
        draft = draft(),
        existingTransactions = listOf(existingTx()),
      )
    assertNotNull(sameDay)
    assertEquals(1, sameDay!!.matchingTransactions.size)

    val nextDay =
      DuplicateDetector.detectPotentialDuplicates(
        draft = draft(occurredOn = LocalDate.of(2026, 11, 11)),
        existingTransactions = listOf(existingTx()),
      )
    assertNotNull(nextDay)
  }

  @Test
  fun `ignores voided entries and entries outside window or with different fields`() {
    val existing =
      listOf(
        existingTx(id = "tx_void", status = TransactionStatus.VOIDED),
        existingTx(id = "tx_old", occurredOn = LocalDate.of(2026, 11, 8)),
        existingTx(id = "tx_amt", amountPaise = 150_001L),
        existingTx(id = "tx_cat", categoryId = "cat_dining"),
        existingTx(id = "tx_type", type = TransactionType.INCOME),
      )

    val warning =
      DuplicateDetector.detectPotentialDuplicates(
        draft = draft(),
        existingTransactions = existing,
      )
    assertNull(warning)
  }

  @Test
  fun `excludes the transaction currently being edited`() {
    val self = existingTx(id = "tx_editing")
    val warning =
      DuplicateDetector.detectPotentialDuplicates(
        draft = draft(),
        existingTransactions = listOf(self),
        excludeTransactionId = "tx_editing",
      )
    assertNull(warning)
  }
}
