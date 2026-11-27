"""China hat (a conical straw hat). Origin where the hat's inside meets the top of the head; the cone opens downward.

A shallow cone of woven straw with a slight upward flare at the edge, the rim bound with a wrapped cord, a small knot
at the apex and a headband ring inside where it sits on the head. The weave carries the glow mask lightly.
"""

import math

from mathutils import Vector

import jewel
import tmdl

R = 0.66
H = 0.32
N = 64

def build(out_path):
    straw = jewel.Part("straw")
    binding = jewel.Part("binding")
    rows = 12
    grid = []
    for j in range(rows + 1):
        f = j / rows
        r = R * f
        z = H * (1 - f) + 0.025 * max(0.0, f - 0.8) / 0.2 * f
        row = []
        for i in range(N):
            a = i / N * math.tau
            row.append(straw.vert(Vector((math.cos(a) * r, math.sin(a) * r, z - H)), (a * r * 2 + 0.001, f * math.hypot(R, H) * 2),
                                  0.35 + 0.4 * f, 0.25))
        grid.append(row)
    for j in range(rows):
        for i in range(N):
            k = (i + 1) % N
            straw.face((grid[j][i], grid[j][k], grid[j + 1][k], grid[j + 1][i]))
    under = []
    urows = 6
    for j in range(urows + 1):
        f = j / urows
        r = R * f
        z = H * (1 - f) + 0.025 * max(0.0, f - 0.8) / 0.2 * f - 0.008
        under.append([straw.vert(Vector((math.cos(i / N * math.tau) * r, math.sin(i / N * math.tau) * r, z - H)),
                                 (i / N * math.tau * r * 2, f * 2), 0.25, 0.0) for i in range(N)])
    for j in range(urows):
        for i in range(N):
            k = (i + 1) % N
            straw.face((under[j][k], under[j][i], under[j + 1][i], under[j + 1][k]))

    rim = [(Vector((math.cos(a) * R, math.sin(a) * R, 0.025 - H + 0.0)), Vector((math.cos(a), math.sin(a), 0))) for a in
           [i / 72 * math.tau for i in range(72)]]
    jewel.loop_tube(binding, rim, -0.004, 0, 0.013, around=6, ramp=0.5, glow=0.1,
                    wobble=lambda i: 0.002 * math.sin(i / 72 * math.tau * 36))
    jewel.sphere(binding, Vector((0, 0, 0.012)), 0.022, rings=6, segments=10, ramp=0.55, glow=0.2, squash=0.8)
    band_r = 0.3
    band_z = -H * band_r / R - 0.012
    band = [(Vector((math.cos(a), math.sin(a), 0)) * band_r + Vector((0, 0, band_z)), Vector((math.cos(a), math.sin(a), 0))) for a in
            [i / 40 * math.tau for i in range(40)]]
    jewel.loop_tube(binding, band, 0, 0, 0.008, around=5, ramp=0.4)
    tmdl.export(out_path, [("straw", straw.finish(70), -1), ("binding", binding.finish(60), -1)], ao_radius=0.03, ao_strength=0.5, max_tris=1600)
