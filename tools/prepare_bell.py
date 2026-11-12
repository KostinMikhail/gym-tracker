#!/usr/bin/env python3
"""
Подготовка гонга таймера из записи (стандартная библиотека Python).

Источник: «Boxing Bell» — Universfield, Pixabay (universfield-boxing-bell-122093.mp3), Pixabay Content License.
MP3 сначала декодируется в WAV (любым декодером; на Windows — встроенным MediaTranscoder), затем:

    python tools/prepare_bell.py bell_full.wav

Результат — app/src/main/res/raw/timer_bell.wav: моно, 16 бит, от первого удара (тишина в начале срезана,
чтобы сигнал звучал сразу), длина 5 с, пик −1 дБ. Звон гонга к 5 с ещё громкий (≈ −30 дБ), поэтому короткое
затухание звучит как обрыв: с 2,5 с звон дополнительно плавно гасится по кривой в децибелах (−40 дБ к концу,
квадратично — без заметного «излома»), последние 20 мс — до нуля. В начале — 3 мс плавного входа (без щелчка).
"""
import array
import os
import sys
import wave

LENGTH_S = 5.0
FADE_FROM_S = 2.5  # начало дополнительного затухания
FADE_DB = 40.0  # насколько тише к концу
TAIL_S = 0.02  # последние миллисекунды — до нуля
FADE_IN_S = 0.003
ONSET_LEVEL = 0.02  # порог начала удара (доля полной шкалы)
PEAK = 0.89

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "raw", "timer_bell.wav")


def main(src):
    with wave.open(src) as w:
        ch, rate, width, n = w.getnchannels(), w.getframerate(), w.getsampwidth(), w.getnframes()
        assert width == 2, "ожидается 16-битный PCM"
        a = array.array("h", w.readframes(n))
    mono = [sum(a[i:i + ch]) / ch for i in range(0, len(a), ch)]
    limit = ONSET_LEVEL * 32767
    start = next(i for i, v in enumerate(mono) if abs(v) > limit)
    start = max(0, start - int(rate * 0.002))  # 2 мс запаса до удара
    length = int(rate * LENGTH_S)
    seg = mono[start:start + length]
    seg += [0.0] * (length - len(seg))
    fade_from = int(rate * FADE_FROM_S)
    tail = int(rate * TAIL_S)
    fade_in = int(rate * FADE_IN_S)
    for i in range(length):
        g = 1.0
        if i < fade_in:
            g *= i / fade_in
        if i >= fade_from:
            x = (i - fade_from) / (length - fade_from)
            g *= 10 ** (-FADE_DB * x * x / 20)
        if i >= length - tail:
            g *= (length - 1 - i) / tail
        seg[i] *= g
    k = PEAK * 32767 / max(abs(v) for v in seg)
    out = array.array("h", (int(max(-32767, min(32767, v * k))) for v in seg))
    with wave.open(OUT, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(out.tobytes())
    print(f"{OUT}: {length / rate:.2f} s, {rate} Hz mono, onset at {start / rate:.3f} s, {os.path.getsize(OUT)} bytes")


if __name__ == "__main__":
    main(sys.argv[1])
