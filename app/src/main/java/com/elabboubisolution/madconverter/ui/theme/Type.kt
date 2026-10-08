package com.elabboubisolution.madconverter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Type scale (Material 3 sizes, system font so Arabic is shaped by the platform). Roles used:
 *  - displaySmall   main conversion result, the focal point (Medium weight)
 *  - headlineSmall  amount being typed
 *  - titleLarge     screen and sheet titles, selected currency codes
 *  - titleMedium    result source line, exchange rate, banner titles
 *  - titleSmall     section headers, Quick Conversion codes
 *  - body*          supporting text; bodySmall (12sp) only where space is tight
 *  - label*         field labels, chips, buttons
 * Styles not listed keep the Material 3 defaults.
 */
private val Default = Typography()
private val AppFont = FontFamily.Default

val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = Default.labelLarge,
    labelMedium = Default.labelMedium,
    labelSmall = Default.labelSmall,
)

/** OpenType feature for tabular (fixed-width) digits. */
const val TABULAR_FIGURES = "tnum"

/**
 * Same style with tabular (fixed-width) digits, for amounts and rates: digits keep their width
 * as values change, so numbers do not shift while typing. Only digits are affected; fonts
 * without the feature ignore it.
 */
fun TextStyle.tabularFigures(): TextStyle = copy(fontFeatureSettings = TABULAR_FIGURES)
