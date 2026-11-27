"""Halo. Origin at the ring's centre; the ring lies flat (XY plane in Blender).

A flat band with rolled rims inside and out, engraved on its upper and lower faces, with a recessed channel round
the middle where the light runs. A thinner second band sits just inside it.
"""

import math

from mathutils import Vector

import jewel
import tmdl

R = 0.3
N = 72

def build(out_path):
    metal = jewel.Part("metal")
    circle = [(Vector((math.cos(a), math.sin(a), 0)) * R, Vector((math.cos(a), math.sin(a), 0))) for a in
              [i / N * math.tau for i in range(N)]]
    width = 0.05
    thick = 0.012
    profile = []
    for k in range(6):
        a = -math.pi / 2 + k / 5 * math.pi
        profile.append((width / 2 + math.cos(a) * thick / 2, math.sin(a) * thick / 2, 0.15, 0.0))
    for k in range(1, 6):
        x = width / 2 - k / 6 * width
        channel = math.exp(-((x) / (width * 0.12)) ** 2)
        profile.append((x, thick / 2 - channel * thick * 0.35, 0.5 + 0.3 * channel, channel))
    for k in range(6):
        a = math.pi / 2 + k / 5 * math.pi
        profile.append((-width / 2 + math.cos(a) * thick / 2, math.sin(a) * thick / 2, 0.15, 0.25))
    for k in range(1, 4):
        x = -width / 2 + k / 4 * width
        profile.append((x, -thick / 2, 0.45, 0.0))
    rings = []
    length = math.tau * R
    for i, (p, nrm) in enumerate(circle):
        ring = []
        for j, (x, z, ramp, glow) in enumerate(profile):
            ring.append(metal.vert(p + nrm * x + Vector((0, 0, z)), (i / N * length, j / len(profile) * width * 2.5), ramp, glow))
        rings.append(ring)
    for i in range(N):
        k = (i + 1) % N
        for j in range(len(profile)):
            m = (j + 1) % len(profile)
            metal.face((rings[i][j], rings[k][j], rings[k][m], rings[i][m]))
    inner = [(p * 0.8, nrm) for p, nrm in circle]
    jewel.loop_tube(metal, inner, 0, 0, 0.006, around=6, ramp=0.6, glow=0.5)
    tmdl.export(out_path, [("metal", metal.finish(60), -1)], ao_radius=0.02, ao_strength=0.4, max_tris=1000)
