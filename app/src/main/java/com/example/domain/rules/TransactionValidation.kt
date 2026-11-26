package com.example.domain.rules

import com.example.domain.model.Category
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.Member
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import java.time.LocalDate

data class TransactionDraft(
  val familyId: String,
  val type: TransactionType,
  val amountPaise: Long,
  val title: String,
  val note: String = "",
  val categoryId: String,
  val occurredOn: LocalDate,
  val paidByUid: String,
  val createdByUid: String,
  val fundingSource: FundingSource = FundingSource.HOUSEHOLD,
  val paymentMethod: PaymentMethod = PaymentMethod.UPI,
  val expenseClassification: ExpenseClassification = ExpenseClassification.NON_SALARY,
)

enum class ValidationErrorCode {
  AMOUNT_MUST_BE_POSITIVE,
  AMOUNT_TOO_LARGE,
  TITLE_REQUIRED,
  TITLE_TOO_LONG,
  NOTE_TOO_LONG,
  CATEGORY_NOT_FOUND,
  CATEGORY_TYPE_MISMATCH,
  PAYER_NOT_IN_FAMILY,
  CREATOR_NOT_IN_FAMILY,
  VOID_REASON_REQUIRED,
  ALREADY_VOIDED,
}

data class ValidationError(
  val code: ValidationErrorCode,
  val message: String,
)

sealed interface ValidationResult {
  val isValid: Boolean
    get() = this is Valid

  data object Valid : ValidationResult

  data class Invalid(val errors: List<ValidationError>) : ValidationResult
}

object TransactionValidation {
  const val MAX_AMOUNT_PAISE: Long = 10_000_000_000L // ₹10 crore upper sanity limit
  const val MAX_TITLE_LENGTH: Int = 80
  const val MAX_NOTE_LENGTH: Int = 240

  fun validateDraft(
    draft: TransactionDraft,
    categories: List<Category>,
    familyMembers: List<Member>,
  ): ValidationResult {
    val errors = mutableListOf<ValidationError>()

    if (draft.amountPaise <= 0L) {
      errors.add(
        ValidationError(
          code = ValidationErrorCode.AMOUNT_MUST_BE_POSITIVE,
          message = "Enter an amount greater than ₹0.00.",
        )
      )
    } else if (draft.amountPaise > MAX_AMOUNT_PAISE) {
      errors.add(
        ValidationError(
          code = ValidationErrorCode.AMOUNT_TOO_LARGE,
          message = "Amount exceeds the maximum single-entry limit.",
        )
      )
    }

    val trimmedTitle = draft.title.trim()
    if (trimmedTitle.isEmpty()) {
      errors.add(
        ValidationError(
          code = ValidationErrorCode.TITLE_REQUIRED,
          message = "Add a short title or merchant name.",
        )
      )
    } else if (trimmedTitle.length > MAX_TITLE_LENGTH) {
      errors.add(
        ValidationError(
          code = ValidationErrorCode.TITLE_TOO_LONG,
          message = "Title must be $MAX_TITLE_LENGTH characters or fewer.",
        )
      )
    }

    if (draft.note.trim().length > MAX_NOTE_LENGTH) {
      errors.add(
        ValidationError(
          code = ValidationErrorCode.NOTE_TOO_LONG,
          message = "Note must be $MAX_NOTE_LENGTH characters or fewer.",
        )
      )
    }

    val category = categories.firstOrNull {
      it.id == draft.categoryId && it.familyId == draft.familyId
    }
    if (category == null) {
      errors.add(
        ValidationError(
          code = ValidationErrorCode.CATEGORY_NOT_FOUND,
          message = "Choose a valid category for this family.",
        )
      )
    } else if (category.type != draft.type) {
      errors.add(
        ValidationError(
          code = ValidationErrorCode.CATEGORY_TYPE_MISMATCH,
          message = "Selected category belongs to ${category.type.name.lowercase()} records.",
        )
      )
    }

    val activeMemberUids =
      familyMembers
        .filter { it.familyId == draft.familyId && it.isActive }
        .map { it.uid }
        .toSet()

    if (draft.paidByUid !in activeMemberUids) {
      errors.add(
        ValidationError(
          code = ValidationErrorCode.PAYER_NOT_IN_FAMILY,
          message = "Select an active family member who paid or received this entry.",
        )
      )
    }

    if (draft.createdByUid !in activeMemberUids) {
      errors.add(
        ValidationError(
          code = ValidationErrorCode.CREATOR_NOT_IN_FAMILY,
          message = "Creator must be an active member of this family.",
        )
      )
    }

    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
  }

  fun validateVoid(transaction: Transaction, reason: String): ValidationResult {
    val errors = mutableListOf<ValidationError>()
    if (transaction.isVoided) {
      errors.add(
        ValidationError(
          code = ValidationErrorCode.ALREADY_VOIDED,
          message = "This transaction has already been voided.",
        )
      )
    }
    if (reason.trim().isEmpty()) {
      errors.add(
        ValidationError(
          code = ValidationErrorCode.VOID_REASON_REQUIRED,
          message = "Provide a brief reason before voiding this record.",
        )
      )
    }
    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
  }
}
