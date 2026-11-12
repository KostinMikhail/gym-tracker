package com.gym.tracker.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import com.gym.tracker.data.db.Feel

/**
 * Расширенные токены темы — то, чего нет в Material ColorScheme/Typography.
 * Все экраны рисуются одними и теми же composable; тема меняет только токены.
 * null у необязательного поля = «как в Material по умолчанию» (так устроена Классическая).
 */

/** Плотность пиксельного дизеринга (клетки 2 dp). */
enum class Dither { SPARSE, DOTS, CHECKER, DENSE }

@Immutable
data class Glow(val color: Color, val radius: Dp)

@Immutable
data class HardShadow(val offset: Dp, val color: Color)

@Immutable
data class FeelStyle(
    val content: Color,
    val border: Color,
    val selectedFill: Color,
    val selectedContent: Color,
    val swatch: Dither? = null,
    val selectedGlow: Glow? = null,
)

/** Роль карточки: определяет цвет фона (в Классической — как было раньше на каждом экране). */
enum class CardRole { LIST, ACTIVE, DONE, HIGHLIGHT }

@Immutable
data class CardStyle(
    val list: Color,
    val active: Color,
    val done: Color,
    val highlight: Color,
    /** Цвет текста на карточке; null — contentColorFor(фона) из Material. */
    val content: Color?,
    val shape: Shape?,
    val border: BorderStroke?,
    val highlightBorder: BorderStroke?,
    val glow: Glow? = null,
    val hardShadow: HardShadow? = null,
    /** Вторая тонкая обводка в 4 dp снаружи («двойная неоновая трубка»). */
    val outerOutline: Color? = null,
    /** Яркие уголки-скобки 16×2 dp по углам. */
    val cornerBrackets: Color? = null,
    /** Полоса 4 dp слева у открытой (предстоящей) карточки. */
    val activeLeftEdge: Color? = null,
    /** Пунктирная рамка у выполненной карточки. */
    val doneDashedBorder: Color? = null,
    /** Своя цветовая схема внутри карточки (если карточки контрастны странице). */
    val scheme: ColorScheme? = null,
)

/** Выбранный вариант («чип»): заливка, текст, рамка, свечение. */
@Immutable
data class SelectionStyle(val fill: Color, val content: Color, val border: Color?, val glow: Glow? = null)

@Immutable
data class StepperStyle(val container: Color, val content: Color, val border: Color?, val shape: Shape)

enum class WeightDecor { NONE, DASHED_LINES, UNDERLINE }

@Immutable
data class WeightStyle(
    val textStyle: TextStyle?,
    val color: Color?,
    val decor: WeightDecor = WeightDecor.NONE,
    val decorColor: Color = Color.Unspecified,
)

@Immutable
data class DoneButtonStyle(
    val container: Color,
    val content: Color,
    val shape: Shape,
    val border: BorderStroke? = null,
    val hardShadow: HardShadow? = null,
    val glow: Glow? = null,
    val textStyle: TextStyle? = null,
    val uppercase: Boolean = false,
)

@Immutable
data class NavStyle(
    val container: Color,
    val indicator: Color,
    /** Значок активного пункта (на индикаторе). */
    val selectedIcon: Color,
    /** Подпись активного пункта (на фоне панели). */
    val selectedText: Color,
    val unselected: Color,
)

enum class ThemeIcon { PALETTE, DITHER, BOLT, RED_SQUARE }

@Immutable
data class ThemeButtonStyle(
    val icon: ThemeIcon,
    val iconColor: Color,
    val container: Color,
    val border: Color?,
    val shape: Shape,
    val glow: Glow? = null,
)

enum class BackgroundDecor { NONE, PIXEL_GRID, SYNTHWAVE, FAINT_GRID, CORNER_STRIPES }

enum class HeaderDecor { NONE, DITHER_BAND, GLOW_LINE, HAZARD_BAND, HUD_STRIP }

@Immutable
data class GymTokens(
    val id: GymThemeId,
    /** Светлый фон страницы → тёмные значки в системных панелях. */
    val isLight: Boolean,
    val background: Color,
    /** Вторичный текст на странице (на карточке — onSurfaceVariant схемы карточки). */
    val pageSecondary: Color,
    val card: CardStyle,
    val feel: Map<Feel, FeelStyle>,
    val feelShape: Shape,
    /** Плашка «3 × 12», выбранный вариант упражнения. */
    val chipShape: Shape,
    /** Крупные кнопки (Начать тренировку, На главную…). */
    val buttonShape: Shape?,
    val selectedOption: SelectionStyle?,
    val pillDashed: Boolean,
    val stepper: StepperStyle?,
    val weight: WeightStyle,
    val doneButton: DoneButtonStyle?,
    val doneIcon: Color,
    /** Крупный заголовок экрана (название тренировки ~30 sp). */
    val titleStyle: TextStyle?,
    val titleColor: Color?,
    val titleGlow: Color?,
    val titleUppercase: Boolean,
    /** Названия групп (слотов) в карточках. */
    val groupStyle: TextStyle?,
    val groupUppercase: Boolean,
    val nav: NavStyle?,
    val themeButton: ThemeButtonStyle,
    /** Шрифт названия темы в списке выбора. */
    val displayFont: FontFamily?,
    val backgroundDecor: BackgroundDecor,
    val headerDecor: HeaderDecor,
    /** Цвета точек дизеринга (полоса, образцы самочувствия). */
    val ditherInk: Color,
    val scanlines: Color?,
    /** Надпись-«кикер» над заголовком: «// ТРЕНИРОВКА». */
    val kicker: Boolean,
    /** Цвета подписей самочувствия прямо на странице (вне карточек); null — как на карточке. */
    val feelOnPage: Map<Feel, Color>? = null,
) {
    fun feel(f: Feel): FeelStyle = feel.getValue(f)
}

val LocalGymTokens = staticCompositionLocalOf<GymTokens> { error("GymTheme не задан") }

/** true внутри GymCard: текст рисуется на карточке, а не на фоне страницы. */
val LocalOnCard = staticCompositionLocalOf { false }
