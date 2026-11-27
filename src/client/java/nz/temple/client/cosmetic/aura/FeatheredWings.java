package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.cosmetic.Colours;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

final class FeatheredWings {
	private FeatheredWings() {
	}
	static void draw(AuraContext c, WingRig w, PieceLook look, int primaries, boolean fallen) {
		Meshes mesh = c.mesh;
		double s = w.scale;
		double glow = look.glow();
		boolean phoenix = !fallen && look.is("Phoenix");
		double[] bonePath = new double[12];
		for (int i = 0; i < 4; i++) {
			bonePath[i * 3] = w.joints[i].x;
			bonePath[i * 3 + 1] = w.joints[i].y;
			bonePath[i * 3 + 2] = w.joints[i].z;
		}
		look.apply(mesh);
		mesh.tube(bonePath, 4, new double[] {0.03 * s, 0.026 * s, 0.02 * s, 0.009 * s},
				new int[] {look.colour(0.3), look.colour(0.35), look.colour(0.4), look.colour(0.45)}, 8);
		int seed = w.side > 0 ? 17 : 41;
		for (int i = 0; i < primaries; i++) {
			double f = primaries == 1 ? 0 : (double) i / (primaries - 1);
			double at = 2.02 + f * 0.93;
			double reach = 0.2 + f * (0.55 + 0.6 * w.open) + w.downstroke * 0.18 * f;
			Vector3d trail = w.trail(2);
			Vector3d dir = new Vector3d(trail).mul(1 - reach * 0.7).add(new Vector3d(w.bones[2]).mul(reach)).normalize();
			double len = s * (0.34 + 0.24 * Math.sin(Math.min(1, f * 1.15) * Math.PI * 0.62));
			feather(c, w, look, at, 2, dir, len, 0.075 * s, 0.0005 * s * i, 0.55 + f * 0.1, 0.07 * s, fallen, seed + i, glow, true);
		}
		int secondaries = Math.max(6, (int) Math.round(primaries * 1.2));
		for (int i = 0; i < secondaries; i++) {
			double f = (double) i / (secondaries - 1);
			double at = 1.02 + f * 0.98;
			Vector3d dir = new Vector3d(w.trail(1)).add(new Vector3d(w.bones[1]).mul(-0.1 + f * 0.12)).normalize();
			feather(c, w, look, at, 1, dir, s * (0.31 + 0.04 * f), 0.085 * s, (0.004 + 0.0005 * i) * s, 0.45, 0.045 * s, fallen, seed + 50 + i,
					glow, false);
		}
		for (int i = 0; i < 3; i++) {
			Vector3d dir = new Vector3d(w.trail(0)).add(new Vector3d(w.bones[0]).mul(-0.35)).normalize();
			feather(c, w, look, 0.5 + i * 0.22, 0, dir, s * (0.23 - i * 0.015), 0.085 * s, (0.008 + 0.0006 * i) * s, 0.38, 0.025 * s, fallen,
					seed + 90 + i, glow, false);
		}
		int total = secondaries + primaries;
		coverts(c, w, look, total, 0.9, 2.9, 0.17, 0.08, 0.015, 0.28, fallen, seed + 100, glow);
		if (c.detailed) {
			coverts(c, w, look, total, 0.45, 2.6, 0.105, 0.068, 0.024, 0.18, fallen, seed + 200, glow);
			coverts(c, w, look, total + 4, 0.12, 2.35, 0.062, 0.056, 0.032, 0.08, fallen, seed + 300, glow);
		}
		if (phoenix && glow > 0) {
			embers(c, w, look, glow);
		}
		if (fallen) {
			ash(c, w, look);
		}
	}
	private static void coverts(AuraContext c, WingRig w, PieceLook look, int count, double from, double to, double length, double width,
			double lift, double tone, boolean fallen, int seed, double glow) {
		double s = w.scale;
		for (int i = 0; i < count; i++) {
			double f = (double) i / (count - 1);
			double at = from + f * (to - from);
			int seg = Math.max(0, Math.min(2, (int) Math.floor(at)));
			Vector3d dir = new Vector3d(w.trail(seg)).add(new Vector3d(w.bones[seg]).mul(0.28)).normalize();
			double len = s * length * (seg == 2 ? 1.1 : 1) * (0.92 + 0.08 * Math.sin(i * 2.3));
			feather(c, w, look, at, seg, dir, len, width * s, (lift + 0.0004 * i) * s, tone + f * 0.12, 0.012 * s, fallen, seed + i, glow, false);
		}
	}
	private static void feather(AuraContext c, WingRig w, PieceLook look, double at, int seg, Vector3d dir, double length, double width,
			double lift, double tone, double bend, boolean fallen, int seed, double glow, boolean primary) {
		double h = hash(seed);
		boolean broken = false;
		if (fallen) {
			if (h < 0.13) {
				return;
			}
			broken = h < 0.24;
			if (broken) {
				length *= 0.55 + 0.15 * hash(seed * 3);
				bend *= 2.5;
			}
			width *= 0.75 + 0.25 * hash(seed * 7);
		}
		Vector3d n = w.normal(seg);
		Vector3d root = w.chain(at).fma(lift, n);
		double wob = Math.sin(at * 37.1 + tone * 11) * 0.04;
		Vector3d d = new Vector3d(dir).fma(wob, w.bones[seg]).normalize();
		Quaternionf q = frame(d, n);
		Vector3f z = q.transform(new Vector3f(0, 0, 1));
		Vector3d trail = w.trail(seg);
		double wd = z.x * trail.x + z.y * trail.y + z.z * trail.z < 0 ? -width : width;
		int rootColour = look.colour(tone * 0.6 + 0.2);
		int tipColour = look.colour(Math.min(1, tone * 0.6 + 0.45));
		Meshes mesh = c.mesh;
		look.apply(mesh);
		mesh.pattern(Meshes.PATTERN_BARBS, 1);
		int glowColour = look.glowColour(tone);
		if (fallen) {
			tipColour = Colours.lerp(tipColour, 0xFF0A0808, 0.7);
			double burn = 0.55 + 0.25 * hash(seed * 11);
			mesh.accent(0, glowColour);
			mesh.accentAt((u, v) -> glow * 0.8 * Math.exp(-Math.pow((u - burn) / 0.06, 2)) * (0.5 + 0.5 * Math.abs(v - 0.5) * 2));
		} else {
			mesh.accent(0, glowColour);
			mesh.accentAt(TIP_GLOW, glow * (primary ? 0.9 : 0.3));
		}
		mesh.occlusionAt(SHINGLE);
		int shaft = Colours.lerp(rootColour, 0xFFFFFFFF, fallen ? 0.1 : 0.2);
		mesh.feather(root.x, root.y, root.z, q, length, wd, 0.09, bend, rootColour, tipColour, shaft);
		mesh.accentAt(null);
		mesh.occlusionAt(null);
	}
	private static final Meshes.Grid TIP_GLOW = new Meshes.Grid((u, v) -> Math.pow(u, 3) * (0.35 + 0.65 * Math.pow(Math.abs(v - 0.5) * 2, 4)));
	private static final Meshes.Grid SHINGLE = new Meshes.Grid(
			(u, v) -> 0.32 + 0.68 * Math.min(1, u / 0.6) * (0.85 + 0.15 * (1 - Math.abs(v - 0.5) * 2)));
	private static void embers(AuraContext c, WingRig w, PieceLook look, double glow) {
		Fx fx = c.fx;
		fx.additive(true);
		for (int i = 0; i < 9; i++) {
			double phase = frac(c.time * 0.7 + hash(i * 5 + w.side) );
			Vector3d tip = w.chain(1.2 + hash(i * 3 + w.side) * 1.8);
			Vector3d p = new Vector3d(tip).fma(-0.25 * phase * w.scale, w.up).fma(0.3 * phase * w.scale, w.back)
					.fma(Math.sin(c.time * 3 + i) * 0.05 * w.scale, w.out);
			double fade = Math.sin(Math.PI * Math.min(1, phase * 1.3)) * (1 - phase);
			int col = Colours.lerp(0xFFFFE8B0, look.glowColour(0.2), phase);
			fx.light(p.x, p.y, p.z, 0.025 * w.scale, Fx.withAlpha(col, (float) (230 * fade * glow)), 0.8);
		}
		fx.additive(false);
	}
	private static void ash(AuraContext c, WingRig w, PieceLook look) {
		Meshes mesh = c.mesh;
		for (int i = 0; i < 3; i++) {
			double phase = frac(c.time * 0.18 + hash(i * 13 + w.side * 3));
			Vector3d from = w.chain(1.0 + hash(i * 7 + w.side) * 1.9);
			double fall = phase * 1.6 * w.scale;
			Vector3d p = new Vector3d(from).fma(-fall, w.up).fma(Math.sin(c.time * 1.7 + i * 2) * 0.12 * w.scale, w.out)
					.fma(0.3 * phase * w.scale, w.back);
			Quaternionf q = new Quaternionf().rotationXYZ((float) (c.time * 1.3 + i), (float) (c.time * 0.9 + i * 2), (float) (Math.sin(c.time + i)));
			int col = Fx.withAlpha(look.colour(0.3), (float) (255 * Math.min(1, (1 - phase) * 3)));
			look.apply(mesh);
			mesh.pattern(Meshes.PATTERN_BARBS, 1);
			mesh.feather(p.x, p.y, p.z, q, 0.12 * w.scale, 0.04 * w.scale, 0.08, 0.01 * w.scale, col, Colours.lerp(col, 0xFF000000, 0.5), col);
		}
		Fx fx = c.fx;
		for (int i = 0; i < 10; i++) {
			double phase = frac(c.time * 0.35 + hash(i * 3.3 + w.side));
			Vector3d from = w.chain(0.6 + hash(i * 5.1) * 2.3);
			Vector3d p = new Vector3d(from).fma(-phase * 0.7 * w.scale, w.up).fma(phase * 0.25 * w.scale, w.back)
					.fma(Math.sin(c.time * 2 + i) * 0.06 * w.scale, w.out);
			fx.smoke(p.x, p.y, p.z, 0.025 * w.scale, Fx.withAlpha(0xFF2A2626, (float) (160 * Math.sin(Math.PI * phase))), i * 1.7, c.time + i);
		}
	}
	private static Quaternionf frame(Vector3d along, Vector3d normal) {
		Vector3f x = new Vector3f((float) along.x, (float) along.y, (float) along.z).normalize();
		Vector3f z = new Vector3f(x).cross((float) normal.x, (float) normal.y, (float) normal.z).normalize();
		Vector3f y = new Vector3f(z).cross(x).normalize();
		return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
	}
	static double hash(double n) {
		double x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
		return x - Math.floor(x);
	}
	static double frac(double x) {
		return x - Math.floor(x);
	}
}
