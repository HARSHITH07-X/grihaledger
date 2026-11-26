package com.example.data.fake

import com.example.domain.model.AuditEvent
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.rules.AuditEventFactory
import com.example.domain.rules.Permissions
import com.example.domain.rules.TransactionDraft
import com.example.domain.rules.TransactionValidation
import com.example.domain.rules.ValidationResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class FakeLedgerRepository @Inject constructor(
  private val store: FakeWorkspaceStore,
  private val sessionRepository: SessionRepository,
  private val familyRepository: FamilyRepository,
) : LedgerRepository {

  override fun observeTransactions(): Flow<List<Transaction>> =
    combine(
      sessionRepository.observeUserProfile(),
      store.transactions,
      store.pendingSyncTransactionIds,
    ) { profile, list, pendingIds ->
      val familyId = profile?.familyId ?: return@combine emptyList()
      list
        .asSequence()
        .filter { it.familyId == familyId }
        .map { tx -> tx.copy(hasPendingSync = tx.id in pendingIds) }
        .sortedWith(
          compareByDescending<Transaction> { it.occurredOn }
            .thenByDescending { it.createdAtEpochMillis }
        )
        .toList()
    }

  override fun observeHasPendingSync(): Flow<Boolean> =
    store.pendingSyncTransactionIds.map { it.isNotEmpty() }

  override fun observeAuditEvents(): Flow<List<AuditEvent>> =
    combine(sessionRepository.observeUserProfile(), store.auditEvents) { profile, events ->
      val familyId = profile?.familyId ?: return@combine emptyList()
      events
        .filter { it.familyId == familyId }
        .sortedByDescending { it.occurredAtEpochMillis }
    }

  override fun observeAuditEventsForTransaction(transactionId: String): Flow<List<AuditEvent>> =
    observeAuditEvents().map { events -> events.filter { it.transactionId == transactionId } }

  override suspend fun createTransaction(
    transactionId: String,
    draft: TransactionDraft,
  ): Result<Transaction> {
    val actor =
      sessionRepository.observeCurrentMember().first()
        ?: return Result.failure(IllegalStateException("Active family membership required."))
    if (!Permissions.canCreateTransaction(actor)) {
      return Result.failure(IllegalStateException("Viewers cannot add transactions."))
    }
    if (draft.familyId != actor.familyId) {
      return Result.failure(IllegalStateException("Cross-family transaction creation is denied."))
    }

    val existing = store.transactions.value.firstOrNull { it.id == transactionId }
    if (existing != null) {
      return Result.success(existing)
    }

    val categories = familyRepository.observeCategories().first()
    val members = familyRepository.observeMembers().first()
    val normalizedDraft = draft.copy(createdByUid = actor.uid)
    when (val validation = TransactionValidation.validateDraft(normalizedDraft, categories, members)) {
      is ValidationResult.Invalid ->
        return Result.failure(IllegalArgumentException(validation.errors.first().message))
      ValidationResult.Valid -> Unit
    }

    val now = System.currentTimeMillis()
    val created =
      Transaction(
        id = transactionId,
        familyId = normalizedDraft.familyId,
        type = normalizedDraft.type,
        amountPaise = normalizedDraft.amountPaise,
        title = normalizedDraft.title.trim(),
        note = normalizedDraft.note.trim(),
        categoryId = normalizedDraft.categoryId,
        occurredOn = normalizedDraft.occurredOn,
        paidByUid = normalizedDraft.paidByUid,
        createdByUid = actor.uid,
        status = TransactionStatus.ACTIVE,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
        fundingSource = normalizedDraft.fundingSource,
        paymentMethod = normalizedDraft.paymentMethod,
        expenseClassification =
          if (normalizedDraft.type == TransactionType.EXPENSE) normalizedDraft.expenseClassification
          else ExpenseClassification.NON_SALARY,
      )

    val auditEvent =
      AuditEventFactory.buildCreatedEvent(
        eventId = newAuditEventId(),
        created = created,
        actorUid = actor.uid,
        occurredAtEpochMillis = now,
        categoriesById = categories.associateBy { it.id },
        membersByUid = members.associateBy { it.uid },
      )

    store.transactions.value = listOf(created) + store.transactions.value
    store.auditEvents.value = listOf(auditEvent) + store.auditEvents.value
    return Result.success(created)
  }

  override suspend fun editTransaction(
    transactionId: String,
    draft: TransactionDraft,
  ): Result<Transaction> {
    val actor =
      sessionRepository.observeCurrentMember().first()
        ?: return Result.failure(IllegalStateException("Active family membership required."))
    val existing =
      store.transactions.value.firstOrNull { it.id == transactionId }
        ?: return Result.failure(IllegalStateException("Transaction not found."))

    if (!Permissions.canEditTransaction(actor, existing)) {
      return Result.failure(
        IllegalStateException("You don't have permission to edit this transaction.")
      )
    }

    val categories = familyRepository.observeCategories().first()
    val members = familyRepository.observeMembers().first()
    val normalizedDraft =
      draft.copy(
        familyId = existing.familyId,
        createdByUid = existing.createdByUid,
      )
    when (val validation = TransactionValidation.validateDraft(normalizedDraft, categories, members)) {
      is ValidationResult.Invalid ->
        return Result.failure(IllegalArgumentException(validation.errors.first().message))
      ValidationResult.Valid -> Unit
    }

    val now = System.currentTimeMillis()
    val updated =
      existing.copy(
        type = normalizedDraft.type,
        amountPaise = normalizedDraft.amountPaise,
        title = normalizedDraft.title.trim(),
        note = normalizedDraft.note.trim(),
        categoryId = normalizedDraft.categoryId,
        occurredOn = normalizedDraft.occurredOn,
        paidByUid = normalizedDraft.paidByUid,
        fundingSource = normalizedDraft.fundingSource,
        paymentMethod = normalizedDraft.paymentMethod,
        expenseClassification =
          if (normalizedDraft.type == TransactionType.EXPENSE) normalizedDraft.expenseClassification
          else ExpenseClassification.NON_SALARY,
        updatedAtEpochMillis = now,
      )

    val auditEvent =
      AuditEventFactory.buildEditedEvent(
        eventId = newAuditEventId(),
        before = existing,
        after = updated,
        actorUid = actor.uid,
        occurredAtEpochMillis = now,
        categoriesById = categories.associateBy { it.id },
        membersByUid = members.associateBy { it.uid },
      )

    store.transactions.value =
      store.transactions.value.map { if (it.id == transactionId) updated else it }
    store.auditEvents.value = listOf(auditEvent) + store.auditEvents.value
    return Result.success(updated)
  }

  override suspend fun voidTransaction(
    transactionId: String,
    reason: String,
  ): Result<Unit> {
    val actor =
      sessionRepository.observeCurrentMember().first()
        ?: return Result.failure(IllegalStateException("Active family membership required."))
    val existing =
      store.transactions.value.firstOrNull { it.id == transactionId }
        ?: return Result.failure(IllegalStateException("Transaction not found."))

    if (!Permissions.canVoidTransaction(actor, existing)) {
      return Result.failure(
        IllegalStateException("You don't have permission to void this transaction.")
      )
    }
    when (val validation = TransactionValidation.validateVoid(existing, reason)) {
      is ValidationResult.Invalid ->
        return Result.failure(IllegalArgumentException(validation.errors.first().message))
      ValidationResult.Valid -> Unit
    }

    val now = System.currentTimeMillis()
    val trimmedReason = reason.trim()
    val voided =
      existing.copy(
        status = TransactionStatus.VOIDED,
        voidReason = trimmedReason,
        voidedByUid = actor.uid,
        updatedAtEpochMillis = now,
      )
    val auditEvent =
      AuditEventFactory.buildVoidedEvent(
        eventId = newAuditEventId(),
        before = existing,
        afterVoided = voided,
        actorUid = actor.uid,
        reason = trimmedReason,
        occurredAtEpochMillis = now,
      )

    store.transactions.value =
      store.transactions.value.map { if (it.id == transactionId) voided else it }
    store.auditEvents.value = listOf(auditEvent) + store.auditEvents.value
    return Result.success(Unit)
  }

  override suspend fun clearAllData(): Result<Unit> {
    val actor =
      sessionRepository.observeCurrentMember().first()
        ?: return Result.failure(IllegalStateException("Active family membership required."))
    if (!Permissions.canClearAllData(actor)) {
      return Result.failure(IllegalStateException("Only admins can clear all family data."))
    }

    val familyId = actor.familyId
    val clearedIds = store.transactions.value.asSequence().filter { it.familyId == familyId }.map { it.id }.toSet()
    store.transactions.value = store.transactions.value.filterNot { it.familyId == familyId }
    store.auditEvents.value = store.auditEvents.value.filterNot { it.familyId == familyId }
    store.pendingSyncTransactionIds.value = store.pendingSyncTransactionIds.value - clearedIds
    val existingFamily = store.families.value[familyId]
    if (existingFamily != null) {
      store.families.value =
        store.families.value + (familyId to existingFamily.copy(ledgerEpoch = existingFamily.ledgerEpoch + 1))
    }
    return Result.success(Unit)
  }

  private fun newAuditEventId(): String =
    "aud_${UUID.randomUUID().toString().replace("-", "").take(16)}"
}
