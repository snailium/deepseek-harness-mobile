package com.labteto.dshmobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.labteto.dshmobile.R

/**
 * DeepSeek Harness type scale (gradient-shadow-text.css):
 * sizes 11..24, system UI stack + mono code stack.
 */
object DsFonts {
    /**
     * Inter (OFL), bundled as a variable font. Used for the display voice — heros, headlines,
     * nav titles — where a designed face gives the app its identity; body stays on the system
     * stack so paragraphs keep the platform's native legibility and RTL coverage.
     */
    val display = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_regular, FontWeight.Medium),
        Font(R.font.inter_regular, FontWeight.SemiBold),
        Font(R.font.inter_regular, FontWeight.Bold),
    )
}

object DsType {
    val uiFont = FontFamily.SansSerif
    val codeFont = FontFamily.Monospace
    val displayFont: FontFamily get() = DsFonts.display

    // Markdown roles (tuned for phone legibility; body 16 keeps dense replies readable,
    // headings 24–32 give the hierarchy room to breathe, code ≥13 stays crisp)
    val mdH1 = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp)
    val mdH2 = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp)
    val mdH3 = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp)
    val mdH4 = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 26.sp)
    val mdBody = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val mdSmall = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp)
    val mdCode = TextStyle(fontFamily = codeFont, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.sp)
    val mdTable = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val mdTableHeader = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)

    // UI roles
    /** Hero / empty-state / onboarding headline: the biggest display voice in the app. */
    val brandDisplay = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp)

    // ---- Material 3 roles (Android-native voice; the app bars, rows and section
    // labels below pick from this scale so every surface shares one hierarchy) ----
    /** M3 headlineMedium: the largest in-app screen title (28/34 Bold). */
    val m3HeadlineMedium = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp)
    /** M3 titleLarge: top-app-bar titles (22/28 Medium). */
    val m3TitleLarge = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp)
    /** M3 titleMedium: list-row headlines (16/24 Medium). */
    val m3TitleMedium = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp)
    /** M3 bodyLarge: standard body copy (16/24). */
    val m3BodyLarge = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    /** M3 bodyMedium: supporting text under a headline (14/20). */
    val m3BodyMedium = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    /** M3 labelLarge: section and group labels (14/20 SemiBold). */
    val m3LabelLarge = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
    /** M3 labelMedium: captions and chip text (12/16). */
    val m3LabelMedium = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
    /** M3 labelSmall: micro captions and meta (11/16). */
    val m3LabelSmall = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp)
    val display24 = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp)
    val hero26 = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp)
    val large20 = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp)
    /** Section-heading voice: 20 SemiBold, used for card-group titles and the details sheet. */
    val title2 = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp)
    /** Sub-section voice: 18 Medium, used for settings-group headers. */
    val title3 = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp)
    val base16 = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val base16Strong = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp)
    val std14 = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp)
    val std14Strong = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 22.sp)
    val small13 = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp)
    val small13Strong = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 20.sp)
    val xsmall12 = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp)
    val caption11 = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp)
    val caption11Strong = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)

    // UI roles kept for compatibility with existing call sites (superseded by the M3 roles
    // above for new work; the app bars, rows and section labels now pick from m3*).
    /** Large-title navigation: 28/34 Bold. */
    val largeTitle = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp)
    /** Navigation-bar title: 17 SemiBold. */
    val navTitle = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp)
    /** List-row title: 16 Medium. */
    val rowTitle = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp)
    /** Body copy: 17. */
    val body17 = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp)
    /** Footnote: secondary text on rows and captions. */
    val footnote = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.1).sp)

    // Composer / rows / bubbles
    val bubbleText = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp)
    val rowText = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val tabText = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)
    val dockTitle = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 24.sp)
    val statsText = TextStyle(fontFamily = uiFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 20.sp)
}

/** Material 3 mapping: sizes follow DsType, colors come from DsColors. */
val DsTypography = Typography(
    displayLarge = DsType.brandDisplay,
    displayMedium = DsType.display24,
    displaySmall = DsType.title2,
    headlineLarge = DsType.largeTitle,
    headlineMedium = DsType.m3HeadlineMedium,
    titleLarge = DsType.m3TitleLarge,
    titleMedium = DsType.m3TitleMedium,
    bodyLarge = DsType.m3BodyLarge,
    bodyMedium = DsType.m3BodyMedium,
    bodySmall = DsType.small13,
    labelLarge = DsType.m3LabelLarge,
    labelMedium = DsType.m3LabelMedium,
    labelSmall = DsType.m3LabelSmall,
)
