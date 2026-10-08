package com.elabboubisolution.madconverter.ui.theme

import androidx.compose.ui.graphics.Color

// Brand references (approved): deep green #123B35, teal #168579, gold #D5A64C, background #F5F7F4.
// Text-bearing roles are adjusted where needed for WCAG AA (4.5:1): the raw teal and gold are too
// light on the light background, so the light scheme uses a deeper teal and a deeper gold.
// ThemeContrastTest checks every essential pair.

val BrandDeepGreen = Color(0xFF123B35)
val BrandBackground = Color(0xFFF5F7F4)

// --- Light ---
val LightPrimary = Color(0xFF0E6B61) // deeper brand teal
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFC9EBE4)
val LightOnPrimaryContainer = BrandDeepGreen
val LightSecondary = Color(0xFF4A6360)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD0E8E3)
val LightOnSecondaryContainer = Color(0xFF0B2B27)
val LightTertiary = Color(0xFF7A5A14) // deeper brand gold
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF8E2B4)
val LightOnTertiaryContainer = Color(0xFF3A2900)
val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFF9DEDC)
val LightOnErrorContainer = Color(0xFF410E0B)
val LightSurface = BrandBackground
val LightOnSurface = Color(0xFF171D1B)
val LightSurfaceVariant = Color(0xFFDAE5E1)
val LightOnSurfaceVariant = Color(0xFF3F4946)
val LightOutline = Color(0xFF6F7976)
val LightOutlineVariant = Color(0xFFBEC9C5)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFEFF2EF)
val LightSurfaceContainer = Color(0xFFE9EEEB)
val LightSurfaceContainerHigh = Color(0xFFE3E9E5)
val LightSurfaceContainerHighest = Color(0xFFDDE4E0)
val LightSurfaceDim = Color(0xFFD5DCD8)
val LightInverseSurface = Color(0xFF2B3230)
val LightInverseOnSurface = Color(0xFFECF2EF)
val LightInversePrimary = Color(0xFF7FD5C8)

// --- Dark ---
val DarkPrimary = Color(0xFF7FD5C8)
val DarkOnPrimary = Color(0xFF003731)
val DarkPrimaryContainer = BrandDeepGreen
val DarkOnPrimaryContainer = Color(0xFFA9F0E4)
val DarkSecondary = Color(0xFFB1CCC7)
val DarkOnSecondary = Color(0xFF1C3532)
val DarkSecondaryContainer = Color(0xFF334B47)
val DarkOnSecondaryContainer = Color(0xFFCDE8E2)
val DarkTertiary = Color(0xFFE9C17A) // lighter brand gold
val DarkOnTertiary = Color(0xFF412D00)
val DarkTertiaryContainer = Color(0xFF5C4300)
val DarkOnTertiaryContainer = Color(0xFFFFDEA6)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkSurface = Color(0xFF0E1513)
val DarkOnSurface = Color(0xFFDDE4E1)
val DarkSurfaceVariant = Color(0xFF3F4946)
val DarkOnSurfaceVariant = Color(0xFFBEC9C5)
val DarkOutline = Color(0xFF89938F)
val DarkOutlineVariant = Color(0xFF3F4946)
val DarkSurfaceContainerLowest = Color(0xFF09100E)
val DarkSurfaceContainerLow = Color(0xFF161D1B)
val DarkSurfaceContainer = Color(0xFF1A211F)
val DarkSurfaceContainerHigh = Color(0xFF252B29)
val DarkSurfaceContainerHighest = Color(0xFF2F3634)
val DarkSurfaceBright = Color(0xFF343B39)
val DarkInverseSurface = Color(0xFFDDE4E1)
val DarkInverseOnSurface = Color(0xFF2B3230)
val DarkInversePrimary = Color(0xFF0E6B61)

// --- Warning (stale exchange rates); Material 3 has no warning role ---
val LightWarning = Color(0xFF7A5A14)
val LightOnWarning = Color(0xFFFFFFFF)
val LightWarningContainer = Color(0xFFF8E2B4)
val LightOnWarningContainer = Color(0xFF3A2900)
val DarkWarning = Color(0xFFE9C17A)
val DarkOnWarning = Color(0xFF412D00)
val DarkWarningContainer = Color(0xFF5C4300)
val DarkOnWarningContainer = Color(0xFFFFDEA6)
