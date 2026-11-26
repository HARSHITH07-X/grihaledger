package com.example.ui.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppLanguageStore @Inject constructor(
  @ApplicationContext context: Context,
) {
  private val prefs =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _language =
    MutableStateFlow(
      run {
        val saved = prefs.getString(KEY_LANGUAGE_TAG, null)
        if (!saved.isNullOrBlank()) {
          AppLanguage.fromTag(saved)
        } else if (Locale.getDefault().language.equals("hi", ignoreCase = true)) {
          AppLanguage.HINDI
        } else {
          AppLanguage.ENGLISH
        }
      }
    )
  val language: StateFlow<AppLanguage> = _language.asStateFlow()

  fun setLanguage(newLanguage: AppLanguage) {
    if (_language.value == newLanguage) return
    prefs.edit().putString(KEY_LANGUAGE_TAG, newLanguage.tag).apply()
    _language.value = newLanguage
  }

  fun toggleLanguage() {
    val next =
      if (_language.value == AppLanguage.ENGLISH) AppLanguage.HINDI else AppLanguage.ENGLISH
    setLanguage(next)
  }

  private companion object {
    const val PREFS_NAME = "griha_language_prefs"
    const val KEY_LANGUAGE_TAG = "selected_language_tag"
  }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }
val LocalToggleAppLanguage = staticCompositionLocalOf<() -> Unit> { {} }
val LocalSetAppLanguage = staticCompositionLocalOf<(AppLanguage) -> Unit> { {} }

/**
 * Wraps the base context only for [getResources] so [androidx.compose.ui.res.stringResource]
 * switches immediately while [baseContext] continues to resolve to the hosting [android.app.Activity]
 * for Credential Manager Google Sign-In.
 */
private class LocalizedContextWrapper(
  base: Context,
  private val localizedResources: Resources,
) : ContextWrapper(base) {
  override fun getResources(): Resources = localizedResources
}

@Composable
fun ProvideAppLanguage(
  language: AppLanguage,
  onToggleLanguage: () -> Unit,
  onSetLanguage: (AppLanguage) -> Unit = {},
  content: @Composable () -> Unit,
) {
  val baseContext = LocalContext.current
  val baseConfig = LocalConfiguration.current

  val (localizedContext, localizedConfig) =
    remember(baseContext, baseConfig, language) {
      val targetLocale =
        if (language == AppLanguage.HINDI) Locale.forLanguageTag("hi-IN") else Locale.ENGLISH
      val config =
        Configuration(baseConfig).apply {
          setLocale(targetLocale)
        }
      val configContext = baseContext.createConfigurationContext(config)
      LocalizedContextWrapper(baseContext, configContext.resources) to config
    }

  CompositionLocalProvider(
    LocalAppLanguage provides language,
    LocalToggleAppLanguage provides onToggleLanguage,
    LocalSetAppLanguage provides onSetLanguage,
    LocalContext provides localizedContext,
    LocalConfiguration provides localizedConfig,
    content = content,
  )
}

@Composable
fun LanguageToggleButton(
  modifier: Modifier = Modifier,
  contentColor: Color = Color.White,
  testTag: String = "language_toggle_button",
) {
  val language = LocalAppLanguage.current
  val onToggle = LocalToggleAppLanguage.current
  val cd = stringResource(R.string.language_toggle_cd)
  val nextChipLabel =
    if (language == AppLanguage.ENGLISH) AppLanguage.HINDI.shortChipLabel
    else AppLanguage.ENGLISH.shortChipLabel

  Surface(
    onClick = onToggle,
    shape = RoundedCornerShape(50),
    color = contentColor.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.3f)),
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .semantics { contentDescription = cd }
        .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.Translate,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = nextChipLabel,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = contentColor,
      )
    }
  }
}
