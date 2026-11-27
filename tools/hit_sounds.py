"""Generates the Hit Sounds presets as OGG files, played through Minecraft's own sound engine.

    python tools/hit_sounds.py            # writes src/client/resources/assets/templenz/sounds/hit/*.ogg and their sounds.json entries
    python tools/hit_sounds.py --preview  # also writes build/hit-sounds-preview.wav, every sound in a row

The sounds are built like tools/ui_sounds.py: short contacts striking a few damped resonances, plus shaped noise, with
nothing left to ring. Every sound is dry (no tails past about a tenth of a second, no room) and has three slight
variations that Minecraft picks between at random, so a fast combo never sounds machine-identical. Only the entries
starting with "hit." in sounds.json are touched.
"""
import json
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import butter, fftconvolve, sosfilt

SR = 44100
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/client/resources/assets/templenz/sounds/hit"
SOUNDS_JSON = ROOT / "src/client/resources/assets/templenz/sounds.json"
VARIANTS = 3

NAMES = ["pop", "punch", "crit", "tick", "thud", "snap", "crunch", "coin", "ding", "bubble"]

def length(seconds):
    return int(seconds * SR)

def place(out, x, at):
    start = int(at * SR)
    end = min(len(out), start + len(x))
    out[start:end] += x[: end - start]

def body(rng, modes, contact_ms, strength=1.0):
    """A soft contact of {@code contact_ms} striking damped modes [(Hz, decay ms, gain)]: the sound of something solid hit."""
    contact = max(6, int(contact_ms / 1000 * SR * rng.uniform(0.9, 1.1)))
    press = np.sin(np.linspace(0, np.pi, contact)) * strength
    t = np.arange(length(0.25)) / SR
    ir = np.zeros_like(t)
    for f, tau, g in modes:
        f *= rng.uniform(0.97, 1.03)
        tau *= rng.uniform(0.9, 1.1)
        ir += g * rng.uniform(0.9, 1.1) * np.exp(-t / (tau / 1000)) * np.sin(2 * np.pi * f * t)
    return fftconvolve(press, ir)[: len(t)] / 10.0

def sweep(rng, f0, f1, glide, decay_ms, attack_ms=0.6, dur=0.2):
    """A sine gliding exponentially from f0 to f1 over {@code glide} s, dying away with time constant {@code decay_ms}."""
    t = np.arange(length(dur)) / SR
    f0 *= rng.uniform(0.97, 1.03)
    f1 *= rng.uniform(0.97, 1.03)
    f = np.where(t < glide, f0 * (f1 / f0) ** (np.minimum(t, glide) / glide), f1)
    phase = 2 * np.pi * np.cumsum(f) / SR
    env = np.minimum(1, t / (attack_ms / 1000)) * np.exp(-t / (decay_ms / 1000))
    return np.sin(phase) * env

def burst(rng, dur_ms, lo, hi, decay_ms, attack_ms=0.3):
    """A burst of band-passed noise: the texture of an impact."""
    n = length(dur_ms / 1000)
    x = rng.standard_normal(n)
    x = sosfilt(butter(2, [lo, min(hi, SR / 2 - 100)], "band", fs=SR, output="sos"), x)
    t = np.arange(n) / SR
    return x * np.minimum(1, t / (attack_ms / 1000)) * np.exp(-t / (decay_ms / 1000))

def grains(rng, dur_ms, count, lo, hi):
    """A run of tiny clicks with a falling density: something crumbling."""
    n = length(dur_ms / 1000)
    x = np.zeros(n)
    times = np.sort(rng.uniform(0, 1, count) ** 1.8) * (n - 200)
    for i, at in enumerate(times.astype(int)):
        g = rng.uniform(0.4, 1.0) * (1 - i / count) ** 0.6
        k = rng.integers(20, 70)
        x[at:at + k] += rng.standard_normal(k) * np.hanning(k) * g
    return sosfilt(butter(2, [lo, hi], "band", fs=SR, output="sos"), x)

def finish(x, low=70, high=12000, drive=1.4):
    """Removes rumble and fizz, saturates gently, sets a common peak and trims to a dry, silent end."""
    x = sosfilt(butter(2, low, "high", fs=SR, output="sos"), x)
    x = sosfilt(butter(4, high, "low", fs=SR, output="sos"), x)
    x = x / (np.max(np.abs(x)) + 1e-9)
    x = np.tanh(x * drive) / np.tanh(drive)
    above = np.nonzero(np.abs(x) > 10 ** (-54 / 20))[0]
    x = x[: max(above[-1] + length(0.003), length(0.02))]
    fade = min(len(x), length(0.003))
    x[-fade:] *= np.linspace(1, 0, fade)
    return (x * 0.89).astype(np.float32)

def sound(name, rng):
    n = length(0.3)
    x = np.zeros(n)
    if name == "pop":
        place(x, sweep(rng, 950, 260, 0.022, 16, attack_ms=1.2), 0)
        place(x, body(rng, [(1800, 2.0, 0.5), (3100, 1.2, 0.25)], 0.8, 0.5), 0)
        return finish(x, high=9000)
    if name == "punch":
        place(x, sweep(rng, 150, 58, 0.05, 45, attack_ms=1.0) * 1.0, 0)
        place(x, burst(rng, 40, 400, 3200, 7) * 0.7, 0)
        place(x, body(rng, [(240, 9, 0.8), (520, 6, 0.6), (980, 3, 0.35)], 1.6, 0.9), 0)
        return finish(x, high=7000, drive=1.8)
    if name == "crit":
        place(x, burst(rng, 25, 2500, 11000, 3.5, 0.1) * 1.0, 0)
        place(x, body(rng, [(1300, 3.5, 0.9), (2300, 2.2, 0.7), (3900, 1.4, 0.4)], 0.5, 0.9), 0)
        place(x, sweep(rng, 210, 70, 0.04, 35, attack_ms=0.8) * 0.75, 0.002)
        return finish(x, drive=2.0)
    if name == "tick":
        place(x, body(rng, [(2400, 1.6, 1.0), (3900, 1.0, 0.6), (6100, 0.6, 0.3)], 0.35), 0)
        place(x, burst(rng, 8, 3000, 12000, 1.2, 0.05) * 0.5, 0)
        return finish(x, low=300)
    if name == "thud":
        place(x, body(rng, [(190, 18, 1.0), (430, 10, 0.7), (870, 5, 0.45), (1500, 2.5, 0.2)], 1.8), 0)
        place(x, burst(rng, 20, 200, 2200, 5) * 0.3, 0)
        return finish(x, high=6000)
    if name == "snap":
        place(x, burst(rng, 30, 1500, 9000, 4, 0.1), 0)
        place(x, body(rng, [(1900, 2.8, 0.7), (2700, 2.0, 0.5)], 0.4, 0.8), 0)
        return finish(x, low=400)
    if name == "crunch":
        place(x, burst(rng, 20, 900, 7000, 3, 0.1) * 0.9, 0)
        place(x, grains(rng, 90, 26, 1200, 8000) * 0.6, 0.004)
        place(x, body(rng, [(420, 6, 0.6), (900, 3.5, 0.4)], 1.0, 0.6), 0)
        return finish(x, low=150, drive=1.6)
    if name == "coin":
        f = 1320
        place(x, body(rng, [(f, 22, 1.0), (f * 2.0, 10, 0.35), (f * 3.0, 5, 0.12)], 0.5), 0)
        place(x, body(rng, [(f * 4 / 3, 40, 1.0), (f * 8 / 3, 16, 0.3), (f * 4, 7, 0.1)], 0.5), 0.048)
        return finish(x, high=10000)
    if name == "ding":
        f = 1250
        place(x, body(rng, [(f, 55, 1.0), (f * 2.76, 22, 0.4), (f * 5.4, 9, 0.15)], 0.9), 0)
        return finish(x, high=9000)
    if name == "bubble":
        place(x, sweep(rng, 420, 1450, 0.03, 18, attack_ms=1.5), 0)
        return finish(x, high=8000)
    raise ValueError(name)

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for stale in OUT.glob("*.ogg"):
        stale.unlink()
    rng = np.random.default_rng(9182)
    events = json.loads(SOUNDS_JSON.read_text(encoding="utf-8")) if SOUNDS_JSON.exists() else {}
    events = {k: v for k, v in events.items() if not k.startswith("hit.")}
    preview = []
    for name in NAMES:
        files = []
        for v in range(1, VARIANTS + 1):
            x = sound(name, rng)
            sf.write(OUT / f"{name}{v}.ogg", x, SR, format="OGG", subtype="VORBIS")
            files.append({"name": f"templenz:hit/{name}{v}", "preload": True})
            preview += [x, np.zeros(length(0.22), np.float32)]
        events[f"hit.{name}"] = {"sounds": files}
        preview.append(np.zeros(length(0.4), np.float32))
    SOUNDS_JSON.write_text(json.dumps(events, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {len(NAMES) * VARIANTS} sounds to {OUT}")
    if "--preview" in sys.argv:
        (ROOT / "build").mkdir(exist_ok=True)
        sf.write(ROOT / "build/hit-sounds-preview.wav", np.concatenate(preview), SR)
        print("preview: build/hit-sounds-preview.wav")

if __name__ == "__main__":
    main()
