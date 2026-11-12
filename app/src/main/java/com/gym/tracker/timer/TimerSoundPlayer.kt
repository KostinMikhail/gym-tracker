package com.gym.tracker.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.gym.tracker.R
import com.gym.tracker.domain.timer.TimerSignal

/**
 * Сигналы таймера: SoundPool (малая задержка) + вибрация. На время сигнала берётся кратковременный
 * аудиофокус с приглушением (музыка пользователя становится тише и возвращается после сигнала).
 * Звук идёт как медиа — громкость регулируется кнопками громкости медиа.
 */
class TimerSoundPlayer(context: Context) {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
    private val bell = pool.load(context, R.raw.timer_bell, 1)
    private val knock = pool.load(context, R.raw.timer_knock, 1)

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private var hasFocus = false

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val token = Any()
    private val streams = mutableListOf<Int>()

    fun play(signal: TimerSignal) {
        // Новый сигнал заменяет недоигранный (фокус не отпускается между ними).
        handler.removeCallbacksAndMessages(token)
        val (sample, times, tailMs) = when (signal) {
            TimerSignal.BELL -> Triple(bell, BELL_1, BELL_TAIL_MS)
            TimerSignal.DOUBLE_BELL -> Triple(bell, BELL_2, BELL_TAIL_MS)
            TimerSignal.TRIPLE_BELL -> Triple(bell, BELL_3, BELL_TAIL_MS)
            TimerSignal.KNOCKS -> Triple(knock, KNOCKS_3, KNOCK_TAIL_MS)
        }
        if (!hasFocus) hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        val start = SystemClock.uptimeMillis()
        times.forEach { at ->
            handler.postAtTime({ playSample(sample) }, token, start + at)
        }
        vibrate(times, if (signal == TimerSignal.KNOCKS) KNOCK_VIBRATION_MS else BELL_VIBRATION_MS)
        handler.postAtTime({ abandonFocus() }, token, start + times.last() + tailMs)
    }

    private fun playSample(sample: Int) {
        val id = pool.play(sample, 1f, 1f, 1, 0, 1f)
        if (id != 0) {
            streams += id
            if (streams.size > 8) streams.removeAt(0)
        }
    }

    private fun vibrate(times: List<Long>, pulse: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        // [пауза, вибрация, пауза, вибрация, …] — вибрация одновременно с каждым ударом.
        val timings = mutableListOf<Long>()
        var t = 0L
        times.forEach { at ->
            timings += at - t
            timings += pulse
            t = at + pulse
        }
        val effect = VibrationEffect.createWaveform(timings.toLongArray(), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
        } else {
            v.vibrate(effect)
        }
    }

    /** Пауза / сброс: ни одного «запоздалого» звука. */
    fun silence() {
        handler.removeCallbacksAndMessages(token)
        streams.forEach { pool.stop(it) }
        streams.clear()
        vibrator?.cancel()
        abandonFocus()
    }

    private fun abandonFocus() {
        if (hasFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasFocus = false
        }
    }

    fun release() {
        silence()
        pool.release()
    }

    private companion object {
        val BELL_1 = listOf(0L)
        val BELL_2 = listOf(0L, 450L)
        val BELL_3 = listOf(0L, 450L, 900L)

        /** Три стука с интервалом ~0,35 с. */
        val KNOCKS_3 = listOf(0L, 350L, 700L)
        const val BELL_TAIL_MS = 2_100L
        const val KNOCK_TAIL_MS = 250L
        const val BELL_VIBRATION_MS = 250L
        const val KNOCK_VIBRATION_MS = 70L
    }
}
