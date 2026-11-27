"""Spiked crown. Origin at the centre of the top of the head (hat layer).

An engraved band with rolled lips, eight gothic spires with concave faces (tall at front, back and sides, short at
the corners), each tipped with a pearl and set above a gem; a large gem at the brow.
"""

import math

from mathutils import Vector

import jewel
import tmdl

A = 0.281 + 0.012
RC = 0.05
Z0 = -0.06
Z1 = 0.045
N = 84

def build(out_path):
    metal = jewel.Part("metal")
    gems = jewel.Part("gem")
    pearls = jewel.Part("pearl")
    loop = jewel.rounded_square(A, RC, N)
    up = Vector((0, 0, 1))

    jewel.band(metal, loop, Z0, Z1, 0.012, rows=4, glow=0.7)
    jewel.loop_tube(metal, loop, Z1, -0.006, 0.008, around=6, ramp=0.85, glow=0.2)
    jewel.loop_tube(metal, loop, Z0, -0.006, 0.008, around=6, ramp=0.8, glow=0.2)

    colours = [0.1, 0.4, 0.1, 0.4, 0.1, 0.4, 0.1, 0.4]
    for q in range(8):
        idx = int(q / 8 * N)
        p, nrm = loop[idx]
        tall = q % 2 == 0
        h = 0.24 if tall else 0.15
        base = p - nrm * 0.006 + up * (Z1 - 0.004)
        jewel.spike(metal, base, nrm, 0.085 if tall else 0.07, h, 0.026, ramp=0.55, glow=0.3)
        jewel.sphere(pearls, base + up * (h + 0.012) + nrm * 0.012, 0.016 if tall else 0.013, rings=8, segments=12, ramp=0.75)
        if q != 0:
            jewel.gem(gems, p + nrm * 0.004 + up * ((Z0 + Z1) / 2), nrm, 0.017, colours[q])
    front_p, front_n = loop[0]
    jewel.gem(gems, front_p + front_n * 0.006 + up * ((Z0 + Z1) / 2), front_n, 0.03, 0.1)
    tangent = Vector((1, 0, 0))
    bezel = [(front_p + front_n * 0.004 + up * ((Z0 + Z1) / 2) + (tangent * math.cos(a) + up * math.sin(a)) * 0.034, front_n)
             for a in [k / 20 * math.tau for k in range(20)]]
    jewel.loop_tube(metal, bezel, 0, 0, 0.005, around=6, ramp=0.85, glow=0.3)

    parts = [("metal", metal.finish(50), -1), ("gem", gems.finish(5), -1), ("pearl", pearls.finish(60), -1)]
    tmdl.export(out_path, parts, ao_radius=0.04, ao_strength=0.6, max_tris=2600)
