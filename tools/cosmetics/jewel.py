"""Jewellery parts for crowns, circlets and fittings (Blender axes: Z up, front toward -Y).

Everything is added into bmesh "part builders" so each material slot of a model becomes one object. Each builder
keeps per-vertex values (uv, ramp, glow) that are written as attributes when the part is finished.
"""

import math

import bmesh
from mathutils import Vector

import tmdl

class Part:
    """A material slot being built: a bmesh plus per-vertex uv, ramp and glow."""

    def __init__(self, name):
        self.name = name
        self.bm = bmesh.new()
        self.uv = []
        self.ramp = []
        self.glow = []

    def vert(self, co, uv=(0.0, 0.0), ramp=0.5, glow=0.0):
        v = self.bm.verts.new(co)
        self.uv.append(uv)
        self.ramp.append(ramp)
        self.glow.append(glow)
        return v

    def face(self, verts):
        try:
            return self.bm.faces.new(verts)
        except ValueError:
            return None

    def finish(self, smooth_angle=50):
        uv, ramp, glow = self.uv, self.ramp, self.glow
        self.bm.normal_update()
        obj = tmdl.mesh_object(self.name, self.bm)
        tmdl.set_uv(obj, lambda i, co: uv[i])
        tmdl.set_attr(obj, "ramp", lambda i, co: ramp[i])
        tmdl.set_attr(obj, "glow", lambda i, co: glow[i])
        tmdl.smooth(obj, smooth_angle)
        return obj

def rounded_square(a, rc, n):
    """n (point, outward normal) pairs round a rounded square of half-size a and corner radius rc, in the XY plane,
    starting at the middle of the front (-Y) and going round toward +X."""
    c = a - rc
    straight = 2 * c
    arc = math.pi / 2 * rc
    total = 4 * straight + 4 * arc
    out = []
    for i in range(n):
        d = i / n * total
        d = (d + straight / 2) % total
        side = int(d // (straight + arc))
        r = d - side * (straight + arc)
        if r < straight:
            local = Vector((-c + r, -a, 0))
            nrm = Vector((0, -1, 0))
        else:
            ang = -math.pi / 2 + (r - straight) / rc
            local = Vector((c + rc * math.cos(ang), -c + rc * math.sin(ang), 0))
            nrm = Vector((math.cos(ang), math.sin(ang), 0))
        rot = side * math.pi / 2
        cr, sr = math.cos(rot), math.sin(rot)
        p = Vector((local.x * cr - local.y * sr, local.x * sr + local.y * cr, 0))
        nn = Vector((nrm.x * cr - nrm.y * sr, nrm.x * sr + nrm.y * cr, 0))
        out.append((p, nn))
    return out

def band(part, loop, z0, z1, thickness, rows=4, bulge=0.004, ramp_lo=0.35, ramp_hi=0.7, glow=0.6):
    """A closed band standing on a loop of (point, normal): outer wall (barrelled slightly), inner wall and top and
    bottom faces. Pattern uv in real distances: u round the loop, v up the band."""
    n = len(loop)
    length = sum((loop[(i + 1) % n][0] - loop[i][0]).length for i in range(n))
    height = z1 - z0

    def ring(offset, z, v, lift=0.0):
        return [part.vert(p + nrm * offset + Vector((0, 0, z)), (i / n * length, v * height), ramp_lo + (ramp_hi - ramp_lo) * v, glow)
                for i, (p, nrm) in enumerate(loop)]

    walls = []
    for j in range(rows + 1):
        v = j / rows
        walls.append(ring(math.sin(v * math.pi) * bulge, z0 + v * height, v))
    inner_top = ring(-thickness, z1, 1.0)
    inner_bot = ring(-thickness, z0, 0.0)
    for j in range(rows):
        for i in range(n):
            k = (i + 1) % n
            part.face((walls[j][i], walls[j][k], walls[j + 1][k], walls[j + 1][i]))
    for i in range(n):
        k = (i + 1) % n
        part.face((inner_bot[k], inner_bot[i], inner_top[i], inner_top[k]))
        part.face((walls[rows][i], walls[rows][k], inner_top[k], inner_top[i]))
        part.face((walls[0][k], walls[0][i], inner_bot[i], inner_bot[k]))

def loop_tube(part, loop, z, offset, radius, around=10, ramp=0.75, glow=0.0, wobble=None):
    """A rolled tube following a planar loop (a lip along a band's edge). wobble(i) adds to the radius (beading)."""
    n = len(loop)
    length = sum((loop[(i + 1) % n][0] - loop[i][0]).length for i in range(n))
    rings = []
    for i, (p, nrm) in enumerate(loop):
        r = radius + (wobble(i) if wobble else 0.0)
        centre = p + nrm * offset + Vector((0, 0, z))
        ring = []
        for j in range(around):
            a = j / around * math.tau
            d = nrm * math.cos(a) + Vector((0, 0, math.sin(a)))
            ring.append(part.vert(centre + d * r, (i / n * length, j / around * math.tau * r), ramp, glow))
        rings.append(ring)
    for i in range(n):
        k = (i + 1) % n
        for j in range(around):
            m = (j + 1) % around
            part.face((rings[i][j], rings[k][j], rings[k][m], rings[i][m]))

def path_tube(part, path, radius_fn, around=10, ramp=0.75, glow=0.0, caps=True):
    """A tube along an open path (list of Vectors); radius_fn(t) with t 0..1."""
    n = len(path)
    from shapes import frames
    fr = frames(path)
    length = sum((path[i + 1] - path[i]).length for i in range(n - 1))
    rings = []
    for i, p in enumerate(path):
        t = i / (n - 1)
        tan, nor, bi = fr[i]
        r = radius_fn(t)
        ring = [part.vert(p + (nor * math.cos(j / around * math.tau) + bi * math.sin(j / around * math.tau)) * r,
                          (t * length, j / around * math.tau * r), ramp, glow)
                for j in range(around)]
        rings.append(ring)
    for i in range(n - 1):
        for j in range(around):
            m = (j + 1) % around
            part.face((rings[i][j], rings[i + 1][j], rings[i + 1][m], rings[i][m]))
    if caps:
        for ring, tip in ((rings[0], path[0] - fr[0][0] * radius_fn(0) * 0.6), (rings[-1], path[-1] + fr[-1][0] * radius_fn(1) * 0.6)):
            c = part.vert(tip, (0, 0), ramp, glow)
            for j in range(around):
                part.face((ring[j], ring[(j + 1) % around], c))

def sphere(part, centre, radius, rings=8, segments=12, ramp=0.6, glow=0.0, squash=1.0):
    """A UV sphere (pearls, orbs, studs)."""
    verts = []
    for i in range(1, rings):
        phi = math.pi * i / rings
        row = []
        for j in range(segments):
            th = math.tau * j / segments
            d = Vector((math.sin(phi) * math.cos(th), math.sin(phi) * math.sin(th), math.cos(phi) * squash))
            row.append(part.vert(centre + d * radius, (j / segments * math.tau * radius, i / rings * math.pi * radius), ramp, glow))
        verts.append(row)
    top = part.vert(centre + Vector((0, 0, radius * squash)), (0, 0), ramp, glow)
    bot = part.vert(centre - Vector((0, 0, radius * squash)), (0, 1), ramp, glow)
    for j in range(segments):
        k = (j + 1) % segments
        part.face((top, verts[0][j], verts[0][k]))
        part.face((bot, verts[-1][k], verts[-1][j]))
    for i in range(len(verts) - 1):
        for j in range(segments):
            k = (j + 1) % segments
            part.face((verts[i][j], verts[i + 1][j], verts[i + 1][k], verts[i][k]))

def gem(part, centre, facing, size, colour_ramp, glow=1.0, sides=8, depth=0.75):
    """A brilliant-cut gem: a flat table, crown facets, a girdle and a pavilion to a point, facing along `facing`.
    colour_ramp picks the gem's colour in the client (0.1 ruby, 0.4 sapphire, 0.7 emerald, 0.95 diamond)."""
    f = facing.normalized()
    t = f.orthogonal().normalized()
    b = f.cross(t)

    def at(r, h, a):
        return centre + (t * math.cos(a) + b * math.sin(a)) * r + f * h

    table = [part.vert(at(size * 0.55, size * 0.35, math.tau * i / sides), (0, 0), colour_ramp, glow) for i in range(sides)]
    crown = [part.vert(at(size * 0.85, size * 0.18, math.tau * (i + 0.5) / sides), (0, 0), colour_ramp, glow) for i in range(sides)]
    girdle = [part.vert(at(size, 0.0, math.tau * i / sides), (0, 0), colour_ramp, glow) for i in range(sides)]
    culet = part.vert(centre - f * size * depth, (0, 0), colour_ramp, glow)
    part.face(table)
    for i in range(sides):
        k = (i + 1) % sides
        part.face((table[i], crown[i], table[k]))
        part.face((crown[i], girdle[k], table[k]))
        part.face((girdle[i], crown[i], table[i]))
        part.face((girdle[i], girdle[k], crown[i]))
        part.face((girdle[k], girdle[i], culet))

def plate(part, outline, origin, right, up, thickness, ramp=0.6, glow=0.0):
    """A solid plate from a 2D outline (list of (x, y) counter-clockwise), laid in the plane spanned by right and up
    at origin, extruded `thickness` along the plane's normal (half each way)."""
    n_vec = right.cross(up).normalized()
    front = [part.vert(origin + right * x + up * y + n_vec * thickness / 2, (x, y), ramp, glow) for x, y in outline]
    back = [part.vert(origin + right * x + up * y - n_vec * thickness / 2, (x, y), ramp, glow) for x, y in outline]
    _fill(part, front, False)
    _fill(part, back, True)
    n = len(outline)
    for i in range(n):
        k = (i + 1) % n
        part.face((front[i], back[i], back[k], front[k]))

def _fill(part, ring, flip):
    """Fills a (possibly concave) polygon ring with triangles by ear clipping in its own plane."""
    pts = [v.co.copy() for v in ring]
    normal = Vector((0, 0, 0))
    for i in range(len(pts)):
        a, b = pts[i], pts[(i + 1) % len(pts)]
        normal += Vector(((a.y - b.y) * (a.z + b.z), (a.z - b.z) * (a.x + b.x), (a.x - b.x) * (a.y + b.y)))
    normal.normalize()
    idx = list(range(len(ring)))

    def convex(i0, i1, i2):
        return (pts[i1] - pts[i0]).cross(pts[i2] - pts[i1]).dot(normal) > 1e-12

    def inside(p, a, b, c):
        def side(p1, p2):
            return (p2 - p1).cross(p - p1).dot(normal)
        return side(a, b) >= 0 and side(b, c) >= 0 and side(c, a) >= 0

    guard = 0
    while len(idx) > 3 and guard < 10000:
        guard += 1
        for k in range(len(idx)):
            i0, i1, i2 = idx[k - 1], idx[k], idx[(k + 1) % len(idx)]
            if not convex(i0, i1, i2):
                continue
            if any(inside(pts[j], pts[i0], pts[i1], pts[i2]) for j in idx if j not in (i0, i1, i2)):
                continue
            tri = (ring[i0], ring[i1], ring[i2])
            part.face(tuple(reversed(tri)) if flip else tri)
            idx.pop(k)
            break
        else:
            break
    if len(idx) == 3:
        tri = (ring[idx[0]], ring[idx[1]], ring[idx[2]])
        part.face(tuple(reversed(tri)) if flip else tri)

def cross_pattee(w, h):
    """Outline of a cross pattee (arms flaring outward), w wide and h tall, base at y = 0."""
    cy = h * 0.55
    arm = w / 2
    neck = w * 0.09
    flare = w * 0.2
    return [(-neck, 0), (neck, 0), (neck * 1.1, cy - neck * 1.6), (arm, cy - flare), (arm, cy + flare), (neck * 1.1, cy + neck * 1.6),
            (flare * 0.95, h), (-flare * 0.95, h), (-neck * 1.1, cy + neck * 1.6), (-arm, cy + flare), (-arm, cy - flare),
            (-neck * 1.1, cy - neck * 1.6)]

def fleur_de_lis(w, h):
    """Outline of a stylised fleur-de-lis, w wide and h tall, base at y = 0."""
    pts = []
    for i in range(9):
        a = i / 8
        pts.append((w * 0.13 * math.sin(a * math.pi) * (1 - a) + w * 0.02, h * 0.35 + h * 0.65 * a))
    centre = [(x, y) for x, y in pts]
    right = []
    for i in range(10):
        a = i / 9
        ang = math.pi * 0.95 * a
        r = w * 0.32 * (1 - 0.35 * a)
        right.append((w * 0.12 + math.sin(ang) * r, h * 0.38 + math.cos(ang) * r * 0.85 - h * 0.05 * a))
    outline = [(w * 0.06, 0), (w * 0.11, h * 0.18)] + [(w * 0.12, h * 0.3)] + right + [(w * 0.08, h * 0.4)] + centre[1:]
    mirrored = [(-x, y) for x, y in reversed(outline)]
    return outline + mirrored[1:-1]

def spike(part, base_centre, outward, w, h, depth, ramp=0.6, glow=0.0, steps=10):
    """A gothic spire: a four-sided point with concave faces, rising from base_centre, leaning slightly outward."""
    up = Vector((0, 0, 1))
    side = outward.cross(up).normalized()
    rings = []
    for i in range(steps + 1):
        t = i / steps
        half_w = w / 2 * (1 - t) ** 1.7
        half_d = depth / 2 * (1 - t) ** 1.7
        c = base_centre + up * h * t + outward * (0.012 * t * t)
        ring = [part.vert(c + side * sx * half_w + outward * sy * half_d, (t * h, k / 4 * w), ramp + 0.3 * t, glow * (1 - t))
                for k, (sx, sy) in enumerate(((-1, -1), (1, -1), (1, 1), (-1, 1)))]
        rings.append(ring)
    for i in range(steps):
        for k in range(4):
            m = (k + 1) % 4
            part.face((rings[i][k], rings[i][m], rings[i + 1][m], rings[i + 1][k]))
    tip = part.vert(base_centre + up * h * 1.01 + outward * 0.012, (1, 0), ramp + 0.3, 0.0)
    for k in range(4):
        part.face((rings[-1][k], rings[-1][(k + 1) % 4], tip))
