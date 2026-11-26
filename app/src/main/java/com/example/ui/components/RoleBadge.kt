package com.example.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.Role
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.GrihaTheme

@Composable
fun RoleBadge(
  role: Role,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  val localizedRole = LocalizedLabels.role(role, language)
  val (containerColor, contentColor) =
    when (role) {
      Role.ADMIN -> GrihaTheme.colors.goodContainer to GrihaTheme.colors.onGoodContainer
      Role.MEMBER -> GrihaTheme.colors.attentionContainer to GrihaTheme.colors.onAttentionContainer
      Role.VIEWER -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }

  Surface(
    modifier = modifier.semantics { contentDescription = "Role: $localizedRole" },
    shape = RoundedCornerShape(50),
    color = containerColor,
  ) {
    Text(
      text = localizedRole,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = contentColor,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}
