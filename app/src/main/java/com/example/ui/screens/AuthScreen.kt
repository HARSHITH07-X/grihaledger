package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.domain.rules.InviteRules
import com.example.ui.auth.AuthUiState
import com.example.ui.auth.AuthViewModel
import com.example.ui.i18n.LanguageToggleButton
import com.example.ui.theme.GrihaTheme

@Composable
fun AuthRouteScreen(
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: AuthViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  LaunchedEffect(Unit) {
    viewModel.attemptAutoSignInOnce(context)
  }

  AuthScreen(
    uiState = uiState,
    isDarkTheme = isDarkTheme,
    onToggleTheme = onToggleTheme,
    onToggleJoinInvite = viewModel::toggleJoinInviteExpanded,
    onInviteCodeChange = viewModel::onInviteCodeChanged,
    onDismissError = viewModel::dismissError,
    onGoogleSignInClick = { ctx -> viewModel.onSignInWithGoogleClick(ctx) },
    modifier = modifier,
  )
}

@Composable
fun AuthScreen(
  uiState: AuthUiState,
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  onGoogleSignInClick: (Context) -> Unit,
  modifier: Modifier = Modifier,
  onToggleJoinInvite: () -> Unit = {},
  onInviteCodeChange: (String) -> Unit = {},
  onDismissError: () -> Unit = {},
) {
  val context = LocalContext.current
  val hasValidPendingCode = InviteRules.isValidCodeFormat(uiState.inviteCodeInput)

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("screen_auth"),
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(bottom = innerPadding.calculateBottomPadding())
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Surface(
        color = GrihaTheme.colors.brandForest,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier =
            Modifier
              .widthIn(max = 680.dp)
              .fillMaxWidth()
              .statusBarsPadding()
              .padding(horizontal = 24.dp, vertical = 28.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Surface(
                shape = CircleShape,
                color = GrihaTheme.colors.brandAccent.copy(alpha = 0.2f),
                modifier = Modifier.size(44.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    tint = GrihaTheme.colors.brandAccent,
                    modifier = Modifier.size(24.dp),
                  )
                }
              }
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = GrihaTheme.colors.onBrandForest,
              )
            }

            Row(
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              LanguageToggleButton(
                contentColor = GrihaTheme.colors.onBrandForest,
                testTag = "auth_language_toggle",
              )
              IconButton(
                onClick = onToggleTheme,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("auth_theme_toggle"),
              ) {
                Icon(
                  imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                  contentDescription = stringResource(R.string.theme_toggle_cd),
                  tint = GrihaTheme.colors.onBrandForest,
                )
              }
            }
          }

          Text(
            text = stringResource(R.string.auth_tagline),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = GrihaTheme.colors.onBrandForest,
            modifier = Modifier.semantics { heading() },
          )

          Text(
            text = stringResource(R.string.auth_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = GrihaTheme.colors.onBrandForest.copy(alpha = 0.88f),
          )
        }
      }

      Column(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
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
            AuthFeatureRow(
              icon = Icons.Outlined.Bolt,
              title = stringResource(R.string.auth_feature_fast_title),
              description = stringResource(R.string.auth_feature_fast_desc),
            )
            AuthFeatureRow(
              icon = Icons.Outlined.Group,
              title = stringResource(R.string.auth_feature_roles_title),
              description = stringResource(R.string.auth_feature_roles_desc),
            )
            AuthFeatureRow(
              icon = Icons.Outlined.AutoAwesome,
              title = stringResource(R.string.auth_feature_audit_title),
              description = stringResource(R.string.auth_feature_audit_desc),
            )
          }
        }

        // Optional pre-entry for family members arriving with an invite code
        OutlinedCard(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          border =
            BorderStroke(
              width = 1.dp,
              color =
                if (uiState.isJoinInviteExpanded || hasValidPendingCode) GrihaTheme.colors.good
                else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
              onClick = onToggleJoinInvite,
              color = Color.Transparent,
              modifier =
                Modifier
                  .fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("auth_invite_toggle"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Key,
                  contentDescription = null,
                  tint = GrihaTheme.colors.good,
                  modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = stringResource(R.string.auth_have_invite_code),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text =
                      if (hasValidPendingCode) {
                        stringResource(R.string.auth_code_ready_after_signin, uiState.inviteCodeInput)
                      } else {
                        stringResource(R.string.auth_join_steps_body)
                      },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Icon(
                  imageVector =
                    if (uiState.isJoinInviteExpanded) Icons.Outlined.ExpandLess
                    else Icons.Outlined.ExpandMore,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            AnimatedVisibility(visible = uiState.isJoinInviteExpanded) {
              Column(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = stringResource(R.string.auth_invite_code_helper),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                  value = uiState.inviteCodeInput,
                  onValueChange = onInviteCodeChange,
                  label = { Text(stringResource(R.string.onboarding_invite_code_label)) },
                  placeholder = { Text(stringResource(R.string.onboarding_invite_code_placeholder)) },
                  singleLine = true,
                  enabled = !uiState.isLoading,
                  textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                  modifier = Modifier.fillMaxWidth().testTag("auth_invite_code_input"),
                )
              }
            }
          }
        }

        if (!uiState.errorMessage.isNullOrBlank()) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = GrihaTheme.colors.overBudgetContainer,
            modifier = Modifier.fillMaxWidth().testTag("auth_error_banner"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = GrihaTheme.colors.onOverBudgetContainer,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = onDismissError,
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = stringResource(R.string.action_close),
                  tint = GrihaTheme.colors.onOverBudgetContainer,
                )
              }
            }
          }
        }

        Button(
          onClick = { onGoogleSignInClick(context) },
          enabled = !uiState.isLoading,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = GrihaTheme.colors.good,
              contentColor = GrihaTheme.colors.onGood,
            ),
          shape = MaterialTheme.shapes.medium,
          modifier =
            Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("google_sign_in_button"),
        ) {
          if (uiState.isLoading) {
            CircularProgressIndicator(
              color = GrihaTheme.colors.onGood,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
              text = stringResource(R.string.auth_signing_in),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
          } else {
            Surface(
              shape = CircleShape,
              color = Color.White,
              modifier = Modifier.size(24.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                GoogleMark(modifier = Modifier.size(16.dp))
              }
            }
            Spacer(modifier = Modifier.size(10.dp))
            Text(
              text =
                if (hasValidPendingCode) stringResource(R.string.auth_sign_in_google_and_join)
                else stringResource(R.string.auth_sign_in_google),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = stringResource(R.string.auth_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun GoogleMark(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val strokeWidth = size.minDimension * 0.22f
    val inset = strokeWidth / 2f
    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val topLeft = Offset(inset, inset)

    // Blue top-right & crossbar
    drawArc(
      color = Color(0xFF4285F4),
      startAngle = -38f,
      sweepAngle = 82f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
    )
    // Green bottom
    drawArc(
      color = Color(0xFF34A853),
      startAngle = 44f,
      sweepAngle = 104f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
    )
    // Yellow left
    drawArc(
      color = Color(0xFFFBBC05),
      startAngle = 148f,
      sweepAngle = 68f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
    )
    // Red top
    drawArc(
      color = Color(0xFFEA4335),
      startAngle = 216f,
      sweepAngle = 104f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
    )
    // Crossbar
    drawLine(
      color = Color(0xFF4285F4),
      start = Offset(size.width * 0.50f, size.height * 0.50f),
      end = Offset(size.width - inset * 0.4f, size.height * 0.50f),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Butt,
    )
  }
}

@Composable
private fun AuthFeatureRow(
  icon: ImageVector,
  title: String,
  description: String,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = GrihaTheme.colors.goodContainer,
      modifier = Modifier.size(36.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = GrihaTheme.colors.onGoodContainer,
          modifier = Modifier.size(20.dp),
        )
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
