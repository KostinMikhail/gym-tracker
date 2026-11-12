package com.gym.tracker.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Декоративные слои темы. Фон и оверлей рисуются ОДИН раз на весь экран (не в элементах списка),
 * в отдельных graphicsLayer — прокрутка их не перерисовывает.
 */

/** Фон приложения: цвет темы + фоновый декор; поверх содержимого — сканлайны (не перехватывают касания). */
@Composable
fun GymBackground(content: @Composable BoxScope.() -> Unit) {
    val t = LocalGymTokens.current
    Box(Modifier.fillMaxSize().background(t.background)) {
        if (t.backgroundDecor != BackgroundDecor.NONE) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer()
                    .backgroundDecor(t.backgroundDecor, t, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.error),
            )
        }
        // Экраны прозрачные (чтобы был виден декор), поэтому цвет текста по умолчанию задаём здесь.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
        val scan = t.scanlines
        if (scan != null) {
            Box(Modifier.fillMaxSize().graphicsLayer().scanlines(scan))
        }
    }
}

/** [accent]/[accent2] — цвета темы для синтвейва (маджента и оранжевый). Остальной декор рисуется «чернилами» темы. */
private fun Modifier.backgroundDecor(decor: BackgroundDecor, t: GymTokens, accent: Color, accent2: Color): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    when (decor) {
        BackgroundDecor.NONE -> onDrawBehind { }

        BackgroundDecor.PIXEL_GRID -> {
            val step = 3.dp.toPx()
            val path = Path()
            var x = 0f
            while (x <= w) { path.moveTo(x, 0f); path.lineTo(x, h); x += step }
            var y = 0f
            while (y <= h) { path.moveTo(0f, y); path.lineTo(w, y); y += step }
            val color = t.ditherInk.copy(alpha = 0.07f)
            onDrawBehind { drawPath(path, color, style = Stroke(1f)) }
        }

        BackgroundDecor.FAINT_GRID -> {
            val step = 24.dp.toPx()
            val path = Path()
            var x = 0f
            while (x <= w) { path.moveTo(x, 0f); path.lineTo(x, h); x += step }
            var y = 0f
            while (y <= h) { path.moveTo(0f, y); path.lineTo(w, y); y += step }
            val color = Color.White.copy(alpha = 0.03f)
            onDrawBehind { drawPath(path, color, style = Stroke(1f)) }
        }

        BackgroundDecor.SYNTHWAVE -> {
            val magenta = accent
            // Перспективная сетка в нижней части экрана.
            val horizon = h * 0.62f
            val grid = Path()
            val cx = w / 2
            val n = 12
            for (i in -n..n) {
                grid.moveTo(cx, horizon)
                grid.lineTo(cx + i * (w / 5f), h)
            }
            var k = 1
            while (k <= 14) {
                val f = k / 14f
                val yy = horizon + (h - horizon) * f * f
                grid.moveTo(0f, yy); grid.lineTo(w, yy)
                k++
            }
            val gridColor = magenta.copy(alpha = 0.28f)
            val stroke = Stroke(1.dp.toPx())
            // Полосатое «солнце» за шапкой.
            val r = 72.dp.toPx()
            val sunCenter = Offset(w * 0.74f, 96.dp.toPx())
            val sunBrush = Brush.verticalGradient(
                listOf(magenta.copy(alpha = 0.32f), accent2.copy(alpha = 0.22f)),
                startY = sunCenter.y - r, endY = sunCenter.y + r,
            )
            val gap = 4.dp.toPx()
            onDrawBehind {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, magenta.copy(alpha = 0.06f)), startY = horizon, endY = h),
                    topLeft = Offset(0f, horizon), size = androidx.compose.ui.geometry.Size(w, h - horizon),
                )
                drawPath(grid, gridColor, style = stroke)
                // Горизонтальные прорези в нижней половине солнца.
                val stripes = Path().apply {
                    addOval(Rect(sunCenter, r))
                }
                drawPath(stripes, sunBrush)
                var sy = sunCenter.y
                var hgap = gap * 0.4f
                while (sy < sunCenter.y + r) {
                    drawRect(t.background, topLeft = Offset(sunCenter.x - r, sy), size = androidx.compose.ui.geometry.Size(2 * r, hgap))
                    sy += gap * 2.2f
                    hgap += gap * 0.25f
                }
            }
        }

        BackgroundDecor.CORNER_STRIPES -> {
            val side = 180.dp.toPx()
            val stripe = 8.dp.toPx()
            val color = t.ditherInk.copy(alpha = 0.07f)
            val path = Path()
            var o = -side
            while (o < side * 2) {
                // диагональные полосы −45°
                path.moveTo(w - side + o, 0f)
                path.lineTo(w - side + o + stripe, 0f)
                path.lineTo(w - side + o + stripe + side, side)
                path.lineTo(w - side + o + side, side)
                path.close()
                o += stripe * 2
            }
            onDrawBehind {
                clipRect(left = w - side, top = 0f, right = w, bottom = side) { drawPath(path, color) }
            }
        }
    }
}

private fun Modifier.scanlines(color: Color): Modifier = drawWithCache {
    val step = 3.dp.toPx()
    val line = 1.dp.toPx()
    val path = Path()
    var y = 0f
    while (y < size.height) {
        path.addRect(Rect(0f, y, size.width, y + line))
        y += step
    }
    onDrawBehind { drawPath(path, color) }
}

/**
 * Декор под шапкой экрана: полоса дизеринга / светящаяся линия / «опасная» лента / HUD-строка.
 * [hudText] — текст строки данных для HUD («РЕЖИМ: ЗАЛ · …»).
 */
@Composable
fun HeaderDecor(hudText: String, modifier: Modifier = Modifier) {
    val t = LocalGymTokens.current
    when (t.headerDecor) {
        HeaderDecor.NONE -> Unit

        HeaderDecor.DITHER_BAND -> {
            // Первые сегменты — средним тоном (в Game Boy это #306230), последние — основными «чернилами».
            val light = MaterialTheme.colorScheme.secondary
            Row(modifier.fillMaxWidth().height(16.dp)) {
                listOf(Dither.SPARSE to light, Dither.DOTS to light, Dither.CHECKER to t.ditherInk, Dither.DENSE to t.ditherInk)
                    .forEach { (d, ink) -> Box(Modifier.weight(1f).height(16.dp).ditherFill(d, ink)) }
            }
        }

        HeaderDecor.GLOW_LINE -> {
            val cyan = MaterialTheme.colorScheme.secondary
            Box(
                modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .neonGlow(Glow(cyan, 8.dp), RectangleShape, strokeWidth = 1.dp)
                    .background(cyan),
            )
        }

        HeaderDecor.HAZARD_BAND -> {
            val black = t.ditherInk
            val yellow = t.background
            Canvas(modifier.fillMaxWidth().height(10.dp)) {
                drawRect(yellow)
                val stripe = 8.dp.toPx()
                val hh = size.height
                var x = -hh
                while (x < size.width + hh) {
                    val p = Path().apply {
                        moveTo(x, hh); lineTo(x + stripe, hh); lineTo(x + stripe + hh, 0f); lineTo(x + hh, 0f); close()
                    }
                    drawPath(p, black)
                    x += stripe * 2
                }
            }
        }

        HeaderDecor.HUD_STRIP -> {
            Text(
                hudText,
                style = TextStyle(fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = MaterialTheme.typography.bodySmall.fontFamily),
                color = t.pageSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
    }
}

/** Крупный заголовок экрана в стиле темы ([fallback] — стиль Классической). */
@Composable
fun ScreenTitle(
    text: String,
    fallback: TextStyle,
    modifier: Modifier = Modifier,
    fallbackColor: Color = Color.Unspecified,
    sizeOverride: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    val t = LocalGymTokens.current
    var style = t.titleStyle ?: fallback
    if (t.titleStyle != null && sizeOverride != androidx.compose.ui.unit.TextUnit.Unspecified) {
        style = style.copy(fontSize = sizeOverride, lineHeight = sizeOverride * 1.2f)
    }
    val glow = t.titleGlow
    if (glow != null) style = style.copy(shadow = Shadow(color = glow, blurRadius = 16f))
    Text(
        if (t.titleUppercase) text.uppercase() else text,
        style = style,
        color = t.titleColor ?: fallbackColor,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** «// ТРЕНИРОВКА» над заголовком — только в темах с kicker. */
@Composable
fun ScreenKicker(text: String, modifier: Modifier = Modifier) {
    val t = LocalGymTokens.current
    if (!t.kicker) return
    Text(
        "// " + text.uppercase(),
        style = LocalTextStyle.current.copy(fontSize = 12.sp, letterSpacing = 2.sp, fontFamily = MaterialTheme.typography.labelSmall.fontFamily),
        color = t.pageSecondary,
        modifier = modifier,
    )
}

/** Квадратик дизеринга (образец самочувствия, значок кнопки темы). */
@Composable
fun DitherSwatch(d: Dither, ink: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(modifier.size(size).ditherFill(d, ink).drawBehind { drawRect(ink, style = Stroke(1.dp.toPx())) })
}
