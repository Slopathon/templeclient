package nz.temple.client.modules.cosmetic;

import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;

public final class SoftTrail {
	private SoftTrail() {
	}
	public static void trail(Fx fx, Look look, double[] path, int n, double[] radius, int[] colours, double glow) {
		if (n < 2) {
			return;
		}
		double[] body = scale(radius, 2);
		if (look.solid()) {
			Meshes mesh = fx.mesh();
			look.apply(mesh, 0.3f);
			mesh.tube(path, n, radius, colours, 14);
			fx.additive(true);
			fx.ribbon(path, n, scale(radius, 7), fade(colours, 0.2 * glow * Math.max(0.4, look.bloom())), 0);
			fx.additive(false);
			return;
		}
		if (look.matte()) {
			fx.additive(false);
			fx.ribbon(path, n, scale(radius, 2.4), colours, 0);
			return;
		}
		fx.additive(false);
		fx.ribbon(path, n, body, fade(colours, 0.55), 0);
		fx.additive(true);
		fx.ribbon(path, n, scale(radius, 1.7), colours, look.neon() ? 0.95 : 0.7);
		fx.ribbon(path, n, scale(radius, look.neon() ? 9 : 6), fade(colours, (look.neon() ? 0.3 : 0.18) * glow), 0);
		if (look.neon()) {
			Look.markGlow();
			fx.bloom(true);
			fx.ribbon(path, n, scale(radius, 3), fade(colours, 0.6 * glow), 0);
			fx.bloom(false);
		}
		fx.additive(false);
	}
	public static void head(Fx fx, Look look, double x, double y, double z, double r, int colour) {
		if (look.solid()) {
			Meshes mesh = fx.mesh();
			look.apply(mesh, 0.5f);
			mesh.sphere(x, y, z, r, colour, 14);
			fx.additive(true);
			fx.light(x, y, z, r * 4, Fx.fade(colour, 0.3 * Math.max(0.4, look.bloom())), 0);
			fx.additive(false);
			return;
		}
		fx.additive(!look.matte());
		fx.light(x, y, z, r * 1.6, colour, look.matte() ? 0.2 : 0.9);
		if (!look.matte()) {
			fx.light(x, y, z, r * 5, Fx.fade(colour, look.neon() ? 0.4 : 0.25), 0);
		}
		fx.additive(false);
	}
	public static double[] scale(double[] values, double k) {
		double[] out = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			out[i] = values[i] * k;
		}
		return out;
	}

	public static int[] fade(int[] colours, double k) {
		int[] out = new int[colours.length];
		for (int i = 0; i < colours.length; i++) {
			int a = (int) Math.round(Fx.alpha(colours[i]) * Math.max(0, Math.min(1, k)));
			out[i] = (a << 24) | (colours[i] & 0xFFFFFF);
		}
		return out;
	}
}
