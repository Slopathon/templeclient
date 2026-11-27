package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.GlowLook;
import nz.temple.client.render.Fx;

public final class SpiritsPiece extends AuraPiece {
	private final GlowLook look;
	public SpiritsPiece(Host host) {
		super(host, "Spirits", false, "Three wisps weaving around you with tails of light.", true);
		look = new GlowLook(host::add, "Spirit", this::showsNew);
	}
	@Override
	public void draw(AuraContext c) {
		double s = c.size;
		double t = c.time;
		double cx = c.feet.x, cy = c.feet.y + c.player.getBbHeight() * 0.55, cz = c.feet.z;
		Fx fx = c.fx;
		look.markGlow();
		fx.additive(true);
		double[][] heads = new double[3][];
		for (int i = 0; i < 3; i++) {
			heads[i] = at(cx, cy, cz, t, i, s);
		}
		for (int i = 0; i < 3; i++) {
			double near = 0;
			for (int j = 0; j < 3; j++) {
				if (j != i) {
					double d = Math.sqrt(sq(heads[i][0] - heads[j][0]) + sq(heads[i][1] - heads[j][1]) + sq(heads[i][2] - heads[j][2]));
					near = Math.max(near, Math.max(0, 1 - d / (0.5 * s)));
				}
			}
			int steps = 22;
			double[] path = new double[steps * 3];
			for (int k = 0; k < steps; k++) {
				double[] p = at(cx, cy, cz, t - (steps - 1 - k) * 0.035, i, s);
				path[k * 3] = p[0];
				path[k * 3 + 1] = p[1];
				path[k * 3 + 2] = p[2];
			}
			double u = i / 3.0;
			fx.ribbon(path, steps, 0.07 * s * (1 + near * 0.5), 0.004 * s, look.colour(u, 0.75), look.colour(u + 0.3, 0), 0.55);
			double[] h = heads[i];
			fx.light(h[0], h[1], h[2], 0.09 * s * (1 + near * 0.6), look.colour(u, 1), 0.85);
			fx.glow(h[0], h[1], h[2], 0.3 * s * (1 + near), look.colour(u, 0.22 + near * 0.3));
			for (int m = 0; m < 6; m++) {
				double phase = frac(t * 0.9 + m * 0.17 + i * 0.31);
				double[] p = at(cx, cy, cz, t - phase * 0.6, i, s);
				double dx = Math.sin(m * 3.1 + i) * phase * 0.12 * s, dy = phase * 0.15 * s, dz = Math.cos(m * 2.3 + i) * phase * 0.12 * s;
				fx.light(p[0] + dx, p[1] + dy, p[2] + dz, 0.018 * s, look.colour(u, 0.8 * (1 - phase)), 0.9);
			}
		}
		fx.additive(false);
	}
	private static double[] at(double x, double y, double z, double t, int i, double s) {
		double a = t * 1.3 + i * Math.PI * 2 / 3;
		double r = (0.7 + 0.14 * Math.sin(t * 0.7 + i)) * s;
		double tilt = i * 1.1;
		double lx = Math.cos(a) * r, lz = Math.sin(a) * r;
		double ly = Math.sin(a * 2) * 0.3 * s + Math.sin(t * 0.9 + i * 2) * 0.14 * s;
		return new double[] {x + lx * Math.cos(tilt) - lz * Math.sin(tilt), y + ly, z + lx * Math.sin(tilt) + lz * Math.cos(tilt)};
	}
	private static double sq(double v) {
		return v * v;
	}
	private static double frac(double x) {
		return x - Math.floor(x);
	}
}
