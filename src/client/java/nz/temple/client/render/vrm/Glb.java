package nz.temple.client.render.vrm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class Glb {
	final JsonObject json;
	private final ByteBuffer bin;
	private Glb(JsonObject json, ByteBuffer bin) {
		this.json = json;
		this.bin = bin;
	}
	static Glb read(byte[] data) throws IOException {
		ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		if (data.length < 20 || b.getInt(0) != 0x46546C67) {
			throw new IOException("not a glTF binary file");
		}
		int length = Math.min(b.getInt(8), data.length);
		int at = 12;
		JsonObject json = null;
		ByteBuffer bin = null;
		while (at + 8 <= length) {
			int chunkLength = b.getInt(at);
			int type = b.getInt(at + 4);
			int start = at + 8;
			if (chunkLength < 0 || start + chunkLength > length) {
				throw new IOException("broken glTF chunk");
			}
			if (type == 0x4E4F534A) {
				json = JsonParser.parseString(new String(data, start, chunkLength, StandardCharsets.UTF_8)).getAsJsonObject();
			} else if (type == 0x004E4942) {
				bin = ByteBuffer.wrap(data, start, chunkLength).slice().order(ByteOrder.LITTLE_ENDIAN);
			}
			at = start + ((chunkLength + 3) & ~3);
		}
		if (json == null) {
			throw new IOException("glTF file has no JSON");
		}
		return new Glb(json, bin == null ? ByteBuffer.allocate(0) : bin);
	}
	JsonArray array(String key) {
		return json.has(key) ? json.getAsJsonArray(key) : new JsonArray();
	}
	JsonObject get(String key, int index) {
		return array(key).get(index).getAsJsonObject();
	}
	byte[] view(int index) {
		JsonObject v = get("bufferViews", index);
		int offset = v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0;
		int len = v.get("byteLength").getAsInt();
		byte[] out = new byte[len];
		bin.slice(offset, len).get(out);
		return out;
	}
	int count(int accessor) {
		return get("accessors", accessor).get("count").getAsInt();
	}
	private static int components(String type) {
		return switch (type) {
			case "SCALAR" -> 1;
			case "VEC2" -> 2;
			case "VEC3" -> 3;
			case "VEC4", "MAT2" -> 4;
			case "MAT3" -> 9;
			case "MAT4" -> 16;
			default -> throw new IllegalArgumentException(type);
		};
	}
	float[] floats(int index) {
		JsonObject a = get("accessors", index);
		int n = a.get("count").getAsInt();
		int comps = components(a.get("type").getAsString());
		int type = a.get("componentType").getAsInt();
		boolean norm = a.has("normalized") && a.get("normalized").getAsBoolean();
		float[] out = new float[n * comps];
		if (a.has("bufferView")) {
			read(a.get("bufferView").getAsInt(), a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0, n, comps, type, norm, out, null);
		}
		if (a.has("sparse")) {
			JsonObject s = a.getAsJsonObject("sparse");
			int sc = s.get("count").getAsInt();
			JsonObject idx = s.getAsJsonObject("indices");
			int[] where = new int[sc];
			read(idx.get("bufferView").getAsInt(), idx.has("byteOffset") ? idx.get("byteOffset").getAsInt() : 0, sc, 1,
					idx.get("componentType").getAsInt(), false, null, where);
			JsonObject val = s.getAsJsonObject("values");
			float[] values = new float[sc * comps];
			read(val.get("bufferView").getAsInt(), val.has("byteOffset") ? val.get("byteOffset").getAsInt() : 0, sc, comps, type, norm, values, null);
			for (int i = 0; i < sc; i++) {
				System.arraycopy(values, i * comps, out, where[i] * comps, comps);
			}
		}
		return out;
	}
	int[] ints(int index) {
		JsonObject a = get("accessors", index);
		int n = a.get("count").getAsInt();
		int comps = components(a.get("type").getAsString());
		int[] out = new int[n * comps];
		if (a.has("bufferView")) {
			read(a.get("bufferView").getAsInt(), a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0, n, comps,
					a.get("componentType").getAsInt(), false, null, out);
		}
		return out;
	}
	private void read(int viewIndex, int extra, int n, int comps, int type, boolean norm, float[] f, int[] i) {
		JsonObject v = get("bufferViews", viewIndex);
		int base = (v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0) + extra;
		int size = switch (type) {
			case 5120, 5121 -> 1;
			case 5122, 5123 -> 2;
			default -> 4;
		};
		int stride = v.has("byteStride") ? v.get("byteStride").getAsInt() : size * comps;
		for (int e = 0; e < n; e++) {
			int at = base + e * stride;
			for (int c = 0; c < comps; c++) {
				int p = at + c * size;
				double value;
				switch (type) {
					case 5120 -> value = norm ? Math.max(bin.get(p) / 127.0, -1) : bin.get(p);
					case 5121 -> value = norm ? (bin.get(p) & 0xFF) / 255.0 : bin.get(p) & 0xFF;
					case 5122 -> value = norm ? Math.max(bin.getShort(p) / 32767.0, -1) : bin.getShort(p);
					case 5123 -> value = norm ? (bin.getShort(p) & 0xFFFF) / 65535.0 : bin.getShort(p) & 0xFFFF;
					case 5125 -> value = bin.getInt(p) & 0xFFFFFFFFL;
					default -> value = bin.getFloat(p);
				}
				if (f != null) {
					f[e * comps + c] = (float) value;
				} else {
					i[e * comps + c] = (int) value;
				}
			}
		}
	}
}
