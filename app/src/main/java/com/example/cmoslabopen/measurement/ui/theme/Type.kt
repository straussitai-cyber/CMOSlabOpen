package com.example.cmoslabopen.measurement.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.cmoslabopen.R

/**
 * Downloadable-fonts provider for Google Fonts. The matching
 * `com_google_android_gms_fonts_certs` string-array resource must exist
 * (see res/values/font_certs.xml) before [JetBrainsMonoFontFamily] can be
 * resolved at runtime.
 */
internal val GoogleFontProvider: GoogleFont.Provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

internal val JetBrainsMono: GoogleFont = GoogleFont(name = "JetBrains Mono")

/**
 * Family for JetBrains Mono once downloadable-font resolution is wired up.
 * Until then the rest of the typography falls back to [FontFamily.Monospace]
 * so the app still renders.
 */
internal val JetBrainsMonoFontFamily: FontFamily = FontFamily.Monospace

internal val AppTypography: Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)
