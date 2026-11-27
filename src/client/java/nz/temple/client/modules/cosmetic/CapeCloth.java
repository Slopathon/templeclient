package nz.temple.client.modules.cosmetic;

final class CapeCloth {
	static final int COLS = 9;
	static final int ROWS = 15;
	private static final double STEP = 1.0 / 120;
	private static final int MAX_STEPS = 10;
	private static final int ITERATIONS = 8;
	private static final double GRAVITY = 14;
	record Frame(double ox, double oy, double oz, double[] right, double[] up, double[] back) {
		double[] at(double lx, double ly, double lz) {
			return new double[] {ox + right[0] * lx + up[0] * ly + back[0] * lz, oy + right[1] * lx + up[1] * ly + back[1] * lz,
					oz + right[2] * lx + up[2] * ly + back[2] * lz};
		}
	}
	record Body(double shoulderY, double hipY, double backDepth, double lean, double legBack, double legLeft, double legRight,
			double floorY) {
	}
	private final double[] pos = new double[COLS * ROWS * 3];
	private final double[] prev = new double[COLS * ROWS * 3];
	private final double[] normal = new double[COLS * ROWS * 3];
	private final double[] anchorFrom = new double[COLS * 3];
	private final double[] anchorTo = new double[COLS * 3];
	private boolean ready;
	private double accumulator;
	private double width;
	private double length;
	private double clock;
	private final double seed;
	CapeCloth(double seed) {
		this.seed = seed;
	}
	void update(double dt, Frame frame, Body body, double[] anchors, double capeWidth, double capeLength, double[] air, double drag,
			double flutter) {
		boolean resized = Math.abs(capeWidth - width) > 1e-6 || Math.abs(capeLength - length) > 1e-6;
		width = capeWidth;
		length = capeLength;
		double jump = ready ? dist(anchors, 0, anchorTo, 0) + dist(anchors, (COLS - 1) * 3, anchorTo, (COLS - 1) * 3) : 0;
		if (!ready || resized || jump > 6) {
			reset(frame, anchors);
			return;
		}
		System.arraycopy(anchorTo, 0, anchorFrom, 0, anchorFrom.length);
		System.arraycopy(anchors, 0, anchorTo, 0, anchorTo.length);
		accumulator = Math.min(accumulator + dt, STEP * MAX_STEPS);
		int steps = (int) (accumulator / STEP);
		accumulator -= steps * STEP;
		for (int s = 0; s < steps; s++) {
			double k = (s + 1.0) / steps;
			clock += STEP;
			step(frame, body, k, air, drag, flutter);
		}
		computeNormals(frame);
	}
	private void reset(Frame frame, double[] anchors) {
		System.arraycopy(anchors, 0, anchorFrom, 0, anchorFrom.length);
		System.arraycopy(anchors, 0, anchorTo, 0, anchorTo.length);
		for (int r = 0; r < ROWS; r++) {
			double v = (double) r / (ROWS - 1);
			for (int c = 0; c < COLS; c++) {
				int i = (r * COLS + c) * 3;
				double flare = 1 + 0.12 * v;
				double cx = (anchors[(COLS / 2) * 3]);
				double cz = (anchors[(COLS / 2) * 3 + 2]);
				pos[i] = cx + (anchors[c * 3] - cx) * flare + frame.back()[0] * 0.02 * v;
				pos[i + 1] = anchors[c * 3 + 1] - v * length;
				pos[i + 2] = cz + (anchors[c * 3 + 2] - cz) * flare + frame.back()[2] * 0.02 * v;
			}
		}
		System.arraycopy(pos, 0, prev, 0, pos.length);
		accumulator = 0;
		ready = true;
		computeNormals(frame);
	}
	private void step(Frame frame, Body body, double k, double[] air, double drag, double flutter) {
		double h = STEP;
		double damping = 0.992;
		for (int r = 1; r < ROWS; r++) {
			double v = (double) r / (ROWS - 1);
			for (int c = 0; c < COLS; c++) {
				int i = (r * COLS + c) * 3;
				double vx = (pos[i] - prev[i]) / h;
				double vy = (pos[i + 1] - prev[i + 1]) / h;
				double vz = (pos[i + 2] - prev[i + 2]) / h;
				double u = (double) c / (COLS - 1);
				double gust = flutter * (Math.sin(clock * 7.3 + u * 5.1 + v * 3.7 + seed) * 0.6
						+ Math.sin(clock * 12.9 - u * 7.7 + v * 9.1 + seed * 2.3) * 0.4);
				double ax = air[0] + frame.back()[0] * gust * 1.4;
				double ay = air[1] + gust * 0.5;
				double az = air[2] + frame.back()[2] * gust * 1.4;
				double rx = vx - ax, ry = vy - ay, rz = vz - az;
				double nx = normal[i], ny = normal[i + 1], nz = normal[i + 2];
				double vn = rx * nx + ry * ny + rz * nz;
				double speed = Math.sqrt(rx * rx + ry * ry + rz * rz);
				double push = drag * vn * Math.abs(vn) * (0.35 + Math.min(1.6, speed / 6));
				double dv = push * h;
				if (Math.abs(dv) > Math.abs(vn)) {
					dv = vn;
				}
				double fx = -nx * dv - rx * 0.05 * h;
				double fy = -ny * dv - ry * 0.05 * h;
				double fz = -nz * dv - rz * 0.05 * h;
				double px = pos[i], py = pos[i + 1], pz = pos[i + 2];
				pos[i] += (px - prev[i]) * damping + fx * h;
				pos[i + 1] += (py - prev[i + 1]) * damping + fy * h - GRAVITY * h * h;
				pos[i + 2] += (pz - prev[i + 2]) * damping + fz * h;
				prev[i] = px;
				prev[i + 1] = py;
				prev[i + 2] = pz;
			}
		}
		pin(k);
		double dy = length / (ROWS - 1);
		for (int it = 0; it < ITERATIONS; it++) {
			for (int r = 0; r < ROWS; r++) {
				double v = (double) r / (ROWS - 1);
				double dx = width * (0.94 + 0.16 * v) / (COLS - 1);
				for (int c = 0; c < COLS; c++) {
					if (c + 1 < COLS) {
						solve(r, c, r, c + 1, dx, 1);
					}
					if (r + 1 < ROWS) {
						solve(r, c, r + 1, c, dy, 1);
						if (c + 1 < COLS) {
							double diag = Math.sqrt(dx * dx + dy * dy);
							solve(r, c, r + 1, c + 1, diag, 0.5);
							solve(r, c + 1, r + 1, c, diag, 0.5);
						}
					}
					if (c + 2 < COLS) {
						solve(r, c, r, c + 2, dx * 2, 0.12);
					}
					if (r + 2 < ROWS) {
						solve(r, c, r + 2, c, dy * 2, 0.1);
					}
				}
			}
			pin(k);
			collide(frame, body);
		}
	}
	private void pin(double k) {
		for (int c = 0; c < COLS; c++) {
			int i = c * 3;
			for (int a = 0; a < 3; a++) {
				double p = anchorFrom[i + a] + (anchorTo[i + a] - anchorFrom[i + a]) * k;
				pos[i + a] = p;
				prev[i + a] = p;
			}
		}
	}
	private void solve(int r0, int c0, int r1, int c1, double rest, double stiffness) {
		int a = (r0 * COLS + c0) * 3;
		int b = (r1 * COLS + c1) * 3;
		double dx = pos[b] - pos[a];
		double dy = pos[b + 1] - pos[a + 1];
		double dz = pos[b + 2] - pos[a + 2];
		double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (d < 1e-9) {
			return;
		}
		if (stiffness < 0.2 && d < rest) {
			return;
		}
		double diff = (d - rest) / d * stiffness;
		boolean pa = r0 == 0;
		boolean pb = r1 == 0;
		if (pa && pb) {
			return;
		}
		double wa = pa ? 0 : pb ? 1 : 0.5;
		double wb = pb ? 0 : pa ? 1 : 0.5;
		pos[a] += dx * diff * wa;
		pos[a + 1] += dy * diff * wa;
		pos[a + 2] += dz * diff * wa;
		pos[b] -= dx * diff * wb;
		pos[b + 1] -= dy * diff * wb;
		pos[b + 2] -= dz * diff * wb;
	}
	private void collide(Frame f, Body body) {
		double[] rt = f.right(), up = f.up(), bk = f.back();
		for (int r = 1; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				int i = (r * COLS + c) * 3;
				double px = pos[i] - f.ox(), py = pos[i + 1] - f.oy(), pz = pos[i + 2] - f.oz();
				double lx = px * rt[0] + py * rt[1] + pz * rt[2];
				double ly = px * up[0] + py * up[1] + pz * up[2];
				double lz = px * bk[0] + py * bk[1] + pz * bk[2];
				if (ly > -0.05 && ly < body.shoulderY() + 0.1 && Math.abs(lx) < 0.42) {
					double min;
					double hips = body.backDepth() + (body.shoulderY() - body.hipY()) * body.lean();
					if (ly > body.hipY()) {

						min = body.backDepth() + (body.shoulderY() - Math.min(body.shoulderY(), ly)) * body.lean();
					} else {
						double leg = lx < 0 ? body.legLeft() : body.legRight();
						min = Math.max(hips - 0.03, body.backDepth() - 0.02 + body.legBack()) + Math.max(0, leg) * (body.hipY() - ly) * 0.9;
					}
					double side = Math.max(0, Math.abs(lx) - 0.26) / 0.16;
					min -= side * side * 0.18;
					if (lz < min) {
						double push = min - lz;
						pos[i] += bk[0] * push;
						pos[i + 1] += bk[1] * push;
						pos[i + 2] += bk[2] * push;
						prev[i] += (pos[i] - prev[i]) * 0.3;
						prev[i + 1] += (pos[i + 1] - prev[i + 1]) * 0.3;
						prev[i + 2] += (pos[i + 2] - prev[i + 2]) * 0.3;
					}
				}
				if (pos[i + 1] < body.floorY()) {
					pos[i + 1] = body.floorY();
					prev[i] += (pos[i] - prev[i]) * 0.5;
					prev[i + 2] += (pos[i + 2] - prev[i + 2]) * 0.5;
				}
			}
		}
	}
	private void computeNormals(Frame frame) {
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				int l = (r * COLS + Math.max(0, c - 1)) * 3;
				int rr = (r * COLS + Math.min(COLS - 1, c + 1)) * 3;
				int t = (Math.max(0, r - 1) * COLS + c) * 3;
				int b = (Math.min(ROWS - 1, r + 1) * COLS + c) * 3;
				double ax = pos[rr] - pos[l], ay = pos[rr + 1] - pos[l + 1], az = pos[rr + 2] - pos[l + 2];
				double bx = pos[b] - pos[t], by = pos[b + 1] - pos[t + 1], bz = pos[b + 2] - pos[t + 2];
				double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
				double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
				int i = (r * COLS + c) * 3;
				if (len < 1e-12) {
					nx = frame.back()[0];
					ny = frame.back()[1];
					nz = frame.back()[2];
					len = 1;
				}
				nx /= len;
				ny /= len;
				nz /= len;
				if (nx * frame.back()[0] + ny * frame.back()[1] + nz * frame.back()[2] < 0) {
					nx = -nx;
					ny = -ny;
					nz = -nz;
				}
				normal[i] = nx;
				normal[i + 1] = ny;
				normal[i + 2] = nz;
			}
		}
	}
	double[] sample(double u, double v, double offset) {
		return sample(u, v, offset, new double[6]);
	}
	void samplePair(double u, double v, double half, double[] outer, double[] inner) {
		sample(u, v, 0, outer);
		for (int a = 0; a < 3; a++) {
			double n = outer[a + 3];
			inner[a] = outer[a] - n * half;
			inner[a + 3] = -n;
			outer[a] += n * half;
		}
	}
	double[] sample(double u, double v, double offset, double[] out) {
		double fx = Math.max(0, Math.min(1, u)) * (COLS - 1);
		double fy = Math.max(0, Math.min(1, v)) * (ROWS - 1);
		int c = Math.min(COLS - 2, (int) fx);
		int r = Math.min(ROWS - 2, (int) fy);
		double tx = fx - c;
		double ty = fy - r;
		for (int a = 0; a < 3; a++) {
			out[a] = patch(pos, c, r, tx, ty, a);
			out[a + 3] = patch(normal, c, r, tx, ty, a);
		}
		double len = Math.sqrt(out[3] * out[3] + out[4] * out[4] + out[5] * out[5]);
		if (len < 1e-9) {
			out[3] = 0;
			out[4] = 1;
			out[5] = 0;
			len = 1;
		}
		double sign = offset >= 0 ? 1 : -1;
		for (int a = 3; a < 6; a++) {
			out[a] /= len;
		}
		for (int a = 0; a < 3; a++) {
			out[a] += out[a + 3] * offset;
			out[a + 3] *= sign;
		}
		return out;
	}
	private static double patch(double[] grid, int c, int r, double tx, double ty, int axis) {
		return catmull(row(grid, c, r - 1, tx, axis), row(grid, c, r, tx, axis), row(grid, c, r + 1, tx, axis), row(grid, c, r + 2, tx, axis), ty);
	}
	private static double row(double[] grid, int c, int r, double tx, int axis) {
		int rr = Math.max(0, Math.min(ROWS - 1, r));
		double p0 = grid[(rr * COLS + Math.max(0, c - 1)) * 3 + axis];
		double p1 = grid[(rr * COLS + c) * 3 + axis];
		double p2 = grid[(rr * COLS + Math.min(COLS - 1, c + 1)) * 3 + axis];
		double p3 = grid[(rr * COLS + Math.min(COLS - 1, c + 2)) * 3 + axis];
		return catmull(p0, p1, p2, p3, tx);
	}
	private static double catmull(double p0, double p1, double p2, double p3, double t) {
		double t2 = t * t;
		double t3 = t2 * t;
		return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
	}
	double hemSpeed() {
		int i = ((ROWS - 1) * COLS + COLS / 2) * 3;
		double dx = pos[i] - prev[i], dy = pos[i + 1] - prev[i + 1], dz = pos[i + 2] - prev[i + 2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz) / STEP;
	}
	private static double dist(double[] a, int ia, double[] b, int ib) {
		double dx = a[ia] - b[ib], dy = a[ia + 1] - b[ib + 1], dz = a[ia + 2] - b[ib + 2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
