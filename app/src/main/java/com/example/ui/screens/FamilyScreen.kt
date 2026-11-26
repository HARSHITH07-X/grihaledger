package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.Family
import com.example.domain.model.Invite
import com.example.domain.model.Member
import com.example.domain.model.Role
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.InviteRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.rules.LedgerAction
import com.example.domain.rules.Permissions
import com.example.ui.auth.GoogleCredentialHelper
import com.example.ui.components.RoleBadge
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalSetAppLanguage
import com.example.ui.theme.GrihaTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class MemberDirectoryRow(
  val member: Member,
  val isCurrentUser: Boolean,
  val allowedTargetRoles: List<Role>,
  val canPromoteOrChangeRole: Boolean,
  val canRemove: Boolean,
  val isLastAdminProtected: Boolean,
)

@Immutable
data class FamilyUiState(
  val family: Family? = null,
  val currentMember: Member? = null,
  val memberRows: List<MemberDirectoryRow> = emptyList(),
  val pendingInvites: List<Invite> = emptyList(),
  val selectedInviteRole: Role = Role.MEMBER,
  val canInviteMembers: Boolean = false,
  val canManageCategories: Boolean = false,
  val canClearAllData: Boolean = false,
  val activeAdminCount: Int = 0,
  val isBusy: Boolean = false,
  val feedbackMessage: String? = null,
  val feedbackMessageRes: Int? = null,
  val isErrorFeedback: Boolean = false,
)

private data class FamilyFeedback(
  val message: String? = null,
  val messageRes: Int? = null,
  val isError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FamilyViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  sessionRepository: SessionRepository,
  private val familyRepository: FamilyRepository,
  private val inviteRepository: InviteRepository,
  private val ledgerRepository: LedgerRepository,
) : ViewModel() {

  private val selectedInviteRole = MutableStateFlow(Role.MEMBER)
  private val isBusy = MutableStateFlow(false)
  private val feedback = MutableStateFlow<FamilyFeedback?>(null)

  private val pendingInvitesFlow =
    combine(
        familyRepository.observeFamily(),
        sessionRepository.observeCurrentMember(),
      ) { family, actor ->
        if (family != null && actor?.isActive == true && actor.role == Role.ADMIN) family.id
        else null
      }
      .distinctUntilChanged()
      .flatMapLatest { adminFamilyId ->
        if (adminFamilyId == null) flowOf(emptyList())
        else inviteRepository.observePendingInvites(adminFamilyId)
      }

  private val workspaceFlow =
    combine(
      familyRepository.observeFamily(),
      sessionRepository.observeCurrentMember(),
      familyRepository.observeMembers(),
      pendingInvitesFlow,
    ) { family, actor, members, pendingInvites ->
      QuadWorkspace(family, actor, members, pendingInvites)
    }

  val uiState: StateFlow<FamilyUiState> =
    combine(
        workspaceFlow,
        selectedInviteRole,
        isBusy,
        feedback,
      ) { workspace, inviteRole, busy, feedbackItem ->
        val family = workspace.family
        val actor = workspace.actor
        val activeMembers = workspace.members.filter { it.isActive }
        val activeAdminCount = activeMembers.count { it.role == Role.ADMIN }

        val rows =
          activeMembers.map { target ->
            val allowedRoles =
              if (actor == null) emptyList()
              else
                Role.entries.filter { candidate ->
                  Permissions.canChangeMemberRole(
                    actor = actor,
                    target = target,
                    newRole = candidate,
                    activeAdminCount = activeAdminCount,
                  )
                }
            val canRemove =
              actor != null &&
                Permissions.canRemoveMember(
                  actor = actor,
                  target = target,
                  activeAdminCount = activeAdminCount,
                )
            MemberDirectoryRow(
              member = target,
              isCurrentUser = actor?.uid == target.uid,
              allowedTargetRoles = allowedRoles,
              canPromoteOrChangeRole = allowedRoles.isNotEmpty(),
              canRemove = canRemove,
              isLastAdminProtected = target.role == Role.ADMIN && activeAdminCount <= 1,
            )
          }

        FamilyUiState(
          family = family,
          currentMember = actor,
          memberRows = rows,
          pendingInvites = workspace.pendingInvites,
          selectedInviteRole = inviteRole,
          canInviteMembers =
            actor?.let { Permissions.isAllowed(it.role, LedgerAction.CREATE_INVITE) } ?: false,
          canManageCategories =
            actor?.let { Permissions.isAllowed(it.role, LedgerAction.MANAGE_CATEGORIES) } ?: false,
          canClearAllData = actor?.let { Permissions.canClearAllData(it) } ?: false,
          activeAdminCount = activeAdminCount,
          isBusy = busy,
          feedbackMessage = feedbackItem?.message,
          feedbackMessageRes = feedbackItem?.messageRes,
          isErrorFeedback = feedbackItem?.isError ?: false,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = FamilyUiState(),
      )

  fun selectInviteRole(role: Role) {
    if (role == Role.MEMBER || role == Role.VIEWER) {
      selectedInviteRole.value = role
    }
  }

  fun generateInvite() {
    val family = uiState.value.family ?: return
    if (isBusy.value) return
    viewModelScope.launch {
      isBusy.value = true
      feedback.value = null
      val result =
        inviteRepository.createInvite(
          familyId = family.id,
          familyName = family.name,
          role = selectedInviteRole.value,
        )
      result.fold(
        onSuccess = { invite ->
          feedback.value =
            FamilyFeedback(message = "Invite ${invite.code} created for ${invite.role.label}.", isError = false)
        },
        onFailure = { error ->
          feedback.value =
            FamilyFeedback(
              message = error.localizedMessage ?: "Could not create invite code.",
              isError = true,
            )
        },
      )
      isBusy.value = false
    }
  }

  fun revokeInvite(code: String) {
    if (isBusy.value) return
    viewModelScope.launch {
      isBusy.value = true
      feedback.value = null
      val result = inviteRepository.revokeInvite(code)
      result.fold(
        onSuccess = { feedback.value = FamilyFeedback(message = "Invite $code revoked.", isError = false) },
        onFailure = { error ->
          feedback.value =
            FamilyFeedback(message = error.localizedMessage ?: "Could not revoke invite.", isError = true)
        },
      )
      isBusy.value = false
    }
  }

  fun changeMemberRole(targetUid: String, newRole: Role) {
    val familyId = uiState.value.family?.id ?: return
    if (isBusy.value) return
    viewModelScope.launch {
      isBusy.value = true
      feedback.value = null
      val result = familyRepository.changeMemberRole(familyId, targetUid, newRole)
      result.fold(
        onSuccess = {
          feedback.value = FamilyFeedback(message = "Role updated to ${newRole.label}.", isError = false)
        },
        onFailure = { error ->
          feedback.value =
            FamilyFeedback(
              message = error.localizedMessage ?: "Could not update member role.",
              isError = true,
            )
        },
      )
      isBusy.value = false
    }
  }

  fun removeMember(targetUid: String) {
    val familyId = uiState.value.family?.id ?: return
    if (isBusy.value) return
    viewModelScope.launch {
      isBusy.value = true
      feedback.value = null
      val result = familyRepository.removeMember(familyId, targetUid)
      result.fold(
        onSuccess = {
          feedback.value = FamilyFeedback(message = "Member removed from family.", isError = false)
        },
        onFailure = { error ->
          feedback.value =
            FamilyFeedback(
              message = error.localizedMessage ?: "Could not remove member.",
              isError = true,
            )
        },
      )
      isBusy.value = false
    }
  }

  fun clearAllData() {
    val actor = uiState.value.currentMember ?: return
    if (!Permissions.canClearAllData(actor)) {
      feedback.value =
        FamilyFeedback(messageRes = R.string.settings_clear_data_error, isError = true)
      return
    }
    if (isBusy.value) return
    viewModelScope.launch {
      isBusy.value = true
      feedback.value = null
      val result = ledgerRepository.clearAllData()
      result.fold(
        onSuccess = {
          feedback.value =
            FamilyFeedback(messageRes = R.string.settings_clear_data_success, isError = false)
        },
        onFailure = { error ->
          val msg = error.localizedMessage
          feedback.value =
            if (!msg.isNullOrBlank()) {
              FamilyFeedback(message = msg, isError = true)
            } else {
              FamilyFeedback(messageRes = R.string.settings_clear_data_error, isError = true)
            }
        },
      )
      isBusy.value = false
    }
  }

  fun showCopiedFeedback(code: String) {
    feedback.value = FamilyFeedback(message = "Copied invite code $code.", isError = false)
  }

  fun dismissFeedback() {
    feedback.value = null
  }

  fun signOut(context: Context) {
    viewModelScope.launch {
      GoogleCredentialHelper.clearCredentialState(context)
      authRepository.signOut()
    }
  }
}

private data class QuadWorkspace(
  val family: Family?,
  val actor: Member?,
  val members: List<Member>,
  val pendingInvites: List<Invite>,
)

@Composable
fun FamilyRouteScreen(
  modifier: Modifier = Modifier,
  viewModel: FamilyViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  FamilyScreen(
    state = state,
    onSelectInviteRole = viewModel::selectInviteRole,
    onGenerateInvite = viewModel::generateInvite,
    onRevokeInvite = viewModel::revokeInvite,
    onChangeMemberRole = viewModel::changeMemberRole,
    onRemoveMember = viewModel::removeMember,
    onClearAllData = viewModel::clearAllData,
    onCopiedInviteCode = viewModel::showCopiedFeedback,
    onDismissFeedback = viewModel::dismissFeedback,
    onSignOut = { viewModel.signOut(context) },
    modifier = modifier,
  )
}

@Composable
fun FamilyScreen(
  state: FamilyUiState,
  onSelectInviteRole: (Role) -> Unit = {},
  onGenerateInvite: () -> Unit = {},
  onRevokeInvite: (String) -> Unit = {},
  onChangeMemberRole: (String, Role) -> Unit = { _, _ -> },
  onRemoveMember: (String) -> Unit = {},
  onClearAllData: () -> Unit = {},
  onCopiedInviteCode: (String) -> Unit = {},
  onDismissFeedback: () -> Unit = {},
  onSignOut: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val currentRole = state.currentMember?.role
  var roleDialogRow by remember { mutableStateOf<MemberDirectoryRow?>(null) }
  var removeDialogRow by remember { mutableStateOf<MemberDirectoryRow?>(null) }
  var showClearDataDialog by remember { mutableStateOf(false) }

  Box(
    modifier = modifier.fillMaxSize().testTag("screen_family"),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().testTag("family_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item(key = "family_role_banner") {
        ElevatedCard(
          modifier = Modifier.fillMaxWidth().testTag("family_role_banner"),
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
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = state.family?.name ?: stringResource(R.string.family_workspace_fallback),
                  style = MaterialTheme.typography.headlineSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.semantics { heading() },
                )
                Text(
                  text =
                    stringResource(
                      R.string.family_members_meta,
                      state.memberRows.size,
                      state.family?.currencyCode ?: "INR",
                    ),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              currentRole?.let { RoleBadge(role = it) }
            }

            when (currentRole) {
              Role.ADMIN -> {
                Surface(
                  shape = MaterialTheme.shapes.medium,
                  color = GrihaTheme.colors.goodContainer,
                  modifier = Modifier.fillMaxWidth().testTag("family_admin_controls"),
                ) {
                  Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.AdminPanelSettings,
                        contentDescription = null,
                        tint = GrihaTheme.colors.onGoodContainer,
                      )
                      Text(
                        text = stringResource(R.string.family_admin_banner_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = GrihaTheme.colors.onGoodContainer,
                      )
                    }
                    Text(
                      text = stringResource(R.string.family_admin_banner_desc),
                      style = MaterialTheme.typography.bodyMedium,
                      color = GrihaTheme.colors.onGoodContainer,
                    )
                  }
                }
              }

              Role.MEMBER -> {
                Surface(
                  shape = MaterialTheme.shapes.medium,
                  color = GrihaTheme.colors.attentionContainer,
                  modifier = Modifier.fillMaxWidth().testTag("family_member_notice"),
                ) {
                  Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = GrihaTheme.colors.onAttentionContainer,
                      )
                      Text(
                        text = stringResource(R.string.family_member_banner_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = GrihaTheme.colors.onAttentionContainer,
                      )
                    }
                    Text(
                      text = stringResource(R.string.family_member_banner_desc),
                      style = MaterialTheme.typography.bodyMedium,
                      color = GrihaTheme.colors.onAttentionContainer,
                    )
                  }
                }
              }

              Role.VIEWER -> {
                Surface(
                  shape = MaterialTheme.shapes.medium,
                  color = MaterialTheme.colorScheme.surfaceContainerHigh,
                  modifier = Modifier.fillMaxWidth().testTag("family_viewer_notice"),
                ) {
                  Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      Text(
                        text = stringResource(R.string.family_viewer_banner_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                    }
                    Text(
                      text = stringResource(R.string.family_viewer_banner_desc),
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }

              null -> Unit
            }
          }
        }
      }

      item(key = "family_language_card") {
        val currentLanguage = LocalAppLanguage.current
        val setLanguage = LocalSetAppLanguage.current
        ElevatedCard(
          modifier = Modifier.fillMaxWidth().testTag("family_language_card"),
          shape = MaterialTheme.shapes.large,
          colors =
            CardDefaults.elevatedCardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = null,
                tint = GrihaTheme.colors.good,
              )
              Text(
                text = stringResource(R.string.language_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Text(
              text = stringResource(R.string.language_section_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              listOf(
                AppLanguage.ENGLISH to stringResource(R.string.language_english),
                AppLanguage.HINDI to stringResource(R.string.language_hindi),
              ).forEach { (langOption, label) ->
                val selected = currentLanguage == langOption
                FilterChip(
                  selected = selected,
                  onClick = { setLanguage(langOption) },
                  label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
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
                      selectedContainerColor = GrihaTheme.colors.goodContainer,
                      selectedLabelColor = GrihaTheme.colors.onGoodContainer,
                      selectedLeadingIconColor = GrihaTheme.colors.onGoodContainer,
                    ),
                  modifier =
                    Modifier
                      .weight(1f)
                      .minimumInteractiveComponentSize()
                      .testTag("language_chip_${langOption.tag}"),
                )
              }
            }
          }
        }
      }

      if (state.feedbackMessageRes != null || !state.feedbackMessage.isNullOrBlank()) {
        item(key = "family_feedback_banner") {
          val resolvedFeedback =
            state.feedbackMessageRes?.let { stringResource(it) } ?: state.feedbackMessage.orEmpty()
          Surface(
            shape = MaterialTheme.shapes.medium,
            color =
              if (state.isErrorFeedback) GrihaTheme.colors.overBudgetContainer
              else GrihaTheme.colors.goodContainer,
            modifier = Modifier.fillMaxWidth().testTag("family_feedback_banner"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = resolvedFeedback,
                style = MaterialTheme.typography.bodyMedium,
                color =
                  if (state.isErrorFeedback) GrihaTheme.colors.onOverBudgetContainer
                  else GrihaTheme.colors.onGoodContainer,
                modifier = Modifier.weight(1f),
              )
              TextButton(
                onClick = onDismissFeedback,
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Text(stringResource(R.string.action_dismiss))
              }
            }
          }
        }
      }

      if (state.canInviteMembers) {
        item(key = "family_pending_invites") {
          FamilyInvitesCard(
            selectedRole = state.selectedInviteRole,
            pendingInvites = state.pendingInvites,
            isBusy = state.isBusy,
            onSelectRole = onSelectInviteRole,
            onGenerateInvite = onGenerateInvite,
            onRevokeInvite = onRevokeInvite,
            onCopiedCode = onCopiedInviteCode,
          )
        }
      }

      item(key = "family_members_heading") {
        Text(
          text = stringResource(R.string.family_members_heading),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.semantics { heading() },
        )
      }

      items(state.memberRows, key = { it.member.uid }) { row ->
        MemberDirectoryCard(
          row = row,
          showAdminControls = state.canInviteMembers,
          isBusy = state.isBusy,
          onChangeRoleClick = { roleDialogRow = row },
          onRemoveClick = { removeDialogRow = row },
        )
      }

      if (state.canClearAllData) {
        item(key = "family_admin_settings_card") {
          AdminSettingsClearDataCard(
            isBusy = state.isBusy,
            onClearAllDataClick = { showClearDataDialog = true },
          )
        }
      }

      item(key = "family_sign_out") {
        OutlinedButton(
          onClick = onSignOut,
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("family_sign_out_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.Logout,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text(stringResource(R.string.action_sign_out))
        }
      }

      item(key = "bottom_spacer") {
        Spacer(modifier = Modifier.size(72.dp))
      }
    }
  }

  roleDialogRow?.let { targetRow ->
    ChangeMemberRoleDialog(
      member = targetRow.member,
      allowedRoles = targetRow.allowedTargetRoles,
      onDismiss = { roleDialogRow = null },
      onConfirmRole = { newRole ->
        roleDialogRow = null
        onChangeMemberRole(targetRow.member.uid, newRole)
      },
    )
  }

  removeDialogRow?.let { targetRow ->
    ConfirmRemoveMemberDialog(
      member = targetRow.member,
      isSelf = targetRow.isCurrentUser,
      onDismiss = { removeDialogRow = null },
      onConfirmRemove = {
        removeDialogRow = null
        onRemoveMember(targetRow.member.uid)
      },
    )
  }

  if (showClearDataDialog && state.canClearAllData) {
    ConfirmClearAllDataDialog(
      familyName = state.family?.name ?: stringResource(R.string.family_workspace_fallback),
      isBusy = state.isBusy,
      onDismiss = { showClearDataDialog = false },
      onConfirmClear = {
        showClearDataDialog = false
        onClearAllData()
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberDirectoryCard(
  row: MemberDirectoryRow,
  showAdminControls: Boolean,
  isBusy: Boolean,
  onChangeRoleClick: () -> Unit,
  onRemoveClick: () -> Unit,
) {
  val member = row.member
  val initial = member.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

  ElevatedCard(
    modifier = Modifier.fillMaxWidth().testTag("family_member_card_${member.uid}"),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      ),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = initial,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }

        Column(modifier = Modifier.weight(1f)) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = member.displayName,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.align(Alignment.CenterVertically),
            )
            if (row.isCurrentUser) {
              Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.align(Alignment.CenterVertically),
              ) {
                Text(
                  text = stringResource(R.string.family_badge_you),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
            }
          }
          Text(
            text = member.email,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        RoleBadge(role = member.role)
      }

      if (showAdminControls) {
        if (row.isLastAdminProtected) {
          Text(
            text = stringResource(R.string.family_last_admin_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("member_admin_hint_${member.uid}"),
          )
        } else if (row.canPromoteOrChangeRole || row.canRemove) {
          Row(
            modifier = Modifier.fillMaxWidth().testTag("member_admin_hint_${member.uid}"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (row.canPromoteOrChangeRole) {
              OutlinedButton(
                onClick = onChangeRoleClick,
                enabled = !isBusy,
                modifier =
                  Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("change_role_button_${member.uid}"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.ManageAccounts,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(stringResource(R.string.action_change_role))
              }
            }
            if (row.canRemove) {
              TextButton(
                onClick = onRemoveClick,
                enabled = !isBusy,
                modifier =
                  Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("remove_member_button_${member.uid}"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.PersonRemove,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                  text = stringResource(R.string.action_remove),
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
        }
      }
    }
  }
}
