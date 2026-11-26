package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class GrihaSemanticColors(
  val brandForest: Color,
  val onBrandForest: Color,
  val brandAccent: Color,
  val good: Color,
  val onGood: Color,
  val goodContainer: Color,
  val onGoodContainer: Color,
  val attention: Color,
  val attentionContainer: Color,
  val onAttentionContainer: Color,
  val overBudget: Color,
  val onOverBudget: Color,
  val overBudgetContainer: Color,
  val onOverBudgetContainer: Color,
)

private val LightSemanticColors =
  GrihaSemanticColors(
    brandForest = DarkForest,
    onBrandForest = WarmIvory,
    brandAccent = EmeraldLight,
    good = EmeraldAccessibleLight,
    onGood = Color.White,
    goodContainer = EmeraldSoft,
    onGoodContainer = Color(0xFF083B26),
    attention = AmberAccessibleLight,
    attentionContainer = AmberSoft,
    onAttentionContainer = Color(0xFF3B2700),
    overBudget = CoralAccessibleLight,
    onOverBudget = Color.White,
    overBudgetContainer = CoralSoft,
    onOverBudgetContainer = Color(0xFF5B120D),
  )

private val DarkSemanticColors =
  GrihaSemanticColors(
    brandForest = DarkForest,
    onBrandForest = IvoryInkPrimary,
    brandAccent = EmeraldLight,
    good = EmeraldLight,
    onGood = Color(0xFF042618),
    goodContainer = EmeraldDarkContainer,
    onGoodContainer = Color(0xFFB7F0D6),
    attention = Color(0xFFF2BA3D),
    attentionContainer = AmberDarkContainer,
    onAttentionContainer = AmberSoft,
    overBudget = Color(0xFFF27B72),
    onOverBudget = Color(0xFF410906),
    overBudgetContainer = CoralDarkContainer,
    onOverBudgetContainer = CoralSoft,
  )

val LocalGrihaSemanticColors = staticCompositionLocalOf { LightSemanticColors }

private val LightColorScheme =
  lightColorScheme(
    primary = DarkForest,
    onPrimary = WarmIvory,
    primaryContainer = Color(0xFFD2E8DD),
    onPrimaryContainer = DarkForest,
    secondary = EmeraldAccessibleLight,
    onSecondary = Color.White,
    secondaryContainer = EmeraldSoft,
    onSecondaryContainer = Color(0xFF083B26),
    tertiary = AmberAccessibleLight,
    onTertiary = Color.White,
    tertiaryContainer = AmberSoft,
    onTertiaryContainer = Color(0xFF3B2700),
    background = WarmIvory,
    onBackground = InkPrimary,
    surface = WarmIvory,
    onSurface = InkPrimary,
    surfaceVariant = WarmIvoryHigh,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = WarmIvoryLow,
    surfaceContainer = WarmIvoryMid,
    surfaceContainerHigh = WarmIvoryHigh,
    surfaceContainerHighest = WarmIvoryHighest,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = CoralAccessibleLight,
    onError = Color.White,
    errorContainer = CoralSoft,
    onErrorContainer = Color(0xFF5B120D),
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Color(0xFF06281A),
    primaryContainer = DarkForest,
    onPrimaryContainer = Color(0xFFCBEFE0),
    secondary = Emerald,
    onSecondary = Color(0xFF032114),
    secondaryContainer = EmeraldDarkContainer,
    onSecondaryContainer = Color(0xFFB7F0D6),
    tertiary = Color(0xFFF2BA3D),
    onTertiary = Color(0xFF2C1D00),
    tertiaryContainer = AmberDarkContainer,
    onTertiaryContainer = AmberSoft,
    background = DarkForestDeep,
    onBackground = IvoryInkPrimary,
    surface = DarkForestDeep,
    onSurface = IvoryInkPrimary,
    surfaceVariant = DarkForestSurfaceHigh,
    onSurfaceVariant = IvoryInkSecondary,
    surfaceContainerLowest = Color(0xFF07140F),
    surfaceContainerLow = Color(0xFF0F261E),
    surfaceContainer = DarkForestElevated,
    surfaceContainerHigh = DarkForestSurfaceHigh,
    surfaceContainerHighest = DarkForestSurfaceHighest,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = Color(0xFFF27B72),
    onError = Color(0xFF410906),
    errorContainer = CoralDarkContainer,
    onErrorContainer = CoralSoft,
  )

object GrihaTheme {
  val colors: GrihaSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalGrihaSemanticColors.current
}

/**
 * GrihaLedger Material 3 theme with dynamic color intentionally disabled to preserve brand tokens.
 */
@Composable
fun GrihaLedgerTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

  CompositionLocalProvider(LocalGrihaSemanticColors provides semanticColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = GrihaTypography,
      shapes = GrihaShapes,
      content = content,
    )
  }
}
