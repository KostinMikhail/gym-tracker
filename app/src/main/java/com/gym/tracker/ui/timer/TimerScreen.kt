package com.gym.tracker.ui.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gym.tracker.R
import com.gym.tracker.domain.timer.TimerConfig
import com.gym.tracker.domain.timer.TimerFormat
import com.gym.tracker.domain.timer.TimerPhase
import com.gym.tracker.domain.timer.TimerSnapshot
import com.gym.tracker.ui.components.ConfirmDialog
import com.gym.tracker.ui.components.GymCard
import com.gym.tracker.ui.theme.GymBackground
import com.gym.tracker.ui.theme.GymTheme
import com.gym.tracker.ui.theme.GymThemeId
import com.gym.tracker.ui.theme.GymThemePreviewProvider
import com.gym.tracker.ui.theme.Glow
import com.gym.tracker.ui.theme.HeaderDecor
import com.gym.tracker.ui.theme.LocalGymTokens
import com.gym.tracker.ui.theme.ScreenKicker
import com.gym.tracker.ui.theme.ScreenTitle
import com.gym.tracker.ui.theme.TimerPhaseStyle
import com.gym.tracker.ui.theme.cornerBrackets
import com.gym.tracker.ui.theme.ditherFill
import com.gym.tracker.ui.theme.neonGlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.gym.tracker.domain.Format

@Composable
fun TimerScreen(vm: TimerViewModel, contentPadding: PaddingValues) {
    val timer by vm.timer.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val notificationsAsked by vm.notificationsAsked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmStop by rememberSaveable { mutableStateOf(false) }

    // Разрешение на уведомления (Android 13+) — один раз, при первом «СТАРТ». Отказ не мешает таймеру.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.start() }
    val onStart = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAsked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            vm.notificationsAskedNow()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.start()
        }
    }

    val active = timer.isActive()
    BackHandler(enabled = active) { confirmStop = true }
    if (confirmStop && active) {
        ConfirmDialog(
            title = stringResource(R.string.timer_stop_title),
            text = stringResource(R.string.timer_stop_text),
            confirmLabel = stringResource(R.string.timer_stop),
            dismissLabel = stringResource(R.string.timer_continue),
            onConfirm = {
                confirmStop = false
                vm.reset()
            },
            onDismiss = { confirmStop = false },
        )
    }

    val s = timer
    when {
        s == null -> config?.let { c ->
            TimerSetup(
                config = c,
                contentPadding = contentPadding,
                onRounds = vm::changeRounds,
                onRound = vm::changeRound,
                onRest = vm::changeRest,
                onStart = onStart,
            )
        }
        s.phase == TimerPhase.DONE -> TimerDone(s, contentPadding, onAgain = vm::again, onSettings = vm::reset)
        else -> TimerRunning(
            s = s,
            contentPadding = contentPadding,
            onPause = vm::pause,
            onResume = vm::resume,
            onRestart = vm::restartPhase,
            onReset = vm::reset,
        )
    }
}

// ---------------------------------------------------------------- Настройки

@Composable
fun TimerSetup(
    config: TimerConfig,
    contentPadding: PaddingValues,
    onRounds: (Int) -> Unit,
    onRound: (Int) -> Unit,
    onRest: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val t = LocalGymTokens.current
    val step = TimerConfig.STEP_SEC
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            ScreenKicker(stringResource(R.string.timer_title))
            ScreenTitle(stringResource(R.string.timer_title), fallback = MaterialTheme.typography.headlineMedium)
        }
        HeaderDecor(
            hudText = stringResource(R.string.hud_strip, Format.weekdayDate(LocalDate.now())),
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val roundsLabel = stringResource(R.string.timer_rounds)
            val roundLabel = stringResource(R.string.timer_round)
            val restLabel = stringResource(R.string.timer_rest)
            SettingRow(roundsLabel, config.rounds.toString(), null, onMinus = { onRounds(-1) }, onPlus = { onRounds(1) })
            SettingRow(roundLabel, TimerFormat.clock(config.roundSec.toLong()), null, onMinus = { onRound(-step) }, onPlus = { onRound(step) })
            SettingRow(
                restLabel,
                TimerFormat.clock(config.restSec.toLong()),
                if (config.restSec == 0) stringResource(R.string.timer_no_rest) else null,
                onMinus = { onRest(-step) },
                onPlus = { onRest(step) },
            )
            Text(
                stringResource(R.string.timer_total, TimerFormat.millis(config.totalMs)),
                style = MaterialTheme.typography.titleLarge,
                color = t.pageSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            val work = t.timer.work
            TimerBigButton(
                text = stringResource(R.string.timer_start),
                container = work.button,
                content = work.onButton,
                onClick = onStart,
                height = 96.dp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, note: String?, onMinus: () -> Unit, onPlus: () -> Unit) {
    val t = LocalGymTokens.current
    GymCard(Modifier.fillMaxWidth()) {
        // Компактно: [−] название + значение [+] — все три строки и «СТАРТ» помещаются без прокрутки.
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TimerStepButton(Icons.Filled.Remove, stringResource(R.string.timer_decrease, label), onMinus)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val title = if (t.groupUppercase) label.uppercase() else label
                Text(
                    if (note != null) "$title · $note" else title,
                    style = t.groupStyle ?: MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 17.sp),
                )
                Text(
                    value,
                    style = t.timer.digits,
                    color = t.weight.color ?: LocalContentColor.current,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 44.sp),
                )
            }
            TimerStepButton(Icons.Filled.Add, stringResource(R.string.timer_increase, label), onPlus)
        }
    }
}

/**
 * Кнопка − / + 64 dp. Нажатие — один шаг; удержание — повтор с ускорением
 * (через 0,4 с, интервал от 180 до 35 мс).
 */
@Composable
private fun TimerStepButton(icon: ImageVector, description: String, onStep: () -> Unit) {
    val s = LocalGymTokens.current.stepper
    val step by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    val shape = s?.shape ?: CircleShape
    val container = s?.container ?: MaterialTheme.colorScheme.secondaryContainer
    val content = s?.content ?: MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        Modifier
            .size(64.dp)
            .clip(shape)
            .background(container)
            .then(if (s?.border != null) Modifier.border(2.dp, s.border, shape) else Modifier)
            .drawBehind { if (pressed) drawRect(content.copy(alpha = 0.16f)) }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick {
                    step()
                    true
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        pressed = true
                        step()
                        val repeat = launch {
                            delay(400)
                            var interval = 180L
                            while (isActive) {
                                step()
                                delay(interval)
                                interval = (interval * 0.85).toLong().coerceAtLeast(35)
                            }
                        }
                        waitForUpOrCancellation()
                        repeat.cancel()
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(34.dp))
    }
}

// ---------------------------------------------------------------- Ход таймера

/** Стиль экрана для текущего состояния: подготовка — как отдых, «Готово» — как работа. */
@Composable
private fun phaseStyle(s: TimerSnapshot): TimerPhaseStyle {
    val ts = LocalGymTokens.current.timer
    return when {
        s.phase == TimerPhase.ROUND && s.warning -> ts.warning
        s.phase == TimerPhase.ROUND || s.phase == TimerPhase.DONE -> ts.work
        else -> ts.rest
    }
}

@Composable
fun TimerRunning(
    s: TimerSnapshot,
    contentPadding: PaddingValues,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onReset: () -> Unit,
) {
    val t = LocalGymTokens.current
    val style = phaseStyle(s)
    val labelStyle = t.timer.digits.copy(fontSize = 34.sp, letterSpacing = 3.sp)
    TimerStage(style, contentPadding) {
        if (style.ditherBand != null) {
            Box(Modifier.fillMaxWidth().height(16.dp).ditherFill(style.ditherBand, style.digits))
            Spacer(Modifier.height(12.dp))
        }
        Text(
            stringResource(
                when (s.phase) {
                    TimerPhase.PREP -> R.string.timer_phase_prep
                    TimerPhase.ROUND -> R.string.timer_phase_round
                    TimerPhase.REST -> R.string.timer_phase_rest
                    TimerPhase.DONE -> R.string.timer_phase_done
                },
            ),
            style = labelStyle,
            color = style.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 34.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        PhaseProgress(s.progress, style)
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (s.paused) {
                Blinking {
                    Text(stringResource(R.string.timer_paused), style = labelStyle.copy(fontSize = 40.sp), color = style.digits)
                }
            }
            TabularText(
                TimerFormat.countdown(s.remainingSec),
                style = t.timer.digits,
                color = style.digits,
                glow = style.digitGlow,
                outline = style.digitOutline,
                modifier = Modifier.fillMaxWidth(),
            )
            val rounds = s.config.rounds
            val counter = when (s.phase) {
                TimerPhase.ROUND -> stringResource(R.string.timer_round_counter, s.round, rounds)
                TimerPhase.PREP -> stringResource(R.string.timer_next_round, 1, rounds)
                else -> stringResource(R.string.timer_next_round, s.round + 1, rounds)
            }
            Text(
                counter,
                style = t.timer.digits.copy(fontSize = 44.sp),
                color = style.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 44.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!s.paused) {
            // Во время хода нажимается только «ПАУЗА» — без случайных касаний.
            TimerBigButton(stringResource(R.string.timer_pause), style.button, style.onButton, onPause, height = 96.dp)
        } else {
            TimerBigButton(stringResource(R.string.timer_resume), style.button, style.onButton, onResume, height = 96.dp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    stringResource(R.string.timer_restart_phase),
                    hint = null,
                    color = style.text,
                    onClick = onRestart,
                    onLongClick = null,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    stringResource(R.string.timer_reset_all),
                    hint = stringResource(R.string.timer_hold_hint),
                    color = style.text,
                    onClick = null,
                    onLongClick = onReset,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Весь экран таймера: фон состояния (поверх фона темы), рамка/уголки, содержимое с отступами. */
@Composable
private fun TimerStage(style: TimerPhaseStyle, contentPadding: PaddingValues, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (style.background != null) Modifier.background(style.background) else Modifier),
    ) {
        val shape = MaterialTheme.shapes.large
        if (style.frame != null) {
            Pulsing(style.pulse) { a ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(6.dp)
                        .alpha(a)
                        .neonGlow(if (style.frameGlow) Glow(style.frame, 18.dp) else null, shape, 6.dp)
                        .border(6.dp, style.frame, shape),
                )
            }
        }
        if (style.brackets != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(6.dp)
                    .cornerBrackets(style.brackets, length = 56.dp, thickness = 6.dp),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            content = content,
        )
    }
}

@Composable
private fun PhaseProgress(progress: Float, style: TimerPhaseStyle) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .drawBehind {
                drawRect(style.track)
                drawRect(style.digits, size = Size(size.width * progress, size.height))
            },
    )
}

/** Мягкое мигание (надпись «ПАУЗА»). */
@Composable
private fun Blinking(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "blink")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "blinkAlpha",
    )
    Box(Modifier.alpha(a)) { content() }
}

@Composable
private fun Pulsing(enabled: Boolean, content: @Composable (Float) -> Unit) {
    if (!enabled) {
        content(1f)
        return
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    content(a)
}

/**
 * Время крупно, на всю ширину. Каждый знак — в ячейке фиксированной ширины (по самой широкой цифре
 * шрифта), поэтому число не «дрожит» при смене секунд даже в пропорциональных шрифтах тем.
 * Размер шрифта подбирается по ширине/высоте места один раз (по шаблону «00:00»).
 */
@Composable
fun TabularText(text: String, style: TextStyle, color: Color, glow: Color?, modifier: Modifier = Modifier, outline: Color? = null) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val base = style.copy(fontSize = 100.sp, lineHeight = 100.sp)
        val m = remember(base, density) {
            val digit = (0..9).maxOf { measurer.measure(it.toString(), base).size.width }
            val colon = measurer.measure(":", base).size.width
            val height = measurer.measure("0", base).size.height
            Triple(digit.toFloat(), colon.toFloat(), height.toFloat())
        }
        val (digitW, colonW, lineH) = m
        val templateW = 4 * digitW + colonW
        val maxW = constraints.maxWidth.toFloat()
        val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else Float.MAX_VALUE
        val scale = minOf(maxW * 0.98f / templateW, maxH / lineH, 3.2f)
        val fontSize = 100.sp * scale
        val glowShadow = glow?.let { Shadow(it, blurRadius = with(density) { (fontSize.toPx() * 0.12f) }) }
        val sized = base.copy(fontSize = fontSize, lineHeight = fontSize, color = color, shadow = glowShadow)
        Row(verticalAlignment = Alignment.CenterVertically) {
            text.forEach { ch ->
                val w: Dp = with(density) { ((if (ch.isDigit()) digitW else colonW) * scale).toDp() }
                Box(Modifier.width(w), contentAlignment = Alignment.Center) {
                    if (outline != null) {
                        // Контур под заливкой: снаружи цифры видна половина обводки.
                        val strokePx = with(density) { fontSize.toPx() * 0.035f }
                        Text(
                            ch.toString(),
                            style = sized.copy(color = outline, shadow = null, drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round)),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Text(ch.toString(), style = sized, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun TimerBigButton(
    text: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 88.dp,
) {
    val t = LocalGymTokens.current
    val shape = t.buttonShape ?: RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = container,
        contentColor = content,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .neonGlow(t.doneButton?.glow?.let { Glow(container, it.radius) }, shape, 2.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text,
                style = t.timer.digits.copy(letterSpacing = 2.sp),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 32.sp),
            )
        }
    }
}

/** Вторичная кнопка на паузе; [onLongClick] — действие только по удержанию (с подсказкой [hint]). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SecondaryButton(
    text: String,
    hint: String?,
    color: Color,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = LocalGymTokens.current.buttonShape ?: RoundedCornerShape(16.dp)
    Column(
        modifier
            .height(72.dp)
            .clip(shape)
            .border(2.dp, color, shape)
            .combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 17.sp),
        )
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

// ---------------------------------------------------------------- Готово

@Composable
fun TimerDone(s: TimerSnapshot, contentPadding: PaddingValues, onAgain: () -> Unit, onSettings: () -> Unit) {
    val t = LocalGymTokens.current
    val style = t.timer.work
    TimerStage(style, contentPadding) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.timer_phase_done),
                style = t.timer.digits.copy(letterSpacing = 4.sp, shadow = style.digitGlow?.let { Shadow(it, blurRadius = 24f) }),
                color = style.digits,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 72.sp),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.timer_done_rounds, s.config.rounds),
                style = t.timer.digits.copy(fontSize = 30.sp),
                color = style.text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.timer_done_time, TimerFormat.millis(s.config.totalMs)),
                style = t.timer.digits.copy(fontSize = 30.sp),
                color = style.text,
            )
        }
        TimerBigButton(stringResource(R.string.timer_again), style.button, style.onButton, onAgain, height = 96.dp)
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            stringResource(R.string.timer_settings),
            hint = null,
            color = style.text,
            onClick = onSettings,
            onLongClick = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------- Превью (все 7 тем)

private val previewConfig = TimerConfig.DEFAULT

private fun previewSnapshot(phase: TimerPhase, remainingMs: Long, warning: Boolean = false, paused: Boolean = false, round: Int = 3) =
    TimerSnapshot(
        config = previewConfig,
        phase = phase,
        round = round,
        remainingMs = remainingMs,
        phaseMs = when (phase) {
            TimerPhase.PREP -> TimerConfig.PREP_MS
            TimerPhase.ROUND -> previewConfig.roundSec * 1000L
            TimerPhase.REST -> previewConfig.restSec * 1000L
            TimerPhase.DONE -> 0
        },
        paused = paused,
        warning = warning,
    )

private val previewPadding = PaddingValues(top = 24.dp)

@Composable
private fun TimerPreviewFrame(themeId: GymThemeId, content: @Composable () -> Unit) {
    GymTheme(themeId) { GymBackground { Box(Modifier.fillMaxHeight()) { content() } } }
}

@Preview(name = "Таймер — настройки", widthDp = 360, heightDp = 760)
@Composable
private fun TimerSetupPreview(@PreviewParameter(GymThemePreviewProvider::class) themeId: GymThemeId) {
    TimerPreviewFrame(themeId) { TimerSetup(previewConfig, previewPadding, {}, {}, {}, {}) }
}

@Preview(name = "Таймер — раунд", widthDp = 360, heightDp = 740)
@Composable
private fun TimerWorkPreview(@PreviewParameter(GymThemePreviewProvider::class) themeId: GymThemeId) {
    TimerPreviewFrame(themeId) { TimerRunning(previewSnapshot(TimerPhase.ROUND, 161_000), previewPadding, {}, {}, {}, {}) }
}

@Preview(name = "Таймер — 10 секунд", widthDp = 360, heightDp = 740)
@Composable
private fun TimerWarningPreview(@PreviewParameter(GymThemePreviewProvider::class) themeId: GymThemeId) {
    TimerPreviewFrame(themeId) { TimerRunning(previewSnapshot(TimerPhase.ROUND, 8_000, warning = true), previewPadding, {}, {}, {}, {}) }
}

@Preview(name = "Таймер — отдых", widthDp = 360, heightDp = 740)
@Composable
private fun TimerRestPreview(@PreviewParameter(GymThemePreviewProvider::class) themeId: GymThemeId) {
    TimerPreviewFrame(themeId) { TimerRunning(previewSnapshot(TimerPhase.REST, 42_000), previewPadding, {}, {}, {}, {}) }
}

@Preview(name = "Таймер — пауза", widthDp = 360, heightDp = 740)
@Composable
private fun TimerPausedPreview(@PreviewParameter(GymThemePreviewProvider::class) themeId: GymThemeId) {
    TimerPreviewFrame(themeId) { TimerRunning(previewSnapshot(TimerPhase.ROUND, 95_000, paused = true), previewPadding, {}, {}, {}, {}) }
}

@Preview(name = "Таймер — готово", widthDp = 360, heightDp = 740)
@Composable
private fun TimerDonePreview(@PreviewParameter(GymThemePreviewProvider::class) themeId: GymThemeId) {
    TimerPreviewFrame(themeId) { TimerDone(previewSnapshot(TimerPhase.DONE, 0, round = 12), previewPadding, {}, {}) }
}
