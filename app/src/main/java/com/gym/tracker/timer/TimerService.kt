package com.gym.tracker.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gym.tracker.GymApplication
import com.gym.tracker.MainActivity
import com.gym.tracker.R
import com.gym.tracker.domain.timer.TimerFormat
import com.gym.tracker.domain.timer.TimerPhase
import com.gym.tracker.domain.timer.TimerSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground-сервис таймера: держит процесс живым при заблокированном экране и в другом приложении,
 * играет сигналы, показывает постоянное уведомление с кнопкой «Пауза/Продолжить».
 * Пока таймер идёт — частичный wake lock (процессор не засыпает между сигналами).
 * Работает с запуска до окончания (после тройного гонга) или сброса.
 */
class TimerService : Service() {

    private val scope = MainScope()
    private lateinit var controller: TimerController
    private lateinit var player: TimerSoundPlayer
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockAt = 0L
    private var stopJob: Job? = null
    private var shownKey: Any? = null
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller = (application as GymApplication).timerController
        player = TimerSoundPlayer(this)
        createChannel()
        scope.launch {
            controller.signals.collect { if (controller.state.value?.paused != true) player.play(it) }
        }
        scope.launch { controller.state.collect(::onState) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) controller.togglePause()
        // Каждый startForegroundService обязан закончиться startForeground.
        val state = controller.state.value
        goForeground(state)
        if (state == null) stopNow()
        return START_NOT_STICKY
    }

    private fun onState(s: TimerSnapshot?) {
        when {
            // До startForeground не останавливаемся — это сделает onStartCommand (иначе система уронит процесс).
            s == null -> if (foreground) stopNow()
            s.phase == TimerPhase.DONE -> {
                releaseWakeLock()
                notifyIfChanged(s)
                // Дать доиграть тройной гонг, затем остановиться (если не нажали «Ещё раз»).
                if (stopJob == null) {
                    stopJob = scope.launch {
                        delay(DONE_LINGER_MS)
                        stopNow()
                    }
                }
            }
            else -> {
                stopJob?.cancel()
                stopJob = null
                if (s.paused) {
                    player.silence()
                    releaseWakeLock()
                } else {
                    holdWakeLock()
                }
                notifyIfChanged(s)
            }
        }
    }

    private fun goForeground(s: TimerSnapshot?) {
        val n = buildNotification(s)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        foreground = true
        shownKey = s?.let(::keyOf)
    }

    private fun stopNow() {
        player.silence()
        releaseWakeLock()
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    /** Уведомление обновляется не чаще раза в секунду — только когда меняется показанное. */
    private fun keyOf(s: TimerSnapshot): Any = listOf(s.phase, s.round, s.remainingSec, s.paused)

    private fun notifyIfChanged(s: TimerSnapshot) {
        val key = keyOf(s)
        if (key == shownKey || !foreground) return
        shownKey = key
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(s))
    }

    private fun buildNotification(s: TimerSnapshot?): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TIMER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (s == null) return b.setContentTitle(getString(R.string.timer_title)).build()

        val rounds = s.config.rounds
        val title = when (s.phase) {
            TimerPhase.PREP -> getString(R.string.timer_phase_prep)
            TimerPhase.ROUND -> getString(R.string.timer_notification_round, s.round, rounds)
            TimerPhase.REST -> getString(R.string.timer_notification_rest, s.round + 1, rounds)
            TimerPhase.DONE -> getString(R.string.timer_phase_done)
        }
        b.setContentTitle(if (s.paused) getString(R.string.timer_notification_paused, title) else title)
        if (s.phase != TimerPhase.DONE) {
            b.setContentText(getString(R.string.timer_notification_remaining, TimerFormat.clock(s.remainingSec)))
            val toggle = PendingIntent.getService(
                this, 1,
                Intent(this, TimerService::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val label = getString(if (s.paused) R.string.timer_resume else R.string.timer_pause)
            b.addAction(0, label, toggle)
        }
        return b.build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.timer_channel), NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Wake lock с тайм-аутом, продлевается раз в минуту, пока таймер идёт. */
    private fun holdWakeLock() {
        val now = SystemClock.elapsedRealtime()
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gym:interval-timer")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        if (!lock.isHeld || now - wakeLockAt > 60_000) {
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
            wakeLockAt = now
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onDestroy() {
        scope.cancel()
        player.release()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "interval_timer"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE = "com.gym.tracker.timer.TOGGLE"
        private const val DONE_LINGER_MS = 3_500L
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60_000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
            } catch (_: IllegalStateException) {
                // Запуск сервиса из фона запрещён (не должно случаться: старт — по нажатию). Таймер всё равно идёт,
                // пока приложение открыто.
            }
        }
    }
}
