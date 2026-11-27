package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SmartDineLightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = CrispWhite,
    primaryContainer = SlateSurfaceVariant,
    onPrimaryContainer = NavyPrimary,
    secondary = NavyElevated,
    onSecondary = CrispWhite,
    secondaryContainer = SlateBorder,
    onSecondaryContainer = NavyPrimary,
    tertiary = EmeraldSuccess,
    onTertiary = CrispWhite,
    background = SlateBackground,
    onBackground = NavyPrimary,
    surface = CrispWhite,
    onSurface = NavyPrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = SlateSubtext,
    outline = SlateBorder,
    outlineVariant = SlateBorder,
    error = RoseAlert,
    onError = CrispWhite
)

@Composable
fun SmartDineTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SmartDineLightColorScheme,
        typography = AppTypography,
        content = content
    )
}

@Composable
fun TasteQrTheme(content: @Composable () -> Unit) = SmartDineTheme(content)
