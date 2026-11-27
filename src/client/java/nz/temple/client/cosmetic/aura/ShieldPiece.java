package nz.temple.client.cosmetic.aura;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.cosmetic.GlowLook;
import nz.temple.client.render.Meshes;
import nz.temple.cosmetic.Colours;
import nz.temple.cosmetic.Finishes;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class ShieldPiece extends AuraPiece {
	private final GlowLook look;
	public ShieldPiece(Host host) {
		super(host, "Shield shell", false, "A hexagonal energy shell that ripples where it is touched.", true);
		look = new GlowLook(host::add, "Shield", this::showsNew);
	}
	private record Impact(double x, double y, double z, double born, double strength) {
	}

	private static final class State {
		final ArrayDeque<Impact> impacts = new ArrayDeque<>();
		int lastHurt;
		double nextDrop;
	}
	@Override
	public void draw(AuraContext c) {
		State st = state(c.player.getUUID(), State::new);
		double t = c.time;
		double s = c.size;
		double rx = 1.0 * s, ry = 1.2 * s;
		double cx = c.feet.x, cy = c.feet.y + 0.85 * s, cz = c.feet.z;
		if (c.player.hurtTime > st.lastHurt && c.player.hurtTime >= 8) {
			Vec3 from = c.player.getLastHurtByMob() != null ? c.player.getLastHurtByMob().position().subtract(c.feet) : c.player.getLookAngle();
			Vec3 d = new Vec3(from.x, Math.max(-0.2, from.y), from.z).normalize();
			st.impacts.add(new Impact(d.x, d.y, d.z, t, 1));
		}
		st.lastHurt = c.player.hurtTime;
		BlockPos above = BlockPos.containing(cx, cy + ry + 0.5, cz);
		if (c.player.level().isRainingAt(above) && t > st.nextDrop) {
			double a = Math.random() * Math.PI * 2;
			double up = 0.45 + Math.random() * 0.55;
			double h = Math.sqrt(1 - up * up);
			st.impacts.add(new Impact(Math.cos(a) * h, up, Math.sin(a) * h, t, 0.35));
			st.nextDrop = t + 0.06 + Math.random() * 0.1;
		}
		for (Iterator<Impact> it = st.impacts.iterator(); it.hasNext(); ) {
			if (t - it.next().born > 1.4) {
				it.remove();
			}
		}
		while (st.impacts.size() > 24) {
			st.impacts.removeFirst();
		}
		Meshes mesh = c.mesh;
		mesh.finish(Finishes.ENERGY);
		mesh.pattern(Meshes.PATTERN_HEX, 1);
		int glowColour = look.colour(0);
		mesh.accent(0, glowColour);
		look.markGlow();
		double sweep = (t * 0.35) % 1.6 - 0.3;
		Impact[] hits = st.impacts.toArray(new Impact[0]);
		mesh.accentAt((u, v) -> {
			double[] d = dir(u, v);
			double w = 0.05 + 0.25 * Math.exp(-Math.pow(((d[1] + 1) / 2 - sweep) / 0.06, 2));
			for (Impact h : hits) {
				double age = t - h.born;
				double ang = Math.acos(Math.max(-1, Math.min(1, d[0] * h.x + d[1] * h.y + d[2] * h.z)));
				double front = age * (h.strength > 0.5 ? 2.6 : 1.6);
				double ring = Math.exp(-Math.pow((ang - front) / 0.12, 2)) * Math.max(0, 1 - age / 1.4);
				double flash = Math.exp(-ang * ang / 0.02) * Math.max(0, 1 - age * 4);
				w += (ring * 0.9 + flash) * h.strength;
			}
			return Math.min(1, w * look.alpha());
		});
		int body = Colours.withAlpha(Colours.lerp(glowColour, 0xFFFFFFFF, 0.5), 0.18 * look.alpha());
		mesh.lightFrom(c.lightPos);
		mesh.surface((u, v, o) -> {
			double[] d = dir(u, v);
			o[0] = cx + d[0] * rx;
			o[1] = cy + d[1] * ry;
			o[2] = cz + d[2] * rx;
			o[3] = d[0] / rx;
			o[4] = d[1] / ry;
			o[5] = d[2] / rx;
		}, 40, 22, body, null);
		mesh.accentAt(null);
		mesh.lightFrom(null);
	}
	private static double[] dir(double u, double v) {
		double theta = u * Math.PI * 2;
		double phi = -0.55 + v * (Math.PI / 2 + 0.55);
		return new double[] {Math.cos(phi) * Math.cos(theta), Math.sin(phi), Math.cos(phi) * Math.sin(theta)};
	}
}
