package nz.temple.client.cosmetic.aura;

import net.minecraft.world.phys.Vec3;
import nz.temple.client.cosmetic.GlowLook;
import nz.temple.client.render.Fx;
import nz.temple.cosmetic.Colours;
import org.joml.Vector3d;

public final class CrownOfFirePiece extends AuraPiece {
	private final GlowLook look;
	public CrownOfFirePiece(Host host) {
		super(host, "Crown of fire", false, "A ring of living flame floating over the head.", false);
		look = new GlowLook(host::add, "Flame", this::showsNew);
	}
	private static final class State {
		double vx;
		double vz;
	}
	@Override
	public void draw(AuraContext c) {
		if (c.cameraInHead) {
			return;
		}
		State st = state(c.player.getUUID(), State::new);
		double ease = 1 - Math.exp(-5 * c.dt);
		st.vx += ((c.player.getX() - c.player.xo) - st.vx) * ease;
		st.vz += ((c.player.getZ() - c.player.zo) - st.vz) * ease;
		double s = c.size;
		double t = c.time;
		Vector3d base = c.anchor.headPoint(0, c.anchor.headTop() + 0.04, 0);
		double radius = 0.3 * s;
		Fx fx = c.fx;
		look.markGlow();
		fx.additive(false);
		int tongues = 14;
		java.util.List<Object[]> drawn = new java.util.ArrayList<>();
		for (int layer = 0; layer < 2; layer++) {
			for (int i = 0; i < tongues; i++) {
				double a = Math.PI * 2 * (i + layer * 0.5) / tongues + t * 0.15;
				double seed = i * 3.7 + layer * 11.3;
				double flicker = 0.7 + 0.3 * noise(t * 3.1 + seed) + 0.15 * noise(t * 7.3 + seed * 2);
				double h = (layer == 0 ? 0.3 : 0.2) * s * flicker;
				double r = radius * (layer == 0 ? 1 : 0.86);
				double bx = base.x + Math.cos(a) * r, by = base.y, bz = base.z + Math.sin(a) * r;
				int points = 7;
				double[] path = new double[points * 3];
				double[] width = new double[points];
				int[] colours = new int[points];
				for (int k = 0; k < points; k++) {
					double f = k / (double) (points - 1);
					double sway = Math.sin(t * 5.2 + seed + f * 3) * 0.035 * s * f;
					double lean = Math.pow(f, 1.5) * h;
					path[k * 3] = bx + Math.cos(a + 1.57) * sway - st.vx * 3.2 * lean + Math.cos(a) * 0.02 * f;
					path[k * 3 + 1] = by + f * h;
					path[k * 3 + 2] = bz + Math.sin(a + 1.57) * sway - st.vz * 3.2 * lean + Math.sin(a) * 0.02 * f;
					width[k] = (layer == 0 ? 0.085 : 0.06) * s * Math.pow(1 - f, 0.8) * (0.6 + 0.4 * Math.sin(Math.PI * Math.min(1, f * 2.5 + 0.2)));
					colours[k] = flameColour(f, i / (double) tongues, layer);
				}
				fx.ribbon(path, points, width, colours, layer == 0 ? 0.35 : 0.6);
				fx.additive(true);
				double[] core = new double[points];
				int[] hot = new int[points];
				for (int k = 0; k < points; k++) {
					core[k] = width[k] * 0.45;
					hot[k] = alpha(Colours.lerp(0xFFFFF4D0, look.colour(0.1), k / (double) (points - 1)), 0.3 * Math.pow(1 - k / (double) (points - 1), 2));
				}
				fx.ribbon(path, points, core, hot, 0.8);
				fx.additive(false);
				if (layer == 0) {
					drawn.add(new Object[] {path, width, colours});
				}
			}
		}
		fx.bloom(true);
		for (Object[] d : drawn) {
			fx.ribbon((double[]) d[0], 7, (double[]) d[1], dim((int[]) d[2], 0.5), 0.2);
		}
		fx.bloom(false);
		fx.additive(true);
		Vec3 ax = new Vec3(1, 0, 0);
		Vec3 az = new Vec3(0, 0, 1);
		fx.softRing(base.x, base.y, base.z, ax, az, radius * 1.15, alpha(look.colour(0), 0.55), 0.5);
		fx.softPool(base.x, base.y - 0.03, base.z, ax, az, radius * 1.3, alpha(look.colour(0.3), 0.25));
		for (int i = 0; i < 12; i++) {
			double phase = frac(t * 0.55 + hash(i));
			double a = hash(i * 7.1) * Math.PI * 2;
			double rise = phase * 0.7 * s;
			double drift = Math.sin(t * 2 + i) * 0.05 * s;
			double ex = base.x + Math.cos(a) * radius * (1 + phase * 0.3) + drift - st.vx * 6 * phase;
			double ey = base.y + 0.1 * s + rise;
			double ez = base.z + Math.sin(a) * radius * (1 + phase * 0.3) - drift - st.vz * 6 * phase;
			double fade = Math.sin(Math.PI * Math.min(1, phase * 1.2)) * (1 - phase);
			fx.light(ex, ey, ez, 0.03 * s, alpha(Colours.lerp(0xFFFFF0C8, look.colour(0.2), phase), 0.9 * fade), 0.8);
		}
		fx.glow(base.x, base.y + 0.22 * s, base.z, 0.45 * s, alpha(look.colour(0.5), 0.12));
		fx.additive(false);
	}
	private int flameColour(double f, double u, int layer) {
		int body = Colours.lerp(look.colour(u), 0xFFFF7A10, 0.3);
		int deep = Colours.lerp(body, 0xFF6A0C00, 0.55);
		int c;
		if (f < 0.1) {
			c = Colours.lerp(0xFFFFF2C0, body, f / 0.1);
		} else {
			c = Colours.lerp(body, deep, (f - 0.1) / 0.9);
		}
		double fade = f < 0.08 ? f / 0.08 * 0.6 + 0.4 : Math.pow(1 - (f - 0.08) / 0.92, 1.2);
		return alpha(c, fade * (layer == 0 ? 0.9 : 1.0));
	}
	private static int[] dim(int[] colours, double k) {
		int[] out = new int[colours.length];
		for (int i = 0; i < colours.length; i++) {
			out[i] = Fx.withAlpha(colours[i], (float) (Fx.alpha(colours[i]) * k));
		}
		return out;
	}
	private int alpha(int rgb, double a) {
		return Fx.withAlpha(rgb, (float) (255 * Math.max(0, Math.min(1, a)) * look.alpha()));
	}

	private static double hash(double n) {
		double x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
		return x - Math.floor(x);
	}
	private static double frac(double x) {
		return x - Math.floor(x);
	}

	private static double noise(double x) {
		double i = Math.floor(x);
		double f = x - i;
		double u = f * f * (3 - 2 * f);
		return hash(i) * (1 - u) + hash(i + 1) * u;
	}
}
