package com.example.domain.model

import java.time.LocalDate

enum class TransactionStatus {
  ACTIVE,
  VOIDED,
}

enum class FundingSource(val label: String, val firestoreValue: String) {
  HOUSEHOLD("Household Pool", "household"),
  PERSONAL("Personal", "personal"),
  FAMILY_CARD("Family Card", "family_card");

  companion object {
    fun fromFirestore(value: String?): FundingSource =
      entries.firstOrNull { it.firestoreValue == value?.lowercase() } ?: HOUSEHOLD
  }
}

enum class PaymentMethod(val label: String, val firestoreValue: String) {
  UPI("UPI", "upi"),
  CARD("Card", "card"),
  CASH("Cash", "cash"),
  BANK_TRANSFER("Bank Transfer", "bank_transfer");

  companion object {
    fun fromFirestore(value: String?): PaymentMethod =
      entries.firstOrNull { it.firestoreValue == value?.lowercase() } ?: UPI
  }
}

enum class ExpenseClassification(val label: String, val firestoreValue: String) {
  SALARY("Salary", "salary"),
  NON_SALARY("Non-Salary", "non_salary");

  companion object {
    fun fromFirestore(value: String?): ExpenseClassification =
      entries.firstOrNull { it.firestoreValue == value?.lowercase() } ?: NON_SALARY
  }
}

data class Transaction(
  val id: String,
  val familyId: String,
  val type: TransactionType,
  val amountPaise: Long,
  val title: String,
  val note: String = "",
  val categoryId: String,
  val occurredOn: LocalDate,
  val paidByUid: String,
  val createdByUid: String,
  val status: TransactionStatus = TransactionStatus.ACTIVE,
  val voidReason: String? = null,
  val voidedByUid: String? = null,
  val createdAtEpochMillis: Long,
  val updatedAtEpochMillis: Long,
  val fundingSource: FundingSource = FundingSource.HOUSEHOLD,
  val paymentMethod: PaymentMethod = PaymentMethod.UPI,
  val expenseClassification: ExpenseClassification = ExpenseClassification.NON_SALARY,
  val hasPendingSync: Boolean = false,
) {
  val isVoided: Boolean
    get() = status == TransactionStatus.VOIDED

  val amount: Money
    get() = Money(amountPaise)
}

enum class AuditAction {
  CREATED,
  EDITED,
  VOIDED,
}

data class AuditEvent(
  val id: String,
  val familyId: String,
  val transactionId: String,
  val action: AuditAction,
  val actorUid: String,
  val occurredAtEpochMillis: Long,
  val summary: String,
  val beforeState: Map<String, String> = emptyMap(),
  val afterState: Map<String, String> = emptyMap(),
)
