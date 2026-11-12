package com.gym.tracker.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.gym.tracker.R

/** Шрифты лежат в res/font (работают офлайн). Лицензии — licenses/ в корне репозитория. */
object GymFonts {
    val ibmPlexMono = FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
        Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
    )

    val pixelifySans = variable(R.font.pixelify_sans, 400, 500, 600, 700)
    val jetBrainsMono = variable(R.font.jetbrains_mono, 400, 500, 600, 700, 800)
    val unbounded = variable(R.font.unbounded, 400, 500, 600, 700, 800, 900)
    val exo2 = variable(R.font.exo2, 400, 500, 600, 700)
    val russoOne = FontFamily(Font(R.font.russo_one, FontWeight.Normal))
    val jura = variable(R.font.jura, 400, 500, 600, 700)
    val rubikMonoOne = FontFamily(Font(R.font.rubik_mono_one, FontWeight.Normal))
    val oswald = variable(R.font.oswald, 400, 500, 600, 700)

    /** Variable-шрифт: по одному Font на каждую нужную толщину (ось wght). */
    @OptIn(ExperimentalTextApi::class)
    private fun variable(resId: Int, vararg weights: Int) = FontFamily(
        weights.map { w ->
            Font(resId, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        },
    )
}
