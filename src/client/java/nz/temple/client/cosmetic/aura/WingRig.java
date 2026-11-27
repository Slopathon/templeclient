package nz.temple.client.cosmetic.aura;

import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;

public final class WingRig {
	public static final class State {
		double open;
		double openVel;
		double sweep;
		double phase;
		double rate = 1.2;
		double amp = 0.03;
		double flick;
		double airTime;
		boolean airborne;
		double idle;
		double stretch;
		double nextStretch = 8;
	}
	private static final double[][] FOLDED = {{0.56, 0.56, -0.61}, {0.29, -0.29, -0.91}, {0.12, -0.92, -0.39}};
	private static final double[][] HALF = {{0.72, 0.18, -0.68}, {0.8, -0.32, -0.5}, {0.55, -0.74, -0.38}};
	private static final double[][] SPREAD = {{0.88, 0.36, -0.3}, {0.98, 0.06, -0.16}, {0.95, -0.2, -0.22}};
	private static final double[][] SWEPT = {{0.55, 0.16, -0.82}, {0.45, -0.04, -0.9}, {0.3, -0.1, -0.95}};
	public final Vector3d[] joints = new Vector3d[4];
	public final Vector3d[] bones = new Vector3d[3];
	public final Vector3d out;
	public final Vector3d up;
	public final Vector3d back;
	public final double open;

	public final double phase;
	public final double amp;
	public final double downstroke;
	private static final double SIZE = 1.75;
	public final double scale;
	public final int side;
	private WingRig(AuraContext c, State st, int side, double lengthScale) {
		this.side = side;
		double unit = c.anchor.px() * 16 * c.size * SIZE;
		this.scale = unit;
		this.open = st.open;
		this.phase = st.phase;
		this.amp = st.amp;
		this.downstroke = Math.max(0, -Math.cos(st.phase)) * Math.min(1, st.amp * 4);
		out = c.anchor.bodyDir(side, 0, 0);
		up = c.anchor.bodyDir(0, 1, 0);
		back = c.anchor.bodyDir(0, 0, -1);
		Vector3d root = c.anchor.bodyPoint(side * 0.09, -0.09, -0.14);
		joints[0] = root;
		double[] lengths = {0.2 * lengthScale, 0.27 * lengthScale, 0.22 * lengthScale};
		double o = Math.max(0, Math.min(1.15, st.open));
		for (int i = 0; i < 3; i++) {
			double[] a = o < 0.5 ? FOLDED[i] : HALF[i];
			double[] b = o < 0.5 ? HALF[i] : SPREAD[i];
			double k = o < 0.5 ? o / 0.5 : (o - 0.5) / 0.5;
			double x = a[0] + (b[0] - a[0]) * k, y = a[1] + (b[1] - a[1]) * k, z = a[2] + (b[2] - a[2]) * k;
			x += (SWEPT[i][0] - x) * st.sweep;
			y += (SWEPT[i][1] - y) * st.sweep;
			z += (SWEPT[i][2] - z) * st.sweep;
			double beat = Math.sin(st.phase - i * 0.45) * st.amp * (1.3 + i * 0.15);
			double cb = Math.cos(beat), sb = Math.sin(beat);
			double bx = x * cb - y * sb, by = x * sb + y * cb;
			Vector3d dir = c.anchor.bodyDir(side * bx, by, z);
			bones[i] = dir;
			joints[i + 1] = new Vector3d(joints[i]).fma(lengths[i] * unit, dir);
		}
	}
	public static WingRig[] pose(AuraContext c, State st, double speedSetting, double lengthScale, boolean spreadAtRest) {
		Player p = c.player;
		double dt = c.dt;
		boolean flying = p.isFallFlying();
		boolean swimming = p.isSwimming();
		boolean airborne = !p.onGround() && !p.isPassenger() && !p.getAbilities().flying || flying;
		double vy = p.getY() - p.yo;
		double moving = c.moving();
		double targetOpen, targetSweep, targetAmp, targetRate;
		if (flying) {
			targetOpen = 0.85;
			targetSweep = 1;
			targetAmp = 0.05;
			targetRate = 1.5;
		} else if (swimming) {
			targetOpen = 0.15;
			targetSweep = 0.85;
			targetAmp = 0.02;
			targetRate = 1;
		} else if (airborne) {
			boolean gliding = vy < -0.45;
			targetOpen = 1;
			targetSweep = 0;
			targetAmp = gliding ? 0.1 : 0.42;
			targetRate = gliding ? 2.2 : 5.5;
		} else if (p.isCrouching()) {
			targetOpen = 0;
			targetSweep = 0.25;
			targetAmp = 0.02;
			targetRate = 1;
		} else if (spreadAtRest) {
			targetOpen = 0.9;
			targetSweep = 0;
			targetAmp = 0.1 + 0.06 * moving;
			targetRate = 1.6 + 1.6 * moving;
		} else {
			targetOpen = moving > 0.05 ? 0.32 + 0.22 * moving : 0;
			targetSweep = 0;
			targetAmp = 0.03 + 0.08 * moving;
			targetRate = 1.2 + 2.2 * moving;
			if (moving < 0.05) {
				st.idle += dt;
				if (st.stretch == 0 && st.idle > st.nextStretch) {
					st.stretch = 1e-6;
				}
			} else {
				st.idle = 0;
				st.stretch = 0;
			}
			if (st.stretch > 0) {
				st.stretch += dt;
				double k = st.stretch / 2.6;
				if (k >= 1) {
					st.stretch = 0;
					st.idle = 0;
					st.nextStretch = 9 + 6 * Math.abs(Math.sin(st.phase * 12.9898));
				} else {
					double env = Math.sin(Math.PI * k);
					targetOpen = Math.max(targetOpen, 0.9 * env);
					targetAmp = Math.max(targetAmp, 0.16 * env);
					targetRate = Math.max(targetRate, 2.4);
				}
			}
		}
		if (airborne) {
			st.airTime += dt;
		} else {
			if (st.airborne && st.airTime > 0.35) {
				st.flick = 1;
			}
			st.airTime = 0;
		}
		st.airborne = airborne;
		st.flick *= Math.exp(-3.5 * dt);
		targetOpen = Math.min(1.1, targetOpen + st.flick * 0.75);
		st.openVel += ((targetOpen - st.open) * 38 - st.openVel * 9) * dt;
		st.open += st.openVel * dt;
		double ease = 1 - Math.exp(-3.0 * dt);
		st.sweep += (targetSweep - st.sweep) * (1 - Math.exp(-2.5 * dt));
		st.amp += (targetAmp - st.amp) * ease;
		st.rate += (targetRate * speedSetting - st.rate) * ease;
		st.phase = (st.phase + st.rate * dt) % (Math.PI * 2000);
		return new WingRig[] {new WingRig(c, st, -1, lengthScale), new WingRig(c, st, 1, lengthScale)};
	}
	public Vector3d chain(double c) {
		int i = Math.max(0, Math.min(2, (int) Math.floor(c)));
		double k = Math.max(0, Math.min(1, c - i));
		return new Vector3d(joints[i]).lerp(joints[i + 1], k);
	}
	public Vector3d trail(int i) {
		Vector3d hang = new Vector3d(up).mul(-0.85).add(new Vector3d(back).mul(0.35 + i * 0.1)).normalize();
		Vector3d d = bones[i];
		Vector3d t = new Vector3d(hang).sub(new Vector3d(d).mul(hang.dot(d)));
		if (t.lengthSquared() < 1e-8) {
			t.set(back);
		}
		return t.normalize();
	}
	public Vector3d normal(int i) {
		Vector3d n = new Vector3d(bones[i]).cross(trail(i)).normalize();
		if (n.dot(back) < 0) {
			n.negate();
		}
		return n;
	}
}
