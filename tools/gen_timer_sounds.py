#!/usr/bin/env python3
"""
Синтез звуков интервального таймера (без записей — только математика, стандартная библиотека Python).

    python tools/gen_timer_sounds.py                 # звуки приложения → app/src/main/res/raw
    python tools/gen_timer_sounds.py --options DIR   # все варианты гонга и демо стука → DIR (для выбора на слух)

Звуки приложения:
  timer_bell.wav  — удар гонга (вариант BELL ниже), ~2 с;
  timer_knock.wav — деревянная колотушка, ~0,15 с.
Двойной/тройной гонг и три стука — последовательности этих сэмплов, их проигрывает код приложения.
Результат детерминирован (фиксированные зёрна генератора шума).
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

# Гонг приложения: "ring", "heavy" или "electric".
BELL = "ring"


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


# Полусферический колокол (как боксёрский гонг ринга): моды тонкой полусферы ≈ 1 : 2,32 : 4,25 : 6,63 : 9,38.
HEMI = [1.0, 2.32, 4.25, 6.63, 9.38]


def bell_ring():
    """1. «Ринг»: латунный гонг на стойке у ринга — яркий звонкий удар с «живыми» биениями и долгим звоном."""
    sec = 2.0
    parts = [(HEMI[0], 1.0, 1.9), (HEMI[1], 0.75, 1.1), (HEMI[2], 0.5, 0.55), (HEMI[3], 0.3, 0.3), (HEMI[4], 0.18, 0.18)]
    x = modal(parts, sec, 1180.0, beat=3.0)
    click = strike_click(len(x), 0.003, 2.5, 7)
    return shape([a + b for a, b in zip(x, click)], sec)


def bell_heavy():
    """2. «Тяжёлый»: большой низкий гонг — глубокое «донг» с длинным гулом."""
    sec = 2.0
    parts = [(1.0, 1.0, 2.2), (2.0, 0.55, 1.4), (2.74, 0.6, 0.9), (3.9, 0.35, 0.55), (5.4, 0.25, 0.35), (6.8, 0.15, 0.22)]
    x = modal(parts, sec, 560.0, beat=1.6)
    click = strike_click(len(x), 0.005, 1.8, 11)
    return shape([a + b for a, b in zip(x, click)], sec)


def bell_electric():
    """3. «Электрический звонок»: старый звонок зала — молоточек быстро бьёт по чашке (трель ~0,6 с), затем звон."""
    sec = 2.0
    one = modal([(HEMI[0], 1.0, 1.2), (HEMI[1], 0.6, 0.6), (HEMI[2], 0.35, 0.3), (HEMI[3], 0.2, 0.15)], sec, 1450.0, beat=4.0)
    click = strike_click(len(one), 0.002, 1.5, 5)
    one = [a + b for a, b in zip(one, click)]
    n = len(one)
    out = [0.0] * n
    rate_hz = 24.0
    strikes = int(0.6 * rate_hz)
    rnd = random.Random(9)
    for k in range(strikes):
        start = int(k * RATE / rate_hz + rnd.uniform(-40, 40))
        amp = 0.55 + 0.45 * rnd.random()
        for i in range(n - max(start, 0)):
            out[start + i] += amp * one[i] * 0.35
    return shape(out, sec, attack=0.001)


BELLS = {"ring": bell_ring, "heavy": bell_heavy, "electric": bell_electric}


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
    if len(sys.argv) == 3 and sys.argv[1] == "--options":
        d = sys.argv[2]
        os.makedirs(d, exist_ok=True)
        for i, (name, fn) in enumerate(BELLS.items(), 1):
            b = fn()
            write_wav(os.path.join(d, f"gong_{i}_{name}.wav"), b)
            # Как звучит конец раунда (два удара).
            write_wav(os.path.join(d, f"gong_{i}_{name}_x2.wav"), sequence(b, [0, 450], 2.5))
        write_wav(os.path.join(d, "knock_x3.wav"), sequence(knock(), [0, 350, 700], 1.0))
    else:
        os.makedirs(RAW, exist_ok=True)
        write_wav(os.path.join(RAW, "timer_bell.wav"), BELLS[BELL]())
        write_wav(os.path.join(RAW, "timer_knock.wav"), knock())
