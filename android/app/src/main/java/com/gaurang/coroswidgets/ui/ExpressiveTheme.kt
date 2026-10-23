package com.gaurang.coroswidgets.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb

/**
 * Official Material 3 semantic color tokens resolved dynamically from the device's system
 * Material You wallpaper palette and Dark/Light configuration via androidx.compose.material3.
 */
data class M3WidgetPalette(
    val isDark: Boolean,
    val surface: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val outline: Int,
    val outlineVariant: Int
)

object ExpressiveTheme {

    fun getComposeColorScheme(context: Context): ColorScheme {
        val nightFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightFlags == Configuration.UI_MODE_NIGHT_YES
        return if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    fun getPalette(context: Context): M3WidgetPalette {
        val nightFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightFlags == Configuration.UI_MODE_NIGHT_YES
        val cs = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        return M3WidgetPalette(
            isDark = isDark,
            surface = cs.surface.toArgb(),
            surfaceContainerLow = cs.surfaceContainerLow.toArgb(),
            surfaceContainer = cs.surfaceContainer.toArgb(),
            surfaceContainerHigh = cs.surfaceContainerHigh.toArgb(),
            surfaceContainerHighest = cs.surfaceContainerHighest.toArgb(),
            onSurface = cs.onSurface.toArgb(),
            onSurfaceVariant = cs.onSurfaceVariant.toArgb(),
            primary = cs.primary.toArgb(),
            onPrimary = cs.onPrimary.toArgb(),
            primaryContainer = cs.primaryContainer.toArgb(),
            onPrimaryContainer = cs.onPrimaryContainer.toArgb(),
            secondary = cs.secondary.toArgb(),
            onSecondary = cs.onSecondary.toArgb(),
            secondaryContainer = cs.secondaryContainer.toArgb(),
            onSecondaryContainer = cs.onSecondaryContainer.toArgb(),
            tertiary = cs.tertiary.toArgb(),
            onTertiary = cs.onTertiary.toArgb(),
            tertiaryContainer = cs.tertiaryContainer.toArgb(),
            onTertiaryContainer = cs.onTertiaryContainer.toArgb(),
            outline = cs.outline.toArgb(),
            outlineVariant = cs.outlineVariant.toArgb()
        )
    }
}
