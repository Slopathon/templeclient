"""Crystal cluster. Origin at the base where the crystals meet; they point up (+Z) and splay outward.

Five hexagonal prisms of different lengths with pyramidal terminations, the facets flat so each catches the light on
its own. The glow sits in the cores and rises toward the tips.
"""

import math

from mathutils import Matrix, Vector

import jewel
import tmdl

def prism(part, base, direction, length, radius, twist):
    d = direction.normalized()
    t = d.orthogonal().normalized()
    b = d.cross(t)
    body = length * 0.72
    rings = []
    for h, scale in ((0.0, 0.75), (0.08, 1.0), (body, 0.92)):
        ring = []
        for k in range(6):
            a = twist + k / 6 * math.tau
            p = base + d * (h * length / length) + (t * math.cos(a) + b * math.sin(a)) * radius * scale
            f = h / length
            ring.append(part.vert(p, (h * 3, k / 6 * radius * 6), 0.3 + 0.5 * f, 0.08 + 0.3 * f ** 3))
        rings.append(ring)
    for r in range(2):
        for k in range(6):
            m = (k + 1) % 6
            part.face((rings[r][k], rings[r][m], rings[r + 1][m], rings[r + 1][k]))
    tip = part.vert(base + d * length, (length * 3, 0), 0.95, 0.7)
    for k in range(6):
        part.face((rings[2][k], rings[2][(k + 1) % 6], tip))
    bottom = part.vert(base - d * radius * 0.4, (0, 0), 0.2, 0.05)
    for k in range(6):
        part.face((rings[0][(k + 1) % 6], rings[0][k], bottom))

def build(out_path):
    crystal = jewel.Part("crystal")
    specs = [(0.0, 0.0, 0.34, 0.036, 0.1), (0.5, 0.36, 0.25, 0.028, 0.7), (2.3, 0.44, 0.21, 0.025, 1.4), (4.1, 0.32, 0.26, 0.027, 2.2),
             (3.2, 0.55, 0.15, 0.02, 0.4), (5.3, 0.6, 0.12, 0.017, 1.0), (1.4, 0.65, 0.11, 0.016, 2.6)]
    for azimuth, tilt, length, radius, twist in specs:
        d = Vector((math.sin(tilt) * math.cos(azimuth), math.sin(tilt) * math.sin(azimuth), math.cos(tilt)))
        prism(crystal, Vector((0, 0, 0)) + d * 0.01, d, length, radius, twist)
    obj = crystal.finish(5)
    tmdl.export(out_path, [("crystal", obj, -1)], ao_radius=0.03, ao_strength=0.4)
