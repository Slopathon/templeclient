"""Royal crown. Origin at the centre of the top of the head (hat layer); the band hugs the head's rounded square.

An engraved band with a beaded upper lip, set with gems in bezels; crosses pattee at front, back and sides and
fleurs-de-lis at the corners; two pearl-strung arches crossing over a padded red velvet cap; an orb with a band and
a cross on top; a roll of ermine (white fur with black spots) round the base.
"""

import math

from mathutils import Vector

import jewel
import tmdl

A = 0.281 + 0.012
RC = 0.05
Z0 = -0.075
Z1 = 0.04
N = 84

def build(out_path):
    metal = jewel.Part("metal")
    gems = jewel.Part("gem")
    pearls = jewel.Part("pearl")
    velvet = jewel.Part("velvet")
    ermine = jewel.Part("ermine")
    loop = jewel.rounded_square(A, RC, N)

    jewel.band(metal, loop, Z0, Z1, 0.012, rows=3, glow=0.7)
    jewel.loop_tube(metal, loop, Z1, -0.006, 0.0085, around=6, ramp=0.85, glow=0.2,
                    wobble=lambda i: 0.0035 * max(0.0, math.cos(i / N * math.tau * 28)) ** 2)

    rings = []
    around = 8
    for i, (p, nrm) in enumerate(loop):
        centre = p + nrm * 0.006 + Vector((0, 0, Z0 - 0.006))
        ring = []
        for j in range(around):
            a = j / around * math.tau
            d = nrm * math.cos(a) + Vector((0, 0, math.sin(a)))
            spot_u = (i / N * 20) % 1.0
            spot = abs(spot_u - 0.5) < 0.12 and abs(math.sin(a) - 0.2) < 0.45 and math.cos(a) > 0.1
            ring.append(ermine.vert(centre + d * 0.02, (i / N * 6, j / around), 0.0 if spot else 0.85 + 0.15 * math.sin(a)))
        rings.append(ring)
    for i in range(N):
        k = (i + 1) % N
        for j in range(around):
            m = (j + 1) % around
            ermine.face((rings[i][j], rings[k][j], rings[k][m], rings[i][m]))

    up = Vector((0, 0, 1))
    ornaments = []
    for q in range(8):
        idx = int(q / 8 * N)
        p, nrm = loop[idx]
        tangent = (loop[(idx + 1) % N][0] - loop[idx - 1][0]).normalized()
        ornaments.append((p, nrm, tangent))
        base = p - nrm * 0.006 + up * (Z1 - 0.004)
        if q % 2 == 0:
            outline = jewel.cross_pattee(0.15, 0.155)
            jewel.plate(metal, outline, base, tangent, up, 0.018, ramp=0.65, glow=0.35)
            jewel.gem(gems, base + up * 0.086 + nrm * 0.01, nrm, 0.02, 0.1 if q == 0 else 0.4)
        else:
            outline = jewel.fleur_de_lis(0.14, 0.135)
            jewel.plate(metal, outline, base, tangent, up, 0.016, ramp=0.65, glow=0.35)

    colours = [0.1, 0.4, 0.7, 0.4]
    for q in range(8):
        idx = int((q + 0.5) / 8 * N)
        p, nrm = loop[idx]
        centre = p + nrm * 0.004 + up * ((Z0 + Z1) / 2)
        jewel.gem(gems, centre, nrm, 0.02, colours[q % 4])
        tangent = (loop[(idx + 1) % N][0] - loop[idx - 1][0]).normalized()
        bezel = [(centre + (tangent * math.cos(a) + up * math.sin(a)) * 0.024, nrm) for a in [k / 12 * math.tau for k in range(12)]]
        jewel.loop_tube(metal, bezel, 0, 0, 0.004, around=4, ramp=0.85, glow=0.3)

    apex = Z1 + 0.27
    for axis in (Vector((0, 1, 0)), Vector((1, 0, 0))):
        path = []
        steps = 28
        reach = A - 0.01
        for i in range(steps + 1):
            phi = math.pi * i / steps
            h = Z1 + 0.14 + (apex - Z1 - 0.14) * math.sin(phi) - 0.02 * math.sin(phi) ** 8
            path.append(axis * (-reach * math.cos(phi)) + up * h)
        jewel.path_tube(metal, path, lambda t: 0.0105 * (1 - 0.25 * math.sin(math.pi * t)), around=8, ramp=0.75, glow=0.25, caps=False)
        for k in range(1, 12):
            if k == 6:
                continue
            phi = math.pi * k / 12
            h = Z1 + 0.14 + (apex - Z1 - 0.14) * math.sin(phi) - 0.02 * math.sin(phi) ** 8
            out = Vector((axis.x * -math.cos(phi), axis.y * -math.cos(phi), math.sin(phi))).normalized()
            jewel.sphere(pearls, axis * (-reach * math.cos(phi)) + up * h + out * 0.017, 0.0085, rings=5, segments=8, ramp=0.7)

    rows = 9
    cols = N // 2
    cap = []
    for j in range(rows + 1):
        v = j / rows
        k = math.cos(v * math.pi / 2)
        row = []
        for i in range(cols):
            p, nrm = loop[i * 2]
            ang = math.atan2(p.y, p.x)
            puff = 1 + 0.04 * abs(math.sin(2 * ang)) * math.sin(v * math.pi)
            q = Vector((p.x * k * puff, p.y * k * puff, 0)) - nrm * 0.014 * k
            row.append(velvet.vert(q + up * (Z1 - 0.02 + (apex - Z1) * 0.62 * math.sin(v * math.pi / 2)), (i / cols * 4, v), 0.3 + 0.5 * v))
        cap.append(row)
    for j in range(rows):
        for i in range(cols):
            m = (i + 1) % cols
            velvet.face((cap[j][i], cap[j][m], cap[j + 1][m], cap[j + 1][i]))

    orb = up * (apex + 0.03)
    jewel.sphere(metal, orb, 0.03, rings=8, segments=14, ramp=0.7, glow=0.3)
    band_loop = [(orb + Vector((math.cos(a), math.sin(a), 0)) * 0.03, Vector((math.cos(a), math.sin(a), 0))) for a in
                 [k / 20 * math.tau for k in range(20)]]
    jewel.loop_tube(metal, band_loop, 0, 0, 0.0045, around=6, ramp=0.85, glow=0.3)
    jewel.plate(metal, jewel.cross_pattee(0.05, 0.06), orb + up * 0.026, Vector((1, 0, 0)), up, 0.01, ramp=0.7, glow=0.4)

    parts = [("velvet", velvet.finish(30), -1), ("metal", metal.finish(55), -1), ("gem", gems.finish(5), -1),
             ("pearl", pearls.finish(60), -1), ("ermine", ermine.finish(60), -1)]
    tmdl.export(out_path, parts, ao_radius=0.04, ao_strength=0.6, max_tris=3600)
