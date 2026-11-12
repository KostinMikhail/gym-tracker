package com.gym.tracker.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gym.tracker.data.db.Feel

/** Полное описание темы: Material-схема + типографика + формы + расширенные токены. */
class GymThemeSpec(
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
    val tokens: GymTokens,
)

fun themeSpec(id: GymThemeId): GymThemeSpec = when (id) {
    GymThemeId.CLASSIC -> ClassicTheme.spec
    GymThemeId.DITHER_1BIT -> Dither1BitTheme.spec
    GymThemeId.DITHER_GAMEBOY -> GameBoyTheme.spec
    GymThemeId.NEON_SYNTHWAVE -> SynthwaveTheme.spec
    GymThemeId.NEON_ACID -> AcidTheme.spec
    GymThemeId.CYBER_YELLOW -> CyberYellowTheme.spec
    GymThemeId.CYBER_HUD -> CyberHudTheme.spec
}

/** Шрифт во всех стилях Material; [bodyWeight] — толщина для body*-стилей. */
private fun typographyOf(family: FontFamily, bodyWeight: FontWeight? = null): Typography {
    val t = Typography()
    fun TextStyle.f() = copy(fontFamily = family)
    fun TextStyle.b() = copy(fontFamily = family, fontWeight = bodyWeight ?: fontWeight)
    return Typography(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.b(), bodyMedium = t.bodyMedium.b(), bodySmall = t.bodySmall.b(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

private val squareShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp), medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp), extraLarge = RoundedCornerShape(0.dp),
)

private fun feelMap(easy: FeelStyle, normal: FeelStyle, hard: FeelStyle) =
    mapOf(Feel.EASY to easy, Feel.NORMAL to normal, Feel.HARD to hard)

private fun glowShadow(color: Color, blur: Float = 18f) = Shadow(color = color, blurRadius = blur)

// ---------------------------------------------------------------- 0. Классическая
object ClassicTheme {
    val scheme = darkColorScheme(
        primary = Color(0xFFFFB74D),
        onPrimary = Color(0xFF2B1700),
        primaryContainer = Color(0xFF5A3A00),
        onPrimaryContainer = Color(0xFFFFDDB3),
        secondary = Color(0xFF90CAF9),
        onSecondary = Color(0xFF00243F),
        secondaryContainer = Color(0xFF2A3A4A),
        onSecondaryContainer = Color(0xFFD1E4FF),
        background = Color(0xFF121316),
        onBackground = Color(0xFFE4E2E6),
        surface = Color(0xFF121316),
        onSurface = Color(0xFFE4E2E6),
        surfaceVariant = Color(0xFF2A2C31),
        onSurfaceVariant = Color(0xFFC4C6CF),
        surfaceContainerLowest = Color(0xFF0D0E11),
        surfaceContainerLow = Color(0xFF1A1B1F),
        surfaceContainer = Color(0xFF1E2024),
        surfaceContainerHigh = Color(0xFF292A2F),
        surfaceContainerHighest = Color(0xFF34353A),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44464E),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    )

    private fun classicFeel(c: Color) = FeelStyle(content = c, border = c, selectedFill = c, selectedContent = Color(0xFF111111))

    val spec = GymThemeSpec(
        colorScheme = scheme,
        typography = Typography(),
        shapes = Shapes(),
        tokens = GymTokens(
            id = GymThemeId.CLASSIC,
            isLight = false,
            background = scheme.background,
            pageSecondary = scheme.onSurfaceVariant,
            card = CardStyle(
                list = scheme.surfaceContainer,
                active = scheme.surfaceContainerHigh,
                done = scheme.surfaceContainerLow,
                highlight = scheme.primaryContainer,
                content = null,
                shape = null,
                border = null,
                highlightBorder = BorderStroke(2.dp, scheme.primary),
            ),
            feel = feelMap(classicFeel(FeelColors.easy), classicFeel(FeelColors.normal), classicFeel(FeelColors.hard)),
            feelShape = CircleShape,
            chipShape = CircleShape,
            buttonShape = null,
            selectedOption = null,
            pillDashed = false,
            stepper = null,
            weight = WeightStyle(textStyle = null, color = null),
            doneButton = null,
            doneIcon = FeelColors.easy,
            titleStyle = null,
            titleColor = null,
            titleGlow = null,
            titleUppercase = false,
            groupStyle = null,
            groupUppercase = false,
            nav = null,
            themeButton = ThemeButtonStyle(ThemeIcon.PALETTE, scheme.onSurface, scheme.surfaceContainerHigh, null, RoundedCornerShape(12.dp)),
            displayFont = null,
            backgroundDecor = BackgroundDecor.NONE,
            headerDecor = HeaderDecor.NONE,
            ditherInk = Color.Unspecified,
            scanlines = null,
            kicker = false,
        ),
    )
}

// ---------------------------------------------------------------- 1. Дизеринг 1-bit
object Dither1BitTheme {
    private val ink = Color(0xFFF2F2F2)
    private val secondary = Color(0xFFB5B5B5)
    private val black = Color(0xFF000000)
    private val dashed = Color(0xFF8A8A8A)
    private val shadow = Color(0xFF6E6E6E)
    private val mono = GymFonts.ibmPlexMono

    private val scheme = darkColorScheme(
        primary = ink, onPrimary = black, primaryContainer = ink, onPrimaryContainer = black,
        secondary = ink, onSecondary = black, secondaryContainer = ink, onSecondaryContainer = black,
        tertiary = ink, onTertiary = black,
        background = black, onBackground = ink, surface = black, onSurface = ink,
        surfaceVariant = Color(0xFF1A1A1A), onSurfaceVariant = secondary,
        surfaceContainerLowest = black, surfaceContainerLow = black, surfaceContainer = Color(0xFF0D0D0D),
        surfaceContainerHigh = Color(0xFF141414), surfaceContainerHighest = Color(0xFF1F1F1F),
        outline = ink, outlineVariant = dashed, error = ink, onError = black,
        inverseSurface = ink, inverseOnSurface = black, surfaceTint = ink,
    )

    private val feel = FeelStyle(ink, ink, ink, black)

    val spec = GymThemeSpec(
        colorScheme = scheme,
        typography = typographyOf(mono),
        shapes = squareShapes,
        tokens = GymTokens(
            id = GymThemeId.DITHER_1BIT,
            isLight = false,
            background = black,
            pageSecondary = secondary,
            card = CardStyle(
                list = black, active = black, done = black, highlight = black,
                content = ink, shape = RectangleShape,
                border = BorderStroke(2.dp, ink), highlightBorder = BorderStroke(4.dp, ink),
                doneDashedBorder = dashed,
            ),
            feel = feelMap(feel.copy(swatch = Dither.SPARSE), feel.copy(swatch = Dither.CHECKER), feel.copy(swatch = Dither.DENSE)),
            feelShape = RectangleShape,
            chipShape = RectangleShape,
            buttonShape = RectangleShape,
            selectedOption = SelectionStyle(fill = ink, content = black, border = null),
            pillDashed = true,
            stepper = StepperStyle(container = black, content = ink, border = ink, shape = RectangleShape),
            weight = WeightStyle(
                textStyle = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 32.sp),
                color = ink, decor = WeightDecor.DASHED_LINES, decorColor = dashed,
            ),
            doneButton = DoneButtonStyle(
                container = ink, content = black, shape = RectangleShape,
                hardShadow = HardShadow(4.dp, shadow),
                textStyle = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.sp),
                uppercase = true,
            ),
            doneIcon = ink,
            titleStyle = TextStyle(fontFamily = GymFonts.pixelifySans, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp),
            titleColor = ink,
            titleGlow = null,
            titleUppercase = false,
            groupStyle = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.5.sp),
            groupUppercase = true,
            nav = NavStyle(container = black, indicator = ink, selectedIcon = black, selectedText = ink, unselected = secondary),
            themeButton = ThemeButtonStyle(ThemeIcon.DITHER, ink, black, ink, RectangleShape),
            displayFont = GymFonts.pixelifySans,
            backgroundDecor = BackgroundDecor.NONE,
            headerDecor = HeaderDecor.DITHER_BAND,
            ditherInk = ink,
            scanlines = null,
            kicker = false,
        ),
    )
}

// ---------------------------------------------------------------- 2. Дизеринг Game Boy
object GameBoyTheme {
    private val light = Color(0xFF9BBC0F)
    private val cardBg = Color(0xFF8BAC0F)
    private val dark = Color(0xFF0F380F)
    private val mid = Color(0xFF306230)
    private val mono = GymFonts.jetBrainsMono

    private val scheme = lightColorScheme(
        primary = dark, onPrimary = light, primaryContainer = dark, onPrimaryContainer = light,
        secondary = mid, onSecondary = light, secondaryContainer = dark, onSecondaryContainer = light,
        tertiary = dark, onTertiary = light,
        background = light, onBackground = dark, surface = light, onSurface = dark,
        surfaceVariant = cardBg, onSurfaceVariant = dark,
        surfaceContainerLowest = light, surfaceContainerLow = cardBg, surfaceContainer = cardBg,
        surfaceContainerHigh = cardBg, surfaceContainerHighest = light,
        outline = dark, outlineVariant = mid, error = dark, onError = light,
        inverseSurface = dark, inverseOnSurface = light, surfaceTint = dark,
    )

    private val feel = FeelStyle(dark, dark, dark, light)

    val spec = GymThemeSpec(
        colorScheme = scheme,
        typography = typographyOf(mono),
        shapes = squareShapes,
        tokens = GymTokens(
            id = GymThemeId.DITHER_GAMEBOY,
            isLight = true,
            background = light,
            pageSecondary = dark,
            card = CardStyle(
                list = cardBg, active = cardBg, done = cardBg, highlight = cardBg,
                content = dark, shape = RectangleShape,
                border = BorderStroke(3.dp, dark), highlightBorder = BorderStroke(5.dp, dark),
                hardShadow = HardShadow(5.dp, mid),
            ),
            feel = feelMap(feel.copy(swatch = Dither.SPARSE), feel.copy(swatch = Dither.CHECKER), feel.copy(swatch = Dither.DENSE)),
            feelShape = RectangleShape,
            chipShape = RectangleShape,
            buttonShape = RectangleShape,
            selectedOption = SelectionStyle(fill = dark, content = light, border = null),
            pillDashed = false,
            stepper = StepperStyle(container = mid, content = light, border = dark, shape = RectangleShape),
            weight = WeightStyle(TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 30.sp), dark),
            doneButton = DoneButtonStyle(
                container = dark, content = light, shape = RectangleShape,
                hardShadow = HardShadow(4.dp, mid),
                textStyle = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.sp),
                uppercase = true,
            ),
            doneIcon = dark,
            titleStyle = TextStyle(fontFamily = GymFonts.pixelifySans, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp),
            titleColor = dark,
            titleGlow = null,
            titleUppercase = false,
            groupStyle = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 18.sp),
            groupUppercase = false,
            nav = NavStyle(container = dark, indicator = light, selectedIcon = dark, selectedText = light, unselected = light),
            themeButton = ThemeButtonStyle(ThemeIcon.DITHER, dark, cardBg, dark, RectangleShape),
            displayFont = GymFonts.pixelifySans,
            backgroundDecor = BackgroundDecor.PIXEL_GRID,
            headerDecor = HeaderDecor.DITHER_BAND,
            ditherInk = dark,
            scanlines = null,
            kicker = false,
        ),
    )
}

// ---------------------------------------------------------------- 3. Неон синтвейв
object SynthwaveTheme {
    private val bg = Color(0xFF0B0618)
    private val card = Color(0xF0100822) // 94%
    private val text = Color(0xFFF4ECFF)
    private val secondary = Color(0xFFB9A8D9)
    private val magenta = Color(0xFFFF3EDB)
    private val cyan = Color(0xFF3DF2FF)
    private val mint = Color(0xFF4DFFB0)
    private val orange = Color(0xFFFF8A5C)
    private val onMagenta = Color(0xFF1A0326)

    private val scheme = darkColorScheme(
        primary = magenta, onPrimary = onMagenta, primaryContainer = Color(0xFF3A0F4A), onPrimaryContainer = Color(0xFFFFD6F7),
        secondary = cyan, onSecondary = bg, secondaryContainer = cyan, onSecondaryContainer = bg,
        tertiary = mint, onTertiary = bg,
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = Color(0xFF1E1238), onSurfaceVariant = secondary,
        surfaceContainerLowest = bg, surfaceContainerLow = Color(0xFF120A26), surfaceContainer = Color(0xFF150C2C),
        surfaceContainerHigh = Color(0xFF1A1036), surfaceContainerHighest = Color(0xFF261A48),
        outline = cyan, outlineVariant = Color(0xFF3A2A5C), error = orange, onError = bg, surfaceTint = magenta,
    )

    private fun neonFeel(c: Color) = FeelStyle(c, c.copy(alpha = 0.6f), c, bg, selectedGlow = Glow(c, 10.dp))

    val spec = GymThemeSpec(
        colorScheme = scheme,
        typography = typographyOf(GymFonts.exo2),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(24.dp),
        ),
        tokens = GymTokens(
            id = GymThemeId.NEON_SYNTHWAVE,
            isLight = false,
            background = bg,
            pageSecondary = secondary,
            card = CardStyle(
                list = card, active = card, done = card, highlight = card,
                content = text, shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, cyan), highlightBorder = BorderStroke(2.dp, magenta),
                glow = Glow(cyan.copy(alpha = 0.55f), 10.dp),
            ),
            feel = feelMap(neonFeel(mint), neonFeel(cyan), neonFeel(orange)),
            feelShape = RoundedCornerShape(14.dp),
            chipShape = CircleShape,
            buttonShape = RoundedCornerShape(14.dp),
            selectedOption = SelectionStyle(fill = Color(0xFF2A0B33), content = text, border = magenta, glow = Glow(magenta, 10.dp)),
            pillDashed = false,
            stepper = StepperStyle(container = Color.Transparent, content = cyan, border = cyan.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp)),
            weight = WeightStyle(
                TextStyle(fontFamily = GymFonts.unbounded, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, shadow = glowShadow(magenta)),
                text,
            ),
            doneButton = DoneButtonStyle(
                container = magenta, content = onMagenta, shape = RoundedCornerShape(14.dp),
                glow = Glow(magenta, 14.dp),
                textStyle = TextStyle(fontFamily = GymFonts.unbounded, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
            ),
            doneIcon = mint,
            titleStyle = TextStyle(fontFamily = GymFonts.unbounded, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
            titleColor = cyan,
            titleGlow = cyan,
            titleUppercase = false,
            groupStyle = TextStyle(fontFamily = GymFonts.exo2, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            groupUppercase = false,
            nav = NavStyle(container = Color(0xFF100822), indicator = cyan, selectedIcon = bg, selectedText = cyan, unselected = secondary),
            themeButton = ThemeButtonStyle(ThemeIcon.PALETTE, cyan, Color.Transparent, cyan, RoundedCornerShape(12.dp), Glow(cyan, 8.dp)),
            displayFont = GymFonts.unbounded,
            backgroundDecor = BackgroundDecor.SYNTHWAVE,
            headerDecor = HeaderDecor.GLOW_LINE,
            ditherInk = cyan,
            scanlines = null,
            kicker = false,
        ),
    )
}

// ---------------------------------------------------------------- 4. Неон кислота
object AcidTheme {
    private val bg = Color(0xFF050505)
    private val card = Color(0xFF0C0C0C)
    private val text = Color(0xFFEDEDED)
    private val secondary = Color(0xFFA3A3A3)
    private val lime = Color(0xFFC8FF2E)
    private val violet = Color(0xFFA66CFF)
    private val black = Color(0xFF000000)
    private val shape6 = RoundedCornerShape(6.dp)

    private val scheme = darkColorScheme(
        primary = lime, onPrimary = black, primaryContainer = Color(0xFF1E2A00), onPrimaryContainer = lime,
        secondary = violet, onSecondary = black, secondaryContainer = lime, onSecondaryContainer = black,
        tertiary = violet, onTertiary = black,
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = Color(0xFF161616), onSurfaceVariant = secondary,
        surfaceContainerLowest = bg, surfaceContainerLow = card, surfaceContainer = Color(0xFF101010),
        surfaceContainerHigh = Color(0xFF141414), surfaceContainerHighest = Color(0xFF1C1C1C),
        outline = violet, outlineVariant = Color(0xFF3A2A5C), error = Color(0xFFFF7A45), onError = black, surfaceTint = lime,
    )

    val spec = GymThemeSpec(
        colorScheme = scheme,
        typography = typographyOf(GymFonts.jura, bodyWeight = FontWeight.SemiBold),
        shapes = Shapes(extraSmall = shape6, small = shape6, medium = shape6, large = shape6, extraLarge = shape6),
        tokens = GymTokens(
            id = GymThemeId.NEON_ACID,
            isLight = false,
            background = bg,
            pageSecondary = secondary,
            card = CardStyle(
                list = card, active = card, done = card, highlight = card,
                content = text, shape = shape6,
                border = BorderStroke(2.dp, violet), highlightBorder = BorderStroke(2.dp, lime),
                glow = Glow(violet.copy(alpha = 0.5f), 10.dp),
                outerOutline = violet.copy(alpha = 0.7f),
            ),
            feel = feelMap(
                FeelStyle(Color(0xFF7DFFC4), Color(0xFF2E5E4A), Color(0xFF7DFFC4), black),
                FeelStyle(text, Color(0xFF4A3A6E), violet, black, selectedGlow = Glow(violet, 10.dp)),
                FeelStyle(Color(0xFFFF7A45), Color(0xFF5E3020), Color(0xFFFF7A45), black),
            ),
            feelShape = shape6,
            chipShape = shape6,
            buttonShape = shape6,
            selectedOption = SelectionStyle(fill = lime, content = black, border = null, glow = Glow(lime, 8.dp)),
            pillDashed = false,
            stepper = StepperStyle(container = Color.Transparent, content = lime, border = lime, shape = shape6),
            weight = WeightStyle(TextStyle(fontFamily = GymFonts.russoOne, fontSize = 30.sp), text),
            doneButton = DoneButtonStyle(
                container = Color.Transparent, content = lime, shape = shape6,
                border = BorderStroke(2.dp, lime), glow = Glow(lime, 12.dp),
                textStyle = TextStyle(fontFamily = GymFonts.russoOne, fontSize = 18.sp, letterSpacing = 1.sp, shadow = glowShadow(lime, 12f)),
                uppercase = true,
            ),
            doneIcon = lime,
            titleStyle = TextStyle(fontFamily = GymFonts.russoOne, fontSize = 28.sp, lineHeight = 34.sp),
            titleColor = text,
            titleGlow = violet,
            titleUppercase = false,
            groupStyle = TextStyle(fontFamily = GymFonts.russoOne, fontSize = 18.sp, letterSpacing = 1.sp),
            groupUppercase = true,
            nav = NavStyle(container = card, indicator = lime, selectedIcon = black, selectedText = lime, unselected = secondary),
            themeButton = ThemeButtonStyle(ThemeIcon.BOLT, lime, Color.Transparent, lime, shape6, Glow(lime, 8.dp)),
            displayFont = GymFonts.russoOne,
            backgroundDecor = BackgroundDecor.FAINT_GRID,
            headerDecor = HeaderDecor.NONE,
            ditherInk = lime,
            scanlines = null,
            kicker = false,
        ),
    )
}

// ---------------------------------------------------------------- 5. Киберпанк жёлтый
object CyberYellowTheme {
    private val yellow = Color(0xFFFCEE0A)
    private val black = Color(0xFF0A0A0B)
    private val pageSecondary = Color(0xFF3A3A20)
    private val cardText = Color(0xFFF2F2F2)
    private val cardSecondary = Color(0xFFC9C29A)
    private val cyan = Color(0xFF02D7F2)
    private val red = Color(0xFFFF2A6D)
    private val chip = CutCornerShape(bottomEnd = 8.dp)
    private val cardShape = CutCornerShape(topEnd = 22.dp, bottomStart = 22.dp)

    private val scheme = lightColorScheme(
        primary = black, onPrimary = yellow, primaryContainer = black, onPrimaryContainer = yellow,
        secondary = black, onSecondary = yellow, secondaryContainer = black, onSecondaryContainer = yellow,
        tertiary = black, onTertiary = yellow,
        background = yellow, onBackground = black, surface = yellow, onSurface = black,
        surfaceVariant = Color(0xFFF2E30A), onSurfaceVariant = pageSecondary,
        surfaceContainerLowest = yellow, surfaceContainerLow = Color(0xFFF7E80A), surfaceContainer = Color(0xFFF2E40A),
        surfaceContainerHigh = Color(0xFFFFF35A), surfaceContainerHighest = Color(0xFFFFF7A0),
        outline = black, outlineVariant = pageSecondary, error = Color(0xFFA0002E), onError = yellow, surfaceTint = black,
    )

    /** Внутри чёрных карточек — своя тёмная схема. */
    private val cardScheme = darkColorScheme(
        primary = yellow, onPrimary = black, primaryContainer = red, onPrimaryContainer = black,
        secondary = cyan, onSecondary = black, secondaryContainer = cyan, onSecondaryContainer = black,
        tertiary = red, onTertiary = black,
        background = black, onBackground = cardText, surface = black, onSurface = cardText,
        surfaceVariant = Color(0xFF1A1A1C), onSurfaceVariant = cardSecondary,
        surfaceContainerLowest = black, surfaceContainerLow = black, surfaceContainer = Color(0xFF141416),
        surfaceContainerHigh = Color(0xFF1C1C1E), surfaceContainerHighest = Color(0xFF2A2A22),
        outline = yellow, outlineVariant = Color(0xFF4A4630), error = red, onError = black, surfaceTint = yellow,
    )

    val spec = GymThemeSpec(
        colorScheme = scheme,
        typography = typographyOf(GymFonts.jetBrainsMono),
        shapes = Shapes(
            extraSmall = CutCornerShape(4.dp), small = CutCornerShape(bottomEnd = 8.dp), medium = cardShape,
            large = cardShape, extraLarge = CutCornerShape(topEnd = 22.dp, topStart = 0.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
        ),
        tokens = GymTokens(
            id = GymThemeId.CYBER_YELLOW,
            isLight = true,
            background = yellow,
            pageSecondary = pageSecondary,
            card = CardStyle(
                list = black, active = black, done = black, highlight = black,
                content = cardText, shape = cardShape,
                border = null, highlightBorder = BorderStroke(2.dp, cyan),
                activeLeftEdge = red,
                scheme = cardScheme,
            ),
            feel = feelMap(
                FeelStyle(cyan, cyan, cyan, black),
                FeelStyle(cardText, cardText, yellow, black),
                FeelStyle(red, red, red, black),
            ),
            feelShape = chip,
            chipShape = chip,
            buttonShape = chip,
            selectedOption = SelectionStyle(fill = yellow, content = black, border = null),
            pillDashed = false,
            stepper = StepperStyle(container = cyan, content = black, border = null, shape = chip),
            weight = WeightStyle(
                TextStyle(fontFamily = GymFonts.rubikMonoOne, fontSize = 26.sp),
                yellow, decor = WeightDecor.UNDERLINE, decorColor = cyan,
            ),
            doneButton = DoneButtonStyle(
                container = red, content = black, shape = CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp),
                textStyle = TextStyle(fontFamily = GymFonts.rubikMonoOne, fontSize = 16.sp),
                uppercase = true,
            ),
            doneIcon = cyan,
            titleStyle = TextStyle(fontFamily = GymFonts.rubikMonoOne, fontSize = 24.sp, lineHeight = 30.sp),
            titleColor = black,
            titleGlow = null,
            titleUppercase = true,
            groupStyle = TextStyle(fontFamily = GymFonts.rubikMonoOne, fontSize = 15.sp),
            groupUppercase = true,
            nav = NavStyle(container = black, indicator = yellow, selectedIcon = black, selectedText = yellow, unselected = cardText),
            themeButton = ThemeButtonStyle(ThemeIcon.PALETTE, yellow, black, null, chip),
            displayFont = GymFonts.rubikMonoOne,
            backgroundDecor = BackgroundDecor.CORNER_STRIPES,
            headerDecor = HeaderDecor.HAZARD_BAND,
            ditherInk = black,
            scanlines = null,
            kicker = true,
            feelOnPage = mapOf(Feel.EASY to Color(0xFF005F6B), Feel.NORMAL to black, Feel.HARD to Color(0xFFA0002E)),
        ),
    )
}

// ---------------------------------------------------------------- 6. Киберпанк HUD
object CyberHudTheme {
    private val bg = Color(0xFF07100F)
    private val card = Color(0xFF0A1514)
    private val text = Color(0xFFD7FFF6)
    private val secondary = Color(0xFF8FBFB4)
    private val teal = Color(0xFF3FFFD2)
    private val red = Color(0xFFFF3B3B)
    private val softRed = Color(0xFFFF6B6B)
    private val dark = Color(0xFF04110E)
    private val mono = GymFonts.ibmPlexMono

    private val scheme = darkColorScheme(
        primary = teal, onPrimary = dark, primaryContainer = Color(0xFF0F3A33), onPrimaryContainer = teal,
        secondary = teal, onSecondary = dark, secondaryContainer = teal, onSecondaryContainer = dark,
        tertiary = red, onTertiary = Color(0xFF140404),
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = Color(0xFF0E1B19), onSurfaceVariant = secondary,
        surfaceContainerLowest = bg, surfaceContainerLow = Color(0xFF091413), surfaceContainer = Color(0xFF0B1716),
        surfaceContainerHigh = Color(0xFF0E1C1A), surfaceContainerHighest = Color(0xFF13241F),
        outline = teal, outlineVariant = teal.copy(alpha = 0.3f), error = softRed, onError = dark, surfaceTint = teal,
    )

    private fun hudFeel(c: Color) = FeelStyle(c, c, c, dark)

    val spec = GymThemeSpec(
        colorScheme = scheme,
        typography = typographyOf(mono),
        shapes = squareShapes,
        tokens = GymTokens(
            id = GymThemeId.CYBER_HUD,
            isLight = false,
            background = bg,
            pageSecondary = secondary,
            card = CardStyle(
                list = card, active = card, done = card, highlight = card,
                content = text, shape = RectangleShape,
                border = BorderStroke(1.dp, teal.copy(alpha = 0.18f)), highlightBorder = BorderStroke(1.dp, teal),
                cornerBrackets = teal,
            ),
            feel = feelMap(hudFeel(teal), hudFeel(text), hudFeel(softRed)),
            feelShape = RectangleShape,
            chipShape = RectangleShape,
            buttonShape = RectangleShape,
            selectedOption = SelectionStyle(fill = teal, content = dark, border = null),
            pillDashed = false,
            stepper = StepperStyle(container = Color.Transparent, content = teal, border = teal, shape = RectangleShape),
            weight = WeightStyle(TextStyle(fontFamily = GymFonts.oswald, fontWeight = FontWeight.SemiBold, fontSize = 32.sp), text),
            doneButton = DoneButtonStyle(
                container = red, content = Color(0xFF140404), shape = RectangleShape,
                textStyle = TextStyle(fontFamily = GymFonts.oswald, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 4.sp),
                uppercase = true,
            ),
            doneIcon = teal,
            titleStyle = TextStyle(fontFamily = GymFonts.oswald, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 1.sp),
            titleColor = text,
            titleGlow = null,
            titleUppercase = true,
            groupStyle = TextStyle(fontFamily = GymFonts.oswald, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = 1.sp),
            groupUppercase = true,
            nav = NavStyle(container = card, indicator = teal, selectedIcon = dark, selectedText = teal, unselected = secondary),
            themeButton = ThemeButtonStyle(ThemeIcon.RED_SQUARE, red, red.copy(alpha = 0.12f), red, RectangleShape, Glow(red, 6.dp)),
            displayFont = GymFonts.oswald,
            backgroundDecor = BackgroundDecor.NONE,
            headerDecor = HeaderDecor.HUD_STRIP,
            ditherInk = teal,
            scanlines = teal.copy(alpha = 0.05f),
            kicker = false,
        ),
    )
}
