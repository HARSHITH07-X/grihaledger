package com.example.ui.ledger

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.domain.model.Category

object CategoryVisuals {
  fun iconForKey(iconKey: String): ImageVector =
    when (iconKey) {
      "shopping_basket" -> Icons.Outlined.ShoppingBasket
      "bolt" -> Icons.Outlined.Bolt
      "home" -> Icons.Outlined.Home
      "restaurant" -> Icons.Outlined.Restaurant
      "directions_car" -> Icons.Outlined.DirectionsCar
      "medical_services" -> Icons.Outlined.MedicalServices
      "school" -> Icons.Outlined.School
      "local_mall" -> Icons.Outlined.LocalMall
      "account_balance_wallet" -> Icons.Outlined.AccountBalanceWallet
      "payments" -> Icons.Outlined.Payments
      else -> Icons.Outlined.Category
    }

  fun accentColor(category: Category?): Color =
    category?.let { Color(it.colorHex) } ?: Color(0xFF1E9E6AL)

  fun auditFieldLabel(key: String): String =
    when (key) {
      "type" -> "Type"
      "amount" -> "Amount"
      "title" -> "Title"
      "category" -> "Category"
      "occurredOn" -> "Date"
      "paidBy" -> "Paid by"
      "fundingSource" -> "Paid from"
      "paymentMethod" -> "Method"
      "note" -> "Note"
      "status" -> "Status"
      "voidReason" -> "Void reason"
      else -> key.replaceFirstChar { it.uppercaseChar() }
    }
}
