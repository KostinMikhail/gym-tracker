#!/usr/bin/env python3
"""
Синтез звуков интервального таймера (без записей — только математика, стандартная библиотека Python).

    python tools/gen_timer_sounds.py

Пишет в app/src/main/res/raw:
  timer_bell.wav  — удар боксёрского гонга: сумма негармонических обертонов с экспоненциальным затуханием, ~1,5 с;
  timer_knock.wav — сухой деревянный стук: шум с резким атакующим фронтом через два резонатора, ~80 мс.

Двойной/тройной гонг и три стука — последовательности этих сэмплов, их проигрывает код приложения.
Результат детерминирован (фиксированное зерно генератора шума).
"""
import math
import os
import random
import struct
import wave

RATE = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "raw")


def write_wav(name, samples, peak=0.89):
    m = max(abs(s) for s in samples) or 1.0
    k = peak / m
    data = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s * k)) * 32767)) for s in samples)
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data)
    print(f"{path}: {len(samples) / RATE:.3f} s, {os.path.getsize(path)} bytes")


def bell():
    """Металлический гонг: обертоны (отношение к основному, громкость, время затухания в с)."""
    f0 = 830.0
    partials = [
        (1.000, 1.00, 1.10),
        (1.506, 0.55, 0.80),
        (2.000, 0.45, 0.70),
        (2.742, 0.60, 0.55),
        (3.330, 0.35, 0.40),
        (4.170, 0.30, 0.30),
        (5.410, 0.22, 0.22),
        (6.800, 0.12, 0.15),
    ]
    rnd = random.Random(7)
    n = int(RATE * 1.5)
    out = []
    for i in range(n):
        t = i / RATE
        s = 0.0
        for ratio, amp, decay in partials:
            f = f0 * ratio
            # Две слегка расстроенные составляющие — «живые» биения металла.
            env = amp * math.exp(-t / decay)
            s += env * (math.sin(2 * math.pi * f * t) + 0.6 * math.sin(2 * math.pi * (f + 1.3) * t + 0.7))
        # Короткий щелчок молоточка в первые 4 мс.
        if t < 0.004:
            s += (rnd.random() * 2 - 1) * 1.2 * (1 - t / 0.004)
        # Мягкая атака 1,5 мс (без щелчка на старте) и затухание в ноль к концу файла.
        attack = min(1.0, t / 0.0015)
        tail = min(1.0, (1.5 - t) / 0.1)
        out.append(s * attack * tail)
    return out


def resonator(x, freq, q):
    """Двухполюсный полосовой фильтр (biquad, RBJ)."""
    w0 = 2 * math.pi * freq / RATE
    alpha = math.sin(w0) / (2 * q)
    b0, b1, b2 = alpha, 0.0, -alpha
    a0, a1, a2 = 1 + alpha, -2 * math.cos(w0), 1 - alpha
    y = []
    x1 = x2 = y1 = y2 = 0.0
    for s in x:
        v = (b0 * s + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1 = x1, s
        y2, y1 = y1, v
        y.append(v)
    return y


def knock():
    """Деревянная колотушка: импульс шума с атакой ~0,5 мс и затуханием ~12 мс через «деревянные» резонансы."""
    rnd = random.Random(3)
    n = int(RATE * 0.08)
    noise = []
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / 0.0005) * math.exp(-t / 0.012)
        noise.append((rnd.random() * 2 - 1) * env)
    body = resonator(noise, 1150, 6)
    click = resonator(noise, 2600, 3)
    low = resonator(noise, 520, 4)
    out = []
    for i in range(n):
        t = i / RATE
        fade = min(1.0, (0.08 - t) / 0.01)
        out.append((1.0 * body[i] + 0.6 * click[i] + 0.5 * low[i]) * fade)
    return out


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    write_wav("timer_bell.wav", bell())
    write_wav("timer_knock.wav", knock())
