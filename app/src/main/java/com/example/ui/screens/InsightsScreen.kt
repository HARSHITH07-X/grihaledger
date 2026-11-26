package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.domain.analytics.CategoryComparisonItem
import com.example.domain.analytics.FundingSplitSummary
import com.example.domain.analytics.LedgerAnalytics
import com.example.domain.analytics.LikeForLikeComparison
import com.example.domain.analytics.MemberSummaryItem
import com.example.domain.analytics.MonthEndForecast
import com.example.domain.analytics.PeriodPreset
import com.example.domain.analytics.PeriodResolver
import com.example.domain.analytics.PeriodSummary
import com.example.domain.analytics.SalaryExpenseSplitSummary
import com.example.domain.model.AppLanguage
import com.example.domain.model.Category
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.Member
import com.example.domain.model.Period
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.SessionRepository
import com.example.ui.analytics.InsightsCategoriesTabContent
import com.example.ui.analytics.InsightsComparisonTabContent
import com.example.ui.analytics.InsightsForecastTabContent
import com.example.ui.analytics.InsightsOverviewTabContent
import com.example.ui.analytics.InsightsSkeletonContent
import com.example.ui.analytics.PeriodSelectionState
import com.example.ui.analytics.PeriodSelectorRow
import com.example.ui.analytics.RecordsDrilldownFilter
import com.example.ui.analytics.RecordsFilterCoordinator
import com.example.ui.analytics.SelectedPeriodStore
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.GrihaTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class InsightsTab(val label: String) {
  OVERVIEW("Overview"),
  COMPARISON("Comparison"),
  CATEGORIES("Categories"),
  FORECAST("Forecast"),
}

@Immutable
data class InsightsUiState(
  val isLoading: Boolean = true,
  val selectedTab: InsightsTab = InsightsTab.OVERVIEW,
  val categoryTypeMode: TransactionType = TransactionType.EXPENSE,
  val selectedPreset: PeriodPreset = PeriodPreset.THIS_MONTH,
  val resolvedPeriod: Period = Period.monthToDate(LocalDate.now()),
  val summary: PeriodSummary = LedgerAnalytics.summarizePeriod(emptyList(), Period.monthToDate(LocalDate.now())),
  val expenseCategories: List<CategoryBreakdownItem> = emptyList(),
  val incomeCategories: List<CategoryBreakdownItem> = emptyList(),
  val salaryExpenseSplit: SalaryExpenseSplitSummary =
    SalaryExpenseSplitSummary(
      period = Period.monthToDate(LocalDate.now()),
      totalExpensePaise = 0L,
      salaryExpensePaise = 0L,
      nonSalaryExpensePaise = 0L,
      salaryCount = 0,
      nonSalaryCount = 0,
      items = emptyList(),
    ),
  val fundingSplit: FundingSplitSummary = FundingSplitSummary(Period.monthToDate(LocalDate.now()), 0L, emptyList()),
  val memberSummaries: List<MemberSummaryItem> = emptyList(),
  val comparison: LikeForLikeComparison =
    LedgerAnalytics.compareLikeForLike(emptyList(), emptyList(), PeriodPreset.THIS_MONTH, LocalDate.now()),
  val forecast: MonthEndForecast = LedgerAnalytics.forecastMonthEnd(emptyList(), LocalDate.now()),
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
  familyRepository: FamilyRepository,
  ledgerRepository: LedgerRepository,
  sessionRepository: SessionRepository,
  private val periodStore: SelectedPeriodStore,
  private val filterCoordinator: RecordsFilterCoordinator,
) : ViewModel() {

  private val selectedTab = MutableStateFlow(InsightsTab.OVERVIEW)
  private val categoryTypeMode = MutableStateFlow(TransactionType.EXPENSE)

  private val workspaceFlow =
    combine(
      sessionRepository.observeCurrentMember(),
      familyRepository.observeMembers(),
      familyRepository.observeCategories(),
      ledgerRepository.observeTransactions(),
    ) { _, members, categories, transactions ->
      InsightsWorkspaceSnapshot(members, categories, transactions)
    }

  val uiState: StateFlow<InsightsUiState> =
    combine(
      workspaceFlow,
      periodStore.state,
      selectedTab,
      categoryTypeMode,
    ) { workspace: InsightsWorkspaceSnapshot, selection: PeriodSelectionState, tab: InsightsTab, catMode: TransactionType ->
      val today = LocalDate.now()
      val period = selection.resolve(today)
      InsightsUiState(
        isLoading = false,
        selectedTab = tab,
        categoryTypeMode = catMode,
        selectedPreset = selection.preset,
        resolvedPeriod = period,
        summary = LedgerAnalytics.summarizePeriod(workspace.transactions, period),
        expenseCategories =
          LedgerAnalytics.categoryBreakdown(
            transactions = workspace.transactions,
            categories = workspace.categories,
            period = period,
            type = TransactionType.EXPENSE,
          ),
        incomeCategories =
          LedgerAnalytics.categoryBreakdown(
            transactions = workspace.transactions,
            categories = workspace.categories,
            period = period,
            type = TransactionType.INCOME,
          ),
        salaryExpenseSplit = LedgerAnalytics.salaryExpenseSplit(workspace.transactions, period),
        fundingSplit = LedgerAnalytics.fundingSplit(workspace.transactions, period),
        memberSummaries =
          LedgerAnalytics.memberSummary(workspace.transactions, workspace.members, period),
        comparison =
          LedgerAnalytics.compareLikeForLike(
            transactions = workspace.transactions,
            categories = workspace.categories,
            preset = selection.preset,
            referenceDate = today,
            customStart = selection.customStart,
            customEnd = selection.customEnd,
          ),
        forecast = LedgerAnalytics.forecastMonthEnd(workspace.transactions, today),
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = InsightsUiState(),
      )

  fun selectTab(tab: InsightsTab) {
    selectedTab.value = tab
  }

  fun selectCategoryMode(mode: TransactionType) {
    categoryTypeMode.value = mode
  }

  fun selectPreset(preset: PeriodPreset) = periodStore.selectPreset(preset)

  fun selectCustomRange(start: LocalDate, end: LocalDate) = periodStore.selectCustomRange(start, end)

  fun openTypeFilterRecords(type: TransactionType?) {
    val base =
      when (type) {
        TransactionType.INCOME -> RecordsFilter.INCOME
        TransactionType.EXPENSE -> RecordsFilter.EXPENSE
        null -> RecordsFilter.ALL
      }
    filterCoordinator.apply(
      RecordsDrilldownFilter(
        baseFilter = base,
        period = uiState.value.resolvedPeriod,
      )
    )
  }

  fun openCategoryRecords(item: CategoryBreakdownItem) {
    val base =
      if (item.type == TransactionType.INCOME) RecordsFilter.INCOME else RecordsFilter.EXPENSE
    filterCoordinator.apply(
      RecordsDrilldownFilter(
        baseFilter = base,
        categoryId = item.categoryId,
        categoryName = item.categoryName,
        period = uiState.value.resolvedPeriod,
      )
    )
  }

  fun openComparisonCategoryRecords(item: CategoryComparisonItem) {
    filterCoordinator.apply(
      RecordsDrilldownFilter(
        baseFilter = RecordsFilter.EXPENSE,
        categoryId = item.categoryId,
        categoryName = item.categoryName,
        period = uiState.value.comparison.currentWindow,
      )
    )
  }

  fun openFundingSourceRecords(source: FundingSource) {
    filterCoordinator.apply(
      RecordsDrilldownFilter(
        baseFilter = RecordsFilter.EXPENSE,
        fundingSource = source,
        period = uiState.value.resolvedPeriod,
      )
    )
  }

  fun openSalaryClassificationRecords(classification: ExpenseClassification) {
    filterCoordinator.apply(
      RecordsDrilldownFilter(
        baseFilter =
          if (classification == ExpenseClassification.SALARY) RecordsFilter.SALARY_EXPENSE
          else RecordsFilter.NON_SALARY_EXPENSE,
        expenseClassification = classification,
        period = uiState.value.resolvedPeriod,
      )
    )
  }

  fun openMemberRecords(member: MemberSummaryItem) {
    filterCoordinator.apply(
      RecordsDrilldownFilter(
        baseFilter = RecordsFilter.ALL,
        paidByUid = member.uid,
        paidByName = member.displayName,
        period = uiState.value.resolvedPeriod,
      )
    )
  }

  private data class InsightsWorkspaceSnapshot(
    val members: List<Member>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
  )
}

@Composable
fun InsightsRouteScreen(
  onOpenRecords: () -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: InsightsViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  InsightsScreen(
    state = state,
    onSelectTab = viewModel::selectTab,
    onSelectCategoryMode = viewModel::selectCategoryMode,
    onSelectPreset = viewModel::selectPreset,
    onSelectCustomRange = viewModel::selectCustomRange,
    onBarTap = { type ->
      viewModel.openTypeFilterRecords(type)
      onOpenRecords()
    },
    onCategoryTap = { item ->
      viewModel.openCategoryRecords(item)
      onOpenRecords()
    },
    onComparisonCategoryTap = { item ->
      viewModel.openComparisonCategoryRecords(item)
      onOpenRecords()
    },
    onFundingSourceTap = { source ->
      viewModel.openFundingSourceRecords(source)
      onOpenRecords()
    },
    onSalaryClassificationTap = { classification ->
      viewModel.openSalaryClassificationRecords(classification)
      onOpenRecords()
    },
    onMemberTap = { member ->
      viewModel.openMemberRecords(member)
      onOpenRecords()
    },
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
  state: InsightsUiState,
  onSelectTab: (InsightsTab) -> Unit = {},
  onSelectCategoryMode: (TransactionType) -> Unit = {},
  onSelectPreset: (PeriodPreset) -> Unit = {},
  onSelectCustomRange: (LocalDate, LocalDate) -> Unit = { _, _ -> },
  onBarTap: (TransactionType?) -> Unit = {},
  onCategoryTap: (CategoryBreakdownItem) -> Unit = {},
  onComparisonCategoryTap: (CategoryComparisonItem) -> Unit = {},
  onFundingSourceTap: (FundingSource) -> Unit = {},
  onSalaryClassificationTap: (ExpenseClassification) -> Unit = {},
  onMemberTap: (MemberSummaryItem) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  val displayPeriod =
    androidx.compose.runtime.remember(state.selectedPreset, state.resolvedPeriod, language) {
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
  val displaySummary =
    androidx.compose.runtime.remember(state.summary, displayPeriod) {
      state.summary.copy(period = displayPeriod)
    }
  val displayComparison =
    androidx.compose.runtime.remember(state.comparison, state.selectedPreset, state.resolvedPeriod, language) {
      if (language == AppLanguage.HINDI) {
        val (curW, prevW) =
          PeriodResolver.comparisonWindows(
            preset = state.selectedPreset,
            referenceDate = LocalDate.now(),
            customStart = state.resolvedPeriod.startDate,
            customEnd = state.resolvedPeriod.endDate,
            languageTag = language.tag,
          )
        state.comparison.copy(
          currentWindow = curW,
          previousWindow = prevW,
          windowDescription = "${curW.label} बनाम ${prevW.label}",
        )
      } else {
        state.comparison
      }
    }
  val displayForecast =
    androidx.compose.runtime.remember(state.forecast, language) {
      if (language == AppLanguage.HINDI) {
        val f = state.forecast
        val monthNameHi = f.month.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("hi-IN"))
        f.copy(
          estimateLabel = "माह के अंत का अनुमानित खर्च (दैनिक दर)",
          disclaimer =
            "$monthNameHi के ${f.totalDaysInMonth} में से ${f.elapsedDays} दिनों के आधार पर दैनिक दर का अनुमान (रद्द रिकॉर्ड शामिल नहीं)।",
        )
      } else {
        state.forecast
      }
    }

  Box(
    modifier = modifier.fillMaxSize().testTag("screen_insights"),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth().testTag("insights_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item(key = "header_title") {
        Text(
          text = stringResource(R.string.insights_heading),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.semantics { heading() },
        )
      }

      item(key = "period_selector") {
        PeriodSelectorRow(
          selectedPreset = state.selectedPreset,
          resolvedPeriod = displayPeriod,
          onSelectPreset = onSelectPreset,
          onSelectCustomRange = onSelectCustomRange,
        )
      }

      item(key = "tab_row") {
        PrimaryTabRow(
          selectedTabIndex = state.selectedTab.ordinal,
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = GrihaTheme.colors.good,
          modifier = Modifier.fillMaxWidth().testTag("insights_tab_row"),
        ) {
          InsightsTab.entries.forEach { tab ->
            Tab(
              selected = tab == state.selectedTab,
              onClick = { onSelectTab(tab) },
              text = {
                Text(
                  text = LocalizedLabels.insightsTab(tab, language),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  fontWeight = if (tab == state.selectedTab) FontWeight.Bold else FontWeight.Medium,
                )
              },
              modifier = Modifier.testTag("insights_tab_${tab.name.lowercase()}"),
            )
          }
        }
      }

      if (state.isLoading) {
        item(key = "insights_skeleton") {
          InsightsSkeletonContent()
        }
      } else {
        item(key = "tab_content_${state.selectedTab.name}") {
          when (state.selectedTab) {
            InsightsTab.OVERVIEW ->
              InsightsOverviewTabContent(
                summary = displaySummary,
                salarySplit = state.salaryExpenseSplit,
                fundingSplit = state.fundingSplit,
                memberSummaries = state.memberSummaries,
                onBarTap = onBarTap,
                onSalaryClassificationTap = onSalaryClassificationTap,
                onFundingSourceTap = onFundingSourceTap,
                onMemberTap = onMemberTap,
              )

            InsightsTab.COMPARISON ->
              InsightsComparisonTabContent(
                comparison = displayComparison,
                onCategoryTap = onComparisonCategoryTap,
              )

            InsightsTab.CATEGORIES ->
              InsightsCategoriesTabContent(
                typeMode = state.categoryTypeMode,
                expenseItems = state.expenseCategories,
                incomeItems = state.incomeCategories,
                periodLabel = displayPeriod.label,
                onSelectMode = onSelectCategoryMode,
                onCategoryTap = onCategoryTap,
              )

            InsightsTab.FORECAST ->
              InsightsForecastTabContent(forecast = displayForecast)
          }
        }
      }

      item(key = "bottom_spacer") {
        Spacer(modifier = Modifier.size(72.dp))
      }
    }
  }
}
