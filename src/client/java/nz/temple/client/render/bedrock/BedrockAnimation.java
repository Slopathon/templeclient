package nz.temple.client.render.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BedrockAnimation {
	record Key(float time, Value pre, Value post, boolean smooth) {
		Key(float time, float[] pre, float[] post, boolean smooth) {
			this(time, new Value(pre, null), new Value(post, null), smooth);
		}
	}
	record Value(float[] fixed, Molang.Expr[] expr) {
		float[] at(float t) {
			if (expr == null) {
				return fixed;
			}
			float[] out = new float[3];
			for (int i = 0; i < 3; i++) {
				out[i] = expr[i] != null ? expr[i].eval(t) : fixed[i];
			}
			return out;
		}
	}
	static final class Channel {
		final List<Key> keys = new ArrayList<>();
		float[] sample(float t) {
			if (keys.size() == 1 || t <= keys.get(0).time) {
				return keys.get(0).pre.at(t);
			}
			Key last = keys.get(keys.size() - 1);
			if (t >= last.time) {
				return last.post.at(t);
			}
			int i = 1;
			while (keys.get(i).time < t) {
				i++;
			}
			Key a = keys.get(i - 1);
			Key b = keys.get(i);
			float f = (t - a.time) / Math.max(1e-6f, b.time - a.time);
			float[] out = new float[3];
			float[] ap = a.post.at(t);
			float[] bp = b.pre.at(t);
			if (a.smooth || b.smooth) {
				float[] p0 = (i >= 2 ? keys.get(i - 2) : a).post.at(t);
				float[] p3 = (i + 1 < keys.size() ? keys.get(i + 1) : b).pre.at(t);
				for (int k = 0; k < 3; k++) {
					out[k] = catmullRom(p0[k], ap[k], bp[k], p3[k], f);
				}
			} else {
				for (int k = 0; k < 3; k++) {
					out[k] = ap[k] + (bp[k] - ap[k]) * f;
				}
			}
			return out;
		}
		private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
			float t2 = t * t;
			float t3 = t2 * t;
			return 0.5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
		}
	}
	public record Sound(float time, String effect) {
	}
	public final String name;
	public final float length;
	public final boolean loop;
	final Map<String, Channel[]> bones = new HashMap<>();
	public final List<Sound> sounds = new ArrayList<>();

	private BedrockAnimation(String name, float length, boolean loop) {
		this.name = name;
		this.length = length;
		this.loop = loop;
	}

	public static Map<String, BedrockAnimation> parseAll(JsonObject root) {
		Map<String, BedrockAnimation> out = new HashMap<>();
		JsonObject all = root.getAsJsonObject("animations");
		for (Map.Entry<String, JsonElement> e : all.entrySet()) {
			out.put(e.getKey(), parse(e.getKey(), e.getValue().getAsJsonObject()));
		}
		return out;
	}
	private static BedrockAnimation parse(String name, JsonObject a) {
		boolean loop = a.has("loop") && (a.get("loop").isJsonPrimitive() && a.get("loop").getAsJsonPrimitive().isBoolean()
				? a.get("loop").getAsBoolean() : "loop".equals(a.get("loop").getAsString()));
		Map<String, Channel[]> bones = new HashMap<>();
		float longest = 0;
		if (a.has("bones")) {
			for (Map.Entry<String, JsonElement> b : a.getAsJsonObject("bones").entrySet()) {
				JsonObject ch = b.getValue().getAsJsonObject();
				Channel[] c = {channel(ch.get("rotation"), 0), channel(ch.get("position"), 0), channel(ch.get("scale"), 1)};
				for (Channel x : c) {
					if (x != null && !x.keys.isEmpty()) {
						longest = Math.max(longest, x.keys.get(x.keys.size() - 1).time);
					}
				}
				bones.put(b.getKey(), c);
			}
		}
		float length = a.has("animation_length") ? a.get("animation_length").getAsFloat() : longest;
		BedrockAnimation anim = new BedrockAnimation(name, length, loop);
		anim.bones.putAll(bones);
		if (a.has("sound_effects")) {
			for (Map.Entry<String, JsonElement> s : a.getAsJsonObject("sound_effects").entrySet()) {
				JsonElement v = s.getValue();
				JsonObject first = v.isJsonArray() ? v.getAsJsonArray().get(0).getAsJsonObject() : v.getAsJsonObject();
				if (first.has("effect")) {
					anim.sounds.add(new Sound(Float.parseFloat(s.getKey()), first.get("effect").getAsString()));
				}
			}
		}
		anim.sounds.sort(java.util.Comparator.comparingDouble(Sound::time));
		return anim;
	}
	private static Channel channel(JsonElement e, float rest) {
		if (e == null) {
			return null;
		}
		Channel c = new Channel();
		if (e.isJsonArray() || e.isJsonPrimitive()) {
			Value v = value(e, rest);
			c.keys.add(new Key(0, v, v, false));
			return c;
		}
		List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(e.getAsJsonObject().entrySet());
		entries.sort((x, y) -> Float.compare(Float.parseFloat(x.getKey()), Float.parseFloat(y.getKey())));
		for (Map.Entry<String, JsonElement> k : entries) {
			float t = Float.parseFloat(k.getKey());
			JsonElement v = k.getValue();
			if (v.isJsonObject()) {
				JsonObject o = v.getAsJsonObject();
				Value post = o.has("post") ? value(o.get("post"), rest) : value(o.get("pre"), rest);
				Value pre = o.has("pre") ? value(o.get("pre"), rest) : post;
				boolean smooth = o.has("lerp_mode") && "catmullrom".equals(o.get("lerp_mode").getAsString());
				c.keys.add(new Key(t, pre, post, smooth));
			} else {
				Value val = value(v, rest);
				c.keys.add(new Key(t, val, val, false));
			}
		}
		return c;
	}
	private static Value value(JsonElement e, float rest) {
		if (e == null) {
			return new Value(new float[] {rest, rest, rest}, null);
		}
		JsonElement[] parts = e.isJsonArray()
				? new JsonElement[] {e.getAsJsonArray().get(0), e.getAsJsonArray().get(Math.min(1, e.getAsJsonArray().size() - 1)),
						e.getAsJsonArray().get(Math.min(2, e.getAsJsonArray().size() - 1))}
				: new JsonElement[] {e, e, e};
		float[] fixed = new float[3];
		Molang.Expr[] expr = null;
		for (int i = 0; i < 3; i++) {
			try {
				fixed[i] = parts[i].getAsFloat();
			} catch (NumberFormatException | UnsupportedOperationException ex) {
				fixed[i] = rest;
				String text = parts[i].isJsonPrimitive() ? parts[i].getAsString().trim() : "";
				if (!text.isEmpty()) {
					if (expr == null) {
						expr = new Molang.Expr[3];
					}
					expr[i] = Molang.compile(text, rest);
				}
			}
		}
		return new Value(fixed, expr);
	}
	public void apply(BedrockModel model, float t, float weight, BedrockModel.Pose[] out) {
		if (weight <= 0) {
			return;
		}
		for (Map.Entry<String, Channel[]> e : bones.entrySet()) {
			int i = model.bone(e.getKey());
			if (i < 0) {
				continue;
			}
			BedrockModel.Pose p = out[i];
			Channel[] c = e.getValue();
			if (c[0] != null) {
				float[] r = c[0].sample(t);
				p.rx += r[0] * weight;
				p.ry += r[1] * weight;
				p.rz += r[2] * weight;
			}
			if (c[1] != null) {
				float[] v = c[1].sample(t);
				p.x += v[0] * weight;
				p.y += v[1] * weight;
				p.z += v[2] * weight;
			}
			if (c[2] != null) {
				float[] s = c[2].sample(t);
				p.sx *= 1 + (s[0] - 1) * weight;
				p.sy *= 1 + (s[1] - 1) * weight;
				p.sz *= 1 + (s[2] - 1) * weight;
			}
		}
	}
	public boolean moves(String name) {
		return bones.containsKey(name);
	}
}
