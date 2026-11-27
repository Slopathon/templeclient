package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Meshes;
import nz.temple.cosmetic.Colours;
import nz.temple.cosmetic.Finishes;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

final class MembraneWings {
	private MembraneWings() {
	}
	static void draw(AuraContext c, WingRig w, PieceLook look, boolean demon) {
		Meshes mesh = c.mesh;
		double s = w.scale;
		double glow = look.glow();
		int boneColour = Colours.lerp(look.colour(0.25), 0xFF0C0A0A, demon ? 0.45 : 0.25);
		look.apply(mesh);
		mesh.pattern(Meshes.PATTERN_GRAIN, 3);
		if (demon) {
			mesh.accent(glow * 0.35, look.glowColour(0));
		}
		double[] arm = new double[9];
		for (int i = 0; i < 3; i++) {
			arm[i * 3] = w.joints[i].x;
			arm[i * 3 + 1] = w.joints[i].y;
			arm[i * 3 + 2] = w.joints[i].z;
		}
		mesh.tube(arm, 3, new double[] {0.038 * s, 0.032 * s, 0.024 * s}, new int[] {boneColour, boneColour, boneColour}, 12);
		Vector3d wrist = w.joints[2];
		Vector3d hand = w.bones[2];
		Vector3d trail = w.trail(2);
		double spread = (demon ? 0.5 : 0.45) + (demon ? 0.5 : 0.55) * Math.min(1, w.open);
		double[] lengths = demon ? new double[] {0.74, 0.82, 0.7, 0.56} : new double[] {0.6, 0.68, 0.58, 0.45};
		Vector3d[] dirs = new Vector3d[4];
		double[] lens = new double[4];
		for (int f = 0; f < 4; f++) {
			double k = f / 3.0 * spread * 0.95;
			dirs[f] = new Vector3d(hand).mul(1 - k).add(new Vector3d(trail).mul(k)).normalize();
			lens[f] = lengths[f] * s;
			Vector3d tip = new Vector3d(wrist).fma(lens[f], dirs[f]);
			Vector3d mid = new Vector3d(wrist).fma(lens[f] * 0.5, dirs[f]).fma((demon ? 0.012 : 0.028) * s, w.back);
			look.apply(mesh);
			if (demon) {
				mesh.accent(glow * 0.3, look.glowColour(f / 4.0));
			}
			mesh.tube(new double[] {wrist.x, wrist.y, wrist.z, mid.x, mid.y, mid.z, tip.x, tip.y, tip.z}, 3,
					new double[] {0.016 * s, 0.011 * s, 0.005 * s}, new int[] {boneColour, boneColour, boneColour}, 10);
			if (demon) {
				claw(mesh, look, tip, dirs[f], 0.04 * s, boneColour);
			}
		}
		double billow = (demon ? 0.03 : 0.045) + 0.06 * Math.max(0, Math.cos(w.phase)) * Math.min(1, w.amp * 3);
		int skin = Colours.withAlpha(look.colour(0.4), demon ? 0.97 : 0.93);
		for (int f = 0; f < 3; f++) {
			membrane(c, w, look, wrist, dirs[f], lens[f], dirs[f + 1], lens[f + 1], demon ? 0.24 : 0.17, billow, skin, demon, f, glow);
		}
		Vector3d toWrist = new Vector3d(wrist).sub(w.joints[0]);
		double wristDist = toWrist.length();
		membrane(c, w, look, w.joints[0], new Vector3d(w.bones[0]), 0.2 * s * (demon ? 1.2 : 1), toWrist.normalize(), wristDist, 0.08, billow * 0.5,
				skin, demon, 3, glow);
		Vector3d elbow = w.joints[1];
		Vector3d lastTip = new Vector3d(wrist).fma(lens[3], dirs[3]);
		Vector3d toTip = new Vector3d(lastTip).sub(elbow);
		Vector3d elbowToWrist = new Vector3d(wrist).sub(elbow);
		double forearm = elbowToWrist.length();
		double reach = toTip.length();
		membrane(c, w, look, elbow, elbowToWrist.normalize(), forearm, toTip.normalize(), reach, demon ? 0.28 : 0.22, billow, skin, demon, 4, glow);
		if (demon) {
			for (int k = 0; k < 4; k++) {
				Vector3d at = w.chain(1.15 + k * 0.22);
				Vector3d dir = new Vector3d(w.up).mul(0.8).add(new Vector3d(w.bones[1]).mul(0.5)).normalize();
				look.apply(mesh);
				mesh.accent(glow * 0.2, look.glowColour(0));
				mesh.crystal(at.x + dir.x * 0.02 * s, at.y + dir.y * 0.02 * s, at.z + dir.z * 0.02 * s, quatTo(dir), (0.07 - k * 0.008) * s,
						0.014 * s, boneColour, 4);
			}
			Vector3d hook = new Vector3d(w.up).mul(0.7).add(new Vector3d(hand).mul(0.7)).normalize();
			claw(mesh, look, wrist, hook, 0.07 * s, boneColour);
		} else {
			Vector3d hook = new Vector3d(w.up).mul(0.8).add(hand).normalize();
			mesh.finish(Finishes.OBSIDIAN);
			mesh.crystal(wrist.x + hook.x * 0.02 * s, wrist.y + hook.y * 0.02 * s, wrist.z + hook.z * 0.02 * s, quatTo(hook), 0.045 * s, 0.01 * s,
					0xFF1A1716, 4);
		}
	}
	private static void claw(Meshes mesh, PieceLook look, Vector3d at, Vector3d dir, double length, int colour) {
		mesh.finish(Finishes.OBSIDIAN);
		Vector3d tip = new Vector3d(at).fma(length * 0.5, dir);
		mesh.crystal(tip.x, tip.y, tip.z, quatTo(dir), length, length * 0.18, Colours.lerp(colour, 0xFF050404, 0.5), 4);
	}
	private static void membrane(AuraContext c, WingRig w, PieceLook look, Vector3d root, Vector3d da, double la, Vector3d db, double lb,
			double sag, double billow, int colour, boolean demon, int panel, double glow) {
		Meshes mesh = c.mesh;
		int nu = c.detailed ? 9 : 5, nv = c.detailed ? 7 : 4;
		double[][] grid = new double[(nu + 1) * (nv + 1)][];
		double size = Math.max(la, lb);
		for (int j = 0; j <= nv; j++) {
			double v = (double) j / nv;
			for (int i = 0; i <= nu; i++) {
				double u = (double) i / nu;
				double ax = da.x * la * u, ay = da.y * la * u, az = da.z * la * u;
				double bx = db.x * lb * u, by = db.y * lb * u, bz = db.z * lb * u;
				double x = ax + (bx - ax) * v, y = ay + (by - ay) * v, z = az + (bz - az) * v;
				double pull = Math.sin(Math.PI * v) * Math.pow(u, demon ? 1.4 : 2) * sag;
				double puff = Math.sin(Math.PI * v) * Math.sin(Math.PI * u) * billow * size
						+ Math.sin(u * 5 - c.time * 6 + panel) * 0.006 * size * Math.sin(Math.PI * v);
				grid[j * (nu + 1) + i] = new double[] {root.x + x * (1 - pull) + w.back.x * puff, root.y + y * (1 - pull) + w.back.y * puff,
						root.z + z * (1 - pull) + w.back.z * puff, u, v};
			}
		}
		look.apply(mesh);
		mesh.pattern(demon ? Meshes.PATTERN_GRAIN : Meshes.PATTERN_VEINS, 1);
		mesh.accent(0, look.glowColour(panel / 5.0));
		double[] ua = new double[2], ub = new double[2], uc = new double[2], ud = new double[2];
		Vector3d n = w.normal(1);
		for (int j = 0; j < nv; j++) {
			for (int i = 0; i < nu; i++) {
				double[] a = grid[j * (nu + 1) + i], b = grid[j * (nu + 1) + i + 1], cc = grid[(j + 1) * (nu + 1) + i + 1],
						d = grid[(j + 1) * (nu + 1) + i];
				double cu = (i + 0.5) / nu, cv = (j + 0.5) / nv;
				double tear = demon ? tear(panel, cu, cv, w.side) : 0;
				if (tear > 0.5) {
					continue;
				}
				double[] fa = face(a, b, cc, d, n);
				double[] pa = {a[0], a[1], a[2], fa[0], fa[1], fa[2]};
				double[] pb = {b[0], b[1], b[2], fa[0], fa[1], fa[2]};
				double[] pc = {cc[0], cc[1], cc[2], fa[0], fa[1], fa[2]};
				double[] pd = {d[0], d[1], d[2], fa[0], fa[1], fa[2]};
				ua[0] = a[3] * la * 3;
				ua[1] = a[4] * size * 2;
				ub[0] = b[3] * la * 3;
				ub[1] = b[4] * size * 2;
				uc[0] = cc[3] * la * 3;
				uc[1] = cc[4] * size * 2;
				ud[0] = d[3] * la * 3;
				ud[1] = d[4] * size * 2;
				double wa = edgeGlow(demon, panel, a[3], a[4], w.side, glow), wb = edgeGlow(demon, panel, b[3], b[4], w.side, glow),
						wc = edgeGlow(demon, panel, cc[3], cc[4], w.side, glow), wd = edgeGlow(demon, panel, d[3], d[4], w.side, glow);
				mesh.quad(pa, pb, pc, pd, colour, colour, colour, colour, ua, ub, uc, ud, wa, wb, wc, wd, 1, 1, 1, 1);
			}
		}
	}
	private static double edgeGlow(boolean demon, int panel, double u, double v, int side, double glow) {
		if (!demon) {
			return glow * 0.8;
		}
		double t = tear(panel, u, v, side);
		return glow * Math.max(0, 1 - Math.abs(t - 0.5) / 0.22) * 0.9;
	}
	private static double tear(int panel, double u, double v, int side) {
		double seed = panel * 3.1 + side * 7.7;
		double n = smoothNoise(u * 3.2 + seed, v * 2.6 + seed * 0.5);
		double bias = u * 0.25 + Math.sin(Math.PI * v) * 0.08 - 0.42;
		return n * 0.75 + bias;
	}
	private static double smoothNoise(double x, double y) {
		double xi = Math.floor(x), yi = Math.floor(y);
		double fx = x - xi, fy = y - yi;
		double ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
		double a = FeatheredWings.hash(xi * 57 + yi * 131), b = FeatheredWings.hash((xi + 1) * 57 + yi * 131);
		double c = FeatheredWings.hash(xi * 57 + (yi + 1) * 131), d = FeatheredWings.hash((xi + 1) * 57 + (yi + 1) * 131);
		return (a * (1 - ux) + b * ux) * (1 - uy) + (c * (1 - ux) + d * ux) * uy;
	}
	private static double[] face(double[] a, double[] b, double[] c, double[] d, Vector3d toward) {
		double ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2];
		double vx = d[0] - b[0], vy = d[1] - b[1], vz = d[2] - b[2];
		double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-12) {
			return new double[] {toward.x, toward.y, toward.z};
		}
		nx /= len;
		ny /= len;
		nz /= len;
		if (nx * toward.x + ny * toward.y + nz * toward.z < 0) {
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}
		return new double[] {nx, ny, nz};
	}
	private static Quaternionf quatTo(Vector3d dir) {
		return new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize());
	}
}
