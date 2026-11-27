package nz.temple.client.render.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class BedrockModel {
	record Face(float u, float v, float w, float h) {
	}
	record Cube(float x0, float y0, float z0, float x1, float y1, float z1, float px, float py, float pz, float rx, float ry,
			float rz, Face north, Face south, Face east, Face west, Face up, Face down) {
	}

	public static final class Bone {
		public final String name;
		final int parent;
		final float px;
		final float py;
		final float pz;
		final float rx;
		final float ry;
		final float rz;
		final List<Cube> cubes;
		final List<Integer> children = new ArrayList<>();
		Bone(String name, int parent, float px, float py, float pz, float rx, float ry, float rz, List<Cube> cubes) {
			this.name = name;
			this.parent = parent;
			this.px = px;
			this.py = py;
			this.pz = pz;
			this.rx = rx;
			this.ry = ry;
			this.rz = rz;
			this.cubes = cubes;
		}
	}
	public static final class Pose {
		public float rx;
		public float ry;
		public float rz;
		public float x;
		public float y;
		public float z;
		public float sx = 1;
		public float sy = 1;
		public float sz = 1;
		public void reset() {
			rx = ry = rz = x = y = z = 0;
			sx = sy = sz = 1;
		}
	}
	private final List<Bone> bones = new ArrayList<>();
	private final Map<String, Integer> byName = new HashMap<>();
	private final List<Integer> roots = new ArrayList<>();
	public final float textureWidth;
	public final float textureHeight;
	private BedrockModel(float textureWidth, float textureHeight) {
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
	}

	public static BedrockModel parse(JsonObject root) {
		JsonObject geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		JsonObject desc = geo.getAsJsonObject("description");
		BedrockModel model = new BedrockModel(desc.get("texture_width").getAsFloat(), desc.get("texture_height").getAsFloat());
		JsonArray list = geo.getAsJsonArray("bones");
		int cubeCount = 0;
		for (JsonElement e : list) {
			model.byName.put(e.getAsJsonObject().get("name").getAsString(), model.byName.size());
		}
		for (JsonElement e : list) {
			JsonObject b = e.getAsJsonObject();
			float[] pivot = vec(b.get("pivot"), 0);
			float[] rot = vec(b.get("rotation"), 0);
			int parent = b.has("parent") ? model.byName.getOrDefault(b.get("parent").getAsString(), -1) : -1;
			List<Cube> cubes = new ArrayList<>();
			if (b.has("cubes")) {
				for (JsonElement c : b.getAsJsonArray("cubes")) {
					cubes.add(cube(c.getAsJsonObject(), cubeCount++));
				}
			}
			model.bones.add(new Bone(b.get("name").getAsString(), parent, pivot[0], pivot[1], pivot[2], rot[0], rot[1], rot[2], cubes));
		}
		for (int i = 0; i < model.bones.size(); i++) {
			Bone b = model.bones.get(i);
			if (b.parent >= 0) {
				model.bones.get(b.parent).children.add(i);
			} else {
				model.roots.add(i);
			}
		}
		return model;
	}
	private static final float SEPARATE = 0.0002f;
	private static Cube cube(JsonObject c, int index) {
		float[] o = vec(c.get("origin"), 0);
		float[] s = vec(c.get("size"), 0);
		float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0;
		if (index >= 0) {
			inflate = (Math.abs(inflate) < 0.01f ? inflate * 4 : inflate) + SEPARATE * (index * 7 % 16 + 1);
		}
		float[] pivot = c.has("pivot") ? vec(c.get("pivot"), 0) : new float[] {o[0] + s[0] / 2, o[1] + s[1] / 2, o[2] + s[2] / 2};
		float[] rot = vec(c.get("rotation"), 0);
		Face n, so, ea, we, up, dn;
		JsonElement uv = c.get("uv");
		if (uv != null && uv.isJsonObject()) {
			JsonObject f = uv.getAsJsonObject();
			n = face(f, "north");
			so = face(f, "south");
			ea = face(f, "east");
			we = face(f, "west");
			up = face(f, "up");
			dn = face(f, "down");
		} else {
			float[] at = vec(uv, 0);
			float u = at[0], v = at[1];
			float w = (float) Math.floor(s[0]), h = (float) Math.floor(s[1]), d = (float) Math.floor(s[2]);
			boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
			ea = new Face(u, v + d, d, h);
			n = new Face(u + d, v + d, w, h);
			we = new Face(u + d + w, v + d, d, h);
			so = new Face(u + 2 * d + w, v + d, w, h);
			up = new Face(u + d + w, v + d, -w, -d);
			dn = new Face(u + 2 * w + d, v, -w, d);
			if (mirror) {
				Face t = ea;
				ea = flipU(we);
				we = flipU(t);
				n = flipU(n);
				so = flipU(so);
				up = flipU(up);
				dn = flipU(dn);
			}
		}
		return new Cube(o[0] - inflate, o[1] - inflate, o[2] - inflate, o[0] + s[0] + inflate, o[1] + s[1] + inflate, o[2] + s[2] + inflate,
				pivot[0], pivot[1], pivot[2], rot[0], rot[1], rot[2], n, so, ea, we, up, dn);
	}
	private static Face flipU(Face f) {
		return new Face(f.u + f.w, f.v, -f.w, f.h);
	}
	private static Face face(JsonObject faces, String name) {
		if (!faces.has(name)) {
			return null;
		}
		JsonObject f = faces.getAsJsonObject(name);
		float[] uv = vec(f.get("uv"), 0);
		float[] size = vec(f.get("uv_size"), 0);
		return new Face(uv[0], uv[1], size[0], size[1]);
	}

	static float[] vec(JsonElement e, float fallback) {
		float[] out = {fallback, fallback, fallback};
		if (e != null && e.isJsonArray()) {
			JsonArray a = e.getAsJsonArray();
			for (int i = 0; i < Math.min(3, a.size()); i++) {
				out[i] = a.get(i).getAsFloat();
			}
		}
		return out;
	}
	public int bone(String name) {
		return byName.getOrDefault(name, -1);
	}
	public int boneCount() {
		return bones.size();
	}
	public String boneName(int i) {
		return bones.get(i).name;
	}
	public float[] pivot(int i) {
		Bone b = bones.get(i);
		return new float[] {b.px, b.py, b.pz};
	}

	static void rotate(PoseStack pose, float rx, float ry, float rz) {
		if (rz != 0) {
			pose.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(-rz)));
		}
		if (ry != 0) {
			pose.mulPose(new Quaternionf().rotationY((float) Math.toRadians(ry)));
		}
		if (rx != 0) {
			pose.mulPose(new Quaternionf().rotationX((float) Math.toRadians(-rx)));
		}
	}
	private void boneTransform(PoseStack pose, Bone b, Pose anim) {
		float s = 1 / 16f;
		if (anim != null) {
			pose.translate(anim.x * s, anim.y * s, anim.z * s);
		}
		pose.translate(b.px * s, b.py * s, b.pz * s);
		rotate(pose, b.rx + (anim == null ? 0 : anim.rx), b.ry + (anim == null ? 0 : anim.ry), b.rz + (anim == null ? 0 : anim.rz));
		if (anim != null && (anim.sx != 1 || anim.sy != 1 || anim.sz != 1)) {
			pose.scale(anim.sx, anim.sy, anim.sz);
		}
		pose.translate(-b.px * s, -b.py * s, -b.pz * s);
	}
	public void transformTo(PoseStack pose, int index, Pose[] anim) {
		List<Integer> chain = new ArrayList<>();
		for (int i = index; i >= 0; i = bones.get(i).parent) {
			chain.add(0, i);
		}
		for (int i : chain) {
			boneTransform(pose, bones.get(i), anim == null ? null : anim[i]);
		}
	}
	public record Posed(org.joml.Matrix4f[] pose, org.joml.Matrix3f[] normal) {
	}
	public Posed pose(Pose[] anim) {
		org.joml.Matrix4f[] m = new org.joml.Matrix4f[bones.size()];
		org.joml.Matrix3f[] n = new org.joml.Matrix3f[bones.size()];
		PoseStack ps = new PoseStack();
		for (int r : roots) {
			poseBone(ps, r, anim, m, n);
		}
		return new Posed(m, n);
	}
	private void poseBone(PoseStack ps, int i, Pose[] anim, org.joml.Matrix4f[] m, org.joml.Matrix3f[] n) {
		Bone b = bones.get(i);
		ps.pushPose();
		boneTransform(ps, b, anim == null ? null : anim[i]);
		m[i] = new org.joml.Matrix4f(ps.last().pose());
		n[i] = new org.joml.Matrix3f(ps.last().normal());
		for (int child : b.children) {
			poseBone(ps, child, anim, m, n);
		}
		ps.popPose();
	}
	public void draw(PoseStack.Pose base, Posed posed, VertexConsumer out, int light, int overlay, Predicate<String> skip) {
		draw(base, posed, out, light, overlay, skip, false);
	}
	public void draw(PoseStack.Pose base, Posed posed, VertexConsumer out, int light, int overlay, Predicate<String> skip,
			boolean local) {
		PoseStack ps = new PoseStack();
		for (int r : roots) {
			drawBone(ps, base, posed, r, out, light, overlay, skip, local);
		}
	}
	private void drawBone(PoseStack ps, PoseStack.Pose base, Posed posed, int i, VertexConsumer out, int light, int overlay,
			Predicate<String> skip, boolean local) {
		Bone b = bones.get(i);
		if (skip != null && skip.test(b.name)) {
			return;
		}
		if (!b.cubes.isEmpty()) {
			at(ps, base, posed, i);
			for (Cube c : b.cubes) {
				cube(ps, out, c, light, overlay, local);
			}
		}
		for (int child : b.children) {
			drawBone(ps, base, posed, child, out, light, overlay, skip, local);
		}
	}

	public static void at(PoseStack ps, PoseStack.Pose base, Posed posed, int i) {
		ps.last().pose().set(base.pose()).mul(posed.pose[i]);
		ps.last().normal().set(base.normal()).mul(posed.normal[i]);
	}
	public static void drawBox(PoseStack pose, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1,
			int u, int v, float texW, float texH, int light, int overlay) {
		JsonObject c = new JsonObject();
		JsonArray origin = new JsonArray();
		origin.add(x0);
		origin.add(y0);
		origin.add(z0);
		JsonArray size = new JsonArray();
		size.add(x1 - x0);
		size.add(y1 - y0);
		size.add(z1 - z0);
		JsonArray uv = new JsonArray();
		uv.add(u);
		uv.add(v);
		c.add("origin", origin);
		c.add("size", size);
		c.add("uv", uv);
		drawCube(pose, out, c, texW, texH, light, overlay);
	}
	public static void drawCube(PoseStack pose, VertexConsumer out, JsonObject c, float texW, float texH, int light, int overlay) {
		new BedrockModel(texW, texH).cube(pose, out, cube(c, -1), light, overlay, false);
	}
	private void cube(PoseStack pose, VertexConsumer out, Cube c, int light, int overlay, boolean local) {
		float s = 1 / 16f;
		boolean turned = c.rx != 0 || c.ry != 0 || c.rz != 0;
		if (turned) {
			pose.pushPose();
			pose.translate(c.px * s, c.py * s, c.pz * s);
			rotate(pose, c.rx, c.ry, c.rz);
			pose.translate(-c.px * s, -c.py * s, -c.pz * s);
		}
		PoseStack.Pose p = pose.last();
		float x0 = c.x0 * s, y0 = c.y0 * s, z0 = c.z0 * s, x1 = c.x1 * s, y1 = c.y1 * s, z1 = c.z1 * s;

		quad(p, out, c.north, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, 0, 0, -1, light, overlay, local);
		quad(p, out, c.south, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, 0, 0, 1, light, overlay, local);
		quad(p, out, c.east, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, 1, 0, 0, light, overlay, local);
		quad(p, out, c.west, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, -1, 0, 0, light, overlay, local);
		quad(p, out, c.up, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, light, overlay, local);
		quad(p, out, c.down, x1, y0, z0, x0, y0, z0, x0, y0, z1, x1, y0, z1, 0, -1, 0, light, overlay, local);
		if (turned) {
			pose.popPose();
		}
	}
	private void quad(PoseStack.Pose p, VertexConsumer out, Face f, float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz, int light, int overlay,
			boolean local) {
		if (f == null || f.w == 0 && f.h == 0) {
			return;
		}
		float u0 = f.u / textureWidth, v0 = f.v / textureHeight;
		float u1 = (f.u + f.w) / textureWidth, v1 = (f.v + f.h) / textureHeight;
		vertex(p, out, ax, ay, az, u0, v0, nx, ny, nz, light, overlay, local);
		vertex(p, out, bx, by, bz, u1, v0, nx, ny, nz, light, overlay, local);
		vertex(p, out, cx, cy, cz, u1, v1, nx, ny, nz, light, overlay, local);
		vertex(p, out, dx, dy, dz, u0, v1, nx, ny, nz, light, overlay, local);
	}
	private static void vertex(PoseStack.Pose p, VertexConsumer out, float x, float y, float z, float u, float v, float nx, float ny,
			float nz, int light, int overlay, boolean local) {
		int color = local ? 0xFF000000 | localStep(x) << 16 | localStep(y) << 8 | localStep(z) : 0xFFFFFFFF;
		out.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
	}
	private static int localStep(float blocks) {
		return Math.clamp(Math.round(blocks * 32 + 128), 0, 255);
	}
}
