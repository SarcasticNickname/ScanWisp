package com.myprojects.scanwisp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.myprojects.scanwisp.domain.model.ThemePreference

// --- Схемы для Бордовой темы ---
private val BordeauxDarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim
)
private val BordeauxLightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceTint = md_theme_light_surfaceTint,
    outlineVariant = md_theme_light_outlineVariant,
    scrim = md_theme_light_scrim
)

// --- Схемы для Зеленой темы ---
private val ForestGreenDarkColorScheme = darkColorScheme(
    primary = green_dark_primary,
    onPrimary = green_dark_onPrimary,
    primaryContainer = green_dark_primaryContainer,
    onPrimaryContainer = green_dark_onPrimaryContainer,
    secondary = green_dark_secondary,
    onSecondary = green_dark_onSecondary,
    secondaryContainer = green_dark_secondaryContainer,
    onSecondaryContainer = green_dark_onSecondaryContainer,
    tertiary = green_dark_tertiary,
    onTertiary = green_dark_onTertiary,
    tertiaryContainer = green_dark_tertiaryContainer,
    onTertiaryContainer = green_dark_onTertiaryContainer,
    error = green_dark_error,
    onError = green_dark_onError,
    background = green_dark_background,
    onBackground = green_dark_onBackground,
    surface = green_dark_surface,
    onSurface = green_dark_onSurface,
    surfaceVariant = green_dark_surfaceVariant,
    onSurfaceVariant = green_dark_onSurfaceVariant,
    outline = green_dark_outline
)
private val ForestGreenLightColorScheme = lightColorScheme(
    primary = green_light_primary,
    onPrimary = green_light_onPrimary,
    primaryContainer = green_light_primaryContainer,
    onPrimaryContainer = green_light_onPrimaryContainer,
    secondary = green_light_secondary,
    onSecondary = green_light_onSecondary,
    secondaryContainer = green_light_secondaryContainer,
    onSecondaryContainer = green_light_onSecondaryContainer,
    tertiary = green_light_tertiary,
    onTertiary = green_light_onTertiary,
    tertiaryContainer = green_light_tertiaryContainer,
    onTertiaryContainer = green_light_onTertiaryContainer,
    error = green_light_error,
    onError = green_light_onError,
    background = green_light_background,
    onBackground = green_light_onBackground,
    surface = green_light_surface,
    onSurface = green_light_onSurface,
    surfaceVariant = green_light_surfaceVariant,
    onSurfaceVariant = green_light_onSurfaceVariant,
    outline = green_light_outline
)

// --- Схемы для Синей темы ---
private val OceanBlueDarkColorScheme = darkColorScheme(
    primary = blue_dark_primary,
    onPrimary = blue_dark_onPrimary,
    primaryContainer = blue_dark_primaryContainer,
    onPrimaryContainer = blue_dark_onPrimaryContainer,
    secondary = blue_dark_secondary,
    onSecondary = blue_dark_onSecondary,
    secondaryContainer = blue_dark_secondaryContainer,
    onSecondaryContainer = blue_dark_onSecondaryContainer,
    tertiary = blue_dark_tertiary,
    onTertiary = blue_dark_onTertiary,
    tertiaryContainer = blue_dark_tertiaryContainer,
    onTertiaryContainer = blue_dark_onTertiaryContainer,
    error = blue_dark_error,
    onError = blue_dark_onError,
    background = blue_dark_background,
    onBackground = blue_dark_onBackground,
    surface = blue_dark_surface,
    onSurface = blue_dark_onSurface,
    surfaceVariant = blue_dark_surfaceVariant,
    onSurfaceVariant = blue_dark_onSurfaceVariant,
    outline = blue_dark_outline
)
private val OceanBlueLightColorScheme = lightColorScheme(
    primary = blue_light_primary,
    onPrimary = blue_light_onPrimary,
    primaryContainer = blue_light_primaryContainer,
    onPrimaryContainer = blue_light_onPrimaryContainer,
    secondary = blue_light_secondary,
    onSecondary = blue_light_onSecondary,
    secondaryContainer = blue_light_secondaryContainer,
    onSecondaryContainer = blue_light_onSecondaryContainer,
    tertiary = blue_light_tertiary,
    onTertiary = blue_light_onTertiary,
    tertiaryContainer = blue_light_tertiaryContainer,
    onTertiaryContainer = blue_light_onTertiaryContainer,
    error = blue_light_error,
    onError = blue_light_onError,
    background = blue_light_background,
    onBackground = blue_light_onBackground,
    surface = blue_light_surface,
    onSurface = blue_light_onSurface,
    surfaceVariant = blue_light_surfaceVariant,
    onSurfaceVariant = blue_light_onSurfaceVariant,
    outline = blue_light_outline
)

@Composable
fun ScanWispTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()

    val colorScheme = when (themePreference) {
        // --- Явный выбор пользователя ---
        ThemePreference.BORDEAUX_LIGHT -> BordeauxLightColorScheme
        ThemePreference.BORDEAUX_DARK -> BordeauxDarkColorScheme
        ThemePreference.GREEN_LIGHT -> ForestGreenLightColorScheme
        ThemePreference.GREEN_DARK -> ForestGreenDarkColorScheme
        ThemePreference.BLUE_LIGHT -> OceanBlueLightColorScheme
        ThemePreference.BLUE_DARK -> OceanBlueDarkColorScheme

        // --- Следование системе ---
        ThemePreference.SYSTEM -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (systemIsDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(
                    context
                )
            } else {
                // Фоллбэк на Бордовую по умолчанию
                if (systemIsDark) BordeauxDarkColorScheme else BordeauxLightColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}