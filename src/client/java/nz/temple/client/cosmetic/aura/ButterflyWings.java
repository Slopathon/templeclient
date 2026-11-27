package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Meshes;
import nz.temple.cosmetic.Colours;
import org.joml.Vector3d;

final class ButterflyWings {
	private static final double[][] FOREWING = {{0, 0}, {0.06, 0.12}, {0.18, 0.3}, {0.34, 0.46}, {0.52, 0.56}, {0.66, 0.58},
			{0.74, 0.5}, {0.72, 0.36}, {0.64, 0.2}, {0.52, 0.06}, {0.36, -0.01}, {0.16, -0.03}};
	private static final double[][] HINDWING = {{0, 0}, {0.16, -0.02}, {0.34, -0.08}, {0.48, -0.18}, {0.54, -0.32}, {0.48, -0.46},
			{0.38, -0.54}, {0.33, -0.7}, {0.28, -0.72}, {0.25, -0.56}, {0.14, -0.48}, {0.05, -0.28}};
	private static final int BLACK = 0xFF120F12;
	private ButterflyWings() {
	}
	static void draw(AuraContext c, WingRig w, PieceLook look) {
		Vector3d root = c.anchor.bodyPoint(w.side * 0.02, -0.24, -0.15);
		boolean airborne = !c.player.onGround();
		double size = w.scale * 0.88;
		int rings = c.detailed ? 14 : 7, segs = c.detailed ? 56 : 28;
		look.apply(c.mesh);
		c.mesh.accent(0, look.glowColour(0));
		for (int hind = 0; hind <= 1; hind++) {
			double phase = w.phase - hind * 0.35;
			double beat = Math.sin(phase);
			double spread = airborne ? 0.6 + 0.4 * beat : 0.62 + 0.14 * Math.min(1, w.open) + beat * Math.min(0.25, w.amp * 3);
			double flex = -0.35 * Math.cos(phase) * (airborne ? 1 : 0.4);
			panel(c, w, look, root, hind == 1 ? HINDWING : FOREWING, spread * (hind == 1 ? 0.92 : 1), flex * (hind == 1 ? 0.8 : 1), size,
					hind == 1, rings, segs);
		}
	}
	private static double[] point(WingRig w, Vector3d root, double x, double y, double spread, double flex, double size) {
		double a = Math.max(0.03, Math.min(1.05, spread + flex * x)) * Math.PI / 2;
		double cx = Math.cos(a), sx = Math.sin(a);
		double cup = -0.05 * x * x;
		double ex = (w.back.x * cx + w.out.x * sx) * x + (-w.back.x * sx + w.out.x * cx) * cup + w.up.x * (y + 0.06 * x);
		double ey = (w.back.y * cx + w.out.y * sx) * x + (-w.back.y * sx + w.out.y * cx) * cup + w.up.y * (y + 0.06 * x);
		double ez = (w.back.z * cx + w.out.z * sx) * x + (-w.back.z * sx + w.out.z * cx) * cup + w.up.z * (y + 0.06 * x);
		return new double[] {root.x + ex * size, root.y + ey * size, root.z + ez * size};
	}
	private static void panel(AuraContext c, WingRig w, PieceLook look, Vector3d root, double[][] outline, double spread, double flex,
			double size, boolean hind, int rings, int segs) {
		Meshes mesh = c.mesh;
		int n = outline.length;
		double[][] edge = new double[segs + 1][];
		for (int k = 0; k <= segs; k++) {
			double f = (double) k / segs * n;
			int i = Math.min(n - 1, (int) f);
			double t = f - i;
			double[] p0 = outline[(i - 1 + n) % n], p1 = outline[i % n], p2 = outline[(i + 1) % n], p3 = outline[(i + 2) % n];
			edge[k] = new double[] {catmull(p0[0], p1[0], p2[0], p3[0], t), catmull(p0[1], p1[1], p2[1], p3[1], t)};
		}
		double[][][] pts = new double[rings + 1][segs + 1][];
		int[][] cols = new int[rings + 1][segs + 1];
		double[][] glow = new double[rings + 1][segs + 1];
		double g = look.glow();
		for (int r = 0; r <= rings; r++) {
			double k = (double) r / rings;
			for (int i = 0; i <= segs; i++) {
				double x = edge[i][0] * k, y = edge[i][1] * k;
				pts[r][i] = point(w, root, x, y, spread, flex, size);
				cols[r][i] = colour(look, x, y, k, i, segs, hind);
				glow[r][i] = (cols[r][i] & 0xFFFFFF) == (BLACK & 0xFFFFFF) ? 0 : g * 0.5 * k * k;
			}
		}
		double[] nrm = new double[3];
		double[] none = new double[2];
		for (int r = 0; r < rings; r++) {
			for (int i = 0; i < segs; i++) {
				double[] a = pts[r][i], b = pts[r][i + 1], cc = pts[r + 1][i + 1], d = pts[r + 1][i];
				double ux = cc[0] - a[0], uy = cc[1] - a[1], uz = cc[2] - a[2];
				double vx = d[0] - b[0], vy = d[1] - b[1], vz = d[2] - b[2];
				nrm[0] = uy * vz - uz * vy;
				nrm[1] = uz * vx - ux * vz;
				nrm[2] = ux * vy - uy * vx;
				double len = Math.sqrt(nrm[0] * nrm[0] + nrm[1] * nrm[1] + nrm[2] * nrm[2]);
				if (len < 1e-14) {
					continue;
				}
				nrm[0] /= len;
				nrm[1] /= len;
				nrm[2] /= len;
				if (nrm[0] * w.back.x + nrm[1] * w.back.y + nrm[2] * w.back.z < 0) {
					nrm[0] = -nrm[0];
					nrm[1] = -nrm[1];
					nrm[2] = -nrm[2];
				}
				mesh.quad(withNormal(a, nrm), withNormal(b, nrm), withNormal(cc, nrm), withNormal(d, nrm), cols[r][i], cols[r][i + 1],
						cols[r + 1][i + 1], cols[r + 1][i], none, none, none, none, glow[r][i], glow[r][i + 1], glow[r + 1][i + 1], glow[r + 1][i],
						1, 1, 1, 1);
			}
		}
		int spots = hind ? 9 : 11;
		for (int j = 0; j < spots; j++) {
			for (int row = 0; row < 2; row++) {
				double f = (j + 0.5 + row * 0.5) / (spots + 1);
				int i = (int) Math.round(f * segs);
				double k = row == 0 ? 0.9 : 0.955;
				spot(c, w, root, edge[i][0] * k, edge[i][1] * k, row == 0 ? 0.014 : 0.01, spread, flex, size, 0xFFF4F1E8);
			}
		}
		if (!hind) {
			double[][] apex = {{0.6, 0.47}, {0.64, 0.42}, {0.57, 0.4}, {0.66, 0.34}, {0.52, 0.47}};
			for (double[] a : apex) {
				spot(c, w, root, a[0], a[1], 0.02, spread, flex, size, Colours.lerp(0xFFFFF4D6, look.colour(0.6), 0.25));
			}
		}
	}
	private static double[] withNormal(double[] p, double[] n) {
		return new double[] {p[0], p[1], p[2], n[0], n[1], n[2]};
	}
	private static void spot(AuraContext c, WingRig w, Vector3d root, double x, double y, double r, double spread, double flex, double size,
			int colour) {
		Meshes mesh = c.mesh;
		double[] centre = point(w, root, x, y, spread, flex, size);
		double a = Math.max(0.03, Math.min(1.05, spread + flex * x)) * Math.PI / 2;
		double nx = -w.back.x * Math.sin(a) + w.out.x * Math.cos(a), ny = -w.back.y * Math.sin(a) + w.out.y * Math.cos(a),
				nz = -w.back.z * Math.sin(a) + w.out.z * Math.cos(a);
		if (nx * w.back.x + ny * w.back.y + nz * w.back.z < 0) {
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}
		for (int side = -1; side <= 1; side += 2) {
			double off = 0.0025 * side;
			double[] cc = {centre[0] + nx * off, centre[1] + ny * off, centre[2] + nz * off, nx * side, ny * side, nz * side};
			for (int k = 0; k < 8; k++) {
				double t0 = Math.PI * 2 * k / 8, t1 = Math.PI * 2 * (k + 1) / 8;
				double[] p0 = point(w, root, x + Math.cos(t0) * r, y + Math.sin(t0) * r, spread, flex, size);
				double[] p1 = point(w, root, x + Math.cos(t1) * r, y + Math.sin(t1) * r, spread, flex, size);
				double[] q0 = {p0[0] + nx * off, p0[1] + ny * off, p0[2] + nz * off, nx * side, ny * side, nz * side};
				double[] q1 = {p1[0] + nx * off, p1[1] + ny * off, p1[2] + nz * off, nx * side, ny * side, nz * side};
				mesh.quad(cc, cc, q1, q0, colour, colour, colour, colour);
			}
		}
	}
	private static int colour(PieceLook look, double x, double y, double k, int i, int segs, boolean hind) {
		if (k > 0.86 || (!hind && Math.hypot(x - 0.66, y - 0.46) < 0.13) || (i % 7 == 0 && k > 0.12)) {
			return BLACK;
		}
		double cx = hind ? 0.18 : 0.24, cy = hind ? -0.16 : 0.18;
		double ell = Math.hypot((x - cx) / (hind ? 0.12 : 0.16), (y - cy) / (hind ? 0.1 : 0.08));
		if (Math.abs(ell - 1) < 0.12) {
			return BLACK;
		}
		int base = Colours.vivid(look.colour(0.45 + k * 0.45 + (hind ? 0.05 : 0)));
		base = Colours.lerp(base, 0xFFFFFFFF, 0.08 * k);
		if (k < 0.2) {
			base = Colours.lerp(base, 0xFF2A1E18, 0.65 * (1 - k / 0.2));
		}
		if (k > 0.78) {
			base = Colours.lerp(base, BLACK, (k - 0.78) / 0.08 * 0.5);
		}
		return base;
	}
	private static double catmull(double p0, double p1, double p2, double p3, double t) {
		double t2 = t * t, t3 = t2 * t;
		return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
	}
}
