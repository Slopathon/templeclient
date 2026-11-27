package nz.temple.cosmetic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Model {
	public static final int VERSION = 1;
	public static final int STRIDE = 8;
	public record Joint(String name, int parent, float px, float py, float pz) {
	}
	public record Part(String name, int joint, int firstIndex, int indexCount) {
	}
	private final List<Joint> joints;
	private final List<Part> parts;
	private final float[] vertices;
	private final byte[] extras;
	private final int[] indices;
	public Model(List<Joint> joints, List<Part> parts, float[] vertices, byte[] extras, int[] indices) {
		this.joints = List.copyOf(joints);
		this.parts = List.copyOf(parts);
		this.vertices = vertices;
		this.extras = extras;
		this.indices = indices;
		int count = vertexCount();
		if (extras.length != count * 4) {
			throw new IllegalArgumentException("Vertex extras do not match vertex count");
		}
		for (int i : indices) {
			if (i < 0 || i >= count) {
				throw new IllegalArgumentException("Index " + i + " out of range");
			}
		}
		for (Part p : parts) {
			if (p.firstIndex < 0 || p.indexCount % 3 != 0 || p.firstIndex + p.indexCount > indices.length) {
				throw new IllegalArgumentException("Part " + p.name + " has a bad index range");
			}
			if (p.joint >= joints.size()) {
				throw new IllegalArgumentException("Part " + p.name + " names a missing joint");
			}
		}
	}
	public static Model read(InputStream in) throws IOException {
		byte[] all = in.readAllBytes();
		ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
		if (all.length < 8 || b.get() != 'T' || b.get() != 'M' || b.get() != 'D' || b.get() != 'L') {
			throw new IOException("Not a model file");
		}
		int version = b.getInt();
		if (version != VERSION) {
			throw new IOException("Unsupported model version " + version);
		}
		try {
			List<Joint> joints = new ArrayList<>();
			int jc = count(b, 256);
			for (int i = 0; i < jc; i++) {
				joints.add(new Joint(name(b), b.getInt(), b.getFloat(), b.getFloat(), b.getFloat()));
			}
			List<Part> parts = new ArrayList<>();
			int pc = count(b, 256);
			for (int i = 0; i < pc; i++) {
				parts.add(new Part(name(b).intern(), b.getInt(), b.getInt(), b.getInt()));
			}
			int vc = count(b, 1 << 20);
			float[] v = new float[vc * STRIDE];
			byte[] e = new byte[vc * 4];
			for (int i = 0; i < vc; i++) {
				for (int k = 0; k < STRIDE; k++) {
					v[i * STRIDE + k] = b.getFloat();
				}
				b.get(e, i * 4, 4);
			}
			int ic = count(b, 1 << 22);
			int[] idx = new int[ic];
			for (int i = 0; i < ic; i++) {
				idx[i] = b.getInt();
			}
			return new Model(joints, parts, v, e, idx);
		} catch (java.nio.BufferUnderflowException | IllegalArgumentException ex) {
			throw new IOException("Damaged model file: " + ex.getMessage(), ex);
		}
	}
	private static int count(ByteBuffer b, int max) throws IOException {
		int n = b.getInt();
		if (n < 0 || n > max) {
			throw new IOException("Implausible count " + n);
		}
		return n;
	}
	private static String name(ByteBuffer b) {
		int len = b.get() & 0xFF;
		byte[] s = new byte[len];
		b.get(s);
		return new String(s, StandardCharsets.UTF_8);
	}
	public List<Joint> joints() {
		return Collections.unmodifiableList(joints);
	}
	public List<Part> parts() {
		return Collections.unmodifiableList(parts);
	}
	public int vertexCount() {
		return vertices.length / STRIDE;
	}
	public int[] indices() {
		return indices;
	}
	public float x(int v) {
		return vertices[v * STRIDE];
	}
	public float y(int v) {
		return vertices[v * STRIDE + 1];
	}
	public float z(int v) {
		return vertices[v * STRIDE + 2];
	}
	public float nx(int v) {
		return vertices[v * STRIDE + 3];
	}
	public float ny(int v) {
		return vertices[v * STRIDE + 4];
	}
	public float nz(int v) {
		return vertices[v * STRIDE + 5];
	}
	public float u(int v) {
		return vertices[v * STRIDE + 6];
	}
	public float v(int v) {
		return vertices[v * STRIDE + 7];
	}

	public double occlusion(int v) {
		return (extras[v * 4] & 0xFF) / 255.0;
	}

	public double glowMask(int v) {
		return (extras[v * 4 + 1] & 0xFF) / 255.0;
	}
	public double ramp(int v) {
		return (extras[v * 4 + 2] & 0xFF) / 255.0;
	}
	public double bend(int v) {
		return (extras[v * 4 + 3] & 0xFF) / 255.0;
	}
	public Part part(String name) {
		for (Part p : parts) {
			if (p.name.equals(name)) {
				return p;
			}
		}
		return null;
	}
}
