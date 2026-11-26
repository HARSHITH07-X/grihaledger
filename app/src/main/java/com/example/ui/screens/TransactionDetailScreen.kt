package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.model.AuditAction
import com.example.domain.model.AuditEvent
import com.example.domain.model.Category
import com.example.domain.model.Member
import com.example.domain.model.Money
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.rules.Permissions
import com.example.domain.rules.TransactionValidation
import com.example.domain.rules.ValidationResult
import com.example.ui.components.EmptyStateCard
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.ledger.CategoryVisuals
import com.example.ui.theme.GrihaTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AuditEventUi(
  val event: AuditEvent,
  val actorName: String,
)

@Immutable
data class TransactionDetailUiState(
  val transaction: Transaction? = null,
  val category: Category? = null,
  val paidByName: String = "",
  val createdByName: String = "",
  val voidedByName: String? = null,
  val auditEvents: List<AuditEventUi> = emptyList(),
  val canEdit: Boolean = false,
  val canVoid: Boolean = false,
  val isVoiding: Boolean = false,
  val actionError: String? = null,
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  sessionRepository: SessionRepository,
  familyRepository: FamilyRepository,
  private val ledgerRepository: LedgerRepository,
) : ViewModel() {

  private val transactionId: String = checkNotNull(savedStateHandle.get<String>("transactionId"))
  private val isVoiding = MutableStateFlow(false)
  private val actionError = MutableStateFlow<String?>(null)

  private val workspaceFlow =
    combine(
      sessionRepository.observeCurrentMember(),
      familyRepository.observeMembers(),
      familyRepository.observeCategories(),
      ledgerRepository.observeTransactions(),
      ledgerRepository.observeAuditEventsForTransaction(transactionId),
    ) { currentMember, members, categories, transactions, auditEvents ->
      DetailWorkspaceSnapshot(currentMember, members, categories, transactions, auditEvents)
    }

  val uiState: StateFlow<TransactionDetailUiState> =
    combine(workspaceFlow, isVoiding, actionError) { workspace, voiding, error ->
      val tx = workspace.transactions.firstOrNull { it.id == transactionId }
      val membersByUid = workspace.members.associateBy { it.uid }
      val categoriesById = workspace.categories.associateBy { it.id }
      val actor = workspace.currentMember

      TransactionDetailUiState(
        transaction = tx,
        category = tx?.let { categoriesById[it.categoryId] },
        paidByName = tx?.let { membersByUid[it.paidByUid]?.displayName ?: "Member" }.orEmpty(),
        createdByName = tx?.let { membersByUid[it.createdByUid]?.displayName ?: "Member" }.orEmpty(),
        voidedByName = tx?.voidedByUid?.let { uid -> membersByUid[uid]?.displayName ?: "Member" },
        auditEvents =
          workspace.auditEvents.map { event ->
            AuditEventUi(
              event = event,
              actorName = membersByUid[event.actorUid]?.displayName ?: "Family Member",
            )
          },
        canEdit = actor != null && tx != null && Permissions.canEditTransaction(actor, tx),
        canVoid = actor != null && tx != null && Permissions.canVoidTransaction(actor, tx),
        isVoiding = voiding,
        actionError = error,
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TransactionDetailUiState(),
      )

  fun clearError() {
    actionError.value = null
  }

  fun voidTransaction(reason: String, onVoided: () -> Unit) {
    val tx = uiState.value.transaction ?: return
    when (val validation = TransactionValidation.validateVoid(tx, reason)) {
      is ValidationResult.Invalid -> {
        actionError.value = validation.errors.first().message
        return
      }
      ValidationResult.Valid -> Unit
    }

    viewModelScope.launch {
      isVoiding.value = true
      actionError.value = null
      ledgerRepository
        .voidTransaction(tx.id, reason)
        .onSuccess {
          isVoiding.value = false
          onVoided()
        }
        .onFailure { error ->
          isVoiding.value = false
          actionError.value = error.message ?: "Could not void record."
        }
    }
  }

  private data class DetailWorkspaceSnapshot(
    val currentMember: Member?,
    val members: List<Member>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val auditEvents: List<AuditEvent>,
  )
}

@Composable
fun TransactionDetailRouteScreen(
  onBack: () -> Unit,
  onEditClick: (Transaction) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  TransactionDetailScreen(
    state = state,
    onBack = onBack,
    onEditClick = onEditClick,
    onVoidConfirm = { reason -> viewModel.voidTransaction(reason) {} },
    onDismissError = viewModel::clearError,
    modifier = modifier,
  )
}

@Composable
fun TransactionDetailScreen(
  state: TransactionDetailUiState,
  onBack: () -> Unit,
  onEditClick: (Transaction) -> Unit,
  onVoidConfirm: (String) -> Unit,
  onDismissError: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  var showVoidDialog by remember { mutableStateOf(false) }
  var voidReason by remember { mutableStateOf("") }
  val tx = state.transaction

  Box(
    modifier = modifier.fillMaxSize().testTag("screen_transaction_detail"),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth().testTag("detail_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item(key = "header") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("detail_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
              )
            }
            Text(
              text = stringResource(R.string.detail_title),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.semantics { heading() },
            )
          }

          if (tx?.hasPendingSync == true) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = GrihaTheme.colors.attentionContainer,
              modifier = Modifier.testTag("detail_pending_sync_badge"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Outlined.CloudSync,
                  contentDescription = null,
                  tint = GrihaTheme.colors.onAttentionContainer,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = stringResource(R.string.records_pending_badge),
                  style = MaterialTheme.typography.labelSmall,
                  color = GrihaTheme.colors.onAttentionContainer,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }
      }

      if (tx == null) {
        item(key = "not_found") {
          EmptyStateCard(
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            badgeText = stringResource(R.string.detail_missing_badge),
            title = stringResource(R.string.detail_missing_title),
            description = stringResource(R.string.detail_missing_desc),
            tip = stringResource(R.string.detail_missing_tip),
            testTag = "detail_missing_state",
          )
        }
      } else {
        item(key = "hero_card") {
          val accent = CategoryVisuals.accentColor(state.category)
          val amountText =
            Money(tx.amountPaise).formatEnIn(
              includePaise = tx.amountPaise % 100L != 0L,
              showPlusSign = tx.type == TransactionType.INCOME && !tx.isVoided,
            )
          val dateFormatted =
            remember(tx.occurredOn, language) {
              LocalizedLabels.fullDate(tx.occurredOn, language)
            }
          val localizedCategoryName = LocalizedLabels.categoryName(state.category, language)

          Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().testTag("detail_hero_card"),
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Box(
                    modifier =
                      Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = CategoryVisuals.iconForKey(state.category?.iconKey.orEmpty()),
                      contentDescription = localizedCategoryName,
                      tint = accent,
                    )
                  }
                  Column {
                    Text(
                      text = tx.title,
                      style = MaterialTheme.typography.titleLarge,
                      fontWeight = FontWeight.Bold,
                      textDecoration = if (tx.isVoided) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    Text(
                      text = "$localizedCategoryName · $dateFormatted",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = amountText,
                  style =
                    MaterialTheme.typography.headlineMedium.copy(
                      fontFeatureSettings = "tnum",
                      fontWeight = FontWeight.Bold,
                    ),
                  textDecoration = if (tx.isVoided) TextDecoration.LineThrough else TextDecoration.None,
                  color =
                    when {
                      tx.isVoided -> MaterialTheme.colorScheme.onSurfaceVariant
                      tx.type == TransactionType.INCOME -> GrihaTheme.colors.good
                      else -> MaterialTheme.colorScheme.onSurface
                    },
                  modifier = Modifier.testTag("detail_amount_text"),
                )

                Surface(
                  shape = MaterialTheme.shapes.small,
                  color =
                    if (tx.isVoided) GrihaTheme.colors.overBudgetContainer
                    else GrihaTheme.colors.goodContainer,
                  modifier = Modifier.testTag("detail_status_badge"),
                ) {
                  Text(
                    text =
                      stringResource(
                        if (tx.isVoided) R.string.detail_status_voided
                        else R.string.detail_status_active
                      ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                      if (tx.isVoided) GrihaTheme.colors.onOverBudgetContainer
                      else GrihaTheme.colors.onGoodContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
              }

              if (tx.isVoided) {
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = GrihaTheme.colors.overBudgetContainer.copy(alpha = 0.7f),
                  modifier = Modifier.fillMaxWidth().testTag("detail_void_reason_box"),
                ) {
                  Text(
                    text =
                      stringResource(
                        R.string.detail_voided_by_line,
                        state.voidedByName ?: "Member",
                        tx.voidReason ?: stringResource(R.string.detail_no_reason),
                      ),
                    style = MaterialTheme.typography.bodySmall,
                    color = GrihaTheme.colors.onOverBudgetContainer,
                    modifier = Modifier.padding(10.dp),
                  )
                }
              }

              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

              DetailMetadataRow(
                label = stringResource(R.string.detail_paid_by),
                value = state.paidByName,
                testTag = "detail_paid_by_row",
              )
              DetailMetadataRow(
                label = stringResource(R.string.detail_added_by),
                value = state.createdByName,
                testTag = "detail_added_by_row",
              )
              if (tx.type == TransactionType.EXPENSE) {
                DetailMetadataRow(
                  label = stringResource(R.string.detail_expense_classification),
                  value = LocalizedLabels.expenseClassification(tx.expenseClassification, language),
                  testTag = "detail_expense_classification_row",
                )
                DetailMetadataRow(
                  label = stringResource(R.string.detail_funding_source),
                  value = LocalizedLabels.fundingSource(tx.fundingSource, language),
                )
              }
              DetailMetadataRow(
                label = stringResource(R.string.detail_payment_method),
                value = LocalizedLabels.paymentMethod(tx.paymentMethod, language),
              )
              if (tx.note.isNotBlank()) {
                DetailMetadataRow(
                  label = stringResource(R.string.detail_note_label),
                  value = tx.note,
                )
              }
            }
          }
        }

        if (state.canEdit || state.canVoid) {
          item(key = "actions") {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              if (state.canEdit) {
                OutlinedButton(
                  onClick = { onEditClick(tx) },
                  modifier =
                    Modifier
                      .weight(1f)
                      .minimumInteractiveComponentSize()
                      .testTag("detail_edit_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.size(6.dp))
                  Text(stringResource(R.string.action_edit))
                }
              }
              if (state.canVoid) {
                Button(
                  onClick = {
                    onDismissError()
                    voidReason = ""
                    showVoidDialog = true
                  },
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = GrihaTheme.colors.overBudget,
                      contentColor = GrihaTheme.colors.onOverBudget,
                    ),
                  modifier =
                    Modifier
                      .weight(1f)
                      .minimumInteractiveComponentSize()
                      .testTag("detail_void_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Block,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.size(6.dp))
                  Text(stringResource(R.string.action_void))
                }
              }
            }
          }
        }

        state.actionError?.let { error ->
          item(key = "error_banner") {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = GrihaTheme.colors.overBudgetContainer,
              modifier = Modifier.fillMaxWidth().testTag("detail_error_banner"),
            ) {
              Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = GrihaTheme.colors.onOverBudgetContainer,
                modifier = Modifier.padding(12.dp),
              )
            }
          }
        }

        item(key = "audit_header") {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp).testTag("audit_history_section"),
          ) {
            Icon(
              imageVector = Icons.Outlined.History,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "${stringResource(R.string.detail_audit_history)} (${state.auditEvents.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.semantics { heading() },
            )
          }
        }

        items(state.auditEvents, key = { it.event.id }) { item ->
          AuditEventCard(item = item)
        }
      }

      item(key = "bottom_spacer") {
        Spacer(modifier = Modifier.size(72.dp))
      }
    }
  }

  if (showVoidDialog && tx != null) {
    AlertDialog(
      onDismissRequest = { if (!state.isVoiding) showVoidDialog = false },
      title = { Text(stringResource(R.string.void_dialog_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = stringResource(R.string.void_dialog_message),
            style = MaterialTheme.typography.bodyMedium,
          )
          OutlinedTextField(
            value = voidReason,
            onValueChange = { voidReason = it.take(180) },
            singleLine = false,
            maxLines = 3,
            label = { Text(stringResource(R.string.void_reason_label)) },
            placeholder = { Text(stringResource(R.string.void_reason_placeholder)) },
            modifier = Modifier.fillMaxWidth().testTag("void_reason_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (voidReason.trim().isNotEmpty()) {
              onVoidConfirm(voidReason)
              showVoidDialog = false
            }
          },
          enabled = voidReason.trim().isNotEmpty() && !state.isVoiding,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = GrihaTheme.colors.overBudget,
              contentColor = GrihaTheme.colors.onOverBudget,
            ),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("confirm_void_button"),
        ) {
          Text(stringResource(R.string.action_confirm_void))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showVoidDialog = false },
          enabled = !state.isVoiding,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    )
  }
}

@Composable
private fun DetailMetadataRow(
  label: String,
  value: String,
  testTag: String? = null,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {}
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.End,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun AuditEventCard(item: AuditEventUi) {
  val language = LocalAppLanguage.current
  val event = item.event
  val timestampText =
    remember(event.occurredAtEpochMillis, language) {
      LocalizedLabels.auditTimestamp(event.occurredAtEpochMillis, language)
    }
  val badgeLabel = LocalizedLabels.auditAction(event.action, language)
  val (containerColor, contentColor) =
    when (event.action) {
      AuditAction.CREATED -> GrihaTheme.colors.goodContainer to GrihaTheme.colors.onGoodContainer
      AuditAction.EDITED -> GrihaTheme.colors.attentionContainer to GrihaTheme.colors.onAttentionContainer
      AuditAction.VOIDED -> GrihaTheme.colors.overBudgetContainer to GrihaTheme.colors.onOverBudgetContainer
    }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth().testTag("audit_event_${event.id}"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(shape = MaterialTheme.shapes.extraSmall, color = containerColor) {
          Text(
            text = badgeLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
        Text(
          text = timestampText,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(
        text = "${item.actorName} · ${event.summary}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      val keys = (event.beforeState.keys + event.afterState.keys).distinct()
      if (keys.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          keys.forEach { key ->
            val beforeVal = event.beforeState[key]
            val afterVal = event.afterState[key]
            val diffText =
              when {
                beforeVal != null && afterVal != null && beforeVal != afterVal ->
                  "$beforeVal → $afterVal"
                afterVal != null -> afterVal
                beforeVal != null -> "$beforeVal → —"
                else -> ""
              }
            if (diffText.isNotEmpty()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Text(
                  text = LocalizedLabels.auditFieldLabel(key, language),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = diffText,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface,
                  textAlign = TextAlign.End,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }
    }
  }
}
