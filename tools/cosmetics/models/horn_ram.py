"""Ram horn (the wearer's left; mirrored for the right).

Rises from the top of the head, sweeps back, then curls down behind and around the ear so the tip points forward
past the cheek, moving outward as it turns so it clears the skull. Heavy transverse ridges all along, a rounded
triangular cross-section, thick at the root and tapering. Glow runs in the grooves between ridges.
"""

import math

from mathutils import Vector

import shapes
import tmdl

def build(out_path):
    steps = 72
    path = []
    for i in range(steps + 1):
        f = i / steps
        phi = math.radians(180 - f * 340)
        r = 0.13 * (1 - 0.42 * f)
        y = 0.12 + r * math.cos(phi) + 0.0 * f
        z = 0.06 + r * math.sin(phi)
        x = 0.01 + 0.17 * shapes.smoothstep(0.0, 0.7, f) + 0.02 * f
        path.append(Vector((x, y - 0.12 + 0.13 * 0.0, z - 0.06)))
    off = path[0].copy()
    path = [p - off + Vector((0, 0, -0.03)) for p in path]

    def ridge(t):
        return max(0.0, math.sin(t * math.pi * 2 * 26)) ** 3

    def radius(t, a):
        base = 0.078 * (1 - t) ** 0.75 + 0.012
        tri = 1 + 0.12 * math.cos(3 * a + 0.5)
        return base * tri * (1 + 0.07 * ridge(t) * (1 - 0.6 * t))

    obj, params = shapes.sweep("horn", path, radius, around=28, twist_at=lambda t: t * 0.6)

    tmdl.set_uv(obj, lambda i, co: (params[i][0], params[i][1]))
    tmdl.set_attr(obj, "ramp", lambda i, co: 0.12 + 0.75 * params[i][0] ** 0.7 + 0.1 * ridge(params[i][0]))
    def groove(t):
        ph = (t * 26) % 1.0
        return math.exp(-((ph - 0.75) / 0.07) ** 2)

    tmdl.set_attr(obj, "glow", lambda i, co: groove(params[i][0]) * (1 - shapes.smoothstep(0.65, 0.95, params[i][0]))
                  * shapes.smoothstep(0.02, 0.12, params[i][0]))
    tmdl.smooth(obj, 80)
    tmdl.export(out_path, [("horn", obj, -1)], ao_radius=0.06, max_tris=1500)
