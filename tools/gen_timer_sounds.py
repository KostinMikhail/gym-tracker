#!/usr/bin/env python3
"""
Синтез стука «10 секунд» интервального таймера (без записей — только математика, стандартная библиотека Python).

    python tools/gen_timer_sounds.py                 # → app/src/main/res/raw/timer_knock.wav
    python tools/gen_timer_sounds.py --demo DIR      # демо трёх стуков подряд → DIR

timer_knock.wav — деревянная колотушка, ~0,15 с. Три стука — последовательность, её проигрывает код приложения.
Гонг (timer_bell.wav) — запись, его готовит tools/prepare_bell.py.
Результат детерминирован (фиксированное зерно генератора шума).
"""
import math
import os
import random
import struct
import sys
import wave

RATE = 44100
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RAW = os.path.join(ROOT, "app", "src", "main", "res", "raw")


def write_wav(path, samples, peak=0.89):
    m = max(abs(s) for s in samples) or 1.0
    k = peak / m
    data = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s * k)) * 32767)) for s in samples)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data)
    print(f"{path}: {len(samples) / RATE:.3f} s, {os.path.getsize(path)} bytes")


def modal(partials, seconds, f0, beat=0.0):
    """Сумма затухающих мод: (отношение к f0, громкость, время затухания с). [beat] — биения пар (Гц)."""
    n = int(RATE * seconds)
    out = [0.0] * n
    for ratio, amp, decay in partials:
        f = f0 * ratio
        if f >= RATE / 2 - 500:
            continue
        w1 = 2 * math.pi * f / RATE
        w2 = 2 * math.pi * (f + beat * ratio ** 0.5) / RATE
        k = math.exp(-1.0 / (decay * RATE))
        env = amp
        for i in range(n):
            out[i] += env * (math.sin(w1 * i) + (0.7 * math.sin(w2 * i + 1.1) if beat else 0.0))
            env *= k
    return out


def strike_click(n, seconds, level, seed):
    """Щелчок удара молотка: короткий шум в начале."""
    rnd = random.Random(seed)
    m = int(RATE * seconds)
    return [((rnd.random() * 2 - 1) * level * (1 - i / m) if i < m else 0.0) for i in range(n)]


def shape(x, seconds, attack=0.0015, fade=0.12):
    n = len(x)
    out = []
    for i in range(n):
        t = i / RATE
        out.append(x[i] * min(1.0, t / attack) * min(1.0, (seconds - t) / fade))
    return out


def knock():
    """
    Деревянная колотушка (как «10 секунд» у ринга): твёрдое дерево по дереву.
    Тональное «ток» — основная мода бруска ~1,7 кГц и её обертон ×2,76, глухой корпус ~600 Гц,
    плюс очень короткий щелчок соприкосновения. Затухание десятки миллисекунд — сухо, но звучит как дерево.
    """
    sec = 0.15
    parts = [(1.0, 1.0, 0.045), (2.76, 0.35, 0.015), (0.36, 0.45, 0.022), (5.4, 0.12, 0.006)]
    x = modal(parts, sec, 1720.0)
    click = strike_click(len(x), 0.0015, 1.2, 3)
    return shape([a + b for a, b in zip(x, click)], sec, attack=0.0004, fade=0.02)


def sequence(sample, times_ms, seconds):
    n = int(RATE * seconds)
    out = [0.0] * n
    for t in times_ms:
        start = int(RATE * t / 1000)
        for i, v in enumerate(sample):
            if start + i < n:
                out[start + i] += v
    return out


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--demo":
        os.makedirs(sys.argv[2], exist_ok=True)
        write_wav(os.path.join(sys.argv[2], "knock_x3.wav"), sequence(knock(), [0, 350, 700], 1.0))
    else:
        os.makedirs(RAW, exist_ok=True)
        write_wav(os.path.join(RAW, "timer_knock.wav"), knock())
