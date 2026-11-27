package nz.temple.client.render.vrm;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class VrmPreview {
	public enum Tool {
		JIGGLE, ERASE, MASS, DEFLATE
	}
	private final VrmModel m;
	private final JigglePaint paint;
	private final float[][] pos;
	private final float[][] nrm;
	private final float centreY;
	private final float height;
	private final float[][] screen;
	private float[] depth;
	private int width;
	private int heightPx;
	private int[][] mirror;
	public VrmPreview(VrmAvatar avatar, JigglePaint paint) {
		this.m = avatar.model;
		this.paint = paint;
		Matrix4f[] rest = m.restWorld();
		pos = new float[m.sets.size()][];
		nrm = new float[m.sets.size()][];
		screen = new float[m.sets.size()][];
		float lo = Float.MAX_VALUE;
		float hi = -Float.MAX_VALUE;
		Vector3f p = new Vector3f();
		for (int s = 0; s < pos.length; s++) {
			VrmModel.VertexSet set = m.sets.get(s);
			int count = set.pos.length / 3;
			Matrix4f[] joints = new Matrix4f[set.skinNodes.length];
			for (int j = 0; j < joints.length; j++) {
				joints[j] = new Matrix4f(rest[set.skinNodes[j]]).mul(set.inverseBind[j]);
			}
			float[] out = new float[count * 3];
			float[] outN = new float[count * 3];
			for (int v = 0; v < count; v++) {
				Matrix4f j = joints[Math.min(set.joints[v * 4], joints.length - 1)];
				j.transformPosition(set.pos[v * 3], set.pos[v * 3 + 1], set.pos[v * 3 + 2], p);
				float sign = m.vrm1 ? -1 : 1;
				out[v * 3] = p.x * sign;
				out[v * 3 + 1] = p.y;
				out[v * 3 + 2] = p.z * sign;
				j.transformDirection(set.nrm[v * 3], set.nrm[v * 3 + 1], set.nrm[v * 3 + 2], p).normalize();
				outN[v * 3] = p.x * sign;
				outN[v * 3 + 1] = p.y;
				outN[v * 3 + 2] = p.z * sign;
				lo = Math.min(lo, out[v * 3 + 1]);
				hi = Math.max(hi, out[v * 3 + 1]);
			}
			pos[s] = out;
			nrm[s] = outN;
			screen[s] = new float[count * 3];
		}
		height = Math.max(0.2f, hi - lo);
		centreY = (hi + lo) / 2;
	}
	public void render(int[] argb, int w, int h, float yaw, float pitch, float zoom, float lift) {
		width = w;
		heightPx = h;

		for (int y = 0; y < h; y++) {
			int shade = 0x4A - 0x14 * y / Math.max(1, h);
			java.util.Arrays.fill(argb, y * w, y * w + w, 0xFF000000 | shade << 16 | (shade + 4) << 8 | (shade + 10));
		}
		if (depth == null || depth.length != w * h) {
			depth = new float[w * h];
		}
		java.util.Arrays.fill(depth, Float.MAX_VALUE);
		float cy = (float) Math.cos(Math.toRadians(yaw));
		float sy = (float) Math.sin(Math.toRadians(yaw));
		float cp = (float) Math.cos(Math.toRadians(pitch));
		float sp = (float) Math.sin(Math.toRadians(pitch));
		float scale = h * 0.9f / height * zoom;
		float midY = centreY + lift * height;
		for (int s = 0; s < pos.length; s++) {
			float[] p = pos[s];
			float[] o = screen[s];
			int[] ids = paint.point[s];
			float[] mass = paint.mass[s];
			float sign = m.vrm1 ? -1 : 1;
			for (int v = 0; v < p.length / 3; v++) {
				float x = p[v * 3];
				float y = p[v * 3 + 1] - midY;
				float z = p[v * 3 + 2];
				if (mass[v] > 0) {
					int id = ids[v];
					float push = mass[v] * JigglePaint.MAX_MASS;
					x += paint.pointNormal[id * 3] * sign * push;
					y += paint.pointNormal[id * 3 + 1] * push;
					z += paint.pointNormal[id * 3 + 2] * sign * push;
				}
				float x1 = x * cy + z * sy;
				float z1 = -x * sy + z * cy;
				float y2 = y * cp - z1 * sp;
				float z2 = y * sp + z1 * cp;
				o[v * 3] = w / 2f - x1 * scale;
				o[v * 3 + 1] = h / 2f - y2 * scale;
				o[v * 3 + 2] = z2;
			}
		}
		for (int pass = 0; pass < 2; pass++) {
			for (VrmModel.Prim prim : m.prims) {
				VrmModel.Material mat = m.materials.get(prim.material());
				if ((mat.mode() == 2) != (pass == 1)) {
					continue;
				}
				drawPrim(argb, prim, mat, cy, sy, cp, sp);
			}
		}
	}
	private void drawPrim(int[] argb, VrmModel.Prim prim, VrmModel.Material mat, float cy, float sy, float cp, float sp) {
		int s = prim.set();
		float[] sc = screen[s];
		float[] n = nrm[s];
		float[] uv = m.sets.get(s).uv;
		float[] w = paint.weights[s];
		float[] ms = paint.mass[s];
		int[] tex = mat.image() >= 0 && m.previewPixels != null && mat.image() < m.previewPixels.length ? m.previewPixels[mat.image()] : null;
		int tw = tex != null ? m.previewW[mat.image()] : 0;
		int th = tex != null ? m.previewH[mat.image()] : 0;
		int base = mat.color();
		int[] q = prim.quads();
		for (int k = 0; k + 3 < q.length; k += 4) {
			tri(argb, q[k], q[k + 1], q[k + 2], sc, n, uv, w, ms, tex, tw, th, base, mat.mode(), cy, sy, cp, sp);
			if (q[k + 3] != q[k + 2]) {
				tri(argb, q[k], q[k + 2], q[k + 3], sc, n, uv, w, ms, tex, tw, th, base, mat.mode(), cy, sy, cp, sp);
			}
		}
	}
	private void tri(int[] argb, int a, int b, int c, float[] sc, float[] n, float[] uv, float[] w, float[] ms, int[] tex, int tw, int th,
			int base, int mode, float cy, float sy, float cp, float sp) {
		float ax = sc[a * 3], ay = sc[a * 3 + 1], az = sc[a * 3 + 2];
		float bx = sc[b * 3], by = sc[b * 3 + 1], bz = sc[b * 3 + 2];
		float cx = sc[c * 3], cyy = sc[c * 3 + 1], cz = sc[c * 3 + 2];
		float area = (bx - ax) * (cyy - ay) - (by - ay) * (cx - ax);
		if (Math.abs(area) < 1e-6f) {
			return;
		}
		int x0 = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
		int x1 = Math.min(width - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
		int y0 = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cyy))));
		int y1 = Math.min(heightPx - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cyy))));
		if (x0 > x1 || y0 > y1) {
			return;
		}
		float nx = n[a * 3] + n[b * 3] + n[c * 3];
		float ny = n[a * 3 + 1] + n[b * 3 + 1] + n[c * 3 + 1];
		float nz = n[a * 3 + 2] + n[b * 3 + 2] + n[c * 3 + 2];
		float z1 = -nx * sy + nz * cy;
		float facing = ny * sp + z1 * cp;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		float light = 0.62f + 0.38f * (len > 1e-6f ? Math.abs(facing) / len : 1);
		float inv = 1 / area;
		for (int y = y0; y <= y1; y++) {
			float py = y + 0.5f;
			for (int x = x0; x <= x1; x++) {
				float px = x + 0.5f;
				float wa = ((bx - px) * (cyy - py) - (by - py) * (cx - px)) * inv;
				float wb = ((cx - px) * (ay - py) - (cyy - py) * (ax - px)) * inv;
				float wc = 1 - wa - wb;
				if (wa < 0 || wb < 0 || wc < 0) {
					continue;
				}
				float z = wa * az + wb * bz + wc * cz;
				int i = y * width + x;
				if (z >= depth[i]) {
					continue;
				}
				int col = base;
				if (tex != null) {
					float u = wa * uv[a * 2] + wb * uv[b * 2] + wc * uv[c * 2];
					float v = wa * uv[a * 2 + 1] + wb * uv[b * 2 + 1] + wc * uv[c * 2 + 1];
					u -= (float) Math.floor(u);
					v -= (float) Math.floor(v);
					int t = tex[Math.min(th - 1, (int) (v * th)) * tw + Math.min(tw - 1, (int) (u * tw))];
					col = mul(t, base);
				}
				int alpha = col >>> 24;
				if (mode != 0 && alpha < 128) {
					continue;
				}
				depth[i] = z;
				float pw = wa * w[a] + wb * w[b] + wc * w[c];
				int r = (int) (((col >> 16) & 0xFF) * light);
				int g = (int) (((col >> 8) & 0xFF) * light);
				int bl = (int) ((col & 0xFF) * light);
				if (pw > 0.001f) {
					float mix = Math.min(1, 0.35f + pw * 0.55f);
					int hr = 255;
					int hg = (int) (215 - 175 * pw);
					int hb = (int) (60 - 30 * pw);
					r = (int) (r + (hr - r) * mix);
					g = (int) (g + (hg - g) * mix);
					bl = (int) (bl + (hb - bl) * mix);
				}
				float pm = wa * ms[a] + wb * ms[b] + wc * ms[c];
				if (pm > 0.001f) {
					float mix = Math.min(0.8f, 0.25f + pm * 0.5f);
					r = (int) (r + (70 - r) * mix);
					g = (int) (g + (170 - g) * mix);
					bl = (int) (bl + (255 - bl) * mix);
				}
				argb[i] = 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, bl);
			}
		}
	}
	private static int mul(int a, int b) {
		int al = ((a >>> 24) * (b >>> 24)) / 255;
		int r = (((a >> 16) & 0xFF) * ((b >> 16) & 0xFF)) / 255;
		int g = (((a >> 8) & 0xFF) * ((b >> 8) & 0xFF)) / 255;
		int bl = ((a & 0xFF) * (b & 0xFF)) / 255;
		return (al << 24) | (r << 16) | (g << 8) | bl;
	}

	public boolean stroke(float x, float y, float radius, float strength, Tool tool, boolean mirror) {
		if (depth == null) {
			return false;
		}
		if (mirror && this.mirror == null) {
			buildMirror();
		}
		float r2 = radius * radius;
		float tolerance = height * 0.04f;
		Map<Integer, Float> touched = new HashMap<>();
		for (int s = 0; s < screen.length; s++) {
			float[] sc = screen[s];
			int[] ids = paint.point[s];
			for (int v = 0; v < ids.length; v++) {
				float dx = sc[v * 3] - x;
				float dy = sc[v * 3 + 1] - y;
				float d2 = dx * dx + dy * dy;
				if (d2 > r2) {
					continue;
				}
				int px = (int) sc[v * 3];
				int py = (int) sc[v * 3 + 1];
				if (px < 0 || py < 0 || px >= width || py >= heightPx || sc[v * 3 + 2] > depth[py * width + px] + tolerance) {
					continue;
				}
				float fall = 1 - d2 / r2;
				float delta = strength * fall * fall * 0.35f;
				touched.merge(ids[v], delta, Math::max);
				if (mirror && this.mirror[s][v] >= 0) {
					touched.merge(ids[this.mirror[s][v]], delta, Math::max);
				}
			}
		}
		float[][] values = tool == Tool.MASS || tool == Tool.DEFLATE ? paint.mass : paint.weights;
		boolean down = tool == Tool.ERASE || tool == Tool.DEFLATE;
		boolean changed = false;
		for (Map.Entry<Integer, Float> e : touched.entrySet()) {
			int id = e.getKey();
			float before = values[paint.firstSet(id)][paint.firstVertex(id)];
			float after = down ? Math.max(0, before - e.getValue()) : Math.min(1, before + e.getValue());
			if (after != before) {
				paint.setPoint(values, id, after);
				changed = true;
			}
		}
		if (changed) {
			paint.changed();
		}
		return changed;
	}
	private void buildMirror() {
		mirror = new int[pos.length][];
		for (int s = 0; s < pos.length; s++) {
			float[] p = pos[s];
			int count = p.length / 3;
			Map<Long, Integer> cells = new HashMap<>(count * 2);
			for (int v = 0; v < count; v++) {
				cells.putIfAbsent(cell(p[v * 3], p[v * 3 + 1], p[v * 3 + 2]), v);
			}
			int[] out = new int[count];
			for (int v = 0; v < count; v++) {
				out[v] = -1;
				float best = 1e-4f;
				float mx = -p[v * 3];
				float my = p[v * 3 + 1];
				float mz = p[v * 3 + 2];
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							Integer o = cells.get(cell(mx + dx * 0.01f, my + dy * 0.01f, mz + dz * 0.01f));
							if (o == null) {
								continue;
							}
							float ex = p[o * 3] - mx;
							float ey = p[o * 3 + 1] - my;
							float ez = p[o * 3 + 2] - mz;
							float d = ex * ex + ey * ey + ez * ez;
							if (d < best) {
								best = d;
								out[v] = o;
							}
						}
					}
				}
			}
			mirror[s] = out;
		}
	}
	private static long cell(float x, float y, float z) {
		long ix = (long) Math.floor(x / 0.01f) & 0x1FFFFF;
		long iy = (long) Math.floor(y / 0.01f) & 0x1FFFFF;
		long iz = (long) Math.floor(z / 0.01f) & 0x1FFFFF;
		return (ix << 42) | (iy << 21) | iz;
	}
}
