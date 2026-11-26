package com.example.domain.rules

import com.example.domain.model.Category
import com.example.domain.model.Member
import com.example.domain.model.Role
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionValidationTest {

  private val members =
    listOf(
      Member("u1", "fam1", "Ananya", "a@test.in", Role.ADMIN, 1_000L),
      Member("u2", "fam1", "Rohan", "r@test.in", Role.MEMBER, 1_000L),
      Member("u_inactive", "fam1", "Old", "o@test.in", Role.MEMBER, 1_000L, isActive = false),
    )

  private val categories =
    listOf(
      Category("cat_food", "fam1", "Groceries", TransactionType.EXPENSE, "basket", 0xFF1E9E6AL),
      Category("cat_salary", "fam1", "Salary", TransactionType.INCOME, "wallet", 0xFF1E9E6AL),
    )

  private fun validDraft() =
    TransactionDraft(
      familyId = "fam1",
      type = TransactionType.EXPENSE,
      amountPaise = 125_000L,
      title = "BigBasket Groceries",
      note = "Weekly vegetables",
      categoryId = "cat_food",
      occurredOn = LocalDate.of(2026, 11, 10),
      paidByUid = "u2",
      createdByUid = "u1",
    )

  @Test
  fun `valid expense draft passes validation`() {
    val result = TransactionValidation.validateDraft(validDraft(), categories, members)
    assertTrue(result.isValid)
  }

  @Test
  fun `non-positive and oversized amounts fail validation`() {
    val zeroResult =
      TransactionValidation.validateDraft(validDraft().copy(amountPaise = 0L), categories, members)
    assertFalse(zeroResult.isValid)
    assertTrue(
      (zeroResult as ValidationResult.Invalid).errors.any {
        it.code == ValidationErrorCode.AMOUNT_MUST_BE_POSITIVE
      }
    )

    val tooLargeResult =
      TransactionValidation.validateDraft(
        validDraft().copy(amountPaise = TransactionValidation.MAX_AMOUNT_PAISE + 1L),
        categories,
        members,
      )
    assertTrue(
      (tooLargeResult as ValidationResult.Invalid).errors.any {
        it.code == ValidationErrorCode.AMOUNT_TOO_LARGE
      }
    )
  }

  @Test
  fun `blank or overlong title and note fail validation`() {
    val blankTitle =
      TransactionValidation.validateDraft(validDraft().copy(title = "   "), categories, members)
    assertTrue(
      (blankTitle as ValidationResult.Invalid).errors.any {
        it.code == ValidationErrorCode.TITLE_REQUIRED
      }
    )

    val longTitle =
      TransactionValidation.validateDraft(
        validDraft().copy(title = "x".repeat(TransactionValidation.MAX_TITLE_LENGTH + 1)),
        categories,
        members,
      )
    assertTrue(
      (longTitle as ValidationResult.Invalid).errors.any {
        it.code == ValidationErrorCode.TITLE_TOO_LONG
      }
    )

    val longNote =
      TransactionValidation.validateDraft(
        validDraft().copy(note = "n".repeat(TransactionValidation.MAX_NOTE_LENGTH + 1)),
        categories,
        members,
      )
    assertTrue(
      (longNote as ValidationResult.Invalid).errors.any {
        it.code == ValidationErrorCode.NOTE_TOO_LONG
      }
    )
  }

  @Test
  fun `missing or mismatched category and inactive members fail validation`() {
    val mismatch =
      TransactionValidation.validateDraft(
        validDraft().copy(categoryId = "cat_salary"),
        categories,
        members,
      )
    assertTrue(
      (mismatch as ValidationResult.Invalid).errors.any {
        it.code == ValidationErrorCode.CATEGORY_TYPE_MISMATCH
      }
    )

    val unknownCat =
      TransactionValidation.validateDraft(
        validDraft().copy(categoryId = "missing"),
        categories,
        members,
      )
    assertTrue(
      (unknownCat as ValidationResult.Invalid).errors.any {
        it.code == ValidationErrorCode.CATEGORY_NOT_FOUND
      }
    )

    val inactivePayer =
      TransactionValidation.validateDraft(
        validDraft().copy(paidByUid = "u_inactive", createdByUid = "stranger"),
        categories,
        members,
      )
    val codes = (inactivePayer as ValidationResult.Invalid).errors.map { it.code }.toSet()
    assertEquals(
      setOf(ValidationErrorCode.PAYER_NOT_IN_FAMILY, ValidationErrorCode.CREATOR_NOT_IN_FAMILY),
      codes,
    )
  }

  @Test
  fun `void validation requires non-blank reason and active status`() {
    val activeTx =
      Transaction(
        id = "tx1",
        familyId = "fam1",
        type = TransactionType.EXPENSE,
        amountPaise = 5_000L,
        title = "Coffee",
        categoryId = "cat_food",
        occurredOn = LocalDate.of(2026, 11, 10),
        paidByUid = "u1",
        createdByUid = "u1",
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 1_000L,
      )

    assertTrue(TransactionValidation.validateVoid(activeTx, "Duplicate entry").isValid)
    assertFalse(TransactionValidation.validateVoid(activeTx, "   ").isValid)
    assertFalse(
      TransactionValidation.validateVoid(
        activeTx.copy(status = TransactionStatus.VOIDED),
        "Reason",
      ).isValid
    )
  }
}
