package nz.temple.client.render.vrm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VrmModel {
	static final class VertexSet {
		float[] pos;
		float[] nrm;
		float[] uv;
		int[] joints;
		float[] weights;
		int[] skinNodes;
		Matrix4f[] inverseBind;
		float[] blink;
	}
	record Prim(int set, int[] quads, int material) {
	}

	static int[] quads(int[] tris) {
		int count = tris.length / 3;
		Map<Long, Integer> edges = new HashMap<>(count * 4);
		for (int t = 0; t < count; t++) {
			for (int e = 0; e < 3; e++) {
				edges.put(edge(tris[t * 3 + e], tris[t * 3 + (e + 1) % 3]), t);
			}
		}
		boolean[] used = new boolean[count];
		int[] out = new int[count * 4];
		int n = 0;
		for (int t = 0; t < count; t++) {
			if (used[t]) {
				continue;
			}
			used[t] = true;
			boolean paired = false;
			for (int r = 0; r < 3 && !paired; r++) {
				int a = tris[t * 3 + r], b = tris[t * 3 + (r + 1) % 3], c = tris[t * 3 + (r + 2) % 3];
				Integer u = edges.get(edge(a, c));
				if (u == null || used[u]) {
					continue;
				}
				int d = -1;
				for (int k = 0; k < 3; k++) {
					int x = tris[u * 3 + k];
					if (x != a && x != c) {
						d = x;
					}
				}
				if (d < 0 || d == b) {
					continue;
				}
				used[u] = true;
				out[n++] = a;
				out[n++] = b;
				out[n++] = c;
				out[n++] = d;
				paired = true;
			}
			if (!paired) {
				out[n++] = tris[t * 3];
				out[n++] = tris[t * 3 + 1];
				out[n++] = tris[t * 3 + 2];
				out[n++] = tris[t * 3 + 2];
			}
		}
		return java.util.Arrays.copyOf(out, n);
	}
	private static long edge(int from, int to) {
		return ((long) from << 32) | (to & 0xFFFFFFFFL);
	}
	record Material(int image, int color, int mode, boolean doubleSided, int queue, float outline) {
	}
	static final class SpringJoint {
		int node;

		Vector3f axis;
		float length;
		float stiffness;
		float gravityPower;
		Vector3f gravityDir;
		float drag;
		float hitRadius;
		int[] colliderGroups;
	}
	record Collider(int node, Vector3f offset, float radius) {
	}
	final boolean vrm1;
	final int nodeCount;
	final String[] nodeName;
	final int[] parent;
	final int[] order;
	final Vector3f[] restT;
	final Quaternionf[] restR;
	final Vector3f[] restS;
	final Quaternionf[] restWorldR;
	final Map<String, Integer> human = new HashMap<>();
	final List<VertexSet> sets = new ArrayList<>();
	final List<Prim> prims = new ArrayList<>();
	final List<Material> materials = new ArrayList<>();
	NativeImage[] images;
	int[][] previewPixels;
	int[] previewW;
	int[] previewH;
	final List<SpringJoint> springs = new ArrayList<>();
	final List<List<Collider>> colliderGroups = new ArrayList<>();
	final float height;
	final float eyeHeight;
	final float soleY;
	public final String title;
	public final String author;
	public final String license;
	private VrmModel(Glb g, int maxTexture) throws IOException {
		JsonObject ext = g.json.has("extensions") ? g.json.getAsJsonObject("extensions") : new JsonObject();
		vrm1 = ext.has("VRMC_vrm");
		if (!vrm1 && !ext.has("VRM")) {
			throw new IOException("not a VRM avatar (no VRM extension)");
		}
		JsonArray nodes = g.array("nodes");
		nodeCount = nodes.size();
		nodeName = new String[nodeCount];
		parent = new int[nodeCount];
		restT = new Vector3f[nodeCount];
		restR = new Quaternionf[nodeCount];
		restS = new Vector3f[nodeCount];
		java.util.Arrays.fill(parent, -1);
		for (int i = 0; i < nodeCount; i++) {
			JsonObject n = nodes.get(i).getAsJsonObject();
			nodeName[i] = n.has("name") ? n.get("name").getAsString() : "node" + i;
			float[] t = vec(n, "translation", 3, 0);
			float[] r = vec(n, "rotation", 4, 0);
			float[] s = vec(n, "scale", 3, 1);
			if (n.has("matrix")) {
				Matrix4f m = new Matrix4f().set(vec(n, "matrix", 16, 0));
				Vector3f tt = m.getTranslation(new Vector3f());
				Quaternionf rr = m.getUnnormalizedRotation(new Quaternionf()).normalize();
				Vector3f ss = m.getScale(new Vector3f());
				t = new float[] {tt.x, tt.y, tt.z};
				r = new float[] {rr.x, rr.y, rr.z, rr.w};
				s = new float[] {ss.x, ss.y, ss.z};
			} else if (!n.has("rotation")) {
				r[3] = 1;
			}
			restT[i] = new Vector3f(t[0], t[1], t[2]);
			restR[i] = new Quaternionf(r[0], r[1], r[2], r[3]).normalize();
			restS[i] = new Vector3f(s[0], s[1], s[2]);
			if (n.has("children")) {
				for (JsonElement c : n.getAsJsonArray("children")) {
					parent[c.getAsInt()] = i;
				}
			}
		}
		order = topological();
		restWorldR = new Quaternionf[nodeCount];
		for (int i : order) {
			restWorldR[i] = parent[i] < 0 ? new Quaternionf(restR[i]) : new Quaternionf(restWorldR[parent[i]]).mul(restR[i]);
		}
		readHumanoid(ext);
		readMaterials(g, ext);
		readMeshes(g, ext);
		images = readImages(g, maxTexture);
		readSprings(ext);
		height = measureHeight();
		soleY = measureSoles();
		eyeHeight = measureEyes(ext) - soleY;
		JsonObject meta = vrm1 ? ext.getAsJsonObject("VRMC_vrm").getAsJsonObject("meta") : ext.getAsJsonObject("VRM").getAsJsonObject("meta");
		title = text(meta, vrm1 ? "name" : "title");
		author = vrm1 ? (meta != null && meta.has("authors") ? meta.getAsJsonArray("authors").get(0).getAsString() : "") : text(meta, "author");
		license = vrm1 ? text(meta, "licenseUrl") : text(meta, "licenseName");
	}
	public static VrmModel read(byte[] data, int maxTexture) throws IOException {
		try {
			return new VrmModel(Glb.read(data), maxTexture);
		} catch (RuntimeException e) {
			throw new IOException("broken VRM file: " + e.getMessage(), e);
		}
	}
	private static String text(JsonObject o, String key) {
		return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}
	private static float[] vec(JsonObject o, String key, int n, float fill) {
		float[] out = new float[n];
		java.util.Arrays.fill(out, fill);
		if (o != null && o.has(key) && o.get(key).isJsonArray()) {
			JsonArray a = o.getAsJsonArray(key);
			for (int i = 0; i < Math.min(n, a.size()); i++) {
				out[i] = a.get(i).getAsFloat();
			}
		}
		return out;
	}

	private int[] topological() {
		int[] out = new int[nodeCount];
		boolean[] done = new boolean[nodeCount];
		int at = 0;
		while (at < nodeCount) {
			int before = at;
			for (int i = 0; i < nodeCount; i++) {
				if (!done[i] && (parent[i] < 0 || done[parent[i]])) {
					done[i] = true;
					out[at++] = i;
				}
			}
			if (at == before) {
				throw new IllegalStateException("node tree has a loop");
			}
		}
		return out;
	}
	private void readHumanoid(JsonObject ext) {
		if (vrm1) {
			JsonObject bones = ext.getAsJsonObject("VRMC_vrm").getAsJsonObject("humanoid").getAsJsonObject("humanBones");
			for (Map.Entry<String, JsonElement> e : bones.entrySet()) {
				human.put(e.getKey(), e.getValue().getAsJsonObject().get("node").getAsInt());
			}
		} else {
			for (JsonElement e : ext.getAsJsonObject("VRM").getAsJsonObject("humanoid").getAsJsonArray("humanBones")) {
				JsonObject b = e.getAsJsonObject();
				human.put(b.get("bone").getAsString(), b.get("node").getAsInt());
			}
		}
	}
	private void readMaterials(Glb g, JsonObject ext) {
		Map<String, JsonObject> mtoon = new HashMap<>();
		if (!vrm1 && ext.getAsJsonObject("VRM").has("materialProperties")) {
			for (JsonElement e : ext.getAsJsonObject("VRM").getAsJsonArray("materialProperties")) {
				JsonObject p = e.getAsJsonObject();
				mtoon.put(text(p, "name"), p);
			}
		}
		JsonArray textures = g.array("textures");
		for (JsonElement e : g.array("materials")) {
			JsonObject m = e.getAsJsonObject();
			JsonObject pbr = m.has("pbrMetallicRoughness") ? m.getAsJsonObject("pbrMetallicRoughness") : new JsonObject();
			int texture = pbr.has("baseColorTexture") ? pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt() : -1;
			float[] color = vec(pbr, "baseColorFactor", 4, 1);
			String alpha = m.has("alphaMode") ? m.get("alphaMode").getAsString() : "OPAQUE";
			int mode = alpha.equals("BLEND") ? 2 : alpha.equals("MASK") ? 1 : 0;
			boolean doubleSided = m.has("doubleSided") && m.get("doubleSided").getAsBoolean();
			int queue = mode == 2 ? 3000 : 2000;
			float outline = 0;
			JsonObject p = mtoon.get(text(m, "name"));
			if (p != null) {
				JsonObject tp = p.has("textureProperties") ? p.getAsJsonObject("textureProperties") : new JsonObject();
				JsonObject fp = p.has("floatProperties") ? p.getAsJsonObject("floatProperties") : new JsonObject();
				JsonObject vp = p.has("vectorProperties") ? p.getAsJsonObject("vectorProperties") : new JsonObject();
				if (tp.has("_MainTex")) {
					texture = tp.get("_MainTex").getAsInt();
				}
				if (vp.has("_Color")) {
					color = vec(vp, "_Color", 4, 1);
				}
				if (fp.has("_BlendMode")) {
					int b = fp.get("_BlendMode").getAsInt();
					mode = b == 0 ? 0 : b == 1 ? 1 : 2;
				}
				if (fp.has("_CullMode")) {
					doubleSided = fp.get("_CullMode").getAsInt() == 0;
				}
				if (p.has("renderQueue")) {
					queue = p.get("renderQueue").getAsInt();
				}
				if (fp.has("_OutlineWidthMode") && fp.get("_OutlineWidthMode").getAsInt() == 1 && fp.has("_OutlineWidth")) {
					outline = fp.get("_OutlineWidth").getAsFloat() * 0.01f;
				}
			} else if (m.has("extensions") && m.getAsJsonObject("extensions").has("VRMC_materials_mtoon")) {
				JsonObject t = m.getAsJsonObject("extensions").getAsJsonObject("VRMC_materials_mtoon");
				if (t.has("outlineWidthMode") && "worldCoordinates".equals(text(t, "outlineWidthMode")) && t.has("outlineWidthFactor")) {
					outline = t.get("outlineWidthFactor").getAsFloat();
				}
				if (t.has("renderQueueOffsetNumber")) {
					queue += t.get("renderQueueOffsetNumber").getAsInt();
				}
			}
			int image = texture >= 0 && texture < textures.size() && textures.get(texture).getAsJsonObject().has("source")
					? textures.get(texture).getAsJsonObject().get("source").getAsInt() : -1;
			int argb = (Math.round(clamp01(color[3]) * 255) << 24) | (Math.round(clamp01(color[0]) * 255) << 16)
					| (Math.round(clamp01(color[1]) * 255) << 8) | Math.round(clamp01(color[2]) * 255);
			materials.add(new Material(image, argb, mode, doubleSided, queue, outline));
		}
		if (materials.isEmpty()) {
			materials.add(new Material(-1, 0xFFFFFFFF, 0, true, 2000, 0));
		}
	}
	private static float clamp01(float v) {
		return Math.max(0, Math.min(1, v));
	}
	private void readMeshes(Glb g, JsonObject ext) {
		Map<Integer, Map<Integer, Float>> blink = new HashMap<>();
		if (!vrm1 && ext.getAsJsonObject("VRM").has("blendShapeMaster")) {
			for (JsonElement e : ext.getAsJsonObject("VRM").getAsJsonObject("blendShapeMaster").getAsJsonArray("blendShapeGroups")) {
				JsonObject group = e.getAsJsonObject();
				if ("blink".equalsIgnoreCase(text(group, "presetName")) && group.has("binds")) {
					for (JsonElement b : group.getAsJsonArray("binds")) {
						JsonObject bind = b.getAsJsonObject();
						blink.computeIfAbsent(bind.get("mesh").getAsInt(), k -> new HashMap<>())
								.put(bind.get("index").getAsInt(), bind.get("weight").getAsFloat() / 100f);
					}
				}
			}
		} else if (vrm1) {
			JsonObject expressions = ext.getAsJsonObject("VRMC_vrm").getAsJsonObject("expressions");
			JsonObject preset = expressions != null && expressions.has("preset") ? expressions.getAsJsonObject("preset") : null;
			if (preset != null && preset.has("blink") && preset.getAsJsonObject("blink").has("morphTargetBinds")) {
				for (JsonElement b : preset.getAsJsonObject("blink").getAsJsonArray("morphTargetBinds")) {
					JsonObject bind = b.getAsJsonObject();
					int node = bind.get("node").getAsInt();
					JsonObject n = g.get("nodes", node);
					if (n.has("mesh")) {
						blink.computeIfAbsent(n.get("mesh").getAsInt(), k -> new HashMap<>())
								.put(bind.get("index").getAsInt(), bind.get("weight").getAsFloat());
					}
				}
			}
		}
		JsonArray nodes = g.array("nodes");
		for (int ni = 0; ni < nodes.size(); ni++) {
			JsonObject n = nodes.get(ni).getAsJsonObject();
			if (!n.has("mesh")) {
				continue;
			}
			int meshIndex = n.get("mesh").getAsInt();
			JsonObject mesh = g.get("meshes", meshIndex);
			int[] skinNodes;
			Matrix4f[] inverse;
			if (n.has("skin")) {
				JsonObject skin = g.get("skins", n.get("skin").getAsInt());
				JsonArray joints = skin.getAsJsonArray("joints");
				skinNodes = new int[joints.size()];
				for (int j = 0; j < skinNodes.length; j++) {
					skinNodes[j] = joints.get(j).getAsInt();
				}
				inverse = new Matrix4f[skinNodes.length];
				float[] ibm = skin.has("inverseBindMatrices") ? g.floats(skin.get("inverseBindMatrices").getAsInt()) : null;
				for (int j = 0; j < skinNodes.length; j++) {
					inverse[j] = ibm == null ? new Matrix4f() : new Matrix4f().set(java.util.Arrays.copyOfRange(ibm, j * 16, j * 16 + 16));
				}
			} else {
				skinNodes = new int[] {ni};
				inverse = new Matrix4f[] {new Matrix4f()};
			}
			Map<Integer, Integer> setOf = new HashMap<>();
			for (JsonElement pe : mesh.getAsJsonArray("primitives")) {
				JsonObject p = pe.getAsJsonObject();
				int mode = p.has("mode") ? p.get("mode").getAsInt() : 4;
				if (mode != 4 || !p.getAsJsonObject("attributes").has("POSITION")) {
					continue;
				}
				JsonObject attr = p.getAsJsonObject("attributes");
				int position = attr.get("POSITION").getAsInt();
				Integer set = setOf.get(position);
				if (set == null) {
					VertexSet vs = new VertexSet();
					vs.pos = g.floats(position);
					int count = vs.pos.length / 3;
					vs.nrm = attr.has("NORMAL") ? g.floats(attr.get("NORMAL").getAsInt()) : new float[count * 3];
					vs.uv = attr.has("TEXCOORD_0") ? g.floats(attr.get("TEXCOORD_0").getAsInt()) : new float[count * 2];
					if (attr.has("JOINTS_0") && n.has("skin")) {
						vs.joints = g.ints(attr.get("JOINTS_0").getAsInt());
						vs.weights = g.floats(attr.get("WEIGHTS_0").getAsInt());
					} else {
						vs.joints = new int[count * 4];
						vs.weights = new float[count * 4];
						for (int v = 0; v < count; v++) {
							vs.weights[v * 4] = 1;
						}
					}
					vs.skinNodes = skinNodes;
					vs.inverseBind = inverse;
					Map<Integer, Float> binds = blink.get(meshIndex);
					if (binds != null && p.has("targets")) {
						JsonArray targets = p.getAsJsonArray("targets");
						float[] delta = new float[vs.pos.length];
						boolean any = false;
						for (Map.Entry<Integer, Float> b : binds.entrySet()) {
							if (b.getKey() < targets.size() && targets.get(b.getKey()).getAsJsonObject().has("POSITION")) {
								float[] d = g.floats(targets.get(b.getKey()).getAsJsonObject().get("POSITION").getAsInt());
								for (int k = 0; k < Math.min(d.length, delta.length); k++) {
									delta[k] += d[k] * b.getValue();
								}
								any = true;
							}
						}
						vs.blink = any ? delta : null;
					}
					sets.add(vs);
					set = sets.size() - 1;
					setOf.put(position, set);
				}
				int[] indices;
				if (p.has("indices")) {
					indices = g.ints(p.get("indices").getAsInt());
				} else {
					indices = new int[g.count(position)];
					for (int k = 0; k < indices.length; k++) {
						indices[k] = k;
					}
				}
				int material = p.has("material") ? p.get("material").getAsInt() : 0;
				prims.add(new Prim(set, quads(indices), Math.min(material, materials.size() - 1)));
			}
		}
	}
	private NativeImage[] readImages(Glb g, int maxTexture) throws IOException {
		JsonArray list = g.array("images");
		NativeImage[] out = new NativeImage[list.size()];
		previewPixels = new int[list.size()][];
		previewW = new int[list.size()];
		previewH = new int[list.size()];
		boolean[] used = new boolean[list.size()];
		for (Material m : materials) {
			if (m.image() >= 0 && m.image() < used.length) {
				used[m.image()] = true;
			}
		}
		for (int i = 0; i < out.length; i++) {
			JsonObject img = list.get(i).getAsJsonObject();
			if (!used[i] || !img.has("bufferView")) {
				continue;
			}
			NativeImage decoded = NativeImage.read(g.view(img.get("bufferView").getAsInt()));
			out[i] = shrink(decoded, maxTexture);
			int pw = Math.min(256, out[i].getWidth());
			int ph = Math.max(1, Math.round(out[i].getHeight() * (pw / (float) out[i].getWidth())));
			int[] px = new int[pw * ph];
			for (int y = 0; y < ph; y++) {
				for (int x = 0; x < pw; x++) {
					px[y * pw + x] = out[i].getPixel(x * out[i].getWidth() / pw, y * out[i].getHeight() / ph);
				}
			}
			previewPixels[i] = px;
			previewW[i] = pw;
			previewH[i] = ph;
		}
		return out;
	}
	private static NativeImage shrink(NativeImage image, int max) {
		while (Math.max(image.getWidth(), image.getHeight()) > max && image.getWidth() >= 2 && image.getHeight() >= 2) {
			int w = image.getWidth() / 2;
			int h = image.getHeight() / 2;
			NativeImage half = new NativeImage(w, h, false);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int a = 0, r = 0, gg = 0, b = 0;
					for (int k = 0; k < 4; k++) {
						int c = image.getPixel(x * 2 + (k & 1), y * 2 + (k >> 1));
						a += c >>> 24;
						r += (c >> 16) & 0xFF;
						gg += (c >> 8) & 0xFF;
						b += c & 0xFF;
					}
					half.setPixelABGR(x, y, ((a / 4) << 24) | ((b / 4) << 16) | ((gg / 4) << 8) | (r / 4));
				}
			}
			image.close();
			image = half;
		}
		return image;
	}
	private void readSprings(JsonObject ext) {
		if (vrm1) {
			if (!ext.has("VRMC_springBone")) {
				return;
			}
			JsonObject sb = ext.getAsJsonObject("VRMC_springBone");
			List<Collider> all = new ArrayList<>();
			for (JsonElement e : sb.has("colliders") ? sb.getAsJsonArray("colliders") : new JsonArray()) {
				JsonObject c = e.getAsJsonObject();
				JsonObject shape = c.getAsJsonObject("shape");
				JsonObject sphere = shape.has("sphere") ? shape.getAsJsonObject("sphere") : shape.has("capsule") ? shape.getAsJsonObject("capsule") : null;
				float[] o = vec(sphere, "offset", 3, 0);
				all.add(new Collider(c.get("node").getAsInt(), new Vector3f(o[0], o[1], o[2]),
						sphere != null && sphere.has("radius") ? sphere.get("radius").getAsFloat() : 0));
			}
			for (JsonElement e : sb.has("colliderGroups") ? sb.getAsJsonArray("colliderGroups") : new JsonArray()) {
				List<Collider> group = new ArrayList<>();
				for (JsonElement i : e.getAsJsonObject().getAsJsonArray("colliders")) {
					group.add(all.get(i.getAsInt()));
				}
				colliderGroups.add(group);
			}
			for (JsonElement e : sb.has("springs") ? sb.getAsJsonArray("springs") : new JsonArray()) {
				JsonObject spring = e.getAsJsonObject();
				int[] groups = ints(spring, "colliderGroups");
				JsonArray joints = spring.getAsJsonArray("joints");
				for (int j = 0; j < joints.size() - 1; j++) {
					JsonObject jo = joints.get(j).getAsJsonObject();
					int node = jo.get("node").getAsInt();
					int next = joints.get(j + 1).getAsJsonObject().get("node").getAsInt();
					float[] gd = vec(jo, "gravityDir", 3, 0);
					if (!jo.has("gravityDir")) {
						gd[1] = -1;
					}
					addJoint(node, new Vector3f(restT[next]), num(jo, "stiffness", 1), num(jo, "gravityPower", 0), new Vector3f(gd[0], gd[1], gd[2]),
							num(jo, "dragForce", 0.5f), num(jo, "hitRadius", 0), groups);
				}
			}
			return;
		}
		JsonObject sec = ext.getAsJsonObject("VRM").getAsJsonObject("secondaryAnimation");
		if (sec == null) {
			return;
		}
		for (JsonElement e : sec.has("colliderGroups") ? sec.getAsJsonArray("colliderGroups") : new JsonArray()) {
			JsonObject g = e.getAsJsonObject();
			int node = g.get("node").getAsInt();
			List<Collider> group = new ArrayList<>();
			for (JsonElement c : g.getAsJsonArray("colliders")) {
				JsonObject co = c.getAsJsonObject();
				JsonObject off = co.getAsJsonObject("offset");
				group.add(new Collider(node, new Vector3f(num(off, "x", 0), num(off, "y", 0), -num(off, "z", 0)), num(co, "radius", 0)));
			}
			colliderGroups.add(group);
		}
		for (JsonElement e : sec.has("boneGroups") ? sec.getAsJsonArray("boneGroups") : new JsonArray()) {
			JsonObject b = e.getAsJsonObject();
			JsonObject gd = b.has("gravityDir") ? b.getAsJsonObject("gravityDir") : null;
			Vector3f gravity = gd == null ? new Vector3f(0, -1, 0) : new Vector3f(num(gd, "x", 0), num(gd, "y", -1), -num(gd, "z", 0));
			int[] groups = ints(b, "colliderGroups");
			for (JsonElement root : b.getAsJsonArray("bones")) {
				chain(root.getAsInt(), num(b, "stiffiness", 1), num(b, "gravityPower", 0), gravity, num(b, "dragForce", 0.4f),
						num(b, "hitRadius", 0.02f), groups);
			}
		}
	}
	private void chain(int node, float stiffness, float gravityPower, Vector3f gravity, float drag, float hit, int[] groups) {
		int child = -1;
		for (int i = 0; i < nodeCount; i++) {
			if (parent[i] == node) {
				child = i;
				break;
			}
		}
		Vector3f tail;
		if (child >= 0) {
			tail = new Vector3f(restT[child]);
		} else {
			tail = restT[node].lengthSquared() > 1e-8f ? new Vector3f(restT[node]).normalize(0.07f) : new Vector3f(0, -0.07f, 0);
		}
		addJoint(node, tail, stiffness, gravityPower, gravity, drag, hit, groups);
		for (int i = 0; i < nodeCount; i++) {
			if (parent[i] == node) {
				chain(i, stiffness, gravityPower, gravity, drag, hit, groups);
			}
		}
	}
	private void addJoint(int node, Vector3f tail, float stiffness, float gravityPower, Vector3f gravity, float drag, float hit, int[] groups) {
		if (tail.lengthSquared() < 1e-10f) {
			return;
		}
		SpringJoint j = new SpringJoint();
		j.node = node;
		j.length = tail.length();
		j.axis = tail.normalize();
		j.stiffness = stiffness;
		j.gravityPower = gravityPower;
		j.gravityDir = gravity.lengthSquared() > 0 ? gravity.normalize() : new Vector3f(0, -1, 0);
		j.drag = drag;
		j.hitRadius = hit;
		j.colliderGroups = groups;
		springs.add(j);
	}
	private static float num(JsonObject o, String key, float fallback) {
		return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsFloat() : fallback;
	}
	private static int[] ints(JsonObject o, String key) {
		if (o == null || !o.has(key)) {
			return new int[0];
		}
		JsonArray a = o.getAsJsonArray(key);
		int[] out = new int[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsInt();
		}
		return out;
	}

	private float measureHeight() {
		Matrix4f[] world = restWorld();
		float top = 0;
		for (VertexSet s : sets) {
			Matrix4f[] joint = new Matrix4f[s.skinNodes.length];
			for (int j = 0; j < joint.length; j++) {
				joint[j] = new Matrix4f(world[s.skinNodes[j]]).mul(s.inverseBind[j]);
			}
			Vector3f p = new Vector3f();
			for (int v = 0; v < s.pos.length / 3; v += 3) {
				int j = Math.min(s.joints[v * 4], joint.length - 1);
				joint[j].transformPosition(s.pos[v * 3], s.pos[v * 3 + 1], s.pos[v * 3 + 2], p);
				top = Math.max(top, p.y);
			}
		}
		Integer head = human.get("head");
		return top > 0.2f ? top : head != null ? world[head].m31() + 0.15f : 1.6f;
	}
	private float measureSoles() {
		Matrix4f[] world = restWorld();
		float low = Float.MAX_VALUE;
		Vector3f p = new Vector3f();
		for (VertexSet s : sets) {
			Matrix4f[] joint = new Matrix4f[s.skinNodes.length];
			for (int j = 0; j < joint.length; j++) {
				joint[j] = new Matrix4f(world[s.skinNodes[j]]).mul(s.inverseBind[j]);
			}
			for (int v = 0; v < s.pos.length / 3; v++) {
				joint[Math.min(s.joints[v * 4], joint.length - 1)].transformPosition(s.pos[v * 3], s.pos[v * 3 + 1], s.pos[v * 3 + 2], p);
				low = Math.min(low, p.y);
			}
		}
		Integer foot = human.get("leftFoot");
		float ankle = foot != null ? world[foot].m31() : low;
		return low == Float.MAX_VALUE || ankle - low > 0.3f ? Math.min(0, ankle - 0.08f) : low;
	}
	private float measureEyes(JsonObject ext) {
		Matrix4f[] world = restWorld();
		Integer l = human.get("leftEye");
		Integer r = human.get("rightEye");
		if (l != null && r != null) {
			return (world[l].m31() + world[r].m31()) / 2;
		}
		Integer head = human.get("head");
		if (head == null) {
			return height * 0.93f;
		}
		float y = world[head].m31();
		if (vrm1) {
			JsonObject lookAt = ext.getAsJsonObject("VRMC_vrm").getAsJsonObject("lookAt");
			if (lookAt != null && lookAt.has("offsetFromHeadBone")) {
				return y + lookAt.getAsJsonArray("offsetFromHeadBone").get(1).getAsFloat();
			}
		} else {
			JsonObject fp = ext.getAsJsonObject("VRM").getAsJsonObject("firstPerson");
			if (fp != null && fp.has("firstPersonBoneOffset")) {
				return y + num(fp.getAsJsonObject("firstPersonBoneOffset"), "y", 0.06f);
			}
		}
		return y + 0.06f;
	}
	Matrix4f[] restWorld() {
		Matrix4f[] world = new Matrix4f[nodeCount];
		for (int i : order) {
			Matrix4f local = new Matrix4f().translationRotateScale(restT[i], restR[i], restS[i]);
			world[i] = parent[i] < 0 ? local : new Matrix4f(world[parent[i]]).mul(local);
		}
		return world;
	}
	public void closeTextures() {
		if (images != null) {
			for (NativeImage i : images) {
				if (i != null) {
					i.close();
				}
			}
			images = null;
		}
	}
}
