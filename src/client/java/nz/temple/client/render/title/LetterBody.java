package nz.temple.client.render.title;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class LetterBody {
	static final float GRAVITY = 28;
	static final float STEP = 1 / 240f;
	private static final float RESTITUTION = 0.15f;
	private static final float FRICTION = 0.8f;
	private static final int ITERATIONS = 10;
	private static final float ROLLING = 0.03f;
	private static final float SPECULATIVE = 0.08f;
	private final float[] hx;
	private final float[] hy;
	final float[] wx;
	final float[] wy;
	final float inertia;
	final float mass;
	final float radius;
	final float comX;
	final float comY;
	float x;
	float y;
	float angle;
	float vx;
	float vy;
	float spin;
	boolean asleep = true;

	boolean held;
	boolean touching;
	float impact;
	float restAngle;
	float wallMin = -1e9f;
	float wallMax = 1e9f;
	private float still;
	private int contacts;
	private final float[] rx;
	private final float[] ry;
	private final float[] nx;
	private final float[] ny;
	private final float[] jn;
	private final float[] jt;
	private final float[] bounce;

	LetterBody(List<float[]> hull, float comX, float comY, float inertia, float mass) {
		int n = hull.size();
		hx = new float[n];
		hy = new float[n];
		float r = 0;
		for (int i = 0; i < n; i++) {
			hx[i] = hull.get(i)[0] - comX;
			hy[i] = hull.get(i)[1] - comY;
			r = Math.max(r, (float) Math.hypot(hx[i], hy[i]));
		}
		this.comX = comX;
		this.comY = comY;
		this.inertia = Math.max(1e-3f, inertia);
		this.mass = Math.max(0.05f, mass);
		this.radius = r;
		wx = new float[n];
		wy = new float[n];
		int m = n * 3;
		rx = new float[m];
		ry = new float[m];
		nx = new float[m];
		ny = new float[m];
		jn = new float[m];
		jt = new float[m];
		bounce = new float[m];
	}
	static LetterBody of(LetterMesh.Glyph g) {
		List<float[]> points = new ArrayList<>();
		double area = 0, cx = 0, cy = 0;
		List<double[]> cells = new ArrayList<>();
		for (LetterMesh.Quad q : g.quads()) {
			float[] v = q.v();
			for (int k = 0; k < 4; k++) {
				points.add(new float[] {v[k * 6], v[k * 6 + 1]});
			}
			if (q.side() == 0 && v[5] > 0.5f) {
				double a = 0;
				for (int k = 0; k < 4; k++) {
					int m = (k + 1) % 4;
					a += v[k * 6] * v[m * 6 + 1] - v[m * 6] * v[k * 6 + 1];
				}
				a = Math.abs(a / 2);
				double mx = (v[0] + v[6] + v[12] + v[18]) / 4, my = (v[1] + v[7] + v[13] + v[19]) / 4;
				area += a;
				cx += mx * a;
				cy += my * a;
				cells.add(new double[] {mx, my, a});
			}
		}
		List<float[]> hull = hull(points);
		if (hull.size() < 3 || area <= 0) {
			return null;
		}
		cx /= area;
		cy /= area;
		double second = 0;
		for (double[] c : cells) {
			second += c[2] * ((c[0] - cx) * (c[0] - cx) + (c[1] - cy) * (c[1] - cy));
		}
		return new LetterBody(hull, (float) cx, (float) cy, (float) (second / area), (float) area);
	}
	static List<float[]> hull(List<float[]> points) {
		List<float[]> sorted = new ArrayList<>(points);
		sorted.sort((a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : Float.compare(a[1], b[1]));
		float[][] out = new float[sorted.size() * 2][];
		int k = 0;
		for (int pass = 0; pass < 2; pass++) {
			int start = k;
			for (int i = 0; i < sorted.size(); i++) {
				float[] p = sorted.get(pass == 0 ? i : sorted.size() - 1 - i);
				while (k >= start + 2 && cross(out[k - 2], out[k - 1], p) <= 0) {
					k--;
				}
				out[k++] = p;
			}
			k--;
		}
		return new ArrayList<>(Arrays.asList(out).subList(0, Math.max(0, k)));
	}
	private static float cross(float[] o, float[] a, float[] b) {
		return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
	}
	void set(float x, float y, float angle, float vx, float vy, float spin) {
		this.x = x;
		this.y = y;
		this.angle = angle;
		this.vx = vx;
		this.vy = vy;
		this.spin = spin;
		wake();
	}

	void hold(float x, float y, float angle) {
		set(x, y, angle, 0, 0, 0);
		asleep = true;
		updateWorld();
	}
	void wake() {
		asleep = false;
		still = 0;
	}
	float restHeight(float angle) {
		float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
		float min = Float.MAX_VALUE;
		for (int i = 0; i < hx.length; i++) {
			min = Math.min(min, s * hx[i] + c * hy[i]);
		}
		return -min;
	}

	boolean standsUpright() {
		float min = Float.MAX_VALUE;
		for (float v : hy) {
			min = Math.min(min, v);
		}
		float left = Float.MAX_VALUE, right = -Float.MAX_VALUE;
		for (int i = 0; i < hy.length; i++) {
			if (hy[i] < min + 0.01f) {
				left = Math.min(left, hx[i]);
				right = Math.max(right, hx[i]);
			}
		}
		return left <= 0.02f && right >= -0.02f;
	}

	float[] span() {
		float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
		float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
		for (int i = 0; i < hx.length; i++) {
			float px = x + c * hx[i] - s * hy[i];
			min = Math.min(min, px);
			max = Math.max(max, px);
		}
		return new float[] {min, max};
	}
	boolean grounded() {
		return (asleep || contacts > 0 && Math.abs(vy) < 1.5f) && y - restHeight(angle) < 0.2f;
	}
	void updateWorld() {
		float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
		for (int i = 0; i < hx.length; i++) {
			wx[i] = x + c * hx[i] - s * hy[i];
			wy[i] = y + s * hx[i] + c * hy[i];
		}
	}
	void settle(float maxSeconds) {
		for (int i = 0; i < maxSeconds / STEP && !asleep; i++) {
			accelerate();
			solveBounds();
			integrate();
		}
	}

	void accelerate() {
		if (!asleep) {
			vy -= GRAVITY * STEP;
		}
	}
	void solveBounds() {
		if (asleep) {
			contacts = 0;
			return;
		}
		float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
		int n = 0;
		for (int i = 0; i < hx.length; i++) {
			float wx = c * hx[i] - s * hy[i];
			float wy = s * hx[i] + c * hy[i];
			n = plane(n, wx, wy, 0, 1, y + wy);
			n = plane(n, wx, wy, 1, 0, x + wx - wallMin);
			n = plane(n, wx, wy, -1, 0, wallMax - (x + wx));
		}
		for (int k = 0; k < ITERATIONS && n > 0; k++) {
			for (int i = 0; i < n; i++) {
				float rn = rx[i] * ny[i] - ry[i] * nx[i];
				float vn = (vx - spin * ry[i]) * nx[i] + (vy + spin * rx[i]) * ny[i];
				float dj = (bounce[i] - vn) / (1 + rn * rn / inertia);
				float next = Math.max(0, jn[i] + dj);
				dj = next - jn[i];
				jn[i] = next;
				vx += dj * nx[i];
				vy += dj * ny[i];
				spin += rn * dj / inertia;
				float tx = -ny[i], ty = nx[i];
				float rt = rx[i] * ty - ry[i] * tx;
				float vt = (vx - spin * ry[i]) * tx + (vy + spin * rx[i]) * ty;
				float djt = -vt / (1 + rt * rt / inertia);
				float limit = FRICTION * jn[i];
				float nextT = Math.max(-limit, Math.min(limit, jt[i] + djt));
				djt = nextT - jt[i];
				jt[i] = nextT;
				vx += djt * tx;
				vy += djt * ty;
				spin += rt * djt / inertia;
			}
		}
		float pressed = 0;
		contacts = 0;
		for (int i = 0; i < n; i++) {
			if (jn[i] > 0) {
				contacts++;
				if (ny[i] > 0.5f) {
					pressed += jn[i];
				}
			}
		}
		righting = !held && contacts > 0 && Math.abs(vx) < 0.6f && Math.abs(vy) < 0.6f
				&& Math.abs(Math.IEEEremainder(restAngle - angle, Math.PI * 2)) < 0.25;
		if (righting) {
			float off = (float) Math.IEEEremainder(restAngle - angle, Math.PI * 2);
			spin += (off * 140 - spin * 14) * STEP;
			vx *= 1 - 1.5f * STEP;
		} else if (pressed > 0) {
			float brake = ROLLING * pressed / inertia;
			spin = Math.abs(spin) <= brake ? 0 : spin - Math.signum(spin) * brake;
			vx *= 1 - 1.5f * STEP;
		}
	}
	private boolean righting;
	boolean round() {
		float min = Float.MAX_VALUE;
		for (int i = 0; i < hx.length; i++) {
			min = Math.min(min, (float) Math.hypot(hx[i], hy[i]));
		}
		return min > radius * 0.85f;
	}
	private int plane(int n, float wx, float wy, float px, float py, float gap) {
		if (gap >= SPECULATIVE) {
			return n;
		}
		rx[n] = wx;
		ry[n] = wy;
		nx[n] = px;
		ny[n] = py;
		jn[n] = 0;
		jt[n] = 0;
		float vn = (vx - spin * wy) * px + (vy + spin * wx) * py;
		float target = gap > 0 ? -gap / STEP : -0.2f * gap / STEP;
		if (gap < 0.01f && vn < -3) {

			float along = -(vx * px + vy * py);
			target = Math.max(target, RESTITUTION * Math.max(0, along));
			impact = Math.max(impact, -vn);
		}
		bounce[n] = target;
		return n + 1;
	}

	void integrate() {
		if (asleep) {
			return;
		}
		x += vx * STEP;
		y += vy * STEP;
		angle += spin * STEP;
		float lift = restHeight(angle);
		if (y < lift - 0.05f) {
			y = lift - 0.05f;
		}
		float off = (float) Math.IEEEremainder(restAngle - angle, Math.PI * 2);
		boolean straight = !righting || Math.abs(off) < 0.035f;
		if (!held && straight && (contacts > 0 || touching) && Math.abs(vx) < 0.06f && Math.abs(vy) < 0.06f && Math.abs(spin) < 0.06f) {
			still += STEP;
			if (still > 0.35f) {
				asleep = true;
				vx = vy = spin = 0;
				if (righting) {
					angle = restAngle;
					lift = restHeight(angle);
				}
				if (!touching) {
					y = lift;
				}
			}
		} else {
			still = 0;
		}
		updateWorld();
	}
}
