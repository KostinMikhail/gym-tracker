package com.gym.tracker.domain.timer

import java.util.Locale

/** Форматирование времени таймера. */
object TimerFormat {
    /** «3:00», «0:15», «12:05»; от часа — «1:02:30». */
    fun clock(totalSec: Long): String {
        val s = totalSec.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
    }

    /** Большие цифры: всегда «мм:сс» (две цифры минут — ширина не меняется от 9:59 к 10:00). */
    fun countdown(totalSec: Long): String {
        val s = totalSec.coerceAtLeast(0)
        return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)
    }

    fun millis(ms: Long): String = clock((ms + 999) / 1000)
}
