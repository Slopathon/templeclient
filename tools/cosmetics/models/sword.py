"""Longsword. Origin at the centre of the crossguard; the blade points up (+Z), its flat facing front (-Y).

A diamond-section blade with a fuller groove down each face and a ricasso at the base, tapering to a point; a
crossguard whose quillons curve gently toward the blade and end in discs; a grip wrapped in leather with three risers;
a wheel pommel with a gem set in each face. The glow mask follows the cutting edges and the fuller.
"""

import math

from mathutils import Vector

import jewel
import tmdl

BLADE = 0.62

def blade(part):
    rows = 44
    cols = []
    for k in range(13):
        x = -1 + k / 6
        cols.append(x)
    rings = []
    for i in range(rows + 1):
        f = i / rows
        z = f * BLADE
        if f < 0.08:
            half = 0.036
        elif f < 0.82:
            half = 0.036 * (1 - 0.18 * (f - 0.08) / 0.74)
        else:
            half = 0.0296 * max(0.0, 1 - (f - 0.82) / 0.18) ** 0.8 + 0.0004
        thick = 0.009 * (1 - 0.35 * f) if f < 0.82 else 0.0068 * max(0.08, 1 - (f - 0.82) / 0.18)
        ring = []
        for side in (1, -1):
            for k in range(13):
                x = -1 + k / 6 if side == 1 else 1 - k / 6
                if side == -1 and k in (0, 12):
                    continue
                y = thick * (1 - abs(x))
                fuller = 0.0 if f < 0.06 or f > 0.7 else math.exp(-(x / 0.18) ** 2) * thick * 0.45 * math.sin(math.pi * (f - 0.06) / 0.64)
                y -= fuller
                edge = max(0.0, abs(x) - 0.82) / 0.18
                glow = max(edge, 0.7 * (fuller / (thick * 0.45) if thick > 0 else 0))
                ring.append(part.vert(Vector((x * half, -side * y, z)), (z, x * half), 0.35 + 0.5 * edge, glow))
        rings.append(ring)
    m = len(rings[0])
    for i in range(rows):
        for k in range(m):
            q = (k + 1) % m
            part.face((rings[i][k], rings[i][q], rings[i + 1][q], rings[i + 1][k]))
    base = part.vert(Vector((0, 0, -0.002)), (0, 0), 0.3, 0.0)
    for k in range(m):
        part.face((rings[0][(k + 1) % m], rings[0][k], base))

def build(out_path):
    steel = jewel.Part("blade")
    fittings = jewel.Part("fittings")
    grip = jewel.Part("grip")
    gems = jewel.Part("gem")
    blade(steel)

    path = []
    for i in range(25):
        u = -1 + i / 12
        path.append(Vector((u * 0.11, 0, 0.012 * u * u)))
    jewel.path_tube(fittings, path, lambda t: 0.011 * (1 - 0.25 * math.sin(math.pi * t)), around=8, ramp=0.6, glow=0.2, caps=False)
    for sx in (-1, 1):
        jewel.sphere(fittings, Vector((sx * 0.114, 0, 0.013)), 0.017, rings=6, segments=10, ramp=0.7, glow=0.2, squash=0.7)
    jewel.sphere(fittings, Vector((0, 0, 0)), 0.02, rings=6, segments=10, ramp=0.65, glow=0.1, squash=0.8)

    gpath = [Vector((0, 0, -0.012 - k * 0.0125)) for k in range(13)]
    jewel.path_tube(grip, gpath, lambda t: 0.0125 * (1 + 0.1 * math.sin(math.pi * t)) * (1 + 0.12 * max(0.0, math.cos(t * math.pi * 6)) ** 8),
                    around=10, ramp=0.5, caps=False)

    centre = Vector((0, 0, -0.18))
    rim = [(centre + Vector((math.cos(a), 0, math.sin(a))) * 0.022, Vector((math.cos(a), 0, math.sin(a)))) for a in
           [k / 18 * math.tau for k in range(18)]]
    jewel.loop_tube(fittings, rim, 0, 0, 0.011, around=8, ramp=0.7, glow=0.2)
    for side in (-1, 1):
        jewel.gem(gems, centre + Vector((0, side * 0.008, 0)), Vector((0, side, 0)), 0.014, 0.1)
    jewel.sphere(fittings, centre + Vector((0, 0, -0.03)), 0.008, rings=5, segments=8, ramp=0.7)

    parts = [("blade", steel.finish(25), -1), ("fittings", fittings.finish(50), -1), ("grip", grip.finish(60), -1),
             ("gem", gems.finish(5), -1)]
    tmdl.export(out_path, parts, ao_radius=0.02, ao_strength=0.45, max_tris=1000)
