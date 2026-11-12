package com.gym.tracker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.gym.tracker.data.db.Feel
import com.gym.tracker.ui.theme.GymThemeId
import com.gym.tracker.ui.theme.themeSpec
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG: обычный текст ≥ 4.5:1, крупный (≥ 24 sp) и значки ≥ 3:1 — для всех тем. */
class ThemeContrastTest {

    private fun contrast(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance() + 0.05
        val b = bg.luminance() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    private val failures = mutableListOf<String>()

    private fun check(theme: GymThemeId, what: String, fg: Color, bg: Color, min: Double = 4.5) {
        val c = contrast(fg, bg)
        if (c < min) failures += "$theme: $what = ${"%.2f".format(c)} < $min"
    }

    @Test
    fun allThemesMeetWcag() {
        GymThemeId.entries.forEach { id ->
            val spec = themeSpec(id)
            val t = spec.tokens
            val page = spec.colorScheme
            val cardScheme = t.card.scheme ?: page
            val bg = t.background
            val cards = listOf(t.card.list, t.card.active, t.card.done).map { it.compositeOver(bg) }
            val cardText = t.card.content ?: page.onSurface

            check(id, "текст на странице", page.onSurface, bg)
            check(id, "вторичный текст на странице", t.pageSecondary, bg)
            check(id, "кнопка «Начать»", page.onPrimary, page.primary)
            t.titleColor?.let { check(id, "заголовок", it, bg, 3.0) }
            cards.forEach { card ->
                check(id, "текст на карточке", cardText, card)
                check(id, "вторичный текст на карточке", cardScheme.onSurfaceVariant, card)
                check(id, "акцент (группа) на карточке", cardScheme.primary, card)
                Feel.entries.forEach { f ->
                    check(id, "самочувствие $f на карточке", t.feel(f).content, card)
                }
            }
            Feel.entries.forEach { f ->
                val s = t.feel(f)
                check(id, "выбранное самочувствие $f", s.selectedContent, s.selectedFill)
                // Вне карточки (страница, диалоги) — цвета для страницы, если заданы, иначе карточные.
                val onPage = t.feelOnPage?.get(f) ?: s.content
                check(id, "самочувствие $f на странице", onPage, bg)
                check(id, "самочувствие $f в диалоге", onPage, page.surfaceContainerHigh)
            }
            check(id, "плашка обычная", cardScheme.onSurfaceVariant, cardScheme.surfaceContainerHighest.compositeOver(cards[1]))
            check(id, "плашка подсвеченная", cardScheme.onPrimaryContainer, cardScheme.primaryContainer)
            t.selectedOption?.let { check(id, "выбранный вариант", it.content, it.fill.compositeOver(cards[1])) }
            t.doneButton?.let { check(id, "кнопка «Готово»", it.content, it.container.compositeOver(cards[1])) }
            t.stepper?.let { check(id, "значки − / +", it.content, it.container.compositeOver(cards[1]), 3.0) }
            t.nav?.let { n ->
                check(id, "подпись активной вкладки", n.selectedText, n.container)
                check(id, "неактивная вкладка", n.unselected, n.container)
                check(id, "значок активной вкладки", n.selectedIcon, n.indicator, 3.0)
            }
            // Диалоги и нижние листы рисуются на surfaceContainerHigh/Low страницы.
            check(id, "текст в диалоге", page.onSurface, page.surfaceContainerHigh)
            check(id, "текст в нижнем листе", page.onSurface, page.surfaceContainerLow)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
