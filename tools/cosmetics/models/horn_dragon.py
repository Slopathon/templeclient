"""Dragon horn (the wearer's left; mirrored for the right).

Long and swept back from the top of the head, rising slightly at the end. Built in segments that swell a little
between joints, with a sharp keel along the top and a smooth underside. Glow seams run between the segments and
along the keel.
"""

import math

from mathutils import Vector

import shapes
import tmdl

def build(out_path):
    path = shapes.bezier([
        Vector((0.0, 0.0, -0.05)),
        Vector((0.01, 0.02, 0.07)),
        Vector((0.035, 0.11, 0.17)),
        Vector((0.06, 0.25, 0.24)),
        Vector((0.075, 0.39, 0.3)),
        Vector((0.08, 0.5, 0.4)),
    ], 62)
    segments = 9

    def seg_phase(t):
        return (t * segments) % 1.0

    def radius(t, a):
        base = 0.09 * (1 - t) ** 1.1 + 0.003
        swell = 1 + 0.07 * math.sin(math.pi * seg_phase(t)) - 0.04 * (1 - shapes.smoothstep(0.0, 0.12, seg_phase(t)))
        keel = 1 + 0.32 * math.exp(-((math.atan2(math.sin(a - math.pi / 2), math.cos(a - math.pi / 2))) / 0.32) ** 2)
        return base * swell * keel

    obj, params = shapes.sweep("horn", path, radius, around=32, oval_at=lambda t: (0.82, 1.0))

    def glow(t, v):
        a = v * math.tau
        seam = 1 - shapes.smoothstep(0.0, 0.07, min(seg_phase(t), 1 - seg_phase(t)))
        keel = math.exp(-((math.atan2(math.sin(a - math.pi / 2), math.cos(a - math.pi / 2))) / 0.2) ** 2)
        return max(seam * 0.9, keel * 0.6) * (1 - shapes.smoothstep(0.75, 1.0, t)) * shapes.smoothstep(0.1, 0.18, t)

    tmdl.set_uv(obj, lambda i, co: (params[i][0], params[i][1]))
    tmdl.set_attr(obj, "ramp", lambda i, co: 0.1 + 0.85 * params[i][0] ** 0.8 - 0.08 * (1 - seg_phase(params[i][0])) ** 8)
    tmdl.set_attr(obj, "glow", lambda i, co: glow(*params[i]))
    tmdl.smooth(obj, 70)
    tmdl.export(out_path, [("horn", obj, -1)], ao_radius=0.06, max_tris=1500)
