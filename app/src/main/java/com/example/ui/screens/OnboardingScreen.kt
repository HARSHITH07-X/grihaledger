package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.AuthUser
import com.example.ui.components.RoleBadge
import com.example.ui.i18n.LanguageToggleButton
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.onboarding.OnboardingMode
import com.example.ui.onboarding.OnboardingUiState
import com.example.ui.onboarding.OnboardingViewModel
import com.example.ui.theme.GrihaTheme

@Composable
fun OnboardingRouteScreen(
  authUser: AuthUser,
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: OnboardingViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  OnboardingScreen(
    authUser = authUser,
    state = state,
    isDarkTheme = isDarkTheme,
    onToggleTheme = onToggleTheme,
    onSelectMode = viewModel::selectMode,
    onFamilyNameChange = viewModel::onFamilyNameChanged,
    onInviteCodeChange = viewModel::onInviteCodeChanged,
    onCreateFamily = viewModel::createFamily,
    onCheckInvite = viewModel::checkInviteCode,
    onConfirmJoin = viewModel::confirmJoinFamily,
    onResetInvite = viewModel::resetVerifiedInvite,
    onSignOut = { viewModel.signOut(context) },
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
  authUser: AuthUser,
  state: OnboardingUiState,
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  onSelectMode: (OnboardingMode) -> Unit,
  onFamilyNameChange: (String) -> Unit,
  onInviteCodeChange: (String) -> Unit,
  onCreateFamily: () -> Unit,
  onCheckInvite: () -> Unit,
  onConfirmJoin: () -> Unit,
  onResetInvite: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize().testTag("screen_onboarding"),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
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
            )
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = GrihaTheme.colors.onBrandForest,
            )
          }
        },
        actions = {
          LanguageToggleButton(
            contentColor = GrihaTheme.colors.onBrandForest,
            testTag = "onboarding_language_toggle",
          )
          IconButton(
            onClick = onToggleTheme,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_theme_toggle"),
          ) {
            Icon(
              imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
              contentDescription = stringResource(R.string.theme_toggle_cd),
              tint = GrihaTheme.colors.onBrandForest,
            )
          }
          IconButton(
            onClick = onSignOut,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_sign_out_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.Logout,
              contentDescription = stringResource(R.string.action_sign_out),
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
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = stringResource(R.string.onboarding_welcome_user, authUser.displayName),
            style = MaterialTheme.typography.labelLarge,
            color = GrihaTheme.colors.good,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
          )
          Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          FilterChip(
            selected = state.mode == OnboardingMode.CREATE,
            onClick = { onSelectMode(OnboardingMode.CREATE) },
            label = { Text(stringResource(R.string.onboarding_tab_create)) },
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
              ),
            modifier =
              Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("onboarding_mode_create"),
          )

          FilterChip(
            selected = state.mode == OnboardingMode.JOIN,
            onClick = { onSelectMode(OnboardingMode.JOIN) },
            label = { Text(stringResource(R.string.onboarding_tab_join)) },
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
              ),
            modifier =
              Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("onboarding_mode_join"),
          )
        }

        if (!state.errorMessage.isNullOrBlank()) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = GrihaTheme.colors.overBudgetContainer,
            modifier = Modifier.fillMaxWidth().testTag("invite_validation_error"),
          ) {
            Text(
              text = state.errorMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = GrihaTheme.colors.onOverBudgetContainer,
              modifier = Modifier.padding(14.dp),
            )
          }
        }

        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          colors =
            CardDefaults.elevatedCardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            if (state.mode == OnboardingMode.CREATE) {
              Text(
                text = stringResource(R.string.onboarding_create_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = stringResource(R.string.onboarding_create_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              OutlinedTextField(
                value = state.familyNameInput,
                onValueChange = onFamilyNameChange,
                label = { Text(stringResource(R.string.onboarding_family_name_label)) },
                placeholder = { Text(stringResource(R.string.onboarding_family_name_placeholder)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("create_family_name_input"),
              )

              Button(
                onClick = onCreateFamily,
                enabled = !state.isSubmitting && state.familyNameInput.isNotBlank(),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = GrihaTheme.colors.good,
                    contentColor = GrihaTheme.colors.onGood,
                  ),
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("create_family_submit_button"),
              ) {
                if (state.isSubmitting) {
                  CircularProgressIndicator(
                    color = GrihaTheme.colors.onGood,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.size(8.dp))
                }
                Text(stringResource(R.string.onboarding_create_cta))
              }
            } else {
              val verifiedInvite = state.verifiedInvite
              if (verifiedInvite == null) {
                Text(
                  text = stringResource(R.string.onboarding_join_heading),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = stringResource(R.string.onboarding_join_desc),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                  value = state.inviteCodeInput,
                  onValueChange = onInviteCodeChange,
                  label = { Text(stringResource(R.string.onboarding_invite_code_label)) },
                  placeholder = { Text(stringResource(R.string.onboarding_invite_code_placeholder)) },
                  singleLine = true,
                  enabled = !state.isSubmitting,
                  textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                  modifier = Modifier.fillMaxWidth().testTag("join_invite_code_input"),
                )

                Button(
                  onClick = onCheckInvite,
                  enabled = !state.isSubmitting && state.inviteCodeInput.isNotBlank(),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = GrihaTheme.colors.good,
                      contentColor = GrihaTheme.colors.onGood,
                    ),
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .minimumInteractiveComponentSize()
                      .testTag("check_invite_button"),
                ) {
                  if (state.isSubmitting) {
                    CircularProgressIndicator(
                      color = GrihaTheme.colors.onGood,
                      strokeWidth = 2.dp,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                  }
                  Text(stringResource(R.string.onboarding_check_code_cta))
                }
              } else {
                Surface(
                  shape = MaterialTheme.shapes.medium,
                  color = GrihaTheme.colors.goodContainer,
                  modifier = Modifier.fillMaxWidth().testTag("join_confirmation_card"),
                ) {
                  Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    val language = LocalAppLanguage.current
                    val localizedRole = LocalizedLabels.role(verifiedInvite.role, language)
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.GroupAdd,
                        contentDescription = null,
                        tint = GrihaTheme.colors.onGoodContainer,
                      )
                      Text(
                        text =
                          stringResource(
                            R.string.onboarding_join_confirm_title,
                            verifiedInvite.familyName,
                            localizedRole,
                          ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GrihaTheme.colors.onGoodContainer,
                      )
                    }
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Text(
                        text = stringResource(R.string.onboarding_assigned_role_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GrihaTheme.colors.onGoodContainer,
                      )
                      RoleBadge(role = verifiedInvite.role)
                    }
                    Text(
                      text =
                        stringResource(
                          R.string.onboarding_join_confirm_desc,
                          verifiedInvite.code,
                          authUser.email,
                          verifiedInvite.familyName,
                        ),
                      style = MaterialTheme.typography.bodySmall,
                      color = GrihaTheme.colors.onGoodContainer,
                    )
                  }
                }

                Button(
                  onClick = onConfirmJoin,
                  enabled = !state.isSubmitting,
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = GrihaTheme.colors.good,
                      contentColor = GrihaTheme.colors.onGood,
                    ),
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .minimumInteractiveComponentSize()
                      .testTag("confirm_join_button"),
                ) {
                  if (state.isSubmitting) {
                    CircularProgressIndicator(
                      color = GrihaTheme.colors.onGood,
                      strokeWidth = 2.dp,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                  }
                  Text(stringResource(R.string.onboarding_join_confirm_cta))
                }

                OutlinedButton(
                  onClick = onResetInvite,
                  enabled = !state.isSubmitting,
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                ) {
                  Text(stringResource(R.string.onboarding_try_another_code))
                }
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.onboarding_signed_in_as, authUser.email),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          TextButton(
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
