package com.example.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object RecordsRoute

@Serializable data class TransactionDetailRoute(val transactionId: String)

@Serializable data object InsightsRoute

@Serializable data object AdvisorRoute

@Serializable data object FamilyRoute

enum class TopLevelDestination(
  val route: Any,
  @param:StringRes val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
) {
  HOME(
    route = HomeRoute,
    labelRes = R.string.nav_home,
    selectedIcon = Icons.Filled.Home,
    unselectedIcon = Icons.Outlined.Home,
    testTag = "nav_home",
  ),
  RECORDS(
    route = RecordsRoute,
    labelRes = R.string.nav_records,
    selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
    unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
    testTag = "nav_records",
  ),
  INSIGHTS(
    route = InsightsRoute,
    labelRes = R.string.nav_insights,
    selectedIcon = Icons.Filled.BarChart,
    unselectedIcon = Icons.Outlined.BarChart,
    testTag = "nav_insights",
  ),
  ADVISOR(
    route = AdvisorRoute,
    labelRes = R.string.nav_advisor,
    selectedIcon = Icons.Filled.AutoAwesome,
    unselectedIcon = Icons.Outlined.AutoAwesome,
    testTag = "nav_advisor",
  ),
  FAMILY(
    route = FamilyRoute,
    labelRes = R.string.nav_family,
    selectedIcon = Icons.Filled.Group,
    unselectedIcon = Icons.Outlined.Group,
    testTag = "nav_family",
  ),
}
