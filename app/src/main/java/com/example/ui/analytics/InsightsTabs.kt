package com.example.ui.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.analytics.CategoryBreakdownItem
import com.example.domain.analytics.CategoryComparisonItem
import com.example.domain.analytics.FundingSplitSummary
import com.example.domain.analytics.LedgerAnalytics
import com.example.domain.analytics.LikeForLikeComparison
import com.example.domain.analytics.MemberSummaryItem
import com.example.domain.analytics.MonthEndForecast
import com.example.domain.analytics.PeriodSummary
import com.example.domain.analytics.SalaryExpenseSplitSummary
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.Money
import com.example.domain.model.TransactionType
import com.example.ui.components.EmptyStateCard
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.ledger.CategoryVisuals
import com.example.ui.theme.GrihaTheme

@Composable
fun InsightsOverviewTabContent(
  summary: PeriodSummary,
  salarySplit: SalaryExpenseSplitSummary,
  fundingSplit: FundingSplitSummary,
  memberSummaries: List<MemberSummaryItem>,
  onBarTap: (TransactionType?) -> Unit,
  onSalaryClassificationTap: (ExpenseClassification) -> Unit,
  onFundingSourceTap: (FundingSource) -> Unit,
  onMemberTap: (MemberSummaryItem) -> Unit,
) {
  val language = LocalAppLanguage.current
  if (!summary.hasAnyActivity) {
    EmptyStateCard(
      icon = Icons.Outlined.BarChart,
      badgeText = summary.period.label,
      title = stringResource(R.string.insights_overview_empty_title, summary.period.label),
      description = stringResource(R.string.insights_overview_empty_desc),
      tip = stringResource(R.string.insights_overview_empty_tip),
      testTag = "insights_overview_empty",
    )
    return
  }

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    SectionCard(testTag = "insights_overview_summary_card") {
      Text(
        text = stringResource(R.string.insights_overview_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = stringResource(R.string.insights_tap_to_filter),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      IncomeExpenseDifferenceChart(
        summary = summary,
        onBarTap = onBarTap,
      )
    }

    SectionCard(testTag = "insights_salary_split_card") {
      Text(
        text = stringResource(R.string.insights_salary_split_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      if (salarySplit.totalExpensePaise == 0L) {
        Text(
          text = stringResource(R.string.insights_no_expenses_period),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Text(
          text = stringResource(R.string.insights_salary_split_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val salaryFraction =
          (salarySplit.salaryExpensePaise.toFloat() / salarySplit.totalExpensePaise.toFloat())
            .coerceIn(0f, 1f)
        val nonSalaryFraction = (1f - salaryFraction).coerceIn(0f, 1f)
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(10.dp)
              .clip(RoundedCornerShape(5.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerHigh),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          if (salaryFraction > 0f) {
            Box(
              modifier =
                Modifier
                  .weight(salaryFraction.coerceAtLeast(0.03f))
                  .fillMaxHeight()
                  .background(GrihaTheme.colors.attention),
            )
          }
          if (nonSalaryFraction > 0f) {
            Box(
              modifier =
                Modifier
                  .weight(nonSalaryFraction.coerceAtLeast(0.03f))
                  .fillMaxHeight()
                  .background(GrihaTheme.colors.good),
            )
          }
        }

        salarySplit.items.forEach { item ->
          val dotColor =
            if (item.classification == ExpenseClassification.SALARY) GrihaTheme.colors.attention
            else GrihaTheme.colors.good
          val label = LocalizedLabels.expenseClassification(item.classification, language)
          val shareLabel = LedgerAnalytics.formatBasisPointsPercent(item.shareBps)
          val rowTag =
            if (item.classification == ExpenseClassification.SALARY) {
              "salary_split_row_salary"
            } else {
              "salary_split_row_non_salary"
            }
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { onSalaryClassificationTap(item.classification) }
                .minimumInteractiveComponentSize()
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 4.dp)
                .testTag(rowTag),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(dotColor),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text =
                  stringResource(
                    R.string.insights_salary_split_entries,
                    shareLabel,
                    item.transactionCount,
                  ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text =
                Money(item.amountPaise)
                  .formatEnIn(includePaise = item.amountPaise % 100L != 0L),
              style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }

    SectionCard(testTag = "insights_funding_split_card") {
      Text(
        text = stringResource(R.string.insights_funding_split_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      if (fundingSplit.totalExpensePaise == 0L) {
        Text(
          text = stringResource(R.string.insights_no_expenses_period),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        fundingSplit.items.forEach { item ->
          val barColor =
            when (item.source) {
              FundingSource.HOUSEHOLD -> GrihaTheme.colors.good
              FundingSource.FAMILY_CARD -> GrihaTheme.colors.brandForest
              FundingSource.PERSONAL -> GrihaTheme.colors.attention
            }
          val shareLabel = LedgerAnalytics.formatBasisPointsPercent(item.shareBps)
          val fraction =
            (item.amountPaise.toFloat() / fundingSplit.totalExpensePaise.toFloat()).coerceIn(0f, 1f)
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { onFundingSourceTap(item.source) }
                .minimumInteractiveComponentSize()
                .padding(vertical = 4.dp)
                .testTag("funding_row_${item.source.name.lowercase()}"),
            verticalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "${LocalizedLabels.fundingSource(item.source, language)} ($shareLabel)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
              )
              Text(
                text = Money(item.amountPaise).formatEnIn(includePaise = item.amountPaise % 100L != 0L),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
              )
            }
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .height(7.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
              if (item.amountPaise > 0L) {
                Box(
                  modifier =
                    Modifier
                      .fillMaxWidth(fraction.coerceAtLeast(0.04f))
                      .fillMaxHeight()
                      .clip(RoundedCornerShape(4.dp))
                      .background(barColor),
                )
              }
            }
          }
        }
      }
    }

    SectionCard(testTag = "insights_member_summary_card") {
      Text(
        text = stringResource(R.string.insights_member_summary_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = stringResource(R.string.insights_member_summary_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      memberSummaries.forEach { member ->
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(MaterialTheme.shapes.small)
              .clickable { onMemberTap(member) }
              .minimumInteractiveComponentSize()
              .semantics(mergeDescendants = true) {}
              .padding(vertical = 6.dp)
              .testTag("member_summary_row_${member.uid}"),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = member.displayName,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                stringResource(
                  R.string.insights_member_spend_meta,
                  member.expenseCount,
                  LedgerAnalytics.formatBasisPointsPercent(member.expenseShareBps),
                ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text =
                Money(member.paidExpensePaise)
                  .formatEnIn(includePaise = member.paidExpensePaise % 100L != 0L),
              style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
              fontWeight = FontWeight.Bold,
            )
            if (member.receivedIncomePaise > 0L) {
              val incStr =
                Money(member.receivedIncomePaise)
                  .formatEnIn(includePaise = member.receivedIncomePaise % 100L != 0L)
              Text(
                text = stringResource(R.string.insights_member_income_suffix, incStr),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = GrihaTheme.colors.good,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun InsightsComparisonTabContent(
  comparison: LikeForLikeComparison,
  onCategoryTap: (CategoryComparisonItem) -> Unit,
) {
  val language = LocalAppLanguage.current
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = GrihaTheme.colors.goodContainer,
      modifier = Modifier.fillMaxWidth().testTag("comparison_window_banner"),
    ) {
      Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
          contentDescription = null,
          tint = GrihaTheme.colors.onGoodContainer,
        )
        Column {
          Text(
            text = stringResource(R.string.insights_comparison_window_title),
            style = MaterialTheme.typography.labelSmall,
            color = GrihaTheme.colors.onGoodContainer.copy(alpha = 0.85f),
          )
          Text(
            text = comparison.windowDescription,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = GrihaTheme.colors.onGoodContainer,
          )
        }
      }
    }

    SectionCard(testTag = "comparison_chart_card") {
      ComparisonGroupedBarChart(comparison = comparison)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DeltaPill(
          label = stringResource(R.string.insights_expense_change),
          deltaPaise = comparison.expenseDeltaPaise,
          bps = comparison.expenseDeltaBps,
          invertTone = true,
          modifier = Modifier.weight(1f),
        )
        DeltaPill(
          label = stringResource(R.string.insights_difference_change),
          deltaPaise = comparison.differenceDeltaPaise,
          bps = null,
          invertTone = false,
          modifier = Modifier.weight(1f),
        )
      }
    }

    if (comparison.categoryComparisons.isEmpty()) {
      EmptyStateCard(
        icon = Icons.AutoMirrored.Outlined.CompareArrows,
        badgeText = comparison.windowDescription,
        title = stringResource(R.string.insights_comparison_empty_title),
        description = stringResource(R.string.insights_comparison_empty_desc, comparison.windowDescription),
        tip = stringResource(R.string.insights_comparison_empty_tip),
        testTag = "comparison_empty_state",
      )
    } else {
      SectionCard(testTag = "comparison_categories_card") {
        Text(
          text = stringResource(R.string.insights_category_changes_title, comparison.windowDescription),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        comparison.categoryComparisons.forEach { row ->
          val accent = Color(row.colorHex)
          val catName = LocalizedLabels.categoryNameById(row.categoryId, row.categoryName, language)
          val deltaText =
            Money(row.deltaPaise).formatEnIn(
              includePaise = row.deltaPaise % 100L != 0L,
              showPlusSign = true,
            )
          val deltaColor =
            when {
              row.deltaPaise > 0L -> GrihaTheme.colors.overBudget
              row.deltaPaise < 0L -> GrihaTheme.colors.good
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { onCategoryTap(row) }
                .minimumInteractiveComponentSize()
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 6.dp)
                .testTag("comparison_category_${row.categoryId}"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(accent.copy(alpha = 0.14f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = CategoryVisuals.iconForKey(row.iconKey),
                contentDescription = catName,
                tint = accent,
                modifier = Modifier.size(18.dp),
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = catName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
              val prevStr =
                Money(row.previousPaise).formatEnIn(includePaise = row.previousPaise % 100L != 0L)
              val curStr =
                Money(row.currentPaise).formatEnIn(includePaise = row.currentPaise % 100L != 0L)
              Text(
                text = stringResource(R.string.insights_prev_current_row, prevStr, curStr),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = deltaText,
              style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
              fontWeight = FontWeight.Bold,
              color = deltaColor,
            )
          }
        }
      }
    }
  }
}

@Composable
fun InsightsCategoriesTabContent(
  typeMode: TransactionType,
  expenseItems: List<CategoryBreakdownItem>,
  incomeItems: List<CategoryBreakdownItem>,
  periodLabel: String,
  onSelectMode: (TransactionType) -> Unit,
  onCategoryTap: (CategoryBreakdownItem) -> Unit,
) {
  val language = LocalAppLanguage.current
  val items = if (typeMode == TransactionType.EXPENSE) expenseItems else incomeItems
  val totalPaise = items.sumOf { it.amountPaise }
  var show3dView by rememberSaveable { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = typeMode == TransactionType.EXPENSE,
          onClick = { onSelectMode(TransactionType.EXPENSE) },
          label = { Text(stringResource(R.string.home_label_expense)) },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = GrihaTheme.colors.goodContainer,
              selectedLabelColor = GrihaTheme.colors.onGoodContainer,
            ),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("categories_mode_expense"),
        )
        FilterChip(
          selected = typeMode == TransactionType.INCOME,
          onClick = { onSelectMode(TransactionType.INCOME) },
          label = { Text(stringResource(R.string.home_label_income)) },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = GrihaTheme.colors.goodContainer,
              selectedLabelColor = GrihaTheme.colors.onGoodContainer,
            ),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("categories_mode_income"),
        )
      }

      if (items.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(
            selected = !show3dView,
            onClick = { show3dView = false },
            label = { Text(stringResource(R.string.insights_view_donut)) },
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.PieChart,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("categories_view_donut"),
          )
          FilterChip(
            selected = show3dView,
            onClick = { show3dView = true },
            label = { Text(stringResource(R.string.insights_view_3d)) },
            leadingIcon = {
              Icon(
                imageVector = Icons.Outlined.ViewInAr,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("categories_view_3d"),
          )
        }
      }
    }

    if (items.isEmpty()) {
      val typeWord =
        if (typeMode == TransactionType.EXPENSE) {
          stringResource(R.string.tx_type_expense).lowercase()
        } else {
          stringResource(R.string.tx_type_income).lowercase()
        }
      EmptyStateCard(
        icon = Icons.Outlined.PieChart,
        badgeText = periodLabel,
        title = stringResource(R.string.insights_categories_empty_title, typeWord, periodLabel),
        description = stringResource(R.string.insights_categories_empty_desc, typeWord),
        tip = stringResource(R.string.insights_categories_empty_tip),
        testTag = "categories_empty_state",
      )
    } else {
      SectionCard(testTag = "categories_chart_card") {
        if (show3dView) {
          CategoryIsometric3dChart(
            items = items,
            totalPaise = totalPaise,
            onSliceTap = onCategoryTap,
          )
        } else {
          CategoryDonutChart(
            items = items,
            totalPaise = totalPaise,
            centerSubtitle =
              stringResource(
                if (typeMode == TransactionType.EXPENSE) R.string.insights_total_spent
                else R.string.insights_total_income
              ),
            onSliceTap = onCategoryTap,
          )
        }
        Text(
          text = stringResource(R.string.insights_tap_to_filter),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      SectionCard(testTag = "categories_list_card") {
        items.forEach { item ->
          val accent = Color(item.colorHex)
          val catName = LocalizedLabels.categoryNameById(item.categoryId, item.categoryName, language)
          val share = LedgerAnalytics.formatBasisPointsPercent(item.shareBps)
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { onCategoryTap(item) }
                .minimumInteractiveComponentSize()
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 6.dp)
                .testTag("category_breakdown_row_${item.categoryId}"),
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
                imageVector = CategoryVisuals.iconForKey(item.iconKey),
                contentDescription = catName,
                tint = accent,
                modifier = Modifier.size(18.dp),
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = catName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = stringResource(R.string.insights_entries_count, share, item.transactionCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = Money(item.amountPaise).formatEnIn(includePaise = item.amountPaise % 100L != 0L),
              style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Composable
fun InsightsForecastTabContent(forecast: MonthEndForecast) {
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = GrihaTheme.colors.attentionContainer,
      modifier = Modifier.fillMaxWidth().testTag("insights_forecast_disclaimer"),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = stringResource(R.string.insights_forecast_badge),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = GrihaTheme.colors.onAttentionContainer,
          modifier = Modifier.testTag("insights_forecast_badge"),
        )
        Text(
          text = forecast.disclaimer,
          style = MaterialTheme.typography.bodySmall,
          color = GrihaTheme.colors.onAttentionContainer,
        )
      }
    }

    if (!forecast.hasEnoughData) {
      EmptyStateCard(
        icon = Icons.AutoMirrored.Outlined.TrendingUp,
        badgeText = stringResource(R.string.insights_forecast_empty_badge),
        title = stringResource(R.string.insights_forecast_empty_title),
        description = stringResource(R.string.insights_forecast_empty_desc),
        tip = stringResource(R.string.insights_forecast_empty_tip),
        testTag = "forecast_empty_state",
      )
      return
    }

    SectionCard(testTag = "forecast_summary_card") {
      Text(
        text = forecast.estimateLabel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text =
          Money(forecast.projectedMonthEndExpensePaise)
            .formatEnIn(includePaise = forecast.projectedMonthEndExpensePaise % 100L != 0L),
        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("forecast_projected_spend"),
      )

      ForecastRunRateChart(
        points = forecast.trajectoryPoints,
        elapsedDays = forecast.elapsedDays,
      )

      Spacer(modifier = Modifier.size(4.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatBox(
          label = stringResource(R.string.insights_forecast_daily_rate),
          value =
            Money(forecast.dailyRunRatePaise)
              .formatEnIn(includePaise = forecast.dailyRunRatePaise % 100L != 0L),
          tag = "forecast_daily_rate",
          modifier = Modifier.weight(1f),
        )
        StatBox(
          label = stringResource(R.string.insights_forecast_spent_to_date),
          value =
            Money(forecast.spentToDatePaise)
              .formatEnIn(includePaise = forecast.spentToDatePaise % 100L != 0L),
          tag = "forecast_spent_to_date",
          modifier = Modifier.weight(1f),
        )
        StatBox(
          label = stringResource(R.string.insights_forecast_days_left),
          value = stringResource(R.string.insights_forecast_days_value, forecast.remainingDays),
          tag = "forecast_days_left",
          modifier = Modifier.weight(0.75f),
        )
      }
    }
  }
}

@Composable
private fun SectionCard(
  testTag: String,
  content: @Composable () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().testTag(testTag),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      content()
    }
  }
}

@Composable
private fun DeltaPill(
  label: String,
  deltaPaise: Long,
  bps: Int?,
  invertTone: Boolean,
  modifier: Modifier = Modifier,
) {
  val positiveIsGood = if (invertTone) deltaPaise <= 0L else deltaPaise >= 0L
  val container =
    if (positiveIsGood) GrihaTheme.colors.goodContainer else GrihaTheme.colors.overBudgetContainer
  val content =
    if (positiveIsGood) GrihaTheme.colors.onGoodContainer else GrihaTheme.colors.onOverBudgetContainer
  val deltaStr =
    Money(deltaPaise).formatEnIn(includePaise = deltaPaise % 100L != 0L, showPlusSign = true)
  val pctSuffix =
    bps?.let { " (${if (it > 0) "+" else ""}${LedgerAnalytics.formatBasisPointsPercent(it)})" }.orEmpty()

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = container,
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = content.copy(alpha = 0.8f),
      )
      Text(
        text = deltaStr + pctSuffix,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Bold,
        color = content,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun StatBox(
  label: String,
  value: String,
  tag: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(tag),
      )
    }
  }
}
