package com.gym.tracker.ui.theme

import androidx.annotation.StringRes
import com.gym.tracker.R

/**
 * Темы оформления в порядке показа в списке выбора.
 * [storageKey] сохраняется в DataStore — НЕ менять существующие значения (иначе выбор пользователя сбросится).
 */
enum class GymThemeId(val storageKey: String, @StringRes val titleRes: Int) {
    CLASSIC("classic", R.string.theme_classic),
    DITHER_1BIT("dither_1bit", R.string.theme_dither_1bit),
    DITHER_GAMEBOY("dither_gameboy", R.string.theme_dither_gameboy),
    NEON_SYNTHWAVE("neon_synthwave", R.string.theme_neon_synthwave),
    NEON_ACID("neon_acid", R.string.theme_neon_acid),
    CYBER_YELLOW("cyber_yellow", R.string.theme_cyber_yellow),
    CYBER_HUD("cyber_hud", R.string.theme_cyber_hud),
    ;

    companion object {
        val DEFAULT = CLASSIC

        /** Неизвестное или отсутствующее значение → Классическая. */
        fun fromStorageKey(key: String?): GymThemeId = entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
