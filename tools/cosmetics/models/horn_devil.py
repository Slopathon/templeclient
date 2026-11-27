"""Devil horn (the wearer's left; the client mirrors it for the right).

Short and thick, rising from the skull, sweeping slightly out and back, with the tip hooking forward. Growth rings
stand out near the root and fade toward a smooth polished tip; the cross-section is a little flattened front to back
and twists along the length. Glow cracks wander up from the root.
"""

import math

from mathutils import Vector

import shapes
import tmdl

def build(out_path):
    path = shapes.bezier([
        Vector((0.0, 0.0, -0.05)),
        Vector((0.008, 0.004, 0.05)),
        Vector((0.035, 0.03, 0.15)),
        Vector((0.06, 0.03, 0.24)),
        Vector((0.06, -0.02, 0.33)),
        Vector((0.03, -0.085, 0.37)),
    ], 64)

    def ring(t):
        return max(0.0, math.sin(t * math.pi * 2 * 10)) ** 6 * (1 - shapes.smoothstep(0.0, 0.75, t))

    def radius(t, a):
        r = 0.088 * (1 - t) ** 0.9 + 0.003
        r *= 1 + 0.04 * ring(t)
        r *= 1 + 0.025 * (shapes.noise1(a * 3 + t * 9, 2.0) - 0.5)
        return r

    obj, params = shapes.sweep("horn", path, radius, around=32, twist_at=lambda t: t * 0.9,
                               oval_at=lambda t: (1.0, 0.84))

    def crack_glow(t, v):
        a = v * math.tau
        g = 0.0
        for k in range(3):
            centre = k * math.tau / 3 + 0.7 * math.sin(t * 6.5 + k * 1.9) + 0.4 * (shapes.noise1(t * 11, k) - 0.5)
            d = math.atan2(math.sin(a - centre), math.cos(a - centre))
            width = 0.2 * (1 - 0.6 * t)
            g = max(g, math.exp(-(d / width) ** 2))
        return g * (1 - shapes.smoothstep(0.55, 0.95, t)) * shapes.smoothstep(0.1, 0.18, t)

    tmdl.set_uv(obj, lambda i, co: (params[i][0], params[i][1]))
    tmdl.set_attr(obj, "ramp", lambda i, co: max(0.0, 0.1 + 0.9 * params[i][0] ** 0.8 - 0.06 * (1 - ring(params[i][0]))
                                                 * (1 - params[i][0])))
    tmdl.set_attr(obj, "glow", lambda i, co: crack_glow(*params[i]))
    tmdl.smooth(obj, 80)
    tmdl.export(out_path, [("horn", obj, -1)], ao_radius=0.05, max_tris=1500)
