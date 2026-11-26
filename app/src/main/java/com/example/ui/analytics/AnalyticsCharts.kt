package com.example.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.analytics.CategoryBreakdownItem
import com.example.domain.analytics.ForecastDayPoint
import com.example.domain.analytics.LikeForLikeComparison
import com.example.domain.analytics.PeriodSummary
import com.example.domain.model.Money
import com.example.domain.model.TransactionType
import com.example.ui.theme.GrihaTheme
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun IncomeExpenseDifferenceChart(
  summary: PeriodSummary,
  onBarTap: (TransactionType?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val incomeColor = GrihaTheme.colors.good
  val expenseColor = GrihaTheme.colors.overBudget
  val differenceColor =
    if (summary.differencePaise >= 0L) GrihaTheme.colors.brandForest
    else GrihaTheme.colors.attention
  val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

  val maxAbs =
    maxOf(
      summary.incomePaise,
      summary.expensePaise,
      kotlin.math.abs(summary.differencePaise),
      1L,
    )

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Canvas(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(150.dp)
          .pointerInput(summary) {
            detectTapGestures { offset ->
              val slotWidth = size.width / 3f
              when {
                offset.x < slotWidth -> onBarTap(TransactionType.INCOME)
                offset.x < slotWidth * 2f -> onBarTap(TransactionType.EXPENSE)
                else -> onBarTap(null)
              }
            }
          }
          .testTag("overview_bar_chart_canvas"),
    ) {
      val baselineY = size.height - 10.dp.toPx()
      val chartHeight = baselineY - 12.dp.toPx()
      drawLine(
        color = gridColor,
        start = Offset(0f, baselineY),
        end = Offset(size.width, baselineY),
        strokeWidth = 1.5.dp.toPx(),
      )
      drawLine(
        color = gridColor.copy(alpha = 0.25f),
        start = Offset(0f, baselineY - chartHeight * 0.5f),
        end = Offset(size.width, baselineY - chartHeight * 0.5f),
        strokeWidth = 1.dp.toPx(),
      )

      val values =
        listOf(
          summary.incomePaise to incomeColor,
          summary.expensePaise to expenseColor,
          kotlin.math.abs(summary.differencePaise) to differenceColor,
        )
      val slotWidth = size.width / 3f
      val barWidth = (slotWidth * 0.46f).coerceAtMost(56.dp.toPx())

      values.forEachIndexed { idx, (valuePaise, color) ->
        val ratio = (valuePaise.toFloat() / maxAbs.toFloat()).coerceIn(0f, 1f)
        val barH = if (valuePaise == 0L) 4.dp.toPx() else (chartHeight * ratio).coerceAtLeast(8.dp.toPx())
        val left = (idx * slotWidth) + (slotWidth - barWidth) / 2f
        val top = baselineY - barH
        drawRoundRect(
          color = color,
          topLeft = Offset(left, top),
          size = Size(barWidth, barH),
          cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      ChartBarLegendChip(
        label = stringResource(R.string.home_label_income),
        amount = Money(summary.incomePaise).formatEnIn(includePaise = summary.incomePaise % 100L != 0L),
        dotColor = incomeColor,
        onClick = { onBarTap(TransactionType.INCOME) },
        testTag = "overview_bar_income",
        modifier = Modifier.weight(1f),
      )
      ChartBarLegendChip(
        label = stringResource(R.string.home_label_expense),
        amount = Money(summary.expensePaise).formatEnIn(includePaise = summary.expensePaise % 100L != 0L),
        dotColor = expenseColor,
        onClick = { onBarTap(TransactionType.EXPENSE) },
        testTag = "overview_bar_expense",
        modifier = Modifier.weight(1f),
      )
      ChartBarLegendChip(
        label = stringResource(R.string.home_label_difference),
        amount =
          Money(summary.differencePaise).formatEnIn(
            includePaise = summary.differencePaise % 100L != 0L,
            showPlusSign = true,
          ),
        dotColor = differenceColor,
        onClick = { onBarTap(null) },
        testTag = "overview_bar_difference",
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun ChartBarLegendChip(
  label: String,
  amount: String,
  dotColor: Color,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier =
      modifier
        .clip(MaterialTheme.shapes.small)
        .clickable(onClick = onClick)
        .minimumInteractiveComponentSize()
        .testTag(testTag),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = amount,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun CategoryDonutChart(
  items: List<CategoryBreakdownItem>,
  totalPaise: Long,
  centerSubtitle: String,
  onSliceTap: (CategoryBreakdownItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val ringTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val totalFormatted =
    Money(totalPaise).formatEnIn(includePaise = totalPaise % 100L != 0L)

  Box(
    modifier = modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center,
  ) {
    val totalSlices = items.sumOf { it.amountPaise }.coerceAtLeast(1L)
    Canvas(
      modifier =
        Modifier
          .size(210.dp)
          .pointerInput(items) {
            detectTapGestures { offset ->
              val center = Offset(size.width / 2f, size.height / 2f)
              val dx = offset.x - center.x
              val dy = offset.y - center.y
              val distance = hypot(dx, dy)
              val outerRadius = size.width / 2f
              val innerRadius = outerRadius * 0.52f
              if (distance in innerRadius..outerRadius && items.isNotEmpty()) {
                var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                angleDeg = (angleDeg + 90f + 360f) % 360f
                var runningSweep = 0f
                for (item in items) {
                  val sweep = (item.amountPaise.toFloat() / totalSlices.toFloat()) * 360f
                  if (angleDeg >= runningSweep && angleDeg <= runningSweep + sweep) {
                    onSliceTap(item)
                    break
                  }
                  runningSweep += sweep
                }
              }
            }
          }
          .testTag("categories_donut_canvas"),
    ) {
      val strokeWidth = 30.dp.toPx()
      val diameter = size.minDimension - strokeWidth
      val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
      val arcSize = Size(diameter, diameter)

      if (items.isEmpty() || totalPaise <= 0L) {
        drawArc(
          color = ringTrackColor,
          startAngle = 0f,
          sweepAngle = 360f,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth),
        )
      } else {
        var startAngle = -90f
        val gap = if (items.size > 1) 2.5f else 0f
        items.forEach { item ->
          val fullSweep = (item.amountPaise.toFloat() / totalSlices.toFloat()) * 360f
          val sweep = (fullSweep - gap).coerceAtLeast(1.5f)
          drawArc(
            color = Color(item.colorHex),
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
          )
          startAngle += fullSweep
        }
      }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = centerSubtitle,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = totalFormatted,
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
fun ComparisonGroupedBarChart(
  comparison: LikeForLikeComparison,
  modifier: Modifier = Modifier,
) {
  val prevColor = MaterialTheme.colorScheme.outline
  val curColor = GrihaTheme.colors.good
  val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

  val pairs =
    listOf(
      stringResource(R.string.home_label_income) to
        (comparison.previousSummary.incomePaise to comparison.currentSummary.incomePaise),
      stringResource(R.string.home_label_expense) to
        (comparison.previousSummary.expensePaise to comparison.currentSummary.expensePaise),
      stringResource(R.string.home_label_difference) to
        (kotlin.math.abs(comparison.previousSummary.differencePaise) to
          kotlin.math.abs(comparison.currentSummary.differencePaise)),
    )
  val maxVal =
    pairs.maxOfOrNull { (_, p) -> maxOf(p.first, p.second) }?.coerceAtLeast(1L) ?: 1L

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Canvas(
      modifier = Modifier.fillMaxWidth().height(150.dp).testTag("comparison_bar_chart_canvas"),
    ) {
      val baselineY = size.height - 10.dp.toPx()
      val chartHeight = baselineY - 12.dp.toPx()
      drawLine(
        color = gridColor,
        start = Offset(0f, baselineY),
        end = Offset(size.width, baselineY),
        strokeWidth = 1.5.dp.toPx(),
      )

      val slotWidth = size.width / pairs.size
      val barWidth = (slotWidth * 0.25f).coerceAtMost(28.dp.toPx())
      val gap = 6.dp.toPx()

      pairs.forEachIndexed { idx, (_, values) ->
        val (prevVal, curVal) = values
        val centerX = (idx * slotWidth) + (slotWidth / 2f)

        val prevRatio = (prevVal.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
        val prevH = if (prevVal == 0L) 4.dp.toPx() else (chartHeight * prevRatio).coerceAtLeast(8.dp.toPx())
        drawRoundRect(
          color = prevColor.copy(alpha = 0.65f),
          topLeft = Offset(centerX - barWidth - (gap / 2f), baselineY - prevH),
          size = Size(barWidth, prevH),
          cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )

        val curRatio = (curVal.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
        val curH = if (curVal == 0L) 4.dp.toPx() else (chartHeight * curRatio).coerceAtLeast(8.dp.toPx())
        drawRoundRect(
          color = curColor,
          topLeft = Offset(centerX + (gap / 2f), baselineY - curH),
          size = Size(barWidth, curH),
          cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceAround,
    ) {
      pairs.forEach { (label, _) ->
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.Medium,
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
      LegendDotLabel(color = prevColor.copy(alpha = 0.65f), label = comparison.previousWindow.label)
      LegendDotLabel(color = curColor, label = comparison.currentWindow.label)
    }
  }
}

@Composable
fun ForecastRunRateChart(
  points: List<ForecastDayPoint>,
  elapsedDays: Int,
  modifier: Modifier = Modifier,
) {
  val actualColor = GrihaTheme.colors.good
  val projectedColor = GrihaTheme.colors.attention
  val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
  val maxPaise =
    points
      .maxOfOrNull { maxOf(it.actualCumulativePaise ?: 0L, it.projectedCumulativePaise) }
      ?.coerceAtLeast(1L) ?: 1L

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Canvas(
      modifier = Modifier.fillMaxWidth().height(150.dp).testTag("forecast_chart_canvas"),
    ) {
      if (points.size < 2) return@Canvas
      val pad = 10.dp.toPx()
      val chartW = size.width - (pad * 2f)
      val chartH = size.height - (pad * 2f)
      val baselineY = size.height - pad

      drawLine(
        color = gridColor,
        start = Offset(pad, baselineY),
        end = Offset(size.width - pad, baselineY),
        strokeWidth = 1.5.dp.toPx(),
      )

      fun xForIdx(index: Int): Float =
        pad + (index.toFloat() / (points.size - 1).toFloat()) * chartW

      fun yForPaise(paise: Long): Float {
        val ratio = (paise.toFloat() / maxPaise.toFloat()).coerceIn(0f, 1f)
        return baselineY - (ratio * chartH)
      }

      // Projected linear run-rate path (dashed)
      val projectedPath = Path()
      points.forEachIndexed { idx, pt ->
        val x = xForIdx(idx)
        val y = yForPaise(pt.projectedCumulativePaise)
        if (idx == 0) projectedPath.moveTo(x, y) else projectedPath.lineTo(x, y)
      }
      drawPath(
        path = projectedPath,
        color = projectedColor,
        style =
          Stroke(
            width = 2.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f),
            cap = StrokeCap.Round,
          ),
      )

      // Actual cumulative spending up to reference date (solid)
      val actualPoints = points.mapIndexedNotNull { idx, pt ->
        pt.actualCumulativePaise?.let { idx to it }
      }
      if (actualPoints.isNotEmpty()) {
        val actualPath = Path()
        actualPoints.forEachIndexed { pos, (idx, paise) ->
          val x = xForIdx(idx)
          val y = yForPaise(paise)
          if (pos == 0) actualPath.moveTo(x, y) else actualPath.lineTo(x, y)
        }
        drawPath(
          path = actualPath,
          color = actualColor,
          style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        val (lastIdx, lastPaise) = actualPoints.last()
        drawCircle(
          color = actualColor,
          radius = 5.dp.toPx(),
          center = Offset(xForIdx(lastIdx), yForPaise(lastPaise)),
        )
      }

      // Vertical marker at today's elapsed day
      val todayIdx = (elapsedDays - 1).coerceIn(0, points.lastIndex)
      val todayX = xForIdx(todayIdx)
      drawLine(
        color = gridColor,
        start = Offset(todayX, pad),
        end = Offset(todayX, baselineY),
        strokeWidth = 1.dp.toPx(),
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
      LegendDotLabel(
        color = actualColor,
        label = stringResource(R.string.insights_chart_actual_legend, elapsedDays),
      )
      LegendDotLabel(
        color = projectedColor,
        label = stringResource(R.string.insights_chart_projected_legend),
      )
    }
  }
}

@Composable
private fun LegendDotLabel(
  color: Color,
  label: String,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier
          .size(9.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(color),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
