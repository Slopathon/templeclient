package nz.temple.client.render.vrm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class JigglePaint {
	static final float MAX_MASS = 0.05f;
	final float[][] weights;
	final float[][] mass;
	final int[][] point;
	final int points;
	private final int[] memberStart;
	private final int[] memberSet;
	private final int[] memberVertex;
	final float[] pointNormal;
	public float stiffness = 0.45f;
	public float damping = 0.35f;
	public float amount = 1;
	private int version;
	private boolean hasJiggle;
	private boolean hasMass;
	public JigglePaint(VrmAvatar avatar) {
		VrmModel m = avatar.model;
		int sets = m.sets.size();
		weights = new float[sets][];
		mass = new float[sets][];
		point = new int[sets][];
		Matrix4f[] rest = m.restWorld();
		Map<Long, Integer> ids = new HashMap<>();
		Vector3f p = new Vector3f();
		int total = 0;
		for (int s = 0; s < sets; s++) {
			VrmModel.VertexSet set = m.sets.get(s);
			int count = set.pos.length / 3;
			weights[s] = new float[count];
			mass[s] = new float[count];
			point[s] = new int[count];
			Matrix4f[] joints = new Matrix4f[set.skinNodes.length];
			for (int j = 0; j < joints.length; j++) {
				joints[j] = new Matrix4f(rest[set.skinNodes[j]]).mul(set.inverseBind[j]);
			}
			for (int v = 0; v < count; v++) {
				joints[Math.min(set.joints[v * 4], joints.length - 1)].transformPosition(set.pos[v * 3], set.pos[v * 3 + 1], set.pos[v * 3 + 2], p);
				long key = (Math.round(p.x * 1e4) & 0x1FFFFFL) << 42 | (Math.round(p.y * 1e4) & 0x1FFFFFL) << 21 | (Math.round(p.z * 1e4) & 0x1FFFFFL);
				Integer id = ids.get(key);
				if (id == null) {
					id = ids.size();
					ids.put(key, id);
				}
				point[s][v] = id;
				total++;
			}
		}
		points = ids.size();
		int[] counts = new int[points + 1];
		for (int[] ps : point) {
			for (int id : ps) {
				counts[id + 1]++;
			}
		}
		for (int i = 0; i < points; i++) {
			counts[i + 1] += counts[i];
		}
		memberStart = counts.clone();
		memberSet = new int[total];
		memberVertex = new int[total];
		int[] fill = counts.clone();
		pointNormal = new float[points * 3];
		Vector3f n = new Vector3f();
		for (int s = 0; s < sets; s++) {
			VrmModel.VertexSet set = m.sets.get(s);
			for (int v = 0; v < point[s].length; v++) {
				int id = point[s][v];
				memberSet[fill[id]] = s;
				memberVertex[fill[id]++] = v;
				Matrix4f j = new Matrix4f(rest[set.skinNodes[Math.min(set.joints[v * 4], set.skinNodes.length - 1)]])
						.mul(set.inverseBind[Math.min(set.joints[v * 4], set.skinNodes.length - 1)]);
				j.transformDirection(set.nrm[v * 3], set.nrm[v * 3 + 1], set.nrm[v * 3 + 2], n);
				pointNormal[id * 3] += n.x;
				pointNormal[id * 3 + 1] += n.y;
				pointNormal[id * 3 + 2] += n.z;
			}
		}
		for (int i = 0; i < points; i++) {
			float len = (float) Math.sqrt(pointNormal[i * 3] * pointNormal[i * 3] + pointNormal[i * 3 + 1] * pointNormal[i * 3 + 1]
					+ pointNormal[i * 3 + 2] * pointNormal[i * 3 + 2]);
			if (len > 1e-6f) {
				pointNormal[i * 3] /= len;
				pointNormal[i * 3 + 1] /= len;
				pointNormal[i * 3 + 2] /= len;
			}
		}
	}
	int version() {
		return version;
	}
	public boolean hasJiggle() {
		return hasJiggle;
	}
	public boolean hasMass() {
		return hasMass;
	}
	int firstSet(int id) {
		return memberSet[memberStart[id]];
	}
	int firstVertex(int id) {
		return memberVertex[memberStart[id]];
	}
	interface CopyVisitor {
		void visit(int set, int vertex);
	}

	void forEachCopy(int id, CopyVisitor visitor) {
		for (int k = memberStart[id]; k < memberStart[id + 1]; k++) {
			visitor.visit(memberSet[k], memberVertex[k]);
		}
	}

	void setPoint(float[][] values, int id, float value) {
		for (int k = memberStart[id]; k < memberStart[id + 1]; k++) {
			values[memberSet[k]][memberVertex[k]] = value;
		}
	}

	void changed() {
		version++;
		hasJiggle = any(weights);
		hasMass = any(mass);
	}
	private static boolean any(float[][] values) {
		for (float[] w : values) {
			for (float v : w) {
				if (v > 0) {
					return true;
				}
			}
		}
		return false;
	}
	private void weld(float[][] values) {
		for (int id = 0; id < points; id++) {
			float max = 0;
			for (int k = memberStart[id]; k < memberStart[id + 1]; k++) {
				max = Math.max(max, values[memberSet[k]][memberVertex[k]]);
			}
			setPoint(values, id, max);
		}
	}
	public void clear() {
		for (float[] w : weights) {
			java.util.Arrays.fill(w, 0);
		}
		changed();
	}
	public void clearMass() {
		for (float[] w : mass) {
			java.util.Arrays.fill(w, 0);
		}
		changed();
	}
	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("stiffness", stiffness);
		o.addProperty("damping", damping);
		o.addProperty("amount", amount);
		o.add("sets", write(weights));
		o.add("mass", write(mass));
		return o;
	}
	private static JsonArray write(float[][] values) {
		JsonArray sets = new JsonArray();
		for (float[] w : values) {
			byte[] raw = new byte[w.length];
			for (int i = 0; i < w.length; i++) {
				raw[i] = (byte) Math.round(Math.max(0, Math.min(1, w[i])) * 255);
			}
			Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
			d.setInput(raw);
			d.finish();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			while (!d.finished()) {
				out.write(buf, 0, d.deflate(buf));
			}
			d.end();
			JsonObject set = new JsonObject();
			set.addProperty("count", w.length);
			set.addProperty("data", Base64.getEncoder().encodeToString(out.toByteArray()));
			sets.add(set);
		}
		return sets;
	}
	public void fromJson(JsonObject o) {
		stiffness = num(o, "stiffness", stiffness);
		damping = num(o, "damping", damping);
		amount = num(o, "amount", amount);
		read(o, "sets", weights);
		read(o, "mass", mass);
		weld(weights);
		weld(mass);
		changed();
	}
	private static void read(JsonObject o, String key, float[][] values) {
		if (!o.has(key) || !o.get(key).isJsonArray()) {
			return;
		}
		JsonArray sets = o.getAsJsonArray(key);
		for (int s = 0; s < Math.min(sets.size(), values.length); s++) {
			JsonObject set = sets.get(s).getAsJsonObject();
			if (!set.has("count") || set.get("count").getAsInt() != values[s].length) {
				continue;
			}
			try {
				Inflater in = new Inflater();
				in.setInput(Base64.getDecoder().decode(set.get("data").getAsString()));
				byte[] raw = new byte[values[s].length];
				int at = 0;
				while (at < raw.length && !in.finished()) {
					int n = in.inflate(raw, at, raw.length - at);
					if (n == 0 && (in.needsInput() || in.needsDictionary())) {
						break;
					}
					at += n;
				}
				in.end();
				for (int i = 0; i < raw.length; i++) {
					values[s][i] = (raw[i] & 0xFF) / 255f;
				}
			} catch (DataFormatException | IllegalArgumentException e) {
				java.util.Arrays.fill(values[s], 0);
			}
		}
	}
	private static float num(JsonObject o, String key, float fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? Math.max(0, Math.min(2, e.getAsFloat())) : fallback;
	}
}
