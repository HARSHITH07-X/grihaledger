package com.example.ui.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Category
import com.example.domain.model.ExpenseClassification
import com.example.domain.model.FundingSource
import com.example.domain.model.Member
import com.example.domain.model.PaymentMethod
import com.example.domain.model.TransactionType
import com.example.domain.rules.DuplicateWarning
import com.example.domain.rules.QuickEntryTemplate
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.GrihaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun VoiceAndQuickEntryCard(
  quickText: String,
  voiceState: VoiceEntryState,
  onQuickTextChanged: (String) -> Unit,
  onApplyQuickText: () -> Unit,
  onToggleVoiceRecording: () -> Unit,
  onDismissVoiceBanner: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isRecording = voiceState is VoiceEntryState.Recording
  val isTranscribing = voiceState is VoiceEntryState.Transcribing

  Surface(
    shape = MaterialTheme.shapes.large,
    color =
      if (isRecording) GrihaTheme.colors.attentionContainer
      else MaterialTheme.colorScheme.surfaceContainerLow,
    border =
      BorderStroke(
        width = if (isRecording) 1.5.dp else 1.dp,
        color =
          if (isRecording) GrihaTheme.colors.attention
          else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
      ),
    modifier = modifier.fillMaxWidth().testTag("voice_quick_entry_card"),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = onToggleVoiceRecording,
          enabled = !isTranscribing,
          colors =
            ButtonDefaults.buttonColors(
              containerColor =
                if (isRecording) GrihaTheme.colors.overBudget else GrihaTheme.colors.good,
              contentColor =
                if (isRecording) GrihaTheme.colors.onOverBudget else GrihaTheme.colors.onGood,
            ),
          shape = MaterialTheme.shapes.medium,
          modifier =
            Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("voice_entry_mic_button"),
        ) {
          when {
            isTranscribing -> {
              CircularProgressIndicator(
                color = GrihaTheme.colors.onGood,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.size(8.dp))
              Text(
                text = stringResource(R.string.voice_entry_transcribing),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
              )
            }
            isRecording -> {
              val rec = voiceState as VoiceEntryState.Recording
              Icon(
                imageVector = Icons.Outlined.Stop,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.size(8.dp))
              Text(
                text = stringResource(R.string.voice_entry_listening, rec.elapsedSeconds),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
              )
            }
            else -> {
              Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.size(8.dp))
              Text(
                text = stringResource(R.string.voice_entry_tap_cta),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }

      if (voiceState is VoiceEntryState.Recording) {
        val barFraction = voiceState.level.coerceIn(0.08f, 1f)
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
          Box(
            modifier =
              Modifier
                .fillMaxWidth(barFraction)
                .height(6.dp)
                .clip(CircleShape)
                .background(GrihaTheme.colors.good),
          )
        }
      }

      if (voiceState is VoiceEntryState.TranscriptApplied) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = GrihaTheme.colors.goodContainer,
          modifier = Modifier.fillMaxWidth().testTag("voice_transcript_banner"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Outlined.CheckCircle,
              contentDescription = null,
              tint = GrihaTheme.colors.onGoodContainer,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = stringResource(R.string.voice_entry_heard, voiceState.transcript),
              style = MaterialTheme.typography.bodySmall,
              color = GrihaTheme.colors.onGoodContainer,
              modifier = Modifier.weight(1f),
            )
            TextButton(
              onClick = onDismissVoiceBanner,
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Text(stringResource(R.string.action_ok))
            }
          }
        }
      } else if (voiceState is VoiceEntryState.Error) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = GrihaTheme.colors.overBudgetContainer,
          modifier = Modifier.fillMaxWidth().testTag("voice_error_banner"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = voiceState.message,
              style = MaterialTheme.typography.bodySmall,
              color = GrihaTheme.colors.onOverBudgetContainer,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismissVoiceBanner) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.action_dismiss),
                tint = GrihaTheme.colors.onOverBudgetContainer,
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = quickText,
          onValueChange = onQuickTextChanged,
          singleLine = true,
          label = { Text(stringResource(R.string.quick_entry_label)) },
          placeholder = { Text(stringResource(R.string.quick_entry_placeholder)) },
          modifier = Modifier.weight(1f).testTag("quick_text_input"),
        )
        Button(
          onClick = onApplyQuickText,
          enabled = quickText.isNotBlank(),
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("quick_text_apply_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Bolt,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.size(4.dp))
          Text(stringResource(R.string.quick_entry_apply))
        }
      }
    }
  }
}

@Composable
fun QuickRepeatTemplatesRow(
  templates: List<QuickEntryTemplate>,
  onApplyTemplate: (QuickEntryTemplate) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (templates.isEmpty()) return
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = stringResource(R.string.quick_templates_title),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth().testTag("quick_templates_row"),
    ) {
      items(templates, key = { it.id }) { tpl ->
        AssistChip(
          onClick = { onApplyTemplate(tpl) },
          label = {
            Text(
              text = "${tpl.title} · ${tpl.formattedAmount}",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.Bolt,
              contentDescription = null,
              tint = GrihaTheme.colors.good,
              modifier = Modifier.size(16.dp),
            )
          },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("quick_template_${tpl.id}"),
        )
      }
    }
  }
}

@Composable
fun TransactionTypeToggle(
  selectedType: TransactionType,
  onSelectType: (TransactionType) -> Unit,
  modifier: Modifier = Modifier,
) {
  SingleChoiceSegmentedButtonRow(
    modifier = modifier.fillMaxWidth().testTag("tx_type_toggle")
  ) {
    SegmentedButton(
      selected = selectedType == TransactionType.EXPENSE,
      onClick = { onSelectType(TransactionType.EXPENSE) },
      shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
      colors =
        SegmentedButtonDefaults.colors(
          activeContainerColor = GrihaTheme.colors.brandForest,
          activeContentColor = GrihaTheme.colors.onBrandForest,
        ),
      modifier = Modifier.minimumInteractiveComponentSize().testTag("tx_type_expense"),
    ) {
      Text(stringResource(R.string.tx_type_expense), fontWeight = FontWeight.SemiBold)
    }
    SegmentedButton(
      selected = selectedType == TransactionType.INCOME,
      onClick = { onSelectType(TransactionType.INCOME) },
      shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
      colors =
        SegmentedButtonDefaults.colors(
          activeContainerColor = GrihaTheme.colors.good,
          activeContentColor = GrihaTheme.colors.onGood,
        ),
      modifier = Modifier.minimumInteractiveComponentSize().testTag("tx_type_income"),
    ) {
      Text(stringResource(R.string.tx_type_income), fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
fun AmountDisplayAndKeypad(
  formattedAmount: String,
  type: TransactionType,
  onDigitOrDot: (String) -> Unit,
  onBackspace: () -> Unit,
  onClear: () -> Unit,
  onAddPresetAmount: (Long) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val accentColor =
    if (type == TransactionType.INCOME) GrihaTheme.colors.good
    else MaterialTheme.colorScheme.onSurface

  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = formattedAmount,
          style =
            MaterialTheme.typography.displaySmall.copy(
              fontFeatureSettings = "tnum",
              fontWeight = FontWeight.Bold,
            ),
          color = accentColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).testTag("amount_display_text"),
        )
        if (formattedAmount != stringResource(R.string.tx_amount_zero)) {
          TextButton(
            onClick = onClear,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("keypad_clear"),
          ) {
            Text(stringResource(R.string.action_clear), style = MaterialTheme.typography.labelLarge)
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        listOf(50L, 100L, 500L, 1000L).forEach { preset ->
          Surface(
            onClick = { onAddPresetAmount(preset) },
            shape = MaterialTheme.shapes.small,
            color = GrihaTheme.colors.goodContainer,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("amount_preset_$preset"),
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.padding(vertical = 8.dp),
            ) {
              Text(
                text = "+₹$preset",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = GrihaTheme.colors.onGoodContainer,
              )
            }
          }
        }
      }

      val rows =
        listOf(
          listOf("1", "2", "3"),
          listOf("4", "5", "6"),
          listOf("7", "8", "9"),
          listOf(".", "0", "BACK"),
        )

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { rowKeys ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            rowKeys.forEach { key ->
              val isBackspace = key == "BACK"
              val tag =
                when (key) {
                  "." -> "keypad_dot"
                  "BACK" -> "keypad_backspace"
                  else -> "keypad_$key"
                }
              Surface(
                onClick = { if (isBackspace) onBackspace() else onDigitOrDot(key) },
                shape = MaterialTheme.shapes.small,
                color =
                  if (isBackspace) MaterialTheme.colorScheme.surfaceContainerHigh
                  else MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 1.dp,
                modifier =
                  Modifier
                    .weight(1f)
                    .height(48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag(tag),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  if (isBackspace) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Outlined.Backspace,
                      contentDescription = stringResource(R.string.keypad_backspace_cd),
                      tint = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.size(20.dp),
                    )
                  } else {
                    Text(
                      text = key,
                      style =
                        MaterialTheme.typography.titleLarge.copy(
                          fontFeatureSettings = "tnum",
                          fontWeight = FontWeight.SemiBold,
                        ),
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun CategoryIconGrid(
  categories: List<Category>,
  selectedCategoryId: String?,
  suggestedCategoryId: String?,
  onSelectCategory: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.tx_category_label),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      val suggestedCategory = categories.firstOrNull { it.id == suggestedCategoryId }
      val suggestedName = suggestedCategory?.let { LocalizedLabels.categoryName(it, language) }
      if (suggestedName != null) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = GrihaTheme.colors.goodContainer,
          modifier = Modifier.testTag("suggested_category_badge"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Outlined.AutoAwesome,
              contentDescription = null,
              tint = GrihaTheme.colors.onGoodContainer,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "${stringResource(R.string.tx_suggested_badge)}: $suggestedName",
              style = MaterialTheme.typography.labelSmall,
              color = GrihaTheme.colors.onGoodContainer,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }

    val rows = categories.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      rows.forEach { rowItems ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          rowItems.forEach { category ->
            val selected = category.id == selectedCategoryId
            val accent = CategoryVisuals.accentColor(category)
            Surface(
              shape = MaterialTheme.shapes.medium,
              color =
                if (selected) accent.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceContainerLow,
              border =
                if (selected) BorderStroke(1.5.dp, accent)
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
              modifier =
                Modifier
                  .weight(1f)
                  .clip(MaterialTheme.shapes.medium)
                  .clickable { onSelectCategory(category.id) }
                  .minimumInteractiveComponentSize()
                  .testTag("category_chip_${category.id}"),
            ) {
              Column(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Box(
                  modifier =
                    Modifier
                      .size(32.dp)
                      .clip(CircleShape)
                      .background(accent.copy(alpha = if (selected) 0.24f else 0.14f)),
                  contentAlignment = Alignment.Center,
                ) {
                  val catLabel = LocalizedLabels.categoryName(category, language)
                  Icon(
                    imageVector = CategoryVisuals.iconForKey(category.iconKey),
                    contentDescription = catLabel,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Text(
                  text = LocalizedLabels.categoryName(category, language),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  textAlign = TextAlign.Center,
                )
              }
            }
          }
          repeat(4 - rowItems.size) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
fun ExpenseClassificationSelector(
  selectedClassification: ExpenseClassification,
  suggestedClassification: ExpenseClassification?,
  onSelectClassification: (ExpenseClassification) -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.tx_expense_classification_label),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (suggestedClassification != null) {
        val suggestedLabel = LocalizedLabels.expenseClassification(suggestedClassification, language)
        Surface(
          shape = MaterialTheme.shapes.small,
          color =
            if (suggestedClassification == ExpenseClassification.SALARY) GrihaTheme.colors.attentionContainer
            else GrihaTheme.colors.goodContainer,
          modifier = Modifier.testTag("suggested_classification_badge"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Outlined.AutoAwesome,
              contentDescription = null,
              tint =
                if (suggestedClassification == ExpenseClassification.SALARY) {
                  GrihaTheme.colors.onAttentionContainer
                } else {
                  GrihaTheme.colors.onGoodContainer
                },
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "${stringResource(R.string.tx_auto_classified_badge)}: $suggestedLabel",
              style = MaterialTheme.typography.labelSmall,
              color =
                if (suggestedClassification == ExpenseClassification.SALARY) {
                  GrihaTheme.colors.onAttentionContainer
                } else {
                  GrihaTheme.colors.onGoodContainer
                },
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      val options =
        listOf(
          Triple(
            ExpenseClassification.SALARY,
            stringResource(R.string.tx_classification_salary),
            stringResource(R.string.tx_classification_salary_hint),
          ),
          Triple(
            ExpenseClassification.NON_SALARY,
            stringResource(R.string.tx_classification_non_salary),
            stringResource(R.string.tx_classification_non_salary_hint),
          ),
        )
      options.forEach { (classification, label, hint) ->
        val selected = selectedClassification == classification
        val isSalary = classification == ExpenseClassification.SALARY
        val containerColor =
          when {
            selected && isSalary -> GrihaTheme.colors.attentionContainer
            selected -> GrihaTheme.colors.goodContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
          }
        val contentColor =
          when {
            selected && isSalary -> GrihaTheme.colors.onAttentionContainer
            selected -> GrihaTheme.colors.onGoodContainer
            else -> MaterialTheme.colorScheme.onSurface
          }
        val borderColor =
          when {
            selected && isSalary -> GrihaTheme.colors.attention
            selected -> GrihaTheme.colors.good
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
          }
        val tag =
          if (isSalary) "expense_classification_salary" else "expense_classification_non_salary"

        Surface(
          onClick = { onSelectClassification(classification) },
          shape = MaterialTheme.shapes.medium,
          color = containerColor,
          border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag(tag),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector =
                if (isSalary) Icons.Outlined.Badge else Icons.AutoMirrored.Outlined.ReceiptLong,
              contentDescription = label,
              tint = contentColor,
              modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.78f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FundingSourceSelector(
  selectedSource: FundingSource,
  onSelectSource: (FundingSource) -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = stringResource(R.string.tx_funding_source_label),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      FundingSource.entries.forEach { source ->
        val selected = source == selectedSource
        FilterChip(
          selected = selected,
          onClick = { onSelectSource(source) },
          label = { Text(LocalizedLabels.fundingSource(source, language)) },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = GrihaTheme.colors.goodContainer,
              selectedLabelColor = GrihaTheme.colors.onGoodContainer,
            ),
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .testTag("funding_source_${source.firestoreValue}"),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaidByMemberSelector(
  isIncome: Boolean,
  members: List<Member>,
  selectedUid: String,
  onSelectUid: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text =
        stringResource(
          if (isIncome) R.string.tx_received_by_label else R.string.tx_paid_by_label
        ),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      members.forEach { member ->
        val selected = member.uid == selectedUid
        val firstName = member.displayName.substringBefore(' ').ifBlank { member.displayName }
        FilterChip(
          selected = selected,
          onClick = { onSelectUid(member.uid) },
          label = { Text(firstName) },
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .testTag("paid_by_chip_${member.uid}"),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickDateSelector(
  selectedDate: LocalDate,
  onSelectDate: (LocalDate) -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  var showDatePicker by remember { mutableStateOf(false) }
  val today = remember { LocalDate.now() }
  val yesterday = remember(today) { today.minusDays(1) }
  val isCustomDate = selectedDate != today && selectedDate != yesterday

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = stringResource(R.string.tx_date_label),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      FilterChip(
        selected = selectedDate == today,
        onClick = { onSelectDate(today) },
        label = { Text(stringResource(R.string.tx_date_today)) },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("date_chip_today"),
      )
      FilterChip(
        selected = selectedDate == yesterday,
        onClick = { onSelectDate(yesterday) },
        label = { Text(stringResource(R.string.tx_date_yesterday)) },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("date_chip_yesterday"),
      )
      FilterChip(
        selected = isCustomDate,
        onClick = { showDatePicker = true },
        leadingIcon = {
          Icon(
            imageVector = Icons.Outlined.CalendarToday,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
        label = {
          Text(
            if (isCustomDate) LocalizedLabels.fullDate(selectedDate, language)
            else stringResource(R.string.tx_date_pick)
          )
        },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("date_chip_pick"),
      )
    }
  }

  if (showDatePicker) {
    val initialMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
              onSelectDate(picked)
            }
            showDatePicker = false
          },
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text(stringResource(R.string.action_ok))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDatePicker = false },
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreDetailsSection(
  expanded: Boolean,
  paymentMethod: PaymentMethod,
  note: String,
  onToggleExpanded: () -> Unit,
  onSelectPaymentMethod: (PaymentMethod) -> Unit,
  onNoteChanged: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  Column(modifier = modifier.fillMaxWidth()) {
    Surface(
      onClick = onToggleExpanded,
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("more_details_toggle"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            "${stringResource(R.string.tx_more_details)} · ${LocalizedLabels.paymentMethod(paymentMethod, language)}" +
              if (note.isNotBlank()) " · ${stringResource(R.string.tx_note_added_suffix)}" else "",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
          imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    AnimatedVisibility(visible = expanded) {
      Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = stringResource(R.string.tx_payment_method_label),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          PaymentMethod.entries.forEach { method ->
            FilterChip(
              selected = method == paymentMethod,
              onClick = { onSelectPaymentMethod(method) },
              label = { Text(LocalizedLabels.paymentMethod(method, language)) },
              modifier =
                Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("payment_method_${method.firestoreValue}"),
            )
          }
        }

        OutlinedTextField(
          value = note,
          onValueChange = onNoteChanged,
          label = { Text(stringResource(R.string.tx_note_label)) },
          placeholder = { Text(stringResource(R.string.tx_note_placeholder)) },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth().testTag("tx_note_input"),
        )
      }
    }
  }
}

@Composable
fun DuplicateWarningBanner(
  warning: DuplicateWarning,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = GrihaTheme.colors.attentionContainer,
    modifier = modifier.fillMaxWidth().testTag("duplicate_warning_banner"),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Icon(
        imageVector = Icons.Outlined.WarningAmber,
        contentDescription = null,
        tint = GrihaTheme.colors.onAttentionContainer,
        modifier = Modifier.size(20.dp),
      )
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.tx_possible_duplicate),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = GrihaTheme.colors.onAttentionContainer,
        )
        Text(
          text = warning.summary,
          style = MaterialTheme.typography.bodySmall,
          color = GrihaTheme.colors.onAttentionContainer,
        )
      }
    }
  }
}
