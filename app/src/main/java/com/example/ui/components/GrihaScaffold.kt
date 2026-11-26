package com.example.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.example.BuildConfig
import com.example.R
import com.example.domain.model.Transaction
import com.example.ui.MainUiState
import com.example.ui.i18n.LanguageToggleButton
import com.example.ui.ledger.AddTransactionSheet
import com.example.ui.navigation.TopLevelDestination
import com.example.ui.theme.GrihaTheme

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrihaScaffold(
  uiState: MainUiState,
  currentDestination: NavDestination?,
  isDarkTheme: Boolean,
  snackbarHostState: SnackbarHostState,
  onNavigate: (TopLevelDestination) -> Unit,
  onAddClick: () -> Unit,
  onVoiceAddClick: () -> Unit = {},
  onDismissAddSheet: () -> Unit,
  onTransactionSaved: (Transaction, Boolean) -> Unit,
  onSelectDebugMember: (String) -> Unit,
  onToggleTheme: () -> Unit,
  showDebugSwitcher: Boolean = BuildConfig.DEBUG,
  content: @Composable (Modifier) -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWideScreen = maxWidth >= 600.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (isWideScreen) {
        GrihaNavigationRail(
          currentDestination = currentDestination,
          showAddFab = uiState.canCreateTransaction,
          onNavigate = onNavigate,
          onAddClick = onAddClick,
          onVoiceAddClick = onVoiceAddClick,
        )
      }

      Scaffold(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
          Column {
            TopAppBar(
              title = {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    tint = GrihaTheme.colors.brandAccent,
                    modifier = Modifier.size(24.dp),
                  )
                  Column {
                    Text(
                      text = stringResource(R.string.app_name),
                      style = MaterialTheme.typography.titleLarge,
                      fontWeight = FontWeight.Bold,
                      color = GrihaTheme.colors.onBrandForest,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    uiState.family?.let { family ->
                      Text(
                        text = family.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = GrihaTheme.colors.onBrandForest.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                }
              },
              actions = {
                if (uiState.canCreateTransaction) {
                  IconButton(
                    onClick = onVoiceAddClick,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("voice_add_fab"),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Mic,
                      contentDescription = stringResource(R.string.voice_entry_fab_cd),
                      tint = GrihaTheme.colors.brandAccent,
                    )
                  }
                }
                LanguageToggleButton(
                  contentColor = GrihaTheme.colors.onBrandForest,
                  testTag = "language_toggle_button",
                )
                uiState.currentMember?.let { member ->
                  RoleBadge(
                    role = member.role,
                    modifier = Modifier.padding(horizontal = 4.dp),
                  )
                }
                IconButton(
                  onClick = onToggleTheme,
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("theme_toggle_button"),
                ) {
                  Icon(
                    imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = stringResource(R.string.theme_toggle_cd),
                    tint = GrihaTheme.colors.onBrandForest,
                  )
                }
              },
              colors =
                TopAppBarDefaults.topAppBarColors(
                  containerColor = GrihaTheme.colors.brandForest,
                  titleContentColor = GrihaTheme.colors.onBrandForest,
                ),
            )

            if (showDebugSwitcher) {
              DebugUserSwitcherBar(
                members = uiState.debugMembers,
                currentMember = uiState.currentMember,
                onSelectMember = onSelectDebugMember,
              )
            }
          }
        },
        bottomBar = {
          if (!isWideScreen) {
            GrihaBottomNavigationBar(
              currentDestination = currentDestination,
              onNavigate = onNavigate,
            )
          }
        },
        floatingActionButton = {
          if (!isWideScreen && uiState.canCreateTransaction) {
            AddTransactionFab(onClick = onAddClick)
          }
        },
        floatingActionButtonPosition = FabPosition.Center,
      ) { innerPadding ->
        content(Modifier.fillMaxSize().padding(innerPadding))
      }
    }

    if (uiState.isAddSheetOpen) {
      AddTransactionSheet(
        editingTransaction = uiState.editingTransaction,
        autoStartVoice = uiState.autoStartVoiceOnAdd,
        onDismiss = onDismissAddSheet,
        onSaved = onTransactionSaved,
      )
    }
  }
}

@Composable
private fun GrihaBottomNavigationBar(
  currentDestination: NavDestination?,
  onNavigate: (TopLevelDestination) -> Unit,
) {
  NavigationBar(
    modifier = Modifier.testTag("bottom_navigation_bar"),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    tonalElevation = 3.dp,
  ) {
    TopLevelDestination.entries.forEach { destination ->
      val selected = currentDestination.isDestinationSelected(destination)
      val label = stringResource(destination.labelRes)
      NavigationBarItem(
        selected = selected,
        onClick = { onNavigate(destination) },
        icon = {
          Icon(
            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = label,
          )
        },
        label = {
          Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
          )
        },
        alwaysShowLabel = true,
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        modifier = Modifier.testTag(destination.testTag),
      )
    }
  }
}

@Composable
private fun GrihaNavigationRail(
  currentDestination: NavDestination?,
  showAddFab: Boolean,
  onNavigate: (TopLevelDestination) -> Unit,
  onAddClick: () -> Unit,
  onVoiceAddClick: () -> Unit,
) {
  NavigationRail(
    modifier = Modifier.fillMaxHeight().testTag("navigation_rail"),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    header = {
      if (showAddFab) {
        Spacer(modifier = Modifier.height(12.dp))
        AddTransactionFab(onClick = onAddClick)
        Spacer(modifier = Modifier.height(8.dp))
        SmallFloatingActionButton(
          onClick = onVoiceAddClick,
          containerColor = GrihaTheme.colors.brandForest,
          contentColor = GrihaTheme.colors.onBrandForest,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("voice_add_rail_fab"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Mic,
            contentDescription = stringResource(R.string.voice_entry_fab_cd),
          )
        }
        Spacer(modifier = Modifier.height(12.dp))
      }
    },
  ) {
    TopLevelDestination.entries.forEach { destination ->
      val selected = currentDestination.isDestinationSelected(destination)
      val label = stringResource(destination.labelRes)
      NavigationRailItem(
        selected = selected,
        onClick = { onNavigate(destination) },
        icon = {
          Icon(
            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = label,
          )
        },
        label = {
          Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelMedium,
          )
        },
        alwaysShowLabel = true,
        colors =
          NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        modifier = Modifier.testTag(destination.testTag),
      )
    }
  }
}

@Composable
private fun AddTransactionFab(
  onClick: () -> Unit,
) {
  FloatingActionButton(
    onClick = onClick,
    containerColor = GrihaTheme.colors.good,
    contentColor = GrihaTheme.colors.onGood,
    modifier = Modifier.minimumInteractiveComponentSize().testTag("add_fab"),
  ) {
    Icon(
      imageVector = Icons.Filled.Add,
      contentDescription = stringResource(R.string.action_add_expense),
    )
  }
}

private fun NavDestination?.isDestinationSelected(destination: TopLevelDestination): Boolean =
  this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
