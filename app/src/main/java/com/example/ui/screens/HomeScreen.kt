package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.analytics.CategoryBreakdownItem
import com.example.domain.analytics.HighlightedInsight
import com.example.domain.analytics.InsightTone
import com.example.domain.analytics.LedgerAnalytics
import com.example.domain.analytics.PeriodPreset
import com.example.domain.analytics.PeriodResolver
import com.example.domain.analytics.PeriodSummary
import com.example.domain.model.AppLanguage
import com.example.domain.model.Member
import com.example.domain.model.Money
import com.example.domain.model.Period
import com.example.domain.model.TransactionType
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.rules.Permissions
import com.example.ui.analytics.HomeSkeletonContent
import com.example.ui.analytics.PeriodSelectorRow
import com.example.ui.analytics.RecordsDrilldownFilter
import com.example.ui.analytics.RecordsFilterCoordinator
import com.example.ui.analytics.SelectedPeriodStore
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RoleBadge
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.ledger.CategoryVisuals
import com.example.ui.theme.GrihaTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Immutable
data class HomeUiState(
  val isLoading: Boolean = true,
  val greeting: String = "Hello",
  val familyName: String = "",
  val currentMember: Member? = null,
  val selectedPreset: PeriodPreset = PeriodPreset.THIS_MONTH,
  val resolvedPeriod: Period = Period.monthToDate(LocalDate.now()),
  val summary: PeriodSummary =
    PeriodSummary(
      period = Period.monthToDate(LocalDate.now()),
      incomePaise = 0L,
      expensePaise = 0L,
      differencePaise = 0L,
      activeTransactionCount = 0,
      expenseCount = 0,
      incomeCount = 0,
      voidedExcludedCount = 0,
    ),
  val statusLine: String = "",
  val topCategories: List<CategoryBreakdownItem> = emptyList(),
  val highlightedInsight: HighlightedInsight? = null,
  val recentActivity: List<RecordItemUi> = emptyList(),
  val canAddExpense: Boolean = false,
  val hasPendingSync: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
  sessionRepository: SessionRepository,
  familyRepository: FamilyRepository,
  ledgerRepository: LedgerRepository,
  private val periodStore: SelectedPeriodStore,
  private val filterCoordinator: RecordsFilterCoordinator,
) : ViewModel() {

  private val workspaceFlow =
    combine(
      familyRepository.observeFamily(),
      sessionRepository.observeCurrentMember(),
      familyRepository.observeMembers(),
      familyRepository.observeCategories(),
      ledgerRepository.observeTransactions(),
      ledgerRepository.observeHasPendingSync(),
    ) { values ->
      @Suppress("UNCHECKED_CAST")
      HomeWorkspaceSnapshot(
        familyName = (values[0] as? com.example.domain.model.Family)?.name.orEmpty(),
        currentMember = values[1] as? Member,
        members = values[2] as List<Member>,
        categories = values[3] as List<com.example.domain.model.Category>,
        transactions = values[4] as List<com.example.domain.model.Transaction>,
        hasPendingSync = values[5] as Boolean,
      )
    }

  val uiState: StateFlow<HomeUiState> =
    combine(workspaceFlow, periodStore.state) { workspace, selection ->
      val today = LocalDate.now()
      val period = selection.resolve(today)
      val summary = LedgerAnalytics.summarizePeriod(workspace.transactions, period)
      val topCategories =
        LedgerAnalytics.categoryBreakdown(
            transactions = workspace.transactions,
            categories = workspace.categories,
            period = period,
            type = TransactionType.EXPENSE,
          )
          .take(3)
      val insight =
        LedgerAnalytics.highlightInsight(
          transactions = workspace.transactions,
          categories = workspace.categories,
          preset = selection.preset,
          referenceDate = today,
          customStart = selection.customStart,
          customEnd = selection.customEnd,
        )

      val categoriesById = workspace.categories.associateBy { it.id }
      val membersByUid = workspace.members.associateBy { it.uid }
      val recentActive =
        LedgerAnalytics.filterActiveInPeriod(workspace.transactions, period)
          .take(3)
          .map { tx ->
            RecordItemUi(
              transaction = tx,
              category = categoriesById[tx.categoryId],
              paidByName = membersByUid[tx.paidByUid]?.displayName ?: "Member",
              createdByName = membersByUid[tx.createdByUid]?.displayName ?: "Member",
            )
          }

      val statusLine =
        when {
          !summary.hasAnyActivity -> "No active entries recorded in ${period.label}."
          summary.differencePaise > 0L ->
            "Surplus across ${summary.activeTransactionCount} active entries."
          summary.differencePaise < 0L ->
            "Expenses exceed income across ${summary.activeTransactionCount} active entries."
          else -> "Income matches expenses across ${summary.activeTransactionCount} active entries."
        }

      HomeUiState(
        isLoading = false,
        greeting = timeGreeting(workspace.currentMember?.displayName),
        familyName = workspace.familyName.ifBlank { "Family Ledger" },
        currentMember = workspace.currentMember,
        selectedPreset = selection.preset,
        resolvedPeriod = period,
        summary = summary,
        statusLine = statusLine,
        topCategories = topCategories,
        highlightedInsight = insight,
        recentActivity = recentActive,
        canAddExpense = workspace.currentMember?.let { Permissions.canCreateTransaction(it) } ?: false,
        hasPendingSync = workspace.hasPendingSync || workspace.transactions.any { it.hasPendingSync },
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HomeUiState(),
      )

  fun selectPreset(preset: PeriodPreset) = periodStore.selectPreset(preset)

  fun selectCustomRange(start: LocalDate, end: LocalDate) = periodStore.selectCustomRange(start, end)

  fun openCategoryRecords(category: CategoryBreakdownItem) {
    val period = uiState.value.resolvedPeriod
    filterCoordinator.apply(
      RecordsDrilldownFilter(
        baseFilter = RecordsFilter.EXPENSE,
        categoryId = category.categoryId,
        categoryName = category.categoryName,
        period = period,
      )
    )
  }

  fun openPeriodRecords() {
    filterCoordinator.apply(
      RecordsDrilldownFilter(
        baseFilter = RecordsFilter.ALL,
        period = uiState.value.resolvedPeriod,
      )
    )
  }

  private fun timeGreeting(displayName: String?): String {
    val hour = LocalTime.now().hour
    val prefix =
      when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
      }
    val firstName = displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    return if (firstName != null) "$prefix, $firstName" else prefix
  }

  private data class HomeWorkspaceSnapshot(
    val familyName: String,
    val currentMember: Member?,
    val members: List<Member>,
    val categories: List<com.example.domain.model.Category>,
    val transactions: List<com.example.domain.model.Transaction>,
    val hasPendingSync: Boolean,
  )
}

@Composable
fun HomeRouteScreen(
  onOpenRecords: () -> Unit = {},
  onOpenInsights: () -> Unit = {},
  onOpenTransactionDetail: (String) -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: HomeViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  HomeScreen(
    state = state,
    onSelectPreset = viewModel::selectPreset,
    onSelectCustomRange = viewModel::selectCustomRange,
    onCategoryClick = { category ->
      viewModel.openCategoryRecords(category)
      onOpenRecords()
    },
    onSeeAllRecords = {
      viewModel.openPeriodRecords()
      onOpenRecords()
    },
    onInsightClick = onOpenInsights,
    onTransactionClick = onOpenTransactionDetail,
    modifier = modifier,
  )
}

@Composable
fun HomeScreen(
  state: HomeUiState,
  onSelectPreset: (PeriodPreset) -> Unit = {},
  onSelectCustomRange: (LocalDate, LocalDate) -> Unit = { _, _ -> },
  onCategoryClick: (CategoryBreakdownItem) -> Unit = {},
  onSeeAllRecords: () -> Unit = {},
  onInsightClick: () -> Unit = {},
  onTransactionClick: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  val displayGreeting =
    remember(state.greeting, state.currentMember?.displayName, language) {
      if (language == AppLanguage.HINDI) {
        val hour = LocalTime.now().hour
        val prefix =
          when {
            hour < 12 -> "सुप्रभात"
            hour < 17 -> "नमस्ते"
            else -> "शुभ संध्या"
          }
        val firstName =
          state.currentMember?.displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        if (firstName != null) "$prefix, $firstName" else prefix
      } else {
        state.greeting
      }
    }
  val displayFamilyName =
    if (state.familyName == "Family Ledger" && language == AppLanguage.HINDI) {
      stringResource(R.string.family_workspace_fallback)
    } else {
      state.familyName
    }
  val displayPeriod =
    remember(state.selectedPreset, state.resolvedPeriod, language) {
      if (language == AppLanguage.HINDI) {
        PeriodResolver.resolve(
          preset = state.selectedPreset,
          referenceDate = LocalDate.now(),
          customStart = state.resolvedPeriod.startDate,
          customEnd = state.resolvedPeriod.endDate,
          languageTag = language.tag,
        )
      } else {
        state.resolvedPeriod
      }
    }
  val displayStatusLine =
    remember(state.summary, state.statusLine, displayPeriod, language) {
      if (language == AppLanguage.HINDI) {
        val s = state.summary
        when {
          !s.hasAnyActivity -> "${displayPeriod.label} में कोई सक्रिय प्रविष्टि दर्ज नहीं है।"
          s.differencePaise > 0L ->
            "${s.activeTransactionCount} सक्रिय प्रविष्टियों में आय खर्च से अधिक है (अधिशेष)।"
          s.differencePaise < 0L ->
            "${s.activeTransactionCount} सक्रिय प्रविष्टियों में खर्च आय से अधिक है।"
          else -> "${s.activeTransactionCount} सक्रिय प्रविष्टियों में आय और खर्च बराबर हैं।"
        }
      } else {
        state.statusLine
      }
    }
  val displayInsight =
    remember(state.highlightedInsight, state.summary, state.topCategories, displayPeriod, language) {
      val insight = state.highlightedInsight
      if (insight == null || language != AppLanguage.HINDI) {
        insight
      } else {
        val top = state.topCategories.firstOrNull()
        val s = state.summary
        when {
          !s.hasAnyActivity ->
            insight.copy(
              title = "इस अवधि में कोई सक्रिय प्रविष्टि नहीं",
              body = "खर्च की मुख्य अंतर्दृष्टि देखने के लिए परिवार का कोई खर्च या आय जोड़ें।",
            )
          top != null -> {
            val catHi = LocalizedLabels.categoryNameById(top.categoryId, top.categoryName, language)
            val share = LedgerAnalytics.formatBasisPointsPercent(top.shareBps)
            insight.copy(
              title = "घरेलू खर्च में $catHi सबसे आगे है",
              body =
                "${displayPeriod.label} के कुल खर्च का $share $catHi पर गया (${top.transactionCount} प्रविष्टियाँ)।",
            )
          }
          else ->
            insight.copy(
              title = "बिना किसी खर्च के आय दर्ज की गई",
              body = "${displayPeriod.label} में ${s.incomeCount} आय प्रविष्टि दर्ज हुई।",
            )
        }
      }
    }

  Box(
    modifier = modifier.fillMaxSize().testTag("screen_home"),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth().testTag("home_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item(key = "greeting_header") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = displayGreeting,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground,
              modifier = Modifier.semantics { heading() }.testTag("home_greeting"),
            )
            Text(
              text = displayFamilyName,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (state.hasPendingSync) {
              Surface(
                shape = MaterialTheme.shapes.small,
                color = GrihaTheme.colors.attentionContainer,
              ) {
                Icon(
                  imageVector = Icons.Outlined.CloudSync,
                  contentDescription = stringResource(R.string.records_pending_sync_banner),
                  tint = GrihaTheme.colors.onAttentionContainer,
                  modifier = Modifier.padding(6.dp).size(16.dp),
                )
              }
            }
            state.currentMember?.let { RoleBadge(role = it.role) }
          }
        }
      }

      item(key = "period_selector") {
        PeriodSelectorRow(
          selectedPreset = state.selectedPreset,
          resolvedPeriod = displayPeriod,
          onSelectPreset = onSelectPreset,
          onSelectCustomRange = onSelectCustomRange,
        )
      }

      if (state.isLoading) {
        item(key = "home_skeleton") {
          HomeSkeletonContent()
        }
      } else {
        item(key = "summary_card") {
          HomeSummaryCard(
            summary = state.summary,
            statusLine = displayStatusLine,
          )
        }

        if (!state.summary.hasAnyActivity) {
          item(key = "empty_state") {
            EmptyStateCard(
              icon = Icons.Outlined.Home,
              badgeText = displayPeriod.label,
              title = stringResource(R.string.home_empty_title, displayPeriod.label),
              description = stringResource(R.string.home_empty_desc),
              tip =
                stringResource(
                  if (state.canAddExpense) R.string.home_empty_tip_can_add
                  else R.string.home_empty_tip_readonly
                ),
              testTag = "home_empty_state",
            )
          }
        } else {
          if (state.topCategories.isNotEmpty()) {
            item(key = "top_categories") {
              HomeTopCategoriesCard(
                categories = state.topCategories,
                totalExpensePaise = state.summary.expensePaise,
                onCategoryClick = onCategoryClick,
              )
            }
          }

          displayInsight?.let { insight ->
            item(key = "highlighted_insight") {
              HomeHighlightedInsightCard(
                insight = insight,
                onClick = onInsightClick,
              )
            }
          }

          if (state.recentActivity.isNotEmpty()) {
            item(key = "recent_activity") {
              HomeRecentActivityCard(
                items = state.recentActivity,
                onTransactionClick = onTransactionClick,
                onSeeAllClick = onSeeAllRecords,
              )
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
private fun HomeSummaryCard(
  summary: PeriodSummary,
  statusLine: String,
) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = GrihaTheme.colors.brandForest,
    modifier = Modifier.fillMaxWidth().testTag("home_summary_card"),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = stringResource(R.string.home_label_difference),
          style = MaterialTheme.typography.labelLarge,
          color = GrihaTheme.colors.onBrandForest.copy(alpha = 0.8f),
        )
        Text(
          text =
            Money(summary.differencePaise).formatEnIn(
              includePaise = summary.differencePaise % 100L != 0L,
              showPlusSign = true,
            ),
          style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
          fontWeight = FontWeight.Bold,
          color = GrihaTheme.colors.onBrandForest,
          modifier = Modifier.testTag("home_difference_value"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SummaryStatPill(
          label = stringResource(R.string.home_label_income),
          value = Money(summary.incomePaise).formatEnIn(includePaise = summary.incomePaise % 100L != 0L),
          valueTag = "home_income_value",
          modifier = Modifier.weight(1f),
        )
        SummaryStatPill(
          label = stringResource(R.string.home_label_expense),
          value = Money(summary.expensePaise).formatEnIn(includePaise = summary.expensePaise % 100L != 0L),
          valueTag = "home_expense_value",
          modifier = Modifier.weight(1f),
        )
      }

      Text(
        text = statusLine,
        style = MaterialTheme.typography.bodySmall,
        color = GrihaTheme.colors.onBrandForest.copy(alpha = 0.88f),
        modifier = Modifier.testTag("home_status_line"),
      )
    }
  }
}

@Composable
private fun SummaryStatPill(
  label: String,
  value: String,
  valueTag: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = Color.White.copy(alpha = 0.12f),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = GrihaTheme.colors.onBrandForest.copy(alpha = 0.85f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Bold,
        color = GrihaTheme.colors.onBrandForest,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(valueTag),
      )
    }
  }
}

@Composable
private fun HomeTopCategoriesCard(
  categories: List<CategoryBreakdownItem>,
  totalExpensePaise: Long,
  onCategoryClick: (CategoryBreakdownItem) -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().testTag("home_top_categories_card"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val language = LocalAppLanguage.current
      Text(
        text = stringResource(R.string.home_category_spending_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      categories.forEach { item ->
        val shareFraction =
          if (totalExpensePaise > 0L) {
            (item.amountPaise.toFloat() / totalExpensePaise.toFloat()).coerceIn(0.04f, 1f)
          } else {
            0f
          }
        val accent = Color(item.colorHex)
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(MaterialTheme.shapes.small)
              .clickable { onCategoryClick(item) }
              .minimumInteractiveComponentSize()
              .padding(vertical = 4.dp)
              .testTag("home_category_row_${item.categoryId}"),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = CategoryVisuals.iconForKey(item.iconKey),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = LocalizedLabels.categoryNameById(item.categoryId, item.categoryName, language),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Text(
              text = Money(item.amountPaise).formatEnIn(includePaise = item.amountPaise % 100L != 0L),
              style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
          ) {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth(shareFraction)
                  .fillMaxHeight()
                  .clip(RoundedCornerShape(4.dp))
                  .background(accent),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HomeHighlightedInsightCard(
  insight: HighlightedInsight,
  onClick: () -> Unit,
) {
  val (containerColor, contentColor) =
    when (insight.tone) {
      InsightTone.GOOD -> GrihaTheme.colors.goodContainer to GrihaTheme.colors.onGoodContainer
      InsightTone.ATTENTION ->
        GrihaTheme.colors.attentionContainer to GrihaTheme.colors.onAttentionContainer
      InsightTone.OVER_BUDGET ->
        GrihaTheme.colors.overBudgetContainer to GrihaTheme.colors.onOverBudgetContainer
      InsightTone.NEUTRAL ->
        MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.onSurface
    }

  Surface(
    shape = MaterialTheme.shapes.large,
    color = containerColor,
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .clickable(onClick = onClick)
        .minimumInteractiveComponentSize()
        .semantics(mergeDescendants = true) {}
        .testTag("home_highlighted_insight_card"),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.Lightbulb,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(22.dp),
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = stringResource(R.string.home_insight_badge),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = contentColor.copy(alpha = 0.8f),
        )
        Text(
          text = insight.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = contentColor,
        )
        Text(
          text = insight.body,
          style = MaterialTheme.typography.bodySmall,
          color = contentColor.copy(alpha = 0.9f),
        )
      }
      Text(
        text = insight.metricValue,
        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Bold,
        color = contentColor,
      )
    }
  }
}

@Composable
private fun HomeRecentActivityCard(
  items: List<RecordItemUi>,
  onTransactionClick: (String) -> Unit,
  onSeeAllClick: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().testTag("home_recent_activity_section"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.home_recent_activity_title),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(
          onClick = onSeeAllClick,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("home_see_all_records"),
        ) {
          Text(stringResource(R.string.action_see_all))
          Spacer(modifier = Modifier.size(4.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        }
      }

      val language = LocalAppLanguage.current
      items.forEach { item ->
        val tx = item.transaction
        val accent = CategoryVisuals.accentColor(item.category)
        val dateLabel =
          remember(tx.occurredOn, language) { LocalizedLabels.shortDate(tx.occurredOn, language) }
        val amountText =
          Money(tx.amountPaise).formatEnIn(
            includePaise = tx.amountPaise % 100L != 0L,
            showPlusSign = tx.type == TransactionType.INCOME,
          )
        val categoryLabel = LocalizedLabels.categoryName(item.category, language)
        val paidByLabel = stringResource(R.string.records_paid_by_prefix, item.paidByName.substringBefore(' '))
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(MaterialTheme.shapes.medium)
              .clickable { onTransactionClick(tx.id) }
              .minimumInteractiveComponentSize()
              .semantics(mergeDescendants = true) {}
              .padding(vertical = 6.dp, horizontal = 4.dp)
              .testTag("home_recent_item_${tx.id}"),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = CategoryVisuals.iconForKey(item.category?.iconKey.orEmpty()),
              contentDescription = null,
              tint = accent,
              modifier = Modifier.size(18.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = tx.title,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "$categoryLabel · $dateLabel · $paidByLabel",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
            text = amountText,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color =
              if (tx.type == TransactionType.INCOME) GrihaTheme.colors.good
              else MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}
