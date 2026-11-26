package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.domain.model.Transaction
import com.example.ui.screens.AdvisorRouteScreen
import com.example.ui.screens.FamilyRouteScreen
import com.example.ui.screens.HomeRouteScreen
import com.example.ui.screens.InsightsRouteScreen
import com.example.ui.screens.RecordsRouteScreen
import com.example.ui.screens.TransactionDetailRouteScreen

@Composable
fun GrihaNavHost(
  navController: NavHostController,
  onEditTransaction: (Transaction) -> Unit,
  onNavigateTopLevel: (TopLevelDestination) -> Unit,
  modifier: Modifier = Modifier,
) {
  NavHost(
    navController = navController,
    startDestination = HomeRoute,
    modifier = modifier,
  ) {
    composable<HomeRoute> {
      HomeRouteScreen(
        onOpenRecords = { onNavigateTopLevel(TopLevelDestination.RECORDS) },
        onOpenInsights = { onNavigateTopLevel(TopLevelDestination.INSIGHTS) },
        onOpenTransactionDetail = { transactionId ->
          navController.navigate(TransactionDetailRoute(transactionId))
        },
      )
    }
    composable<RecordsRoute> {
      RecordsRouteScreen(
        onTransactionClick = { transactionId ->
          navController.navigate(TransactionDetailRoute(transactionId))
        }
      )
    }
    composable<TransactionDetailRoute> {
      TransactionDetailRouteScreen(
        onBack = { navController.popBackStack() },
        onEditClick = onEditTransaction,
      )
    }
    composable<InsightsRoute> {
      InsightsRouteScreen(
        onOpenRecords = { onNavigateTopLevel(TopLevelDestination.RECORDS) },
      )
    }
    composable<AdvisorRoute> {
      AdvisorRouteScreen(
        onOpenTransactionDetail = { transactionId ->
          navController.navigate(TransactionDetailRoute(transactionId))
        },
        onOpenRecords = { onNavigateTopLevel(TopLevelDestination.RECORDS) },
        onOpenInsights = { onNavigateTopLevel(TopLevelDestination.INSIGHTS) },
      )
    }
    composable<FamilyRoute> { FamilyRouteScreen() }
  }
}
