package com.example.data.firebase

import com.example.domain.model.AuditAction
import com.example.domain.model.AuditEvent
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.PaymentMethod
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirebaseLedgerRepository @Inject constructor(
  private val auth: FirebaseAuth,
  private val db: FirebaseFirestore,
  private val sessionRepository: SessionRepository,
  private val familyRepository: FamilyRepository,
) : LedgerRepository {

  private val hasPendingSyncFlow = MutableStateFlow(false)

  override fun observeHasPendingSync(): Flow<Boolean> = hasPendingSyncFlow.asStateFlow()

  override fun observeTransactions(): Flow<List<Transaction>> =
    sessionRepository.observeUserProfile().flatMapLatest { profile ->
      val familyId = profile?.familyId
      if (profile == null || familyId.isNullOrBlank()) {
        hasPendingSyncFlow.value = false
        flowOf(emptyList())
      } else {
        val epochFlow =
          familyRepository.observeFamily()
            .map { family -> if (family?.id == familyId) family.ledgerEpoch else 0 }
            .distinctUntilChanged()
        val rawTransactionsFlow =
          callbackFlow {
            val collectionRef =
              db.collection("families").document(familyId).collection("transactions")
            val listener =
              collectionRef
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                  if (error != null) {
                    if (auth.currentUser?.uid == profile.uid) {
                      handleFirestoreError(error, OperationType.LIST, collectionRef.path, auth)
                    }
                    trySend(emptyList())
                    return@addSnapshotListener
                  }
                  if (snapshot == null) {
                    hasPendingSyncFlow.value = false
                    trySend(emptyList())
                    return@addSnapshotListener
                  }

                  hasPendingSyncFlow.value = snapshot.metadata.hasPendingWrites()
                  val mapped =
                    snapshot.documents
                      .mapNotNull { it.toDomainTransactionWithEpochOrNull(familyId) }
                  trySend(mapped)
                }
            awaitClose { listener.remove() }
          }
        combine(rawTransactionsFlow, epochFlow) { list, currentEpoch ->
          list
            .asSequence()
            .filter { it.second == currentEpoch }
            .map { it.first }
            .sortedWith(
              compareByDescending<Transaction> { it.occurredOn }
                .thenByDescending { it.createdAtEpochMillis }
            )
            .toList()
        }
      }
    }

  override fun observeAuditEvents(): Flow<List<AuditEvent>> =
    sessionRepository.observeUserProfile().flatMapLatest { profile ->
      val familyId = profile?.familyId
      if (profile == null || familyId.isNullOrBlank()) {
        flowOf(emptyList())
      } else {
        val epochFlow =
          familyRepository.observeFamily()
            .map { family -> if (family?.id == familyId) family.ledgerEpoch else 0 }
            .distinctUntilChanged()
        val rawAuditEventsFlow =
          callbackFlow {
            val collectionRef =
              db.collection("families").document(familyId).collection("auditEvents")
            val listener =
              collectionRef
                .orderBy("occurredAt", Query.Direction.DESCENDING)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                  if (error != null) {
                    if (auth.currentUser?.uid == profile.uid) {
                      handleFirestoreError(error, OperationType.LIST, collectionRef.path, auth)
                    }
                    trySend(emptyList())
                    return@addSnapshotListener
                  }
                  val mapped =
                    snapshot?.documents?.mapNotNull { it.toDomainAuditEventWithEpochOrNull(familyId) }
                      ?: emptyList()
                  trySend(mapped)
                }
            awaitClose { listener.remove() }
          }
        combine(rawAuditEventsFlow, epochFlow) { events, currentEpoch ->
          events
            .asSequence()
            .filter { it.second == currentEpoch }
            .map { it.first }
            .toList()
        }
      }
    }

  override fun observeAuditEventsForTransaction(transactionId: String): Flow<List<AuditEvent>> =
    observeAuditEvents().map { events -> events.filter { it.transactionId == transactionId } }

  override suspend fun createTransaction(
    transactionId: String,
    draft: TransactionDraft,
  ): Result<Transaction> {
    val uid =
      auth.currentUser?.uid
        ?: return Result.failure(IllegalStateException("Sign in before adding transactions."))
    val actor =
      withTimeoutOrNull(2_000L) { sessionRepository.observeCurrentMember().first { it != null } }
        ?: return Result.failure(IllegalStateException("Active family membership required."))
    if (!Permissions.canCreateTransaction(actor)) {
      return Result.failure(IllegalStateException("Viewers cannot add transactions."))
    }
    if (draft.familyId != actor.familyId) {
      return Result.failure(IllegalStateException("Cross-family transaction creation is denied."))
    }

    val familyRef = db.collection("families").document(actor.familyId)
    val txRef = familyRef.collection("transactions").document(transactionId)

    return try {
      // Idempotency guard for client-generated IDs
      val cachedSnap = runCatching { txRef.get(Source.CACHE).await() }.getOrNull()
      val cachedExisting = cachedSnap?.takeIf { it.exists() }?.toDomainTransactionOrNull(actor.familyId)
      if (cachedExisting != null) {
        return Result.success(cachedExisting)
      }

      val categories =
        withTimeoutOrNull(1_500L) { familyRepository.observeCategories().first { it.isNotEmpty() } }
          ?: familyRepository.observeCategories().first()
      val members =
        withTimeoutOrNull(1_500L) { familyRepository.observeMembers().first { it.isNotEmpty() } }
          ?: familyRepository.observeMembers().first()

      val normalizedDraft = draft.copy(createdByUid = uid)
      when (val validation = TransactionValidation.validateDraft(normalizedDraft, categories, members)) {
        is ValidationResult.Invalid ->
          return Result.failure(IllegalArgumentException(validation.errors.first().message))
        ValidationResult.Valid -> Unit
      }

      val now = System.currentTimeMillis()
      val eventId = newAuditEventId()
      val created =
        Transaction(
          id = transactionId,
          familyId = actor.familyId,
          type = normalizedDraft.type,
          amountPaise = normalizedDraft.amountPaise,
          title = normalizedDraft.title.trim(),
          note = normalizedDraft.note.trim(),
          categoryId = normalizedDraft.categoryId,
          occurredOn = normalizedDraft.occurredOn,
          paidByUid = normalizedDraft.paidByUid,
          createdByUid = uid,
          status = TransactionStatus.ACTIVE,
          createdAtEpochMillis = now,
          updatedAtEpochMillis = now,
          fundingSource = normalizedDraft.fundingSource,
          paymentMethod = normalizedDraft.paymentMethod,
          expenseClassification =
            if (normalizedDraft.type == TransactionType.EXPENSE) normalizedDraft.expenseClassification
            else ExpenseClassification.NON_SALARY,
          hasPendingSync = true,
        )

      val auditEvent =
        AuditEventFactory.buildCreatedEvent(
          eventId = eventId,
          created = created,
          actorUid = uid,
          occurredAtEpochMillis = now,
          categoriesById = categories.associateBy { it.id },
          membersByUid = members.associateBy { it.uid },
        )

      val currentEpoch =
        withTimeoutOrNull(1_500L) { familyRepository.observeFamily().first() }?.ledgerEpoch ?: 0
      val eventRef = familyRef.collection("auditEvents").document(eventId)
      val batch = db.batch()
      batch.set(
        txRef,
        mapOf(
          "familyId" to created.familyId,
          "type" to created.type.toFirestoreValue(),
          "amountPaise" to created.amountPaise,
          "title" to created.title,
          "note" to created.note,
          "categoryId" to created.categoryId,
          "occurredOn" to created.occurredOn.toString(),
          "paidByUid" to created.paidByUid,
          "createdByUid" to uid,
          "status" to "active",
          "fundingSource" to created.fundingSource.firestoreValue,
          "paymentMethod" to created.paymentMethod.firestoreValue,
          "expenseClassification" to created.expenseClassification.firestoreValue,
          "ledgerEpoch" to currentEpoch.toLong(),
          "lastAuditEventId" to eventId,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        eventRef,
        auditEvent.toFirestoreMap(ledgerEpoch = currentEpoch.toLong()),
      )

      val commitTask = batch.commit()
      commitTask.addOnFailureListener { error ->
        handleFirestoreError(error, OperationType.CREATE, txRef.path, auth)
      }
      withTimeoutOrNull(COMMIT_WAIT_MS) { commitTask.await() }
      if (commitTask.isComplete && !commitTask.isSuccessful) {
        val error = commitTask.exception ?: IllegalStateException("Could not save transaction.")
        return Result.failure(
          IllegalStateException(error.toFriendlyFirestoreMessage("Could not save transaction."))
        )
      }
      Result.success(created)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, txRef.path, auth)
      Result.failure(
        IllegalStateException(e.toFriendlyFirestoreMessage("Could not save transaction."))
      )
    }
  }

  override suspend fun editTransaction(
    transactionId: String,
    draft: TransactionDraft,
  ): Result<Transaction> {
    val uid =
      auth.currentUser?.uid
        ?: return Result.failure(IllegalStateException("Sign in before editing transactions."))
    val actor =
      withTimeoutOrNull(2_000L) { sessionRepository.observeCurrentMember().first { it != null } }
        ?: return Result.failure(IllegalStateException("Active family membership required."))

    val familyRef = db.collection("families").document(actor.familyId)
    val txRef = familyRef.collection("transactions").document(transactionId)

    return try {
      val existing =
        loadTransactionOfflineFirst(txRef, actor.familyId)
          ?: return Result.failure(IllegalStateException("Transaction not found."))

      if (!Permissions.canEditTransaction(actor, existing)) {
        return Result.failure(
          IllegalStateException("You don't have permission to edit this transaction.")
        )
      }
      if (existing.isVoided) {
        return Result.failure(IllegalStateException("Voided transactions cannot be edited."))
      }

      val categories =
        withTimeoutOrNull(1_500L) { familyRepository.observeCategories().first { it.isNotEmpty() } }
          ?: familyRepository.observeCategories().first()
      val members =
        withTimeoutOrNull(1_500L) { familyRepository.observeMembers().first { it.isNotEmpty() } }
          ?: familyRepository.observeMembers().first()

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
      val eventId = newAuditEventId()
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
          hasPendingSync = true,
        )

      val auditEvent =
        AuditEventFactory.buildEditedEvent(
          eventId = eventId,
          before = existing,
          after = updated,
          actorUid = uid,
          occurredAtEpochMillis = now,
          categoriesById = categories.associateBy { it.id },
          membersByUid = members.associateBy { it.uid },
        )

      val currentEpoch =
        withTimeoutOrNull(1_500L) { familyRepository.observeFamily().first() }?.ledgerEpoch ?: 0
      val eventRef = familyRef.collection("auditEvents").document(eventId)
      val batch = db.batch()
      batch.update(
        txRef,
        mapOf(
          "type" to updated.type.toFirestoreValue(),
          "amountPaise" to updated.amountPaise,
          "title" to updated.title,
          "note" to updated.note,
          "categoryId" to updated.categoryId,
          "occurredOn" to updated.occurredOn.toString(),
          "paidByUid" to updated.paidByUid,
          "fundingSource" to updated.fundingSource.firestoreValue,
          "paymentMethod" to updated.paymentMethod.firestoreValue,
          "expenseClassification" to updated.expenseClassification.firestoreValue,
          "lastAuditEventId" to eventId,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(eventRef, auditEvent.toFirestoreMap(ledgerEpoch = currentEpoch.toLong()))

      val commitTask = batch.commit()
      commitTask.addOnFailureListener { error ->
        handleFirestoreError(error, OperationType.UPDATE, txRef.path, auth)
      }
      withTimeoutOrNull(COMMIT_WAIT_MS) { commitTask.await() }
      if (commitTask.isComplete && !commitTask.isSuccessful) {
        val error = commitTask.exception ?: IllegalStateException("Could not update transaction.")
        return Result.failure(
          IllegalStateException(error.toFriendlyFirestoreMessage("Could not update transaction."))
        )
      }
      Result.success(updated)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, txRef.path, auth)
      Result.failure(
        IllegalStateException(e.toFriendlyFirestoreMessage("Could not update transaction."))
      )
    }
  }

  override suspend fun voidTransaction(
    transactionId: String,
    reason: String,
  ): Result<Unit> {
    val uid =
      auth.currentUser?.uid
        ?: return Result.failure(IllegalStateException("Sign in before voiding transactions."))
    val actor =
      withTimeoutOrNull(2_000L) { sessionRepository.observeCurrentMember().first { it != null } }
        ?: return Result.failure(IllegalStateException("Active family membership required."))

    val familyRef = db.collection("families").document(actor.familyId)
    val txRef = familyRef.collection("transactions").document(transactionId)

    return try {
      val existing =
        loadTransactionOfflineFirst(txRef, actor.familyId)
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
      val eventId = newAuditEventId()
      val voided =
        existing.copy(
          status = TransactionStatus.VOIDED,
          voidReason = trimmedReason,
          voidedByUid = uid,
          updatedAtEpochMillis = now,
        )
      val auditEvent =
        AuditEventFactory.buildVoidedEvent(
          eventId = eventId,
          before = existing,
          afterVoided = voided,
          actorUid = uid,
          reason = trimmedReason,
          occurredAtEpochMillis = now,
        )

      val currentEpoch =
        withTimeoutOrNull(1_500L) { familyRepository.observeFamily().first() }?.ledgerEpoch ?: 0
      val eventRef = familyRef.collection("auditEvents").document(eventId)
      val batch = db.batch()
      batch.update(
        txRef,
        mapOf(
          "status" to "voided",
          "voidReason" to trimmedReason,
          "voidedByUid" to uid,
          "lastAuditEventId" to eventId,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(eventRef, auditEvent.toFirestoreMap(ledgerEpoch = currentEpoch.toLong()))

      val commitTask = batch.commit()
      commitTask.addOnFailureListener { error ->
        handleFirestoreError(error, OperationType.UPDATE, txRef.path, auth)
      }
      withTimeoutOrNull(COMMIT_WAIT_MS) { commitTask.await() }
      if (commitTask.isComplete && !commitTask.isSuccessful) {
        val error = commitTask.exception ?: IllegalStateException("Could not void transaction.")
        return Result.failure(
          IllegalStateException(error.toFriendlyFirestoreMessage("Could not void transaction."))
        )
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, txRef.path, auth)
      Result.failure(
        IllegalStateException(e.toFriendlyFirestoreMessage("Could not void transaction."))
      )
    }
  }

  override suspend fun clearAllData(): Result<Unit> {
    auth.currentUser?.uid
      ?: return Result.failure(IllegalStateException("Sign in before clearing family data."))
    val actor =
      withTimeoutOrNull(2_000L) { sessionRepository.observeCurrentMember().first { it != null } }
        ?: return Result.failure(IllegalStateException("Active family membership required."))
    if (!Permissions.canClearAllData(actor)) {
      return Result.failure(IllegalStateException("Only admins can clear all family data."))
    }

    val familyRef = db.collection("families").document(actor.familyId)
    return try {
      familyRef
        .update(
          mapOf(
            "ledgerEpoch" to FieldValue.increment(1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, familyRef.path, auth)
      Result.failure(
        IllegalStateException(e.toFriendlyFirestoreMessage("Could not clear family data."))
      )
    }
  }

  private suspend fun loadTransactionOfflineFirst(
    txRef: com.google.firebase.firestore.DocumentReference,
    familyId: String,
  ): Transaction? {
    val cached =
      runCatching { txRef.get(Source.CACHE).await() }
        .getOrNull()
        ?.takeIf { it.exists() }
        ?.toDomainTransactionOrNull(familyId)
    if (cached != null) return cached
    val serverOrCache =
      withTimeoutOrNull(COMMIT_WAIT_MS) { txRef.get().await() }
        ?.takeIf { it.exists() }
        ?.toDomainTransactionOrNull(familyId)
    return serverOrCache
  }

  private fun newAuditEventId(): String =
    "aud_${UUID.randomUUID().toString().replace("-", "").take(16)}"

  private companion object {
    const val COMMIT_WAIT_MS = 1_500L
  }
}

private fun TransactionType.toFirestoreValue(): String =
  when (this) {
    TransactionType.EXPENSE -> "expense"
    TransactionType.INCOME -> "income"
  }

private fun AuditEvent.toFirestoreMap(ledgerEpoch: Long): Map<String, Any> =
  mapOf(
    "familyId" to familyId,
    "transactionId" to transactionId,
    "action" to
      when (action) {
        AuditAction.CREATED -> "created"
        AuditAction.EDITED -> "edited"
        AuditAction.VOIDED -> "voided"
      },
    "actorUid" to actorUid,
    "summary" to summary,
    "beforeState" to beforeState,
    "afterState" to afterState,
    "ledgerEpoch" to ledgerEpoch,
    "occurredAt" to FieldValue.serverTimestamp(),
  )

internal fun DocumentSnapshot.toDomainTransactionOrNull(fallbackFamilyId: String): Transaction? =
  toDomainTransactionWithEpochOrNull(fallbackFamilyId)?.first

private fun DocumentSnapshot.toDomainTransactionWithEpochOrNull(
  fallbackFamilyId: String,
): Pair<Transaction, Int>? {
  val title = getString("title") ?: return null
  val categoryId = getString("categoryId") ?: return null
  val paidByUid = getString("paidByUid") ?: return null
  val createdByUid = getString("createdByUid") ?: return null
  val amountPaise = getLong("amountPaise") ?: return null
  val occurredOnText = getString("occurredOn") ?: return null
  val occurredOn = runCatching { LocalDate.parse(occurredOnText) }.getOrNull() ?: return null

  val type =
    if (getString("type") == "income") TransactionType.INCOME
    else TransactionType.EXPENSE
  val status =
    if (getString("status") == "voided") TransactionStatus.VOIDED
    else TransactionStatus.ACTIVE
  val createdAt =
    getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      ?.toDate()
      ?.time ?: 0L
  val updatedAt =
    getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      ?.toDate()
      ?.time ?: createdAt
  val ledgerEpoch = (getLong("ledgerEpoch") ?: 0L).toInt()

  val tx =
    Transaction(
      id = id,
      familyId = getString("familyId") ?: fallbackFamilyId,
      type = type,
      amountPaise = amountPaise,
      title = title,
      note = getString("note") ?: "",
      categoryId = categoryId,
      occurredOn = occurredOn,
      paidByUid = paidByUid,
      createdByUid = createdByUid,
      status = status,
      voidReason = getString("voidReason"),
      voidedByUid = getString("voidedByUid"),
      createdAtEpochMillis = createdAt,
      updatedAtEpochMillis = updatedAt,
      fundingSource = FundingSource.fromFirestore(getString("fundingSource")),
      paymentMethod = PaymentMethod.fromFirestore(getString("paymentMethod")),
      expenseClassification = ExpenseClassification.fromFirestore(getString("expenseClassification")),
      hasPendingSync = metadata.hasPendingWrites(),
    )
  return tx to ledgerEpoch
}

internal fun DocumentSnapshot.toDomainAuditEventOrNull(fallbackFamilyId: String): AuditEvent? =
  toDomainAuditEventWithEpochOrNull(fallbackFamilyId)?.first

private fun DocumentSnapshot.toDomainAuditEventWithEpochOrNull(
  fallbackFamilyId: String,
): Pair<AuditEvent, Int>? {
  val transactionId = getString("transactionId") ?: return null
  val actorUid = getString("actorUid") ?: return null
  val summary = getString("summary") ?: return null
  val action =
    when (getString("action")) {
      "edited" -> AuditAction.EDITED
      "voided" -> AuditAction.VOIDED
      else -> AuditAction.CREATED
    }
  val occurredAt =
    getTimestamp("occurredAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      ?.toDate()
      ?.time ?: 0L

  val beforeState = readStringMap("beforeState")
  val afterState = readStringMap("afterState")
  val ledgerEpoch = (getLong("ledgerEpoch") ?: 0L).toInt()

  val event =
    AuditEvent(
      id = id,
      familyId = getString("familyId") ?: fallbackFamilyId,
      transactionId = transactionId,
      action = action,
      actorUid = actorUid,
      occurredAtEpochMillis = occurredAt,
      summary = summary,
      beforeState = beforeState,
      afterState = afterState,
    )
  return event to ledgerEpoch
}

private fun DocumentSnapshot.readStringMap(field: String): Map<String, String> {
  val raw = get(field) as? Map<*, *> ?: return emptyMap()
  return buildMap {
    raw.forEach { (key, value) ->
      if (key is String && value != null) {
        put(key, value.toString())
      }
    }
  }
}
