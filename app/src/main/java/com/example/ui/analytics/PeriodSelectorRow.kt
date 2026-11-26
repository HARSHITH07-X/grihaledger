package com.example.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.analytics.PeriodPreset
import com.example.domain.analytics.PeriodResolver
import com.example.domain.model.AppLanguage
import com.example.domain.model.Period
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.GrihaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelectorRow(
  selectedPreset: PeriodPreset,
  resolvedPeriod: Period,
  onSelectPreset: (PeriodPreset) -> Unit,
  onSelectCustomRange: (LocalDate, LocalDate) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showCustomRangeDialog by remember { mutableStateOf(false) }
  val language = LocalAppLanguage.current
  val displayPeriodLabel =
    remember(selectedPreset, resolvedPeriod, language) {
      if (language == AppLanguage.HINDI) {
        PeriodResolver.resolve(
          preset = selectedPreset,
          referenceDate = LocalDate.now(),
          customStart = resolvedPeriod.startDate,
          customEnd = resolvedPeriod.endDate,
          languageTag = language.tag,
        ).label
      } else {
        resolvedPeriod.label
      }
    }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(horizontal = 2.dp),
      modifier = Modifier.fillMaxWidth().testTag("period_selector_row"),
    ) {
      items(PeriodPreset.entries, key = { it.name }) { preset ->
        val selected = preset == selectedPreset
        FilterChip(
          selected = selected,
          onClick = {
            if (preset == PeriodPreset.CUSTOM) {
              showCustomRangeDialog = true
            } else {
              onSelectPreset(preset)
            }
          },
          leadingIcon =
            if (preset == PeriodPreset.CUSTOM) {
              {
                Icon(
                  imageVector = Icons.Outlined.DateRange,
                  contentDescription = null,
                )
              }
            } else null,
          label = {
            Text(
              text = LocalizedLabels.periodPresetShort(preset, language),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
          },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = GrihaTheme.colors.goodContainer,
              selectedLabelColor = GrihaTheme.colors.onGoodContainer,
              selectedLeadingIconColor = GrihaTheme.colors.onGoodContainer,
            ),
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .testTag("period_chip_${preset.name.lowercase()}"),
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = displayPeriodLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("active_period_label"),
      )
    }
  }

  if (showCustomRangeDialog) {
    val initialStartMillis =
      resolvedPeriod.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val initialEndMillis =
      resolvedPeriod.endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState =
      rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis,
      )

    DatePickerDialog(
      onDismissRequest = { showCustomRangeDialog = false },
      confirmButton = {
        TextButton(
          onClick = {
            val startMillis = pickerState.selectedStartDateMillis
            val endMillis = pickerState.selectedEndDateMillis ?: startMillis
            if (startMillis != null && endMillis != null) {
              val start =
                Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
              val end =
                Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
              onSelectCustomRange(start, end)
            }
            showCustomRangeDialog = false
          },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("custom_range_apply"),
        ) {
          Text(stringResource(R.string.period_custom_apply))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showCustomRangeDialog = false },
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    ) {
      DateRangePicker(
        state = pickerState,
        title = {
          Text(
            text = stringResource(R.string.period_custom_range_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
          )
        },
        modifier = Modifier.fillMaxWidth().height(420.dp),
      )
    }
  }
}
