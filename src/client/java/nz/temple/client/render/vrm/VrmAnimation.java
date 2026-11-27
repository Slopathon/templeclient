package nz.temple.client.render.vrm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class VrmAnimation {
	private record Track(float[] times, float[] values, boolean step) {
	}
	private final Map<String, Track> rotations = new HashMap<>();
	private Track hips;
	private float hipsHeight = 1;
	public final float length;
	private VrmAnimation(Glb g) throws IOException {
		JsonObject ext = g.json.has("extensions") ? g.json.getAsJsonObject("extensions") : null;
		if (ext == null || !ext.has("VRMC_vrm_animation")) {
			throw new IOException("not a VRM animation");
		}
		JsonObject bones = ext.getAsJsonObject("VRMC_vrm_animation").getAsJsonObject("humanoid").getAsJsonObject("humanBones");
		Map<Integer, String> boneOf = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : bones.entrySet()) {
			boneOf.put(e.getValue().getAsJsonObject().get("node").getAsInt(), e.getKey());
		}
		int n = g.array("nodes").size();
		int[] parent = new int[n];
		java.util.Arrays.fill(parent, -1);
		Quaternionf[] restR = new Quaternionf[n];
		Vector3f[] restT = new Vector3f[n];
		for (int i = 0; i < n; i++) {
			JsonObject node = g.get("nodes", i);
			restR[i] = node.has("rotation") ? quat(node.getAsJsonArray("rotation")) : new Quaternionf();
			restT[i] = node.has("translation") ? new Vector3f(node.getAsJsonArray("translation").get(0).getAsFloat(),
					node.getAsJsonArray("translation").get(1).getAsFloat(), node.getAsJsonArray("translation").get(2).getAsFloat()) : new Vector3f();
			if (node.has("children")) {
				for (JsonElement c : node.getAsJsonArray("children")) {
					parent[c.getAsInt()] = i;
				}
			}
		}
		Quaternionf[] world = new Quaternionf[n];
		Vector3f[] worldT = new Vector3f[n];
		for (int i = 0; i < n; i++) {
			world(i, parent, restR, restT, world, worldT);
		}
		float longest = 0;
		if (g.array("animations").isEmpty()) {
			throw new IOException("VRM animation has no animation");
		}
		JsonObject anim = g.array("animations").get(0).getAsJsonObject();
		for (JsonElement ce : anim.getAsJsonArray("channels")) {
			JsonObject ch = ce.getAsJsonObject();
			JsonObject target = ch.getAsJsonObject("target");
			if (!target.has("node")) {
				continue;
			}
			String bone = boneOf.get(target.get("node").getAsInt());
			if (bone == null) {
				continue;
			}
			JsonObject sampler = anim.getAsJsonArray("samplers").get(ch.get("sampler").getAsInt()).getAsJsonObject();
			float[] times = g.floats(sampler.get("input").getAsInt());
			float[] values = g.floats(sampler.get("output").getAsInt());
			boolean step = "STEP".equals(sampler.has("interpolation") ? sampler.get("interpolation").getAsString() : "LINEAR");
			if ("CUBICSPLINE".equals(sampler.has("interpolation") ? sampler.get("interpolation").getAsString() : "")) {
				values = middles(values, times.length, target.get("path").getAsString().equals("rotation") ? 4 : 3);
			}
			if (times.length > 0) {
				longest = Math.max(longest, times[times.length - 1]);
			}
			int node = target.get("node").getAsInt();
			String path = target.get("path").getAsString();
			if (path.equals("rotation")) {
				Quaternionf parentWorld = parent[node] >= 0 ? world[parent[node]] : new Quaternionf();
				Quaternionf selfInverse = new Quaternionf(world[node]).invert();
				Quaternionf q = new Quaternionf();
				for (int k = 0; k + 3 < values.length; k += 4) {
					q.set(values[k], values[k + 1], values[k + 2], values[k + 3]);
					Quaternionf out = new Quaternionf(parentWorld).mul(q).mul(selfInverse).normalize();
					values[k] = out.x;
					values[k + 1] = out.y;
					values[k + 2] = out.z;
					values[k + 3] = out.w;
				}
				rotations.put(bone, new Track(times, values, step));
			} else if (path.equals("translation") && bone.equals("hips")) {
				hips = new Track(times, values, step);
				hipsHeight = Math.max(0.1f, worldT[node].y);
			}
		}
		length = longest;
	}
	public static VrmAnimation read(byte[] data) throws IOException {
		try {
			return new VrmAnimation(Glb.read(data));
		} catch (RuntimeException e) {
			throw new IOException("broken VRM animation: " + e.getMessage(), e);
		}
	}
	private static Quaternionf quat(com.google.gson.JsonArray a) {
		return new Quaternionf(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat()).normalize();
	}
	private static void world(int i, int[] parent, Quaternionf[] restR, Vector3f[] restT, Quaternionf[] world, Vector3f[] worldT) {
		if (world[i] != null) {
			return;
		}
		if (parent[i] < 0) {
			world[i] = new Quaternionf(restR[i]);
			worldT[i] = new Vector3f(restT[i]);
			return;
		}
		world(parent[i], parent, restR, restT, world, worldT);
		world[i] = new Quaternionf(world[parent[i]]).mul(restR[i]);
		worldT[i] = world[parent[i]].transform(new Vector3f(restT[i])).add(worldT[parent[i]]);
	}
	private static float[] middles(float[] values, int keys, int width) {
		float[] out = new float[keys * width];
		for (int k = 0; k < keys; k++) {
			System.arraycopy(values, (k * 3 + 1) * width, out, k * width, width);
		}
		return out;
	}
	boolean rotation(String bone, float t, Quaternionf out) {
		Track tr = rotations.get(bone);
		if (tr == null || tr.times.length == 0) {
			return false;
		}
		int k = key(tr, t);
		float[] v = tr.values;
		Quaternionf a = new Quaternionf(v[k * 4], v[k * 4 + 1], v[k * 4 + 2], v[k * 4 + 3]);
		if (tr.step || k + 1 >= tr.times.length) {
			out.set(a);
			return true;
		}
		Quaternionf b = new Quaternionf(v[k * 4 + 4], v[k * 4 + 5], v[k * 4 + 6], v[k * 4 + 7]);
		float f = (t - tr.times[k]) / Math.max(1e-6f, tr.times[k + 1] - tr.times[k]);
		a.slerp(b, Math.max(0, Math.min(1, f)), out);
		return true;
	}
	boolean hipsOffset(float t, Vector3f out) {
		if (hips == null || hips.times.length == 0) {
			return false;
		}
		int k = key(hips, t);
		float[] v = hips.values;
		out.set(v[k * 3], v[k * 3 + 1], v[k * 3 + 2]);
		if (!hips.step && k + 1 < hips.times.length) {
			float f = Math.max(0, Math.min(1, (t - hips.times[k]) / Math.max(1e-6f, hips.times[k + 1] - hips.times[k])));
			out.lerp(new Vector3f(v[k * 3 + 3], v[k * 3 + 4], v[k * 3 + 5]), f);
		}
		out.y -= hipsHeight;
		out.div(hipsHeight);
		return true;
	}
	private static int key(Track tr, float t) {
		int k = 0;
		while (k + 1 < tr.times.length && tr.times[k + 1] <= t) {
			k++;
		}
		return k;
	}
}
