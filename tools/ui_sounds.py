"""Generates the menu's sounds as OGG files: soft, muted key presses rather than synth tones.

    python tools/ui_sounds.py            # writes src/client/resources/assets/templenz/sounds/ui/*.ogg and sounds.json
    python tools/ui_sounds.py --preview  # also writes build/ui-sounds-preview.wav, every sound in a row

Each sound is modelled like a real switch bottoming out: a short, soft contact pulse (a felt-like 2-3 ms press, which is
what takes the sharp edge off) strikes a handful of body resonances that die away within a few hundredths of a second,
with a little contact noise. There are no sustained tones, so nothing rings or beeps. The press is followed by a quieter,
brighter release a moment later, like a key coming back up. Every sound has three slight variations that Minecraft
picks between at random, so a run of clicks never sounds machine-identical.
"""
import json
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import butter, fftconvolve, sosfilt

SR = 44100
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/client/resources/assets/templenz/sounds/ui"
SOUNDS_JSON = ROOT / "src/client/resources/assets/templenz/sounds.json"
VARIANTS = 3

THOCK = [(150, 12, 0.3), (330, 8.5, 1.0), (610, 5.5, 0.85), (1060, 3.8, 1.4), (1800, 2.6, 0.9), (2900, 1.7, 0.45), (4300, 1.1, 0.18)]

def modes(base, scale=1.0, damp=1.0):
    """The same body, pitched by {@code scale} and with every decay multiplied by {@code damp}."""
    return [(f * scale, tau * damp, g) for f, tau, g in base]

def strike(rng, length, at, strength, body, contact_ms, noise=0.2, noise_cut=3800, crisp=0.6):
    """One contact at {@code at} seconds: a half-sine press of {@code contact_ms} ringing the body's modes."""
    out = np.zeros(length)
    contact = max(8, int(contact_ms / 1000 * SR * rng.uniform(0.88, 1.12)))
    press = np.sin(np.linspace(0, np.pi, contact)) * strength
    t = np.arange(int(0.35 * SR)) / SR
    ir = np.zeros_like(t)
    for f, tau, g in body:
        f *= rng.uniform(0.965, 1.035)
        tau *= rng.uniform(0.9, 1.1)
        g *= rng.uniform(0.9, 1.1)
        ir += g * np.exp(-t / (tau / 1000)) * np.sin(2 * np.pi * f * t)
    hit = fftconvolve(press, ir)[: len(t)] / 12.0
    edge = np.sin(np.linspace(0, np.pi, max(4, int(0.00035 * SR)))) * strength * crisp
    hit += fftconvolve(edge, ir)[: len(t)] / 12.0 * (contact / len(edge)) ** 0.5
    grit = rng.standard_normal(int(contact * 1.6)) * np.sin(np.linspace(0, np.pi, int(contact * 1.6))) ** 2
    grit = sosfilt(butter(2, noise_cut, "low", fs=SR, output="sos"), grit) * noise * strength
    start = int(at * SR)
    end = min(length, start + len(hit))
    out[start:end] += hit[: end - start]
    gend = min(length, start + len(grit))
    out[start:gend] += grit[: gend - start]
    return out

def air(rng, length, at, dur, lo, hi, gain):
    """A soft breath of air sweeping from {@code lo} to {@code hi} Hz: noise through a moving low-pass."""
    out = np.zeros(length)
    n = int(dur * SR)
    noise = rng.standard_normal(n)
    cut = np.geomspace(lo, hi, n)
    y = np.zeros(n)
    acc = 0.0
    acc2 = 0.0
    for i in range(n):
        k = 1 - np.exp(-2 * np.pi * cut[i] / SR)
        acc += (noise[i] - acc) * k
        acc2 += (acc - acc2) * k
        y[i] = acc2
    env = np.sin(np.linspace(0, np.pi, n)) ** 1.5
    start = int(at * SR)
    end = min(length, start + n)
    out[start:end] += (y * env * gain)[: end - start]
    return out

def finish(x):
    """Rounds the whole sound off: no rumble, no fizz, gentle saturation, a common peak and a silent tail."""
    x = sosfilt(butter(2, 70, "high", fs=SR, output="sos"), x)
    x = sosfilt(butter(4, 7000, "low", fs=SR, output="sos"), x)
    x = x / (np.max(np.abs(x)) + 1e-9)
    x = np.tanh(x * 1.3) / np.tanh(1.3)
    env = np.abs(x)
    above = np.nonzero(env > 10 ** (-58 / 20))[0]
    x = x[: max(above[-1] + int(0.004 * SR), int(0.02 * SR))]
    fade = min(len(x), int(0.004 * SR))
    x[-fade:] *= np.linspace(1, 0, fade)
    return (x * 0.89).astype(np.float32)

def sound(name, rng):
    n = int(0.45 * SR)
    if name == "tap":
        x = strike(rng, n, 0, 1.0, THOCK, 1.3)
        x += strike(rng, n, rng.uniform(0.05, 0.06), 0.22, modes(THOCK, 1.3, 0.7), 1.3)
    elif name == "soft":
        x = strike(rng, n, 0, 1.0, modes(THOCK, 0.82), 1.7, 0.18, 3000)
        x += strike(rng, n, rng.uniform(0.065, 0.075), 0.16, modes(THOCK, 1.1, 0.7), 1.6)
    elif name == "hover":
        x = strike(rng, n, 0, 0.5, [(480, 3.5, 1.0), (900, 2.5, 0.6), (1600, 1.6, 0.6), (2600, 1.0, 0.25)], 1.1, 0.12, 2800)
    elif name == "tick":
        x = strike(rng, n, 0, 0.6, [(820, 2.6, 1.0), (1500, 1.9, 0.5), (2400, 1.3, 0.22)], 1.0, 0.15)
    elif name == "on":
        x = strike(rng, n, 0, 0.9, THOCK, 1.3)
        x += strike(rng, n, 0.05, 0.42, modes(THOCK, 1.32, 0.75), 1.5)
    elif name == "off":
        x = strike(rng, n, 0, 0.9, modes(THOCK, 0.94), 1.4)
        x += strike(rng, n, 0.05, 0.42, modes(THOCK, 0.72, 0.9), 2.0)
    elif name == "open":
        x = strike(rng, n, 0, 0.75, modes(THOCK, 0.8), 1.8, 0.18, 3000)
        x += air(rng, n, 0.006, 0.12, 400, 1500, 0.07)
    elif name == "close":
        x = strike(rng, n, 0, 0.7, modes(THOCK, 0.74), 1.8, 0.18, 3000)
        x += air(rng, n, 0.006, 0.12, 1400, 350, 0.07)
    elif name == "pluck":
        f0 = 330
        x = strike(rng, n, 0, 1.0, [(f0, 26, 1.0), (f0 * 2.32, 11, 0.5), (f0 * 3.91, 6, 0.28), (f0 * 6.1, 3, 0.3)], 1.6, 0.25)
    else:
        raise ValueError(name)
    return finish(x)

NAMES = ["tap", "soft", "hover", "tick", "on", "off", "open", "close", "pluck"]

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(4471)
    events = json.loads(SOUNDS_JSON.read_text(encoding="utf-8")) if SOUNDS_JSON.exists() else {}
    events = {k: v for k, v in events.items() if not k.startswith("ui.")}
    preview = []
    for name in NAMES:
        files = []
        for v in range(1, VARIANTS + 1):
            x = sound(name, rng)
            path = OUT / f"{name}{v}.ogg"
            sf.write(path, x, SR, format="OGG", subtype="VORBIS")
            files.append({"name": f"templenz:ui/{name}{v}", "preload": True})
            preview += [x, np.zeros(int(0.18 * SR), np.float32)]
        events[f"ui.{name}"] = {"sounds": files}
        preview.append(np.zeros(int(0.35 * SR), np.float32))
    SOUNDS_JSON.write_text(json.dumps(events, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {len(NAMES) * VARIANTS} sounds to {OUT}")
    if "--preview" in sys.argv:
        (ROOT / "build").mkdir(exist_ok=True)
        sf.write(ROOT / "build/ui-sounds-preview.wav", np.concatenate(preview), SR)
        print("preview: build/ui-sounds-preview.wav")

if __name__ == "__main__":
    main()
