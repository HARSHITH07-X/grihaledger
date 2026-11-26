package com.example.domain.repository

import com.example.domain.model.AuditEvent
import com.example.domain.model.Transaction
import com.example.domain.rules.TransactionDraft
import kotlinx.coroutines.flow.Flow

interface LedgerRepository {
  fun observeTransactions(): Flow<List<Transaction>>

  fun observeHasPendingSync(): Flow<Boolean>

  fun observeAuditEvents(): Flow<List<AuditEvent>>

  fun observeAuditEventsForTransaction(transactionId: String): Flow<List<AuditEvent>>

  suspend fun createTransaction(
    transactionId: String,
    draft: TransactionDraft,
  ): Result<Transaction>

  suspend fun editTransaction(
    transactionId: String,
    draft: TransactionDraft,
  ): Result<Transaction>

  suspend fun voidTransaction(
    transactionId: String,
    reason: String,
  ): Result<Unit>

  suspend fun clearAllData(): Result<Unit>
}
