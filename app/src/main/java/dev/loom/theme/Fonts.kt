package dev.loom.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.loom.R

/**
 * Space Grotesk for display, Inter for everything else. Both ship as variable fonts; the
 * static weights are not published in google/fonts, so the weight axis is set explicitly.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(id: Int, weight: Int) = Font(
    resId = id,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val SpaceGrotesk = FontFamily(
    variable(R.font.space_grotesk_variable, 400),
    variable(R.font.space_grotesk_variable, 500),
    variable(R.font.space_grotesk_variable, 700),
)

val Inter = FontFamily(
    variable(R.font.inter_variable, 400),
    variable(R.font.inter_variable, 500),
    variable(R.font.inter_variable, 600),
)
