package com.example.domain.rules

import com.example.domain.model.Transaction
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class DuplicateWarning(
  val matchingTransactions: List<Transaction>,
  val summary: String,
)

/**
 * Pure duplicate detection: warns the user about likely duplicate entries without ever blocking.
 * Voided transactions are never treated as active duplicates.
 */
object DuplicateDetector {
  const val DEFAULT_DAY_WINDOW: Long = 1L

  fun detectPotentialDuplicates(
    draft: TransactionDraft,
    existingTransactions: List<Transaction>,
    excludeTransactionId: String? = null,
    dayWindow: Long = DEFAULT_DAY_WINDOW,
  ): DuplicateWarning? {
    val matches =
      existingTransactions.filter { existing ->
        if (existing.id == excludeTransactionId) return@filter false
        if (existing.isVoided) return@filter false
        if (existing.familyId != draft.familyId) return@filter false
        if (existing.type != draft.type) return@filter false
        if (existing.amountPaise != draft.amountPaise) return@filter false
        if (existing.categoryId != draft.categoryId) return@filter false

        val dayDiff = abs(ChronoUnit.DAYS.between(existing.occurredOn, draft.occurredOn))
        dayDiff <= dayWindow
      }

    if (matches.isEmpty()) return null

    val latest = matches.maxBy { it.createdAtEpochMillis }
    val summary =
      if (matches.size == 1) {
        "Looks similar to \"${latest.title}\" on ${latest.occurredOn}. You can still save if this is intentional."
      } else {
        "Found ${matches.size} similar entries around ${draft.occurredOn}. You can still save if intentional."
      }
    return DuplicateWarning(matchingTransactions = matches, summary = summary)
  }
}
