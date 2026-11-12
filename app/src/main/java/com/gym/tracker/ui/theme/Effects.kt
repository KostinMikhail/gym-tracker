package com.gym.tracker.ui.theme

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Декоративные модификаторы тем. Рисуются в фазе draw (без перекомпозиции) и кэшируются RenderNode,
 * поэтому не нагружают прокрутку.
 */

private fun DrawScope.outlinePath(shape: Shape): Path =
    Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@outlinePath)) }

/**
 * Мягкое неоновое свечение по контуру формы (тень от обводки — свечение и снаружи, и чуть внутрь).
 * Цветные тени требуют API 28+; на старых версиях свечение не рисуется (остаётся обычная рамка).
 */
fun Modifier.neonGlow(glow: Glow?, shape: Shape, strokeWidth: Dp = 1.5.dp): Modifier =
    if (glow == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        this
    } else {
        drawBehind {
            val path = outlinePath(shape)
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                this.strokeWidth = strokeWidth.toPx()
                color = glow.color.toArgb()
                setShadowLayer(glow.radius.toPx(), 0f, 0f, glow.color.toArgb())
            }
            drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
        }
    }

/** Жёсткая тень со смещением вправо-вниз, без размытия. */
fun Modifier.hardShadow(shadow: HardShadow?, shape: Shape): Modifier =
    if (shadow == null) {
        this
    } else {
        drawBehind {
            val o = shadow.offset.toPx()
            translate(o, o) { drawOutline(shape.createOutline(size, layoutDirection, this), shadow.color) }
        }
    }

/** Пунктирная рамка поверх содержимого. */
fun Modifier.dashedBorder(color: Color?, width: Dp, shape: Shape, dash: Dp = 6.dp): Modifier =
    if (color == null) {
        this
    } else {
        drawWithContent {
            drawContent()
            val w = width.toPx()
            val inset = w / 2
            val inner = Size(size.width - w, size.height - w)
            val outline = shape.createOutline(inner, layoutDirection, this)
            translate(inset, inset) {
                drawOutline(
                    outline, color,
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx() * 0.7f))),
                )
            }
        }
    }

/** Яркие уголки-скобки (16 × 2 dp) во всех четырёх углах. */
fun Modifier.cornerBrackets(color: Color?, length: Dp = 16.dp, thickness: Dp = 2.dp): Modifier =
    if (color == null) {
        this
    } else {
        drawWithContent {
            drawContent()
            val l = length.toPx()
            val t = thickness.toPx()
            val w = size.width
            val h = size.height
            // верх-лево
            drawRect(color, Offset(0f, 0f), Size(l, t)); drawRect(color, Offset(0f, 0f), Size(t, l))
            // верх-право
            drawRect(color, Offset(w - l, 0f), Size(l, t)); drawRect(color, Offset(w - t, 0f), Size(t, l))
            // низ-лево
            drawRect(color, Offset(0f, h - t), Size(l, t)); drawRect(color, Offset(0f, h - l), Size(t, l))
            // низ-право
            drawRect(color, Offset(w - l, h - t), Size(l, t)); drawRect(color, Offset(w - t, h - l), Size(t, l))
        }
    }

/** Вторая тонкая обводка на [gap] снаружи формы («двойная неоновая трубка»). */
fun Modifier.outerOutline(color: Color?, shape: Shape, gap: Dp = 4.dp, width: Dp = 1.dp): Modifier =
    if (color == null) {
        this
    } else {
        drawBehind {
            val g = gap.toPx()
            val outer = Size(size.width + 2 * g, size.height + 2 * g)
            val outline = shape.createOutline(outer, layoutDirection, this)
            translate(-g, -g) { drawOutline(outline, color, style = Stroke(width.toPx())) }
        }
    }

/** Цветная полоса слева, обрезанная по форме карточки. */
fun Modifier.leftEdge(color: Color?, shape: Shape, width: Dp = 4.dp): Modifier =
    if (color == null) {
        this
    } else {
        drawWithContent {
            drawContent()
            clipPath(outlinePath(shape)) { drawRect(color, size = Size(width.toPx(), size.height)) }
        }
    }

// ---------------------------------------------------------------- Пиксельный дизеринг

/** Узор 4×4 клетки: true — «чернила». */
private fun ditherCell(d: Dither, x: Int, y: Int): Boolean = when (d) {
    Dither.SPARSE -> (x == 0 && y == 0) || (x == 2 && y == 2) // 12,5 %
    Dither.DOTS -> (y == 0 && (x == 0 || x == 2)) || (y == 2 && (x == 1 || x == 3)) // 25 %
    Dither.CHECKER -> (x + y) % 2 == 0 // 50 %
    Dither.DENSE -> !((y == 0 && (x == 0 || x == 2)) || (y == 2 && (x == 1 || x == 3))) // 75 %
}

/**
 * Кисть с повторяющимся узором дизеринга. Клетка — целое число пикселей ([cellPx]),
 * узор не масштабируется → пиксели чёткие, без размытия.
 */
fun ditherBrush(d: Dither, ink: Color, cellPx: Int): ShaderBrush {
    val cell = cellPx.coerceAtLeast(1)
    val bitmap = ImageBitmap(cell * 4, cell * 4)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { color = ink; isAntiAlias = false }
    for (y in 0 until 4) for (x in 0 until 4) {
        if (ditherCell(d, x, y)) {
            canvas.drawRect(
                (x * cell).toFloat(), (y * cell).toFloat(), ((x + 1) * cell).toFloat(), ((y + 1) * cell).toFloat(), paint,
            )
        }
    }
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

/** Площадь, залитая дизерингом (кисть строится один раз и кэшируется). */
fun Modifier.ditherFill(d: Dither, ink: Color, cell: Dp = 2.dp): Modifier = drawWithCache {
    val brush = ditherBrush(d, ink, cell.roundToPx())
    onDrawBehind { drawRect(brush) }
}
