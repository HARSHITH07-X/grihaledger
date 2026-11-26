package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.Money
import com.example.domain.model.Transaction
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.SessionGate
import com.example.ui.components.GrihaScaffold
import com.example.ui.i18n.ProvideAppLanguage
import com.example.ui.navigation.GrihaNavHost
import com.example.ui.navigation.TopLevelDestination
import com.example.ui.screens.AuthRouteScreen
import com.example.ui.screens.OnboardingRouteScreen
import com.example.ui.theme.GrihaLedgerTheme
import com.example.ui.theme.GrihaTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val APP_CHECK_TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

fun configureAppCheck(context: Context, intent: Intent?) {
  try {
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }

    if (BuildConfig.DEBUG) {
      val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
      if (!debugToken.isNullOrEmpty()) {
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs =
          context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE,
          )
        prefs.edit().putString(DEBUG_SECRET_KEY, debugToken).commit()
        Log.d(APP_CHECK_TAG, "App Check debug secret configured successfully.")
      } else {
        Log.w(APP_CHECK_TAG, "No debug token found in Intent extra.")
      }
      FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
      Log.d(APP_CHECK_TAG, "DebugAppCheckProviderFactory installed.")
    } else {
      FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
      Log.d(APP_CHECK_TAG, "PlayIntegrityAppCheckProviderFactory installed.")
    }
  } catch (e: Exception) {
    Log.e(APP_CHECK_TAG, "Failed to initialize Firebase App Check", e)
  }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge()
    setContent { GrihaLedgerRoot() }
  }
}

@Composable
fun GrihaLedgerRoot(
  mainViewModel: MainViewModel = hiltViewModel(),
) {
  val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
  val appLanguage by mainViewModel.appLanguage.collectAsStateWithLifecycle()
  val isDarkTheme = uiState.darkThemeOverride ?: isSystemInDarkTheme()
  val context = LocalContext.current

  ProvideAppLanguage(
    language = appLanguage,
    onToggleLanguage = mainViewModel::toggleLanguage,
    onSetLanguage = mainViewModel::setLanguage,
  ) {
    GrihaLedgerTheme(darkTheme = isDarkTheme) {
      when (val gate = uiState.sessionGate) {
        SessionGate.SignedOut -> {
          AuthRouteScreen(
            isDarkTheme = isDarkTheme,
            onToggleTheme = { mainViewModel.toggleDarkTheme(isDarkTheme) },
          )
        }

        is SessionGate.Loading -> {
          WorkspaceLoadingScreen(
            hasAuthUser = gate.authUser != null,
            onSignOut = { mainViewModel.signOut(context) },
          )
        }

        is SessionGate.NeedsOnboarding -> {
          key(gate.authUser.uid) {
            OnboardingRouteScreen(
              authUser = gate.authUser,
              isDarkTheme = isDarkTheme,
              onToggleTheme = { mainViewModel.toggleDarkTheme(isDarkTheme) },
            )
          }
        }

        is SessionGate.WorkspaceUnavailable -> {
          WorkspaceUnavailableScreen(
            onReturnToOnboarding = mainViewModel::returnToOnboarding,
            onSignOut = { mainViewModel.signOut(context) },
          )
        }

        is SessionGate.WorkspaceReady -> {
          key(gate.authUser.uid) {
            GrihaLedgerShell(
              uiState = uiState,
              isDarkTheme = isDarkTheme,
              onSelectDebugMember = mainViewModel::switchDebugMember,
              onOpenAddSheet = mainViewModel::openAddSheet,
              onOpenVoiceAddSheet = mainViewModel::openVoiceAddSheet,
              onOpenEditSheet = mainViewModel::openEditSheet,
              onDismissAddSheet = mainViewModel::dismissAddSheet,
              onUndoCreatedTransaction = mainViewModel::undoCreatedTransaction,
              onToggleTheme = { mainViewModel.toggleDarkTheme(isDarkTheme) },
            )
          }
        }
      }
    }
  }
}

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
  navigate(destination.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
  }
}

@Composable
fun GrihaLedgerShell(
  uiState: MainUiState,
  isDarkTheme: Boolean,
  onSelectDebugMember: (String) -> Unit,
  onOpenAddSheet: () -> Unit,
  onOpenVoiceAddSheet: () -> Unit = {},
  onOpenEditSheet: (Transaction) -> Unit,
  onDismissAddSheet: () -> Unit,
  onUndoCreatedTransaction: (String, String, (String) -> Unit) -> Unit,
  onToggleTheme: () -> Unit,
  showDebugSwitcher: Boolean = BuildConfig.DEBUG,
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val localizedContext = LocalContext.current

  GrihaScaffold(
    uiState = uiState,
    currentDestination = navBackStackEntry?.destination,
    isDarkTheme = isDarkTheme,
    snackbarHostState = snackbarHostState,
    onNavigate = { destination: TopLevelDestination ->
      navController.navigateTopLevel(destination)
    },
    onAddClick = onOpenAddSheet,
    onVoiceAddClick = onOpenVoiceAddSheet,
    onDismissAddSheet = onDismissAddSheet,
    onTransactionSaved = { savedTx, wasEdit ->
      onDismissAddSheet()
      scope.launch {
        snackbarHostState.currentSnackbarData?.dismiss()
        val amountLabel =
          Money(savedTx.amountPaise).formatEnIn(includePaise = savedTx.amountPaise % 100L != 0L)
        if (wasEdit) {
          snackbarHostState.showSnackbar(
            message =
              localizedContext.getString(R.string.tx_updated_snackbar, savedTx.title, amountLabel),
            duration = SnackbarDuration.Short,
          )
        } else {
          val undoLabel = localizedContext.getString(R.string.action_undo)
          val undoDoneMessage = localizedContext.getString(R.string.tx_undo_success_snackbar)
          val result =
            snackbarHostState.showSnackbar(
              message =
                localizedContext.getString(R.string.tx_saved_snackbar, savedTx.title, amountLabel),
              actionLabel = undoLabel,
              duration = SnackbarDuration.Short,
            )
          if (result == SnackbarResult.ActionPerformed) {
            onUndoCreatedTransaction(savedTx.id, undoDoneMessage) { undoMessage ->
              scope.launch {
                snackbarHostState.showSnackbar(undoMessage, duration = SnackbarDuration.Short)
              }
            }
          }
        }
      }
    },
    onSelectDebugMember = onSelectDebugMember,
    onToggleTheme = onToggleTheme,
    showDebugSwitcher = showDebugSwitcher,
  ) { contentModifier ->
    GrihaNavHost(
      navController = navController,
      onEditTransaction = onOpenEditSheet,
      onNavigateTopLevel = { destination -> navController.navigateTopLevel(destination) },
      modifier = contentModifier,
    )
  }
}

@Composable
private fun WorkspaceLoadingScreen(
  hasAuthUser: Boolean,
  onSignOut: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxSize().testTag("screen_loading"),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp),
      ) {
        CircularProgressIndicator(color = GrihaTheme.colors.good)
        Text(
          text = stringResource(R.string.workspace_opening),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        if (hasAuthUser) {
          OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Text(stringResource(R.string.action_sign_out))
          }
        }
      }
    }
  }
}

@Composable
private fun WorkspaceUnavailableScreen(
  onReturnToOnboarding: () -> Unit,
  onSignOut: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxSize().testTag("screen_workspace_unavailable"),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.GroupOff,
          contentDescription = null,
          tint = GrihaTheme.colors.overBudget,
          modifier = Modifier.size(48.dp),
        )
        Text(
          text = stringResource(R.string.workspace_unavailable_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
        )
        Text(
          text = stringResource(R.string.workspace_unavailable_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Button(
          onClick = onReturnToOnboarding,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = GrihaTheme.colors.good,
              contentColor = GrihaTheme.colors.onGood,
            ),
          modifier =
            Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("return_to_onboarding_button"),
        ) {
          Text(stringResource(R.string.workspace_unavailable_cta))
        }
        OutlinedButton(
          onClick = onSignOut,
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
        ) {
          Text(stringResource(R.string.action_sign_out))
        }
      }
    }
  }
}
