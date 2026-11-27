"""Cat ear (one; the client places a pair). A pointed, cupped shell a little wider than deep, open to the front, with a
pink inner ear and pale tufts rising from inside. Origin at the middle of the base."""

import ears
import tmdl

def build(out_path):
    (outer, po), (inner, pi), (rim, pr) = ears.shell(height=0.2, width=0.15, depth=0.1, open_angle=120,
                                                      thickness=0.011, lean_back=0.025)
    tuft, pt = ears.tufts(8, 0.2, 0.15)
    ears.finish([(outer, po, "outer"), (inner, pi, "inner"), (rim, pr, "outer"), (tuft, pt, "tufts")])
    tmdl.export(out_path, [("outer", outer, -1), ("rim", rim, -1), ("inner", inner, -1), ("tufts", tuft, -1)], ao_radius=0.025, ao_strength=0.45, max_tris=1500)
