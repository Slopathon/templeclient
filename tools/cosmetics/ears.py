"""Animal ear shells shared by the ear models (Blender axes: Z up, the ear's opening faces front, -Y).

An ear is a cupped shell: an outer furred surface wrapping round the back and sides, a thinner inner surface set
inside it (the pink inner ear), joined along the rim of the opening and at the base. Tufts of fur rise from inside.
"""

import math

import bmesh
from mathutils import Vector

import shapes
import tmdl

def shell(height, width, depth, open_angle, oval=False, rows=28, cols=28, thickness=0.012, lean_back=0.02):
    """Returns (outer, inner, rim) objects with params lists (h, a01) per vertex for attributes.

    A pointed ear narrows straight to its tip (a cat's triangle, the base slightly rounded); an oval ear keeps its width
    most of the way up and closes in a round tip (a rabbit's).
    """

    def profile(h):
        if oval:
            w = width * math.sqrt(max(0.0, 1 - h ** 2.4)) * (0.82 + 0.18 * math.sin(math.pi * min(1.0, h * 1.2)))
        else:
            w = width * (1 - h) ** 1.05 * (1 + 0.12 * math.sin(math.pi * min(1.0, h * 2)))
        return max(w, 0.002)

    def point(h, a, inset):
        w = profile(h) - inset
        d = depth * ((1 - h) ** 0.8 if not oval else math.sqrt(max(0.0, 1 - h ** 3))) + 0.004 - inset * 0.7
        ang = math.radians(open_angle) * a
        x = math.sin(ang) * w
        y = math.cos(ang) * d * 0.5 + lean_back * h * h
        return Vector((x, y, h * height))

    def build(name, inset, flip):
        bm = bmesh.new()
        grid = []
        params = []
        for i in range(rows + 1):
            h = i / rows * 0.985
            row = []
            for j in range(cols + 1):
                a = j / cols * 2 - 1
                row.append(bm.verts.new(point(h, a, inset)))
                params.append((h, (a + 1) / 2))
            grid.append(row)
        for i in range(rows):
            for j in range(cols):
                quad = (grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j])
                bm.faces.new(quad if not flip else tuple(reversed(quad)))
        tip = bm.verts.new(Vector((0, lean_back, height)))
        params.append((1.0, 0.5))
        for j in range(cols):
            tri = (grid[rows][j], grid[rows][j + 1], tip)
            bm.faces.new(tri if not flip else tuple(reversed(tri)))
        bm.normal_update()
        return tmdl.mesh_object(name, bm), params

    outer, p_out = build("outer", 0.0, False)
    inner, p_in = build("inner", thickness, True)

    bm = bmesh.new()
    p_rim = []
    edges = []
    for side in (-1, 1):
        for i in range(rows + 1):
            h = i / rows * 0.985
            edges.append((h, side))
    rim_rows = []
    for h, side in edges:
        a = side
        o = point(h, a, 0.0)
        n = point(h, a, thickness)
        rim_rows.append((bm.verts.new(o), bm.verts.new(n)))
        p_rim += [(h, (a + 1) / 2), (h, (a + 1) / 2)]
    for s in range(2):
        base = s * (rows + 1)
        for i in range(rows):
            a0, b0 = rim_rows[base + i]
            a1, b1 = rim_rows[base + i + 1]
            face = (a0, a1, b1, b0) if s == 0 else (a0, b0, b1, a1)
            bm.faces.new(face)
    bm.normal_update()
    rim = tmdl.mesh_object("rim", bm)
    return (outer, p_out), (inner, p_in), (rim, p_rim)

def tufts(count, height, width, seed=3):
    """Fine fur tufts rising from inside the ear base, curling slightly outward."""
    bm = bmesh.new()
    params = []
    for k in range(count):
        side = (k / max(1, count - 1)) * 2 - 1
        x0 = side * width * 0.45
        base = Vector((x0, 0.006, height * 0.04))
        lean = Vector((side * width * 0.2, -0.012 - 0.008 * shapes.hash1(k + seed), height * (0.22 + 0.14 * shapes.hash1(k * 3 + seed))))
        n = 6
        ring_prev = None
        for i in range(n + 1):
            f = i / n
            c = base + lean * f + Vector((0, -0.006 * f * f, 0))
            r = 0.005 * (1 - f) + 0.0006
            ring = []
            for j in range(5):
                a = j / 5 * math.tau
                ring.append(bm.verts.new(c + Vector((math.cos(a) * r, math.sin(a) * r * 0.6, 0))))
                params.append((f, j / 5))
            if ring_prev:
                for j in range(5):
                    bm.faces.new((ring_prev[j], ring_prev[(j + 1) % 5], ring[(j + 1) % 5], ring[j]))
            ring_prev = ring
    bm.normal_update()
    return tmdl.mesh_object("tufts", bm), params

def finish(objs_params, bend_from=None, bend_to=None):
    """UVs, colour ramp, glow and bend weights for each (object, params, part kind)."""
    for obj, params, kind in objs_params:
        tmdl.set_uv(obj, lambda i, co, p=params: (p[i][0], p[i][1]))
        if kind == "outer":
            tmdl.set_attr(obj, "ramp", lambda i, co, p=params: 0.25 + 0.65 * p[i][0])
            tmdl.set_attr(obj, "glow", lambda i, co, p=params: shapes.smoothstep(0.8, 1.0, p[i][0]))
        elif kind == "inner":
            tmdl.set_attr(obj, "ramp", lambda i, co, p=params: 0.35 + 0.5 * p[i][0])
            tmdl.set_attr(obj, "glow", lambda i, co, p=params: 0.6 * shapes.smoothstep(0.2, 0.9, p[i][0]) * (1 - abs(p[i][1] * 2 - 1)))
        else:
            tmdl.set_attr(obj, "ramp", lambda i, co, p=params: 0.6 + 0.4 * p[i][0])
            tmdl.set_attr(obj, "glow", lambda i, co, p=params: 0.0)
        if bend_from is not None:
            tmdl.set_attr(obj, "bend", lambda i, co: shapes.smoothstep(bend_from, bend_to, co.z))
        tmdl.smooth(obj, 75)
