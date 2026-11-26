package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Member
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalizedLabels

@Composable
fun DebugUserSwitcherBar(
  members: List<Member>,
  currentMember: Member?,
  onSelectMember: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (members.isEmpty()) return

  Surface(
    modifier = modifier.fillMaxWidth().testTag("debug_user_switcher"),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 1.dp,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.BugReport,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "${stringResource(R.string.debug_user_switcher_label)} ${stringResource(R.string.debug_only_suffix)}",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.semantics { heading() },
        )
      }

      val language = LocalAppLanguage.current
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        members.forEach { member ->
          val selected = member.uid == currentMember?.uid
          val firstName = member.displayName.substringBefore(' ')
          FilterChip(
            selected = selected,
            onClick = { onSelectMember(member.uid) },
            label = {
              Text(
                text = "$firstName · ${LocalizedLabels.role(member.role, language)}",
                style = MaterialTheme.typography.labelMedium,
              )
            },
            leadingIcon =
              if (selected) {
                {
                  Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                }
              } else {
                null
              },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
              ),
            modifier =
              Modifier
                .minimumInteractiveComponentSize()
                .testTag("debug_user_chip_${member.role.name.lowercase()}"),
          )
        }
      }
    }
  }
}
