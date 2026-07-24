package com.secondmonday.hodith.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.secondmonday.hodith.R
import com.secondmonday.hodith.data.AppTheme

private val Inter =
    FontFamily(
        Font(R.font.inter_regular_400, FontWeight.Normal),
        Font(R.font.inter_medium_500, FontWeight.Medium),
        Font(R.font.inter_semibold_600, FontWeight.SemiBold),
        Font(R.font.inter_bold_700, FontWeight.Bold),
    )
private val Oswald =
    FontFamily(
        Font(R.font.oswald_medium_500, FontWeight.Medium),
        Font(R.font.oswald_bold_700, FontWeight.Bold),
    )
private val SourceSerif4 =
    FontFamily(
        Font(R.font.sourceserif4_regular_400, FontWeight.Normal),
        Font(R.font.sourceserif4_semibold_600, FontWeight.SemiBold),
    )
private val Baloo2 =
    FontFamily(
        Font(R.font.baloo2_medium_500, FontWeight.Medium),
        Font(R.font.baloo2_bold_700, FontWeight.Bold),
    )
private val Nunito =
    FontFamily(
        Font(R.font.nunito_regular_400, FontWeight.Normal),
        Font(R.font.nunito_bold_700, FontWeight.Bold),
    )

/**
 * One [Typography] per theme (spec §12's "type feel"). Each theme pairs a display face — used
 * for display/headline/label roles, which covers Material3's default button text (`labelLarge`)
 * — with a body face for title/body roles, which covers case names (`titleMedium`) and any
 * long-form text (notes). This mirrors the theme-review mockup, where only the app bar,
 * section labels, and buttons opted into the display face; everything else read in body.
 */
fun hodithTypography(theme: AppTheme): Typography =
    when (theme) {
        AppTheme.PLAIN -> plainTypography
        AppTheme.INTENSE -> intenseTypography
        AppTheme.BRIGHT -> brightTypography
    }

private val base = Typography()

private fun typographyFor(
    displayFont: FontFamily,
    displayWeight: FontWeight,
    bodyFont: FontFamily,
) = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = displayFont, fontWeight = displayWeight),
    displayMedium = base.displayMedium.copy(fontFamily = displayFont, fontWeight = displayWeight),
    displaySmall = base.displaySmall.copy(fontFamily = displayFont, fontWeight = displayWeight),
    headlineLarge = base.headlineLarge.copy(fontFamily = displayFont, fontWeight = displayWeight),
    headlineMedium = base.headlineMedium.copy(fontFamily = displayFont, fontWeight = displayWeight),
    headlineSmall = base.headlineSmall.copy(fontFamily = displayFont, fontWeight = displayWeight),
    titleLarge = base.titleLarge.copy(fontFamily = bodyFont),
    titleMedium = base.titleMedium.copy(fontFamily = bodyFont),
    titleSmall = base.titleSmall.copy(fontFamily = bodyFont),
    bodyLarge = base.bodyLarge.copy(fontFamily = bodyFont),
    bodyMedium = base.bodyMedium.copy(fontFamily = bodyFont),
    bodySmall = base.bodySmall.copy(fontFamily = bodyFont),
    labelLarge = base.labelLarge.copy(fontFamily = displayFont, fontWeight = displayWeight),
    labelMedium = base.labelMedium.copy(fontFamily = displayFont, fontWeight = displayWeight),
    labelSmall = base.labelSmall.copy(fontFamily = displayFont, fontWeight = displayWeight),
)

private val plainTypography = typographyFor(displayFont = Inter, displayWeight = FontWeight.SemiBold, bodyFont = Inter)
private val intenseTypography = typographyFor(displayFont = Oswald, displayWeight = FontWeight.Bold, bodyFont = SourceSerif4)
private val brightTypography = typographyFor(displayFont = Baloo2, displayWeight = FontWeight.Bold, bodyFont = Nunito)
