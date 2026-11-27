"""Gyroscope ring, radius 1 (the client scales it). Origin at the centre; the ring lies flat.

A band with a slightly rounded outer face and chamfered edges, engraved, with a row of runes round its outer face.
The runes are the glow mask, so they light up while the metal around them stays dark.
"""

import math

from mathutils import Vector

import jewel
import tmdl

N = 96

def rune_mask(u, v):
    """Runes as strokes on a grid: u round the ring (0..1), v across the outer face (0..1)."""
    cells = 24
    cu = u * cells
    i = int(cu)
    fu = cu - i
    if fu < 0.12 or fu > 0.88 or v < 0.2 or v > 0.8:
        return 0.0
    x = (fu - 0.12) / 0.76
    y = (v - 0.2) / 0.6
    h = math.sin((i + 1) * 12.9898) * 43758.5453
    h = h - math.floor(h)
    strokes = [(0, 0, 1, 1), (0, 1, 1, 0), (0.5, 0, 0.5, 1), (0, 0.5, 1, 0.5), (0, 0, 0, 1), (1, 0, 1, 1)]
    best = 0.0
    for k in range(3):
        s = strokes[int(h * 997 * (k + 1)) % len(strokes)]
        ax, ay, bx, by = s
        dx, dy = bx - ax, by - ay
        t = max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)))
        d = math.hypot(x - (ax + dx * t), y - (ay + dy * t))
        best = max(best, max(0.0, 1 - d / 0.16))
    return best

def build(out_path):
    n = N if tmdl.LOD_SCALE >= 1 else 40
    metal = jewel.Part("metal")
    w = 0.075
    th = 0.04
    profile = [(0.0, -w / 2, 0.3), (th * 0.15, -w / 2, 0.4), (th, -w * 0.38, 0.55)]
    for k in range(6):
        v = k / 5
        profile.append((th + 0.004 * math.sin(math.pi * v), -w * 0.38 + v * w * 0.76, 0.7, ('rune', v)))
    profile += [(th, w * 0.38, 0.55), (th * 0.15, w / 2, 0.4), (0.0, w / 2, 0.3), (-th * 0.2, w * 0.3, 0.25), (-th * 0.2, -w * 0.3, 0.25)]
    rings = []
    for i in range(n):
        a = i / n * math.tau
        out = Vector((math.cos(a), math.sin(a), 0))
        ring = []
        for j, entry in enumerate(profile):
            r, z, ramp = entry[0], entry[1], entry[2]
            glow = rune_mask(i / n, entry[3][1]) if len(entry) > 3 else 0.0
            ring.append(metal.vert(out * (1 + r) + Vector((0, 0, z)), (i / n * math.tau, (z + w) * 2), ramp, glow))
        rings.append(ring)
    for i in range(n):
        k = (i + 1) % n
        for j in range(len(profile)):
            m = (j + 1) % len(profile)
            metal.face((rings[i][j], rings[k][j], rings[k][m], rings[i][m]))
    tmdl.export(out_path, [("metal", metal.finish(40), -1)], ao_radius=0.02, ao_strength=0.4, reduce=False)
