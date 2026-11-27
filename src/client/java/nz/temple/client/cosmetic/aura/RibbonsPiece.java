package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Meshes;
import nz.temple.cosmetic.Colours;
import nz.temple.cosmetic.Finishes;

public final class RibbonsPiece extends AuraPiece {
	private final PieceLook look;
	public RibbonsPiece(Host host) {
		super(host, "Ribbons", false, "Two cloth ribbons spiralling up around the body.", true);
		look = new PieceLook(host::add, "Ribbon", PieceLook.of(Finishes.SILK, Finishes.SATIN, Finishes.LIGHT), Finishes.SILK, 30, this::showsNew);
	}
	private static final class State {
		double fastX;
		double fastZ;
		double slowX;
		double slowZ;
	}
	@Override
	public void draw(AuraContext c) {
		State st = state(c.player.getUUID(), State::new);
		double dt = Math.max(1e-3, Math.min(0.1, c.dt));
		double vx = (c.player.getX() - c.player.xo) * 20;
		double vz = (c.player.getZ() - c.player.zo) * 20;
		double fast = 1 - Math.exp(-16 * dt);
		double slow = 1 - Math.exp(-6 * dt);
		st.fastX += (vx - st.fastX) * fast;
		st.fastZ += (vz - st.fastZ) * fast;
		st.slowX += (vx - st.slowX) * slow;
		st.slowZ += (vz - st.slowZ) * slow;
		double s = c.size;
		double t = c.time;
		double height = c.player.getBbHeight() + 0.3;
		double px = c.feet.x, py = c.feet.y, pz = c.feet.z;
		Meshes mesh = c.mesh;
		mesh.lightFrom(c.lightPos);
		for (int i = 0; i < 2; i++) {
			int index = i;
			for (int face = -1; face <= 1; face += 2) {
				int f = face;
				look.apply(mesh);
				mesh.pattern(Meshes.PATTERN_WEAVE, 14);
				mesh.accent(0, look.glowColour(i * 0.5));
				mesh.accentAt((u, v) -> look.glow() * 0.6 * Math.pow(Math.max(0, Math.sin(v * 9 - t * 2.5 + index)), 6));
				mesh.surface((u, v, o) -> {
					double a = v * Math.PI * 2.2 + t * 1.0 + index * Math.PI;
					double r = (0.55 + 0.06 * Math.sin(v * 6 + t * 1.8 + index)) * s;
					double drag = v * v * 0.065;
					double dvx = st.fastX + (st.slowX - st.fastX) * v;
					double dvz = st.fastZ + (st.slowZ - st.fastZ) * v;
					double y = py + 0.05 + v * height * s + Math.sin(v * 5 - t * 2.2) * 0.05 * s;
					double half = 0.1 * s * Math.sin(Math.PI * Math.min(1, Math.max(0, v))) + 0.004;
					double w = (u - 0.5) * 2 * half;
					double fold = Math.sin(u * Math.PI * 3 + v * 14 - t * 3) * 0.012 * s;
					double tx = -Math.sin(a), tz = Math.cos(a);
					double nx = Math.cos(a), nz = Math.sin(a);
					double thick = f * 0.003 * s;
					o[0] = px + nx * (r + fold + thick) + tx * w * 0.35 - dvx * drag;
					o[1] = y + w;
					o[2] = pz + nz * (r + fold + thick) + tz * w * 0.35 - dvz * drag;
					o[3] = nx * f;
					o[4] = -0.2 * f;
					o[5] = nz * f;
				}, 3, 56, 0, (u, v) -> {
					int base = look.colour(f > 0 ? 0.65 : 0.35);
					int c0 = f > 0 ? base : Colours.lerp(base, 0xFF000000, 0.25);
					double fade = Math.min(1, Math.min(v, 1 - v) * 7);
					return Colours.withAlpha(c0, fade);
				});
				mesh.accentAt(null);
			}
		}
		mesh.lightFrom(null);
	}
}
