"""Shared shape builders for the cosmetic model scripts (Blender axes: Z up, front toward -Y)."""

import math

import bmesh
from mathutils import Vector

import tmdl

def smoothstep(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)

def hash1(n):
    """Deterministic pseudo-random 0..1 from a number."""
    x = math.sin(n * 127.1 + 311.7) * 43758.5453
    return x - math.floor(x)

def noise1(x, seed=0.0):
    """Smooth 1D value noise, 0..1."""
    i = math.floor(x)
    f = x - i
    u = f * f * (3 - 2 * f)
    return hash1(i + seed * 17.0) * (1 - u) + hash1(i + 1 + seed * 17.0) * u

def frames(path):
    """Parallel-transport frames (tangent, normal, binormal) along a list of Vectors, so a tube never twists by itself."""
    n = len(path)
    tangents = []
    for i in range(n):
        a = path[max(0, i - 1)]
        b = path[min(n - 1, i + 1)]
        tangents.append((b - a).normalized())
    normal = tangents[0].orthogonal().normalized()
    want = Vector((0, -1, 0))
    proj = want - tangents[0] * want.dot(tangents[0])
    if proj.length > 1e-4:
        normal = proj.normalized()
    out = []
    for t in tangents:
        normal = (normal - t * normal.dot(t)).normalized()
        out.append((t, normal, t.cross(normal)))
    return out

def sweep(name, path, radius_at, around=32, twist_at=None, oval_at=None, cap_tip=True):
    """A tube along path (Vectors), radius_at(t, angle) giving the radius at each ring point, with t 0..1 along.

    twist_at(t) turns the cross-section (radians), oval_at(t) -> (sx, sy) squashes it. Returns the object with the
    pattern UV set (u along, v around) and a "t" list per vertex for further attributes.
    """
    bm = bmesh.new()
    fr = frames(path)
    n = len(path)
    rings = []
    params = []
    for i, p in enumerate(path):
        t = i / (n - 1)
        tan, nor, bi = fr[i]
        tw = twist_at(t) if twist_at else 0.0
        sx, sy = oval_at(t) if oval_at else (1.0, 1.0)
        ring = []
        for j in range(around):
            a = j / around * math.tau
            r = radius_at(t, a)
            ca, sa = math.cos(a + tw), math.sin(a + tw)
            v = bm.verts.new(p + nor * (ca * r * sx) + bi * (sa * r * sy))
            ring.append(v)
            params.append((t, j / around))
        rings.append(ring)
    for i in range(n - 1):
        for j in range(around):
            k = (j + 1) % around
            bm.faces.new((rings[i][j], rings[i][k], rings[i + 1][k], rings[i + 1][j]))
    if cap_tip:
        tip = bm.verts.new(path[-1] + fr[-1][0] * 0.002)
        params.append((1.0, 0.0))
        for j in range(around):
            k = (j + 1) % around
            bm.faces.new((rings[-1][j], rings[-1][k], tip))
    bm.normal_update()
    obj = tmdl.mesh_object(name, bm)
    return obj, params

def bezier(points, steps):
    """Points along a Catmull-Rom spline through the given Vectors."""
    out = []
    n = len(points)
    for s in range(steps + 1):
        f = s / steps * (n - 1)
        i = min(n - 2, int(f))
        t = f - i
        p0 = points[max(0, i - 1)]
        p1 = points[i]
        p2 = points[i + 1]
        p3 = points[min(n - 1, i + 2)]
        t2, t3 = t * t, t * t * t
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    return out
