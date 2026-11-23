package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ForgeDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianNight,
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = ForgeAmber,
    onSecondary = ObsidianNight,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = EmeraldBuild,
    onTertiary = ObsidianNight,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = ObsidianNight,
    onBackground = FrostWhite,
    surface = MidnightSlate,
    onSurface = FrostWhite,
    surfaceVariant = ForgeSurface,
    onSurfaceVariant = SlateMuted,
    surfaceContainerLow = MidnightSlate,
    surfaceContainer = ForgeSurface,
    surfaceContainerHigh = ForgeSurfaceVariant,
    outline = ForgeBorder,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = Color(0xFFFECACA)
)

private val ForgeLightColorScheme = lightColorScheme(
    primary = DeepCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F7FF),
    onPrimaryContainer = Color(0xFF002026),
    secondary = DeepAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFECC2),
    onSecondaryContainer = Color(0xFF2D1E00),
    tertiary = EmeraldBuild,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = InkDark,
    surface = LightSurface,
    onSurface = InkDark,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFCBD5E1),
    error = CrimsonError,
    onError = Color.White
)

@Composable
fun ForgeApkTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ForgeDarkColorScheme else ForgeLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ForgeTypography,
        content = content
    )
}
