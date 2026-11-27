"""Circlet. Origin at the centre of the top of the head (hat layer); worn low, round the brow.

Two fine wires twisted round each other with small gems set along them at intervals, and at the brow a leaf-shaped
mount holding a teardrop gem that hangs a little below the band.
"""

import math

from mathutils import Vector

import jewel
import tmdl

A = 0.281 + 0.008
RC = 0.05
ZC = -0.17
N = 132

def build(out_path):
    metal = jewel.Part("metal")
    gems = jewel.Part("gem")
    loop = jewel.rounded_square(A, RC, N)
    up = Vector((0, 0, 1))

    for w in range(2):
        rings = []
        around = 5
        for i, (p, nrm) in enumerate(loop):
            ph = i / N * math.tau * 34 + w * math.pi
            centre = p + nrm * (0.004 + 0.004 * math.cos(ph)) + up * (ZC + 0.006 * math.sin(ph))
            ring = []
            for j in range(around):
                a = j / around * math.tau
                d = nrm * math.cos(a) + up * math.sin(a)
                ring.append(metal.vert(centre + d * 0.0055, (i / N * 8, j / around), 0.75, 0.5))
            rings.append(ring)
        for i in range(N):
            k = (i + 1) % N
            for j in range(around):
                m = (j + 1) % around
                metal.face((rings[i][j], rings[k][j], rings[k][m], rings[i][m]))

    colours = [0.4, 0.7, 0.95]
    for q in range(1, 12):
        idx = int(q / 12 * N)
        p, nrm = loop[idx]
        centre = p + nrm * 0.008 + up * ZC
        jewel.gem(gems, centre, nrm, 0.011, colours[q % 3])
        tangent = (loop[(idx + 1) % N][0] - loop[idx - 1][0]).normalized()
        bezel = [(centre + (tangent * math.cos(a) + up * math.sin(a)) * 0.013, nrm) for a in [k / 10 * math.tau for k in range(10)]]
        jewel.loop_tube(metal, bezel, 0, 0, 0.003, around=4, ramp=0.85, glow=0.4)

    front_p, front_n = loop[0]
    leaf = [(0.0, -0.03), (0.016, -0.012), (0.02, 0.006), (0.012, 0.022), (0.0, 0.032), (-0.012, 0.022), (-0.02, 0.006), (-0.016, -0.012)]
    mount = front_p + front_n * 0.01 + up * ZC
    jewel.plate(metal, leaf, mount, Vector((1, 0, 0)), up, 0.006, ramp=0.7, glow=0.4)
    jewel.gem(gems, mount + front_n * 0.006 + up * 0.004, front_n, 0.012, 0.4)
    drop = mount + up * -0.05 + front_n * 0.006
    jewel.path_tube(metal, [mount + up * -0.028 + front_n * 0.004, drop + up * 0.02], lambda t: 0.0025, around=5, ramp=0.8, glow=0.3)
    jewel.gem(gems, drop, Vector((0, 0, -1)), 0.016, 0.1, depth=1.6)

    parts = [("metal", metal.finish(50), -1), ("gem", gems.finish(5), -1)]
    tmdl.export(out_path, parts, ao_radius=0.03, ao_strength=0.5, max_tris=1800)
