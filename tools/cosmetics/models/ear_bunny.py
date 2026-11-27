"""Bunny ear (one; the client places a pair). Long, narrow and deeply scooped with a rounded tip. The upper half bends
toward a second joint so the ear flops back smoothly when running. Origin at the middle of the base."""

import ears
import tmdl

def build(out_path):
    height = 0.44
    (outer, po), (inner, pi), (rim, pr) = ears.shell(height=height, width=0.085, depth=0.05, open_angle=125, oval=True,
                                                      rows=40, cols=22, thickness=0.009, lean_back=0.015)
    tuft, pt = ears.tufts(5, 0.14, 0.08)
    ears.finish([(outer, po, "outer"), (inner, pi, "inner"), (rim, pr, "outer"), (tuft, pt, "tufts")],
                bend_from=height * 0.3, bend_to=height * 0.75)
    joints = [("base", -1, (0, 0, 0)), ("tip", 0, (0, 0, height * 0.45))]
    tmdl.export(out_path, [("outer", outer, 0), ("rim", rim, 0), ("inner", inner, 0), ("tufts", tuft, 0)], joints=joints,
                ao_radius=0.025, ao_strength=0.45, max_tris=1500)
