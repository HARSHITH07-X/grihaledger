package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AdvisorWebCitation
import com.example.domain.repository.LiveAdvisorSessionState
import com.example.ui.theme.GrihaTheme

@Composable
fun AdvisorWebCitationsSection(
  citations: List<AdvisorWebCitation>,
  searchQueries: List<String>,
  modifier: Modifier = Modifier,
) {
  if (citations.isEmpty() && searchQueries.isEmpty()) return
  val uriHandler = LocalUriHandler.current

  Column(
    modifier = modifier.fillMaxWidth().testTag("advisor_web_citations_section"),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.Public,
        contentDescription = null,
        tint = GrihaTheme.colors.good,
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = stringResource(R.string.advisor_web_sources_heading),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (searchQueries.isNotEmpty()) {
      Text(
        text = stringResource(R.string.advisor_search_queries_used, searchQueries.joinToString(" · ")),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    citations.take(5).forEach { citation ->
      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable {
              runCatching { uriHandler.openUri(citation.uri) }
            }
            .minimumInteractiveComponentSize()
            .testTag("advisor_web_citation"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = citation.title,
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = citation.domain ?: citation.uri,
              style = MaterialTheme.typography.labelSmall,
              color = GrihaTheme.colors.good,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = stringResource(R.string.advisor_open_source_cd),
            tint = GrihaTheme.colors.good,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVoiceAdvisorSheet(
  sessionState: LiveAdvisorSessionState,
  onDismissAndEnd: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismissAndEnd,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    modifier = Modifier.testTag("live_voice_advisor_sheet"),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Outlined.RecordVoiceOver,
          contentDescription = null,
          tint = GrihaTheme.colors.good,
          modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.live_voice_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = stringResource(R.string.live_voice_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      when (sessionState) {
        LiveAdvisorSessionState.Idle -> Unit

        LiveAdvisorSessionState.Connecting -> {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(
                color = GrihaTheme.colors.good,
                modifier = Modifier.size(22.dp),
              )
              Text(
                text = stringResource(R.string.live_voice_connecting),
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
        }

        is LiveAdvisorSessionState.Active -> {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = GrihaTheme.colors.goodContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.GraphicEq,
                contentDescription = null,
                tint = GrihaTheme.colors.onGoodContainer,
              )
              Text(
                text = stringResource(R.string.live_voice_listening_hint),
                style = MaterialTheme.typography.labelLarge,
                color = GrihaTheme.colors.onGoodContainer,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }

          if (sessionState.toolsUsed.isNotEmpty()) {
            Text(
              text = stringResource(R.string.advisor_tools_run, sessionState.toolsUsed.joinToString(", ")),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(sessionState.turns, key = { it.id }) { turn ->
              LiveTranscriptBubble(
                isUser = turn.isUser,
                text = turn.text,
              )
            }
            if (sessionState.userPartialTranscript.isNotBlank()) {
              item(key = "user_partial") {
                LiveTranscriptBubble(isUser = true, text = sessionState.userPartialTranscript)
              }
            }
            if (sessionState.advisorPartialTranscript.isNotBlank()) {
              item(key = "ai_partial") {
                LiveTranscriptBubble(isUser = false, text = sessionState.advisorPartialTranscript)
              }
            }
          }
        }

        is LiveAdvisorSessionState.Error -> {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = GrihaTheme.colors.overBudgetContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = GrihaTheme.colors.onOverBudgetContainer,
              )
              Text(
                text = sessionState.message,
                style = MaterialTheme.typography.bodySmall,
                color = GrihaTheme.colors.onOverBudgetContainer,
              )
            }
          }
        }
      }

      Button(
        onClick = onDismissAndEnd,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = GrihaTheme.colors.overBudget,
            contentColor = GrihaTheme.colors.onOverBudget,
          ),
        modifier =
          Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("live_voice_end_button"),
      ) {
        Icon(
          imageVector = Icons.Outlined.CallEnd,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
          text = stringResource(R.string.live_voice_end_cta),
          fontWeight = FontWeight.Bold,
        )
      }
      Spacer(modifier = Modifier.size(8.dp))
    }
  }
}

@Composable
private fun LiveTranscriptBubble(
  isUser: Boolean,
  text: String,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color =
      if (isUser) MaterialTheme.colorScheme.surfaceContainerLow
      else GrihaTheme.colors.goodContainer.copy(alpha = 0.7f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text =
          stringResource(
            if (isUser) R.string.live_voice_speaker_you else R.string.live_voice_speaker_advisor
          ),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
