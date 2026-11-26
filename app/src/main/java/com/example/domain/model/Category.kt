package com.example.domain.model

enum class TransactionType {
  EXPENSE,
  INCOME,
}

data class Category(
  val id: String,
  val familyId: String,
  val name: String,
  val type: TransactionType,
  val iconKey: String,
  val colorHex: Long,
  val isDefault: Boolean = true,
  val sortOrder: Int = 0,
)
