package com.example.ui.analytics

import com.example.domain.analytics.PeriodPreset
import com.example.domain.analytics.PeriodResolver
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.Period
import com.example.ui.screens.RecordsFilter
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PeriodSelectionState(
  val preset: PeriodPreset = PeriodPreset.THIS_MONTH,
  val customStart: LocalDate? = null,
  val customEnd: LocalDate? = null,
) {
  fun resolve(referenceDate: LocalDate = LocalDate.now()): Period =
    PeriodResolver.resolve(
      preset = preset,
      referenceDate = referenceDate,
      customStart = customStart,
      customEnd = customEnd,
    )
}

@Singleton
class SelectedPeriodStore @Inject constructor() {
  private val _state = MutableStateFlow(PeriodSelectionState())
  val state: StateFlow<PeriodSelectionState> = _state.asStateFlow()

  fun selectPreset(preset: PeriodPreset) {
    _state.update { current ->
      if (preset == PeriodPreset.CUSTOM && current.customStart == null) {
        val today = LocalDate.now()
        current.copy(
          preset = PeriodPreset.CUSTOM,
          customStart = today.withDayOfMonth(1),
          customEnd = today,
        )
      } else {
        current.copy(preset = preset)
      }
    }
  }

  fun selectCustomRange(start: LocalDate, end: LocalDate) {
    val orderedEnd = if (end.isBefore(start)) start else end
    _state.value =
      PeriodSelectionState(
        preset = PeriodPreset.CUSTOM,
        customStart = start,
        customEnd = orderedEnd,
      )
  }

  fun reset() {
    _state.value = PeriodSelectionState()
  }
}

data class RecordsDrilldownFilter(
  val baseFilter: RecordsFilter = RecordsFilter.ALL,
  val categoryId: String? = null,
  val categoryName: String? = null,
  val fundingSource: FundingSource? = null,
  val expenseClassification: ExpenseClassification? = null,
  val paidByUid: String? = null,
  val paidByName: String? = null,
  val period: Period? = null,
) {
  val hasActiveDrilldown: Boolean
    get() =
      categoryId != null ||
        fundingSource != null ||
        expenseClassification != null ||
        paidByUid != null ||
        period != null
}

@Singleton
class RecordsFilterCoordinator @Inject constructor() {
  private val _drilldown = MutableStateFlow<RecordsDrilldownFilter?>(null)
  val drilldown: StateFlow<RecordsDrilldownFilter?> = _drilldown.asStateFlow()

  fun apply(filter: RecordsDrilldownFilter) {
    _drilldown.value = filter
  }

  fun clearDrilldown() {
    _drilldown.value = null
  }
}
