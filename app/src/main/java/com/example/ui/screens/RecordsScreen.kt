package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.analytics.PeriodResolver
import com.example.domain.model.AppLanguage
import com.example.domain.model.Category
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.Member
import com.example.domain.model.Money
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.SessionRepository
import com.example.ui.analytics.RecordsDrilldownFilter
import com.example.ui.analytics.RecordsFilterCoordinator
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

enum class RecordsFilter(val label: String) {
  ALL("All"),
  EXPENSE("Expenses"),
  SALARY_EXPENSE("Salary"),
  NON_SALARY_EXPENSE("Non-Salary"),
  INCOME("Income"),
  VOIDED("Voided"),
  MINE("My Entries"),
}

@Immutable
data class RecordItemUi(
  val transaction: Transaction,
  val category: Category?,
  val paidByName: String,
  val createdByName: String,
)

@Immutable
data class RecordsUiState(
  val currentMember: Member? = null,
  val hasPendingSync: Boolean = false,
  val searchQuery: String = "",
  val selectedFilter: RecordsFilter = RecordsFilter.ALL,
  val activeDrilldown: RecordsDrilldownFilter? = null,
  val items: List<RecordItemUi> = emptyList(),
  val totalMatchingCount: Int = 0,
  val hasMore: Boolean = false,
)

@HiltViewModel
class RecordsViewModel @Inject constructor(
  sessionRepository: SessionRepository,
  familyRepository: FamilyRepository,
  ledgerRepository: LedgerRepository,
  private val filterCoordinator: RecordsFilterCoordinator,
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")
  private val selectedFilter = MutableStateFlow(RecordsFilter.ALL)
  private val pageLimit = MutableStateFlow(PAGE_SIZE)

  init {
    viewModelScope.launch {
      filterCoordinator.drilldown.collect { drilldown ->
        if (drilldown != null) {
          selectedFilter.value = drilldown.baseFilter
          pageLimit.value = PAGE_SIZE
        }
      }
    }
  }

  private val filterParamsFlow =
    combine(searchQuery, selectedFilter, pageLimit, filterCoordinator.drilldown) {
        query,
        filter,
        limit,
        drilldown ->
      FilterParams(query, filter, limit, drilldown)
    }

  private val workspaceFlow =
    combine(
      sessionRepository.observeCurrentMember(),
      familyRepository.observeMembers(),
      familyRepository.observeCategories(),
      ledgerRepository.observeTransactions(),
      ledgerRepository.observeHasPendingSync(),
    ) { member, members, categories, transactions, pending ->
      RecordsWorkspaceSnapshot(member, members, categories, transactions, pending)
    }

  val uiState: StateFlow<RecordsUiState> =
    combine(workspaceFlow, filterParamsFlow) { workspace, params ->
      val categoriesById = workspace.categories.associateBy { it.id }
      val membersByUid = workspace.members.associateBy { it.uid }
      val normalizedQuery = params.query.trim().lowercase()
      val currentUid = workspace.currentMember?.uid
      val drilldown = params.drilldown

      val filtered =
        workspace.transactions.filter { tx ->
          val filterMatch =
            when (params.filter) {
              RecordsFilter.ALL -> true
              RecordsFilter.EXPENSE -> !tx.isVoided && tx.type == TransactionType.EXPENSE
              RecordsFilter.SALARY_EXPENSE ->
                !tx.isVoided &&
                  tx.type == TransactionType.EXPENSE &&
                  tx.expenseClassification == ExpenseClassification.SALARY
              RecordsFilter.NON_SALARY_EXPENSE ->
                !tx.isVoided &&
                  tx.type == TransactionType.EXPENSE &&
                  tx.expenseClassification == ExpenseClassification.NON_SALARY
              RecordsFilter.INCOME -> !tx.isVoided && tx.type == TransactionType.INCOME
              RecordsFilter.VOIDED -> tx.isVoided
              RecordsFilter.MINE -> currentUid != null && tx.createdByUid == currentUid
            }
          if (!filterMatch) return@filter false

          if (drilldown != null) {
            if (drilldown.categoryId != null && tx.categoryId != drilldown.categoryId) {
              return@filter false
            }
            if (drilldown.fundingSource != null && tx.fundingSource != drilldown.fundingSource) {
              return@filter false
            }
            if (
              drilldown.expenseClassification != null &&
                (tx.type != TransactionType.EXPENSE ||
                  tx.expenseClassification != drilldown.expenseClassification)
            ) {
              return@filter false
            }
            if (drilldown.paidByUid != null && tx.paidByUid != drilldown.paidByUid) {
              return@filter false
            }
            if (drilldown.period != null && tx.occurredOn !in drilldown.period) {
              return@filter false
            }
          }

          if (normalizedQuery.isEmpty()) return@filter true

          val categoryName = categoriesById[tx.categoryId]?.name.orEmpty().lowercase()
          val paidByName = membersByUid[tx.paidByUid]?.displayName.orEmpty().lowercase()
          val creatorName = membersByUid[tx.createdByUid]?.displayName.orEmpty().lowercase()
          val classificationEn =
            if (tx.type == TransactionType.EXPENSE) {
              LocalizedLabels.expenseClassification(tx.expenseClassification, AppLanguage.ENGLISH)
                .lowercase()
            } else {
              ""
            }
          tx.title.lowercase().contains(normalizedQuery) ||
            tx.note.lowercase().contains(normalizedQuery) ||
            categoryName.contains(normalizedQuery) ||
            paidByName.contains(normalizedQuery) ||
            creatorName.contains(normalizedQuery) ||
            classificationEn.contains(normalizedQuery)
        }

      val paged =
        filtered.take(params.limit).map { tx ->
          RecordItemUi(
            transaction = tx,
            category = categoriesById[tx.categoryId],
            paidByName = membersByUid[tx.paidByUid]?.displayName ?: "Member",
            createdByName = membersByUid[tx.createdByUid]?.displayName ?: "Member",
          )
        }

      RecordsUiState(
        currentMember = workspace.currentMember,
        hasPendingSync = workspace.hasPendingSync || workspace.transactions.any { it.hasPendingSync },
        searchQuery = params.query,
        selectedFilter = params.filter,
        activeDrilldown = drilldown?.takeIf { it.hasActiveDrilldown },
        items = paged,
        totalMatchingCount = filtered.size,
        hasMore = filtered.size > paged.size,
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = RecordsUiState(),
      )

  fun onSearchQueryChange(query: String) {
    searchQuery.value = query
    pageLimit.value = PAGE_SIZE
  }

  fun onSelectFilter(filter: RecordsFilter) {
    selectedFilter.value = filter
    pageLimit.value = PAGE_SIZE
  }

  fun clearDrilldown() {
    filterCoordinator.clearDrilldown()
    selectedFilter.value = RecordsFilter.ALL
    pageLimit.value = PAGE_SIZE
  }

  fun loadMore() {
    pageLimit.value = pageLimit.value + PAGE_SIZE
  }

  private data class FilterParams(
    val query: String,
    val filter: RecordsFilter,
    val limit: Int,
    val drilldown: RecordsDrilldownFilter?,
  )

  private data class RecordsWorkspaceSnapshot(
    val currentMember: Member?,
    val members: List<Member>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val hasPendingSync: Boolean,
  )

  companion object {
    const val PAGE_SIZE = 15
  }
}

@Composable
fun RecordsRouteScreen(
  onTransactionClick: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: RecordsViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  RecordsScreen(
    state = state,
    onSearchQueryChange = viewModel::onSearchQueryChange,
    onSelectFilter = viewModel::onSelectFilter,
    onClearDrilldown = viewModel::clearDrilldown,
    onLoadMore = viewModel::loadMore,
    onTransactionClick = onTransactionClick,
    modifier = modifier,
  )
}

@Composable
fun RecordsScreen(
  state: RecordsUiState,
  onSearchQueryChange: (String) -> Unit,
  onSelectFilter: (RecordsFilter) -> Unit,
  onLoadMore: () -> Unit,
  onTransactionClick: (String) -> Unit,
  modifier: Modifier = Modifier,
  onClearDrilldown: () -> Unit = {},
) {
  val language = LocalAppLanguage.current
  val listState = rememberLazyListState()
  val shouldAutoLoadMore by remember(state.hasMore, state.items.size) {
    derivedStateOf {
      val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      state.hasMore && state.items.isNotEmpty() && lastVisibleIndex >= state.items.size - 2
    }
  }
  LaunchedEffect(shouldAutoLoadMore) {
    if (shouldAutoLoadMore) {
      onLoadMore()
    }
  }

  Box(
    modifier = modifier.fillMaxSize().testTag("screen_records"),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      state = listState,
      modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth().testTag("records_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (state.hasPendingSync) {
        item(key = "pending_sync_banner") {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = GrihaTheme.colors.attentionContainer,
            modifier = Modifier.fillMaxWidth().testTag("records_pending_sync_banner"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.CloudSync,
                contentDescription = null,
                tint = GrihaTheme.colors.onAttentionContainer,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.records_pending_sync_banner),
                style = MaterialTheme.typography.labelLarge,
                color = GrihaTheme.colors.onAttentionContainer,
              )
            }
          }
        }
      }

      item(key = "search_bar") {
        OutlinedTextField(
          value = state.searchQuery,
          onValueChange = onSearchQueryChange,
          singleLine = true,
          placeholder = { Text(stringResource(R.string.records_search_placeholder)) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.Search,
              contentDescription = null,
            )
          },
          trailingIcon = {
            if (state.searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { onSearchQueryChange("") },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("records_search_clear"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Clear,
                  contentDescription = stringResource(R.string.records_clear_search_cd),
                )
              }
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("records_search_input"),
        )
      }

      item(key = "filter_chips") {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          items(RecordsFilter.entries, key = { it.name }) { filter ->
            val selected = filter == state.selectedFilter
            FilterChip(
              selected = selected,
              onClick = { onSelectFilter(filter) },
              label = { Text(LocalizedLabels.recordsFilter(filter, language)) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = GrihaTheme.colors.goodContainer,
                  selectedLabelColor = GrihaTheme.colors.onGoodContainer,
                ),
              modifier =
                Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("filter_chip_${filter.name.lowercase()}"),
            )
          }
        }
      }

      state.activeDrilldown?.let { drilldown ->
        item(key = "drilldown_banner") {
          DrilldownFilterBanner(
            drilldown = drilldown,
            onClear = onClearDrilldown,
          )
        }
      }

      item(key = "count_header") {
        Text(
          text = stringResource(R.string.records_count_label, state.items.size, state.totalMatchingCount),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.semantics { heading() }.testTag("records_count_label"),
        )
      }

      if (state.items.isEmpty()) {
        item(key = "empty_state") {
          val hasFilters =
            state.searchQuery.isNotBlank() ||
              state.selectedFilter != RecordsFilter.ALL ||
              state.activeDrilldown != null
          EmptyStateCard(
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            badgeText = stringResource(R.string.records_empty_badge),
            title =
              stringResource(
                if (hasFilters) R.string.records_empty_filtered_title
                else R.string.records_empty_fresh_title
              ),
            description =
              stringResource(
                if (hasFilters) R.string.records_empty_filtered_desc
                else R.string.records_empty_fresh_desc
              ),
            tip = stringResource(R.string.records_empty_tip),
            testTag = "records_empty_state",
          )
        }
      } else {
        items(state.items, key = { it.transaction.id }) { item ->
          RecordRowCard(
            item = item,
            onClick = { onTransactionClick(item.transaction.id) },
          )
        }

        if (state.hasMore) {
          item(key = "load_more") {
            OutlinedButton(
              onClick = onLoadMore,
              modifier =
                Modifier
                  .fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("records_load_more_button"),
            ) {
              Text(stringResource(R.string.records_load_more))
            }
          }
        }
      }

      item(key = "bottom_spacer") {
        Spacer(modifier = Modifier.size(72.dp))
      }
    }
  }
}

@Composable
private fun DrilldownFilterBanner(
  drilldown: RecordsDrilldownFilter,
  onClear: () -> Unit,
) {
  val language = LocalAppLanguage.current
  val paidByText = drilldown.paidByName?.let { stringResource(R.string.records_paid_by_prefix, it) }
  val parts =
    buildList {
      drilldown.categoryName?.let {
        add(LocalizedLabels.categoryNameById(drilldown.categoryId, it, language))
      }
      drilldown.expenseClassification?.let {
        add(LocalizedLabels.expenseClassification(it, language))
      }
      drilldown.fundingSource?.let { add(LocalizedLabels.fundingSource(it, language)) }
      paidByText?.let { add(it) }
      drilldown.period?.let { period ->
        add(
          if (language == AppLanguage.HINDI) {
            PeriodResolver.formatRangeLabel(
              start = period.startDate,
              end = period.endDate,
              languageTag = language.tag,
            )
          } else {
            period.label
          }
        )
      }
    }
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = GrihaTheme.colors.goodContainer,
    modifier = Modifier.fillMaxWidth().testTag("records_drilldown_banner"),
  ) {
    Row(
      modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.FilterAlt,
        contentDescription = null,
        tint = GrihaTheme.colors.onGoodContainer,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = stringResource(R.string.records_filtered_prefix, parts.joinToString(" · ")),
        style = MaterialTheme.typography.labelLarge,
        color = GrihaTheme.colors.onGoodContainer,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      TextButton(
        onClick = onClear,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("records_clear_drilldown"),
      ) {
        Text(stringResource(R.string.action_clear), color = GrihaTheme.colors.onGoodContainer)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordRowCard(
  item: RecordItemUi,
  onClick: () -> Unit,
) {
  val language = LocalAppLanguage.current
  val tx = item.transaction
  val category = item.category
  val accent = CategoryVisuals.accentColor(category)
  val dateLabel = remember(tx.occurredOn, language) { LocalizedLabels.shortDate(tx.occurredOn, language) }
  val amountText =
    Money(tx.amountPaise).formatEnIn(
      includePaise = tx.amountPaise % 100L != 0L,
      showPlusSign = tx.type == TransactionType.INCOME && !tx.isVoided,
    )
  val amountColor =
    when {
      tx.isVoided -> MaterialTheme.colorScheme.onSurfaceVariant
      tx.type == TransactionType.INCOME -> GrihaTheme.colors.good
      else -> MaterialTheme.colorScheme.onSurface
    }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onClick)
        .minimumInteractiveComponentSize()
        .semantics(mergeDescendants = true) {}
        .alpha(if (tx.isVoided) 0.68f else 1f)
        .testTag("record_item_${tx.id}"),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = CategoryVisuals.iconForKey(category?.iconKey.orEmpty()),
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(20.dp),
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = tx.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (tx.isVoided) TextDecoration.LineThrough else TextDecoration.None,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterVertically),
          )
          if (!tx.isVoided && tx.type == TransactionType.EXPENSE && tx.expenseClassification == ExpenseClassification.SALARY) {
            Surface(
              shape = MaterialTheme.shapes.extraSmall,
              color = GrihaTheme.colors.attentionContainer,
              modifier = Modifier.align(Alignment.CenterVertically).testTag("record_salary_badge_${tx.id}"),
            ) {
              Text(
                text = stringResource(R.string.records_salary_badge),
                style = MaterialTheme.typography.labelSmall,
                color = GrihaTheme.colors.onAttentionContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
          if (tx.isVoided) {
            Surface(
              shape = MaterialTheme.shapes.extraSmall,
              color = GrihaTheme.colors.overBudgetContainer,
              modifier = Modifier.align(Alignment.CenterVertically),
            ) {
              Text(
                text = stringResource(R.string.records_voided_badge),
                style = MaterialTheme.typography.labelSmall,
                color = GrihaTheme.colors.onOverBudgetContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
          if (tx.hasPendingSync) {
            Surface(
              shape = MaterialTheme.shapes.extraSmall,
              color = GrihaTheme.colors.attentionContainer,
              modifier = Modifier.align(Alignment.CenterVertically),
            ) {
              Text(
                text = stringResource(R.string.records_pending_badge),
                style = MaterialTheme.typography.labelSmall,
                color = GrihaTheme.colors.onAttentionContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }

        val paidFirst = item.paidByName.substringBefore(' ')
        val createdFirst = item.createdByName.substringBefore(' ')
        val paidPart = stringResource(R.string.records_paid_by_prefix, paidFirst)
        val addedPart = stringResource(R.string.records_added_by_prefix, createdFirst)
        val actorText =
          if (tx.paidByUid == tx.createdByUid) {
            paidPart
          } else {
            "$paidPart · $addedPart"
          }
        val categoryLabel = LocalizedLabels.categoryName(category, language)

        Text(
          text = "$categoryLabel · $dateLabel · $actorText",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Text(
        text = amountText,
        style =
          MaterialTheme.typography.titleMedium.copy(
            fontFeatureSettings = "tnum",
            fontWeight = FontWeight.Bold,
          ),
        textDecoration = if (tx.isVoided) TextDecoration.LineThrough else TextDecoration.None,
        color = amountColor,
      )
    }
  }
}
