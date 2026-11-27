package nz.temple.client.cosmetic.aura;

import net.minecraft.world.phys.Vec3;
import nz.temple.client.cosmetic.GlowLook;
import nz.temple.client.render.Fx;

public final class RunesPiece extends AuraPiece {
	private static final Vec3 X = new Vec3(1, 0, 0);
	private static final Vec3 Z = new Vec3(0, 0, 1);
	private final GlowLook look;
	public RunesPiece(Host host) {
		super(host, "Rune circle", false, "A magic circle that draws itself at your feet while you stand still.", true);
		look = new GlowLook(host::add, "Rune", this::showsNew);
	}
	private static final class State {
		double drawn;
	}

	@Override
	public void draw(AuraContext c) {
		State st = state(c.player.getUUID(), State::new);
		boolean still = c.moving() < 0.08 && c.player.onGround();
		st.drawn = still ? Math.min(1, st.drawn + c.dt * 0.6) : Math.max(0, st.drawn - c.dt * 1.8);
		if (st.drawn <= 0.001) {
			return;
		}
		double p = st.drawn;
		double s = c.size;
		double t = c.time;
		double x = c.feet.x, y = c.feet.y + 0.02, z = c.feet.z;
		double big = 1.05 * s;
		double turn = t * 0.3;
		Fx fx = c.fx;
		look.markGlow();
		fx.additive(true);
		fx.softPool(x, y - 0.01, z, X, Z, big * 1.25, a(0.3, 0.18 * p));
		double sweep = smooth(0, 0.3, p);
		arcRing(fx, x, y, z, big, 0.022 * s, sweep, turn, a(0.0, 0.95));
		arcRing(fx, x, y, z, big - 0.15 * s, 0.012 * s, sweep, -turn * 1.3, a(0.15, 0.85));
		arcRing(fx, x, y, z, big * 0.42, 0.016 * s, smooth(0.15, 0.4, p), turn * 2, a(0.4, 0.8));
		double runeR = big - 0.077 * s;
		double cell = 0.045 * s;
		for (int i = 0; i < 18; i++) {
			double show = smooth(0.3 + i * 0.02, 0.36 + i * 0.02, p);
			if (show <= 0) {
				continue;
			}
			double ang = turn + Math.PI * 2 * i / 18;
			double cx = x + Math.cos(ang) * runeR, cz = z + Math.sin(ang) * runeR;
			double tx = -Math.sin(ang), tz = Math.cos(ang), rx = Math.cos(ang), rz = Math.sin(ang);
			double pulse = 0.7 + 0.3 * Math.sin(t * 2 - i * 0.6);
			int colour = a(i / 18.0, 0.95 * show * pulse);
			for (int k = 0; k < 3; k++) {
				int h = (int) Math.abs((long) (Math.sin((i + 1) * 12.9898 + k * 78.233) * 43758.5453));
				int from = h % 9, to = (h / 9) % 9;
				if (from == to) {
					to = (to + 4) % 9;
				}
				double ax = (from % 3 - 1) * cell, ay = (from / 3 - 1) * cell, bx = (to % 3 - 1) * cell, by = (to / 3 - 1) * cell;
				flatLine(fx, cx + tx * ax + rx * ay, cz + tz * ax + rz * ay, cx + tx * bx + rx * by, cz + tz * bx + rz * by, y + 0.002,
						0.011 * s, colour);
			}
		}
		double seal = smooth(0.7, 0.95, p);
		if (seal > 0) {
			double triR = big - 0.17 * s;
			for (int tri = 0; tri < 2; tri++) {
				double base = (tri == 0 ? -turn * 1.4 : turn * 0.9) + tri * Math.PI / 3;
				for (int k = 0; k < 3; k++) {
					double a0 = base + Math.PI * 2 * k / 3, a1 = base + Math.PI * 2 * (k + 1) / 3;
					flatLine(fx, x + Math.cos(a0) * triR, z + Math.sin(a0) * triR, x + Math.cos(a1) * triR, z + Math.sin(a1) * triR, y + 0.003,
							0.017 * s, a(0.25 + tri * 0.3, 0.8 * seal));
				}
			}
			for (int i = 0; i < 8; i++) {
				double ang = -turn * 2 + Math.PI * 2 * i / 8;
				fx.light(x + Math.cos(ang) * big * 0.42, y + 0.02, z + Math.sin(ang) * big * 0.42, 0.05 * s, a(0.5 + i / 16.0, 0.9 * seal), 0.7);
			}
			for (int i = 0; i < 14; i++) {
				double phase = frac(t * 0.4 + i * 0.137);
				double ang = i * 2.39996 + t * 0.1;
				double rise = phase * 1.2 * s;
				double fade = Math.sin(Math.PI * phase) * seal;
				fx.light(x + Math.cos(ang) * big * (1 - phase * 0.15), y + rise, z + Math.sin(ang) * big * (1 - phase * 0.15), 0.025 * s,
						a(i / 14.0, 0.8 * fade), 0.8);
			}
		}
		fx.additive(false);
	}
	private int a(double t, double alpha) {
		return look.colour(t, alpha);
	}

	private static void arcRing(Fx fx, double x, double y, double z, double r, double width, double sweep, double start, int argb) {
		int segments = 72;
		int n = (int) Math.ceil(segments * sweep);
		for (int i = 0; i < n; i++) {
			double a0 = start + Math.PI * 2 * i / segments;
			double a1 = start + Math.PI * 2 * Math.min(i + 1, segments * sweep) / segments;
			double r0 = r - width / 2, r1 = r + width / 2;
			fx.quad(x + Math.cos(a0) * r0, y, z + Math.sin(a0) * r0, argb, x + Math.cos(a1) * r0, y, z + Math.sin(a1) * r0, argb,
					x + Math.cos(a1) * r1, y, z + Math.sin(a1) * r1, argb, x + Math.cos(a0) * r1, y, z + Math.sin(a0) * r1, argb);
		}
	}
	private static void flatLine(Fx fx, double ax, double az, double bx, double bz, double y, double width, int argb) {
		double dx = bx - ax, dz = bz - az;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-6) {
			return;
		}
		double nx = -dz / len * width / 2, nz = dx / len * width / 2;
		fx.quad(ax - nx, y, az - nz, argb, bx - nx, y, bz - nz, argb, bx + nx, y, bz + nz, argb, ax + nx, y, az + nz, argb);
	}
	private static double smooth(double a, double b, double x) {
		double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
		return t * t * (3 - 2 * t);
	}
	private static double frac(double x) {
		return x - Math.floor(x);
	}
}
