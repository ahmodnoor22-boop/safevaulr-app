package com.example.ui.theme

import android.app.Activity
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = NavyDeep,
    primaryContainer = NavyCard,
    onPrimaryContainer = MintLight,
    secondary = BlueSoft,
    onSecondary = TextWhite,
    secondaryContainer = NavyLighter,
    onSecondaryContainer = BlueIce,
    tertiary = MintLight,
    background = NavyDeep,
    onBackground = TextWhite,
    surface = NavySurface,
    onSurface = TextWhite,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextSoftGray,
    outline = GrayBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = TextWhite,
    primaryContainer = BlueIce,
    onPrimaryContainer = BluePrimary,
    secondary = CyanAccent,
    onSecondary = NavyDeep,
    secondaryContainer = Color(0xFFE0F7F5),
    onSecondaryContainer = NavyDeep,
    tertiary = StatusPurple,
    background = OffWhiteBg,
    onBackground = TextNavyDark,
    surface = PureWhite,
    onSurface = TextNavyDark,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextMutedGray,
    outline = GrayBorder
)

@Composable
fun SafeVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = NavyDeep.toArgb()
                it.navigationBarColor = (if (darkTheme) NavyDeep else OffWhiteBg).toArgb()
                val insetsController = WindowCompat.getInsetsController(it, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
