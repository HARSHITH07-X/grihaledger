package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.Invite
import com.example.domain.model.Member
import com.example.domain.model.Role
import com.example.ui.components.RoleBadge
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.GrihaTheme
import java.util.concurrent.TimeUnit

@Composable
fun FamilyInvitesCard(
  selectedRole: Role,
  pendingInvites: List<Invite>,
  isBusy: Boolean,
  onSelectRole: (Role) -> Unit,
  onGenerateInvite: () -> Unit,
  onRevokeInvite: (String) -> Unit,
  onCopiedCode: (String) -> Unit,
) {
  val context = LocalContext.current
  val language = LocalAppLanguage.current

  ElevatedCard(
    modifier = Modifier.fillMaxWidth().testTag("pending_invites_section"),
    shape = MaterialTheme.shapes.large,
    colors =
      CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      ),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.PersonAdd,
          contentDescription = null,
          tint = GrihaTheme.colors.good,
        )
        Text(
          text = stringResource(R.string.family_invites_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      Text(
        text = stringResource(R.string.family_invites_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        listOf(Role.MEMBER, Role.VIEWER).forEach { roleOption ->
          val selected = selectedRole == roleOption
          FilterChip(
            selected = selected,
            onClick = { onSelectRole(roleOption) },
            label = { Text(LocalizedLabels.role(roleOption, language)) },
            leadingIcon =
              if (selected) {
                {
                  Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                }
              } else null,
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
                .testTag("invite_role_${roleOption.name.lowercase()}"),
          )
        }
      }

      Button(
        onClick = onGenerateInvite,
        enabled = !isBusy,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = GrihaTheme.colors.good,
            contentColor = GrihaTheme.colors.onGood,
          ),
        modifier =
          Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("generate_invite_button"),
      ) {
        if (isBusy) {
          CircularProgressIndicator(
            color = GrihaTheme.colors.onGood,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(8.dp))
        }
        Text(stringResource(R.string.family_generate_invite_cta))
      }

      Text(
        text = stringResource(R.string.family_pending_invites_title),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (pendingInvites.isEmpty()) {
        Text(
          text = stringResource(R.string.family_no_pending_invites),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          pendingInvites.forEach { invite ->
            PendingInviteRow(
              invite = invite,
              isBusy = isBusy,
              onCopy = {
                copyInviteCode(context, invite.code)
                onCopiedCode(invite.code)
              },
              onShare = { shareInvite(context, invite, language) },
              onRevoke = { onRevokeInvite(invite.code) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PendingInviteRow(
  invite: Invite,
  isBusy: Boolean,
  onCopy: () -> Unit,
  onShare: () -> Unit,
  onRevoke: () -> Unit,
) {
  val remainingHours =
    ((invite.expiresAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L))
      .let { TimeUnit.MILLISECONDS.toHours(it).coerceAtLeast(1L) }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth().testTag("pending_invite_row_${invite.code}"),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = invite.code,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = stringResource(R.string.family_invite_expires_meta, remainingHours.toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        RoleBadge(role = invite.role)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onShare,
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("share_invite_${invite.code}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.size(6.dp))
          Text(stringResource(R.string.action_share))
        }

        IconButton(
          onClick = onCopy,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_invite_${invite.code}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = stringResource(R.string.action_copy),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        TextButton(
          onClick = onRevoke,
          enabled = !isBusy,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("revoke_invite_${invite.code}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(4.dp))
          Text(
            text = stringResource(R.string.action_revoke),
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

@Composable
fun ChangeMemberRoleDialog(
  member: Member,
  allowedRoles: List<Role>,
  onDismiss: () -> Unit,
  onConfirmRole: (Role) -> Unit,
) {
  val language = LocalAppLanguage.current
  var chosenRole by remember(member.uid) {
    mutableStateOf(allowedRoles.firstOrNull() ?: member.role)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.family_role_dialog_title, member.displayName)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.family_role_dialog_subtitle, member.email),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        allowedRoles.forEach { role ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = chosenRole == role,
              onClick = { chosenRole = role },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("role_option_${role.name.lowercase()}"),
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
              Text(
                text = LocalizedLabels.role(role, language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = roleDescription(role),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmRole(chosenRole) },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("confirm_role_change_button"),
      ) {
        Text(stringResource(R.string.family_save_role_cta))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    },
  )
}

@Composable
fun ConfirmRemoveMemberDialog(
  member: Member,
  isSelf: Boolean,
  onDismiss: () -> Unit,
  onConfirmRemove: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        if (isSelf) stringResource(R.string.family_leave_dialog_title)
        else stringResource(R.string.family_remove_dialog_title, member.displayName)
      )
    },
    text = {
      Text(
        if (isSelf) {
          stringResource(R.string.family_leave_dialog_body)
        } else {
          stringResource(R.string.family_remove_dialog_body, member.displayName, member.email)
        }
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirmRemove,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("confirm_remove_member_button"),
      ) {
        Text(
          stringResource(
            if (isSelf) R.string.family_leave_cta else R.string.family_remove_cta
          )
        )
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    },
  )
}

@Composable
fun AdminSettingsClearDataCard(
  isBusy: Boolean,
  onClearAllDataClick: () -> Unit,
) {
  ElevatedCard(
    modifier = Modifier.fillMaxWidth().testTag("family_admin_settings_card"),
    shape = MaterialTheme.shapes.large,
    colors =
      CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      ),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.DeleteSweep,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
        )
        Text(
          text = stringResource(R.string.settings_admin_section_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      Text(
        text = stringResource(R.string.settings_admin_section_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedButton(
        onClick = onClearAllDataClick,
        enabled = !isBusy,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
        colors =
          ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
          ),
        modifier =
          Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("clear_all_data_button"),
      ) {
        if (isBusy) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.error,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(8.dp))
        } else {
          Icon(
            imageVector = Icons.Outlined.DeleteSweep,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(8.dp))
        }
        Text(
          text = stringResource(R.string.settings_clear_all_data_cta),
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

@Composable
fun ConfirmClearAllDataDialog(
  familyName: String,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onConfirmClear: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = { if (!isBusy) onDismiss() },
    icon = {
      Icon(
        imageVector = Icons.Outlined.WarningAmber,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
      )
    },
    title = { Text(stringResource(R.string.settings_clear_data_dialog_title)) },
    text = {
      Text(
        text = stringResource(R.string.settings_clear_data_dialog_body, familyName),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirmClear,
        enabled = !isBusy,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        modifier =
          Modifier
            .minimumInteractiveComponentSize()
            .testTag("confirm_clear_all_data_button"),
      ) {
        Text(stringResource(R.string.settings_clear_data_confirm_cta))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isBusy,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("cancel_clear_all_data_button"),
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    },
    modifier = Modifier.testTag("confirm_clear_data_dialog"),
  )
}

@Composable
private fun roleDescription(role: Role): String =
  when (role) {
    Role.ADMIN -> stringResource(R.string.role_desc_admin)
    Role.MEMBER -> stringResource(R.string.role_desc_member)
    Role.VIEWER -> stringResource(R.string.role_desc_viewer)
  }

private fun copyInviteCode(context: Context, code: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
  clipboard?.setPrimaryClip(ClipData.newPlainText("GrihaLedger Invite Code", code))
}

private fun shareInvite(context: Context, invite: Invite, language: AppLanguage) {
  val roleName = LocalizedLabels.role(invite.role, language)
  val message =
    if (language == AppLanguage.HINDI) {
      "GrihaLedger पर \"${invite.familyName}\" परिवार में $roleName के रूप में जुड़ें।\n\n" +
        "आमंत्रण कोड: ${invite.code}\n" +
        "(48 घंटों के लिए मान्य, केवल एक बार उपयोग)\n\n" +
        "GrihaLedger खोलें → कोड से जुड़ें और ${invite.code} दर्ज करें।"
    } else {
      "Join \"${invite.familyName}\" on GrihaLedger as $roleName.\n\n" +
        "Invite code: ${invite.code}\n" +
        "(Valid for 48 hours, single-use)\n\n" +
        "Open GrihaLedger → Join with a code and enter ${invite.code}."
    }
  val subject =
    if (language == AppLanguage.HINDI) "${invite.familyName} (GrihaLedger) में शामिल हों"
    else "Join ${invite.familyName} on GrihaLedger"
  val chooserTitle =
    if (language == AppLanguage.HINDI) "परिवार आमंत्रण कोड शेयर करें"
    else "Share family invite code"
  val sendIntent =
    Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, subject)
      putExtra(Intent.EXTRA_TEXT, message)
    }
  val chooser = Intent.createChooser(sendIntent, chooserTitle)
  context.startActivity(chooser)
}
