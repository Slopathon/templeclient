"""Export sculpted cosmetic models from Blender to the client's .tmdl format (see nz.temple.cosmetic.Model).

Run inside Blender (headless): models are built by script, never hand-edited, so every asset can be regenerated.

Axes: Blender is Z-up with the front toward -Y; the game's model space is Y-up with the front toward +Z. A point
(x, y, z) in Blender becomes (x, z, -y). Units are kept: model scripts work in head-space units, where 1 unit is
16 skin pixels (the skull is 8/16 wide).

Each part object needs a UV layer named "pattern" (u along the piece, v across) and may carry float point
attributes "glow" (where the magic layer may show, 0..1), "ramp" (position on the finish's colour ramp, 0 dark
to 1 light) and "bend" (how far a vertex follows the part's next joint, for smooth bends).
Crease shading is baked here by ray casting against every part of the model together.
"""

import math
import random
import struct

import bpy
import bmesh
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

VERSION = 1
LOD_SCALE = 1.0
SMALL_PART = 200
TO_GAME = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, -1, 0, 0), (0, 0, 0, 1)))

def clear_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)

def mesh_object(name, bm):
    """Links a new object made from a bmesh (which is freed)."""
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    obj = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(obj)
    return obj

def smooth(obj, angle_deg=40):
    """Smooth shading, keeping edges sharper than angle_deg crisp."""
    for p in obj.data.polygons:
        p.use_smooth = True
    obj.data.set_sharp_from_angle(angle=math.radians(angle_deg))
    obj["smooth_angle"] = angle_deg

def set_uv(obj, fn):
    """Fills the "pattern" UV layer from fn(vertex_index, co) -> (u, v), per corner."""
    me = obj.data
    layer = me.uv_layers.get("pattern") or me.uv_layers.new(name="pattern")
    for loop in me.loops:
        v = loop.vertex_index
        layer.data[loop.index].uv = fn(v, me.vertices[v].co)

def set_uv_corners(obj, fn):
    """Fills the "pattern" UV layer from fn(polygon, loop) -> (u, v), for seams that need per-corner values."""
    me = obj.data
    layer = me.uv_layers.get("pattern") or me.uv_layers.new(name="pattern")
    for poly in me.polygons:
        for li in poly.loop_indices:
            layer.data[li].uv = fn(poly, me.loops[li])

def set_attr(obj, name, fn):
    """Fills a float point attribute from fn(vertex_index, co) -> value."""
    me = obj.data
    attr = me.attributes.get(name) or me.attributes.new(name=name, type="FLOAT", domain="POINT")
    for v in me.vertices:
        attr.data[v.index].value = fn(v.index, v.co)

def apply_modifiers(obj):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = obj.evaluated_get(dg)
    me = bpy.data.meshes.new_from_object(ev, preserve_all_data_layers=True, depsgraph=dg)
    obj.modifiers.clear()
    old = obj.data
    obj.data = me
    bpy.data.meshes.remove(old)

def _triangles(obj):
    """World-space (position, normal, uv, glow, ramp, bend) per corner of each triangle, in game axes."""
    dg = bpy.context.evaluated_depsgraph_get()
    ev = obj.evaluated_get(dg)
    me = ev.to_mesh(preserve_all_data_layers=True, depsgraph=dg)
    me.calc_loop_triangles()
    world = TO_GAME @ obj.matrix_world
    normal_m = world.to_3x3().inverted_safe().transposed()
    uv = me.uv_layers.get("pattern")
    glow = me.attributes.get("glow")
    ramp = me.attributes.get("ramp")
    bend = me.attributes.get("bend")
    normals = me.corner_normals
    tris = []
    for tri in me.loop_triangles:
        pts = [world @ me.vertices[me.loops[li].vertex_index].co for li in tri.loops]
        face_n = (pts[1] - pts[0]).cross(pts[2] - pts[0])
        if face_n.length < 1e-10:
            continue
        face_n.normalize()
        corners = []
        for li in tri.loops:
            vi = me.loops[li].vertex_index
            p = world @ me.vertices[vi].co
            n = normal_m @ normals[li].vector
            n = n.normalized() if n.length > 1e-6 else face_n
            t = tuple(uv.data[li].uv) if uv else (0.0, 0.0)
            g = glow.data[vi].value if glow else 0.0
            r = ramp.data[vi].value if ramp else 0.5
            k = bend.data[vi].value if bend else 0.0
            corners.append((p, n, t, g, r, k))
        tris.append(corners)
    ev.to_mesh_clear()
    return tris

def _ambient_occlusion(points, bvh, radius, rays=40, seed=7):
    """Fraction of the hemisphere over each (position, normal) left open within radius."""
    rng = random.Random(seed)
    local = []
    for _ in range(rays):
        a = rng.random() * math.tau
        r = math.sqrt(rng.random())
        local.append((math.cos(a) * r, math.sin(a) * r, math.sqrt(max(0.0, 1 - r * r))))
    out = []
    for p, n in points:
        t = n.orthogonal().normalized()
        b = n.cross(t)
        origin = p + n * radius * 0.01
        open_count = 0
        for x, y, z in local:
            d = t * x + b * y + n * z
            hit, _, _, _ = bvh.ray_cast(origin, d, radius)
            if hit is None:
                open_count += 1
        out.append(open_count / rays)
    return out

def _tri_count(obj):
    me = obj.data
    me.calc_loop_triangles()
    return len(me.loop_triangles)

def _reduce(parts, max_tris):
    """Collapses edges until the model fits max_tris (times LOD_SCALE), keeping the silhouette and the baked
    attributes; small parts are left whole."""
    counts = [(obj, _tri_count(obj)) for _, obj, _ in parts]
    total = sum(c for _, c in counts)
    target = (max_tris or total) * LOD_SCALE
    small = sum(c for _, c in counts if c < SMALL_PART)
    big = total - small
    if total <= target * 1.02 or big == 0:
        return
    ratio = max(0.05, (target - small) / big)
    for obj, c in counts:
        if c < SMALL_PART:
            continue
        m = obj.modifiers.new("reduce", "DECIMATE")
        m.decimate_type = "COLLAPSE"
        m.ratio = ratio
        m.use_collapse_triangulate = True
        apply_modifiers(obj)
        angle = obj.get("smooth_angle")
        if angle is not None:
            smooth(obj, angle)

def export(path, parts, joints=(), ao_radius=0.08, ao_strength=0.7, max_tris=None, reduce=True):
    """Writes a .tmdl file.

    parts: list of (name, object, joint_index) - joint_index -1 for a rigid model.
    joints: list of (name, parent_index, (x, y, z) pivot in Blender axes).
    ao_radius: how far creases look for occluders, in model units.
    ao_strength: how dark a fully enclosed spot gets (1 black, 0 no shading); the shader adds its own light on top.
    max_tris: triangle budget; larger models are reduced to fit (and the far version to LOD_SCALE of it).
    reduce: False for shapes edge collapsing damages (thin closed bands); they build their own lighter far version.
    """
    if reduce:
        _reduce(parts, max_tris)
    vertices = []
    keys = {}
    indices = []
    part_ranges = []
    all_tris = []
    for name, obj, joint in parts:
        tris = _triangles(obj)
        first = len(indices)
        for corners in tris:
            for p, n, t, g, r, k in corners:
                key = (round(p.x, 5), round(p.y, 5), round(p.z, 5), round(n.x, 3), round(n.y, 3), round(n.z, 3),
                       round(t[0], 4), round(t[1], 4), round(k, 3))
                i = keys.get(key)
                if i is None:
                    i = len(vertices)
                    keys[key] = i
                    vertices.append([p, n, t, g, r, 1.0, k])
                indices.append(i)
            all_tris.append([c[0] for c in corners])
        part_ranges.append((name, joint, first, len(indices) - first))

    flat = [v for tri in all_tris for v in tri]
    bvh = BVHTree.FromPolygons(flat, [(i * 3, i * 3 + 1, i * 3 + 2) for i in range(len(all_tris))], epsilon=0.0)
    ao = _ambient_occlusion([(v[0], v[1]) for v in vertices], bvh, ao_radius)
    for v, o in zip(vertices, ao):
        v[5] = 1 - (1 - o) * ao_strength

    def byte(x):
        return max(0, min(255, int(round(x * 255))))

    def name_bytes(s):
        b = s.encode("utf-8")
        return struct.pack("<B", len(b)) + b

    out = bytearray(b"TMDL")
    out += struct.pack("<i", VERSION)
    out += struct.pack("<i", len(joints))
    for jname, parent, pivot in joints:
        g = TO_GAME @ Vector(pivot)
        out += name_bytes(jname) + struct.pack("<ifff", parent, g.x, g.y, g.z)
    out += struct.pack("<i", len(part_ranges))
    for name, joint, first, count in part_ranges:
        out += name_bytes(name) + struct.pack("<iii", joint, first, count)
    out += struct.pack("<i", len(vertices))
    for p, n, t, g, r, o, k in vertices:
        out += struct.pack("<8f", p.x, p.y, p.z, n.x, n.y, n.z, t[0], t[1])
        out += struct.pack("<4B", byte(o), byte(g), byte(r), byte(k))
    out += struct.pack("<i", len(indices))
    out += struct.pack("<%di" % len(indices), *indices)
    with open(path, "wb") as f:
        f.write(out)
    print("TMDL wrote %s: %d parts, %d vertices, %d triangles" % (path, len(part_ranges), len(vertices), len(indices) // 3))
