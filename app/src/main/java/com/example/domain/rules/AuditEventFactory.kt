package com.example.domain.rules

import com.example.domain.model.AuditAction
import com.example.domain.model.AuditEvent
import com.example.domain.model.Category
import com.example.domain.model.Member
import com.example.domain.model.Money
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType

object AuditEventFactory {

  fun snapshotMap(
    transaction: Transaction,
    categoriesById: Map<String, Category>,
    membersByUid: Map<String, Member>,
  ): Map<String, String> =
    buildMap {
      put("type", if (transaction.type == TransactionType.INCOME) "Income" else "Expense")
      put("amount", Money(transaction.amountPaise).formatEnIn(includePaise = false))
      put("title", transaction.title)
      put("category", categoriesById[transaction.categoryId]?.name ?: transaction.categoryId)
      put("occurredOn", transaction.occurredOn.toString())
      put("paidBy", membersByUid[transaction.paidByUid]?.displayName ?: transaction.paidByUid)
      if (transaction.type == TransactionType.EXPENSE) {
        put("expenseClassification", transaction.expenseClassification.label)
        put("fundingSource", transaction.fundingSource.label)
      }
      put("paymentMethod", transaction.paymentMethod.label)
      if (transaction.note.isNotBlank()) {
        put("note", transaction.note)
      }
      put("status", if (transaction.isVoided) "Voided" else "Active")
      if (!transaction.voidReason.isNullOrBlank()) {
        put("voidReason", transaction.voidReason)
      }
    }

  fun buildCreatedEvent(
    eventId: String,
    created: Transaction,
    actorUid: String,
    occurredAtEpochMillis: Long,
    categoriesById: Map<String, Category>,
    membersByUid: Map<String, Member>,
  ): AuditEvent {
    val amountText = Money(created.amountPaise).formatEnIn(includePaise = false)
    return AuditEvent(
      id = eventId,
      familyId = created.familyId,
      transactionId = created.id,
      action = AuditAction.CREATED,
      actorUid = actorUid,
      occurredAtEpochMillis = occurredAtEpochMillis,
      summary = "Created \"${created.title}\" ($amountText)",
      beforeState = emptyMap(),
      afterState = snapshotMap(created, categoriesById, membersByUid),
    )
  }

  fun buildEditedEvent(
    eventId: String,
    before: Transaction,
    after: Transaction,
    actorUid: String,
    occurredAtEpochMillis: Long,
    categoriesById: Map<String, Category>,
    membersByUid: Map<String, Member>,
  ): AuditEvent {
    val beforeMap = snapshotMap(before, categoriesById, membersByUid)
    val afterMap = snapshotMap(after, categoriesById, membersByUid)
    val changedKeys =
      (beforeMap.keys + afterMap.keys)
        .filter { key -> beforeMap[key] != afterMap[key] }
        .distinct()

    val beforeDiff = changedKeys.associateWith { key -> beforeMap[key] ?: "—" }
    val afterDiff = changedKeys.associateWith { key -> afterMap[key] ?: "—" }

    val summary =
      if (changedKeys.isEmpty()) {
        "Edited \"${after.title}\""
      } else {
        "Edited ${changedKeys.joinToString(", ")} on \"${after.title}\""
      }

    return AuditEvent(
      id = eventId,
      familyId = after.familyId,
      transactionId = after.id,
      action = AuditAction.EDITED,
      actorUid = actorUid,
      occurredAtEpochMillis = occurredAtEpochMillis,
      summary = summary.take(240),
      beforeState = beforeDiff.ifEmpty { beforeMap },
      afterState = afterDiff.ifEmpty { afterMap },
    )
  }

  fun buildVoidedEvent(
    eventId: String,
    before: Transaction,
    afterVoided: Transaction,
    actorUid: String,
    reason: String,
    occurredAtEpochMillis: Long,
  ): AuditEvent {
    val trimmedReason = reason.trim()
    return AuditEvent(
      id = eventId,
      familyId = before.familyId,
      transactionId = before.id,
      action = AuditAction.VOIDED,
      actorUid = actorUid,
      occurredAtEpochMillis = occurredAtEpochMillis,
      summary = "Voided \"${before.title}\": $trimmedReason".take(240),
      beforeState = mapOf("status" to "Active"),
      afterState = mapOf("status" to "Voided", "voidReason" to trimmedReason),
    )
  }
}
