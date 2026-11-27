package nz.temple.client.cosmetic.aura;

import net.minecraft.world.phys.Vec3;
import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.ModelCache;
import nz.temple.cosmetic.Finishes;
import nz.temple.cosmetic.Model;
import org.joml.Matrix4d;
import org.joml.Vector3d;

public final class HaloPiece extends AuraPiece {
	private final PieceLook look;
	public HaloPiece(Host host) {
		super(host, "Halo", false, "An engraved ring above the head that gives off a soft light.", true);
		look = new PieceLook(host::add, "Halo", PieceLook.of(Finishes.GOLD, Finishes.LIGHT, Finishes.CRYSTAL), Finishes.GOLD, 70, this::showsNew);
	}
	@Override
	public void draw(AuraContext c) {
		Model model = c.model("halo_ring");
		if (model == null) {
			return;
		}
		double s = c.size;
		double bob = Math.sin(c.time * 1.4) * 0.025 * s;
		double[] st = state(c.player.getUUID(), () -> new double[] {0, 0, 0, 0, c.player.getYHeadRot(), c.player.getXRot()});
		double dt = Math.max(1e-3, Math.min(0.1, c.dt));
		double yawVel = net.minecraft.util.Mth.wrapDegrees(c.player.getYHeadRot() - st[4]) / dt;
		double pitchVel = (c.player.getXRot() - st[5]) / dt;
		st[4] = c.player.getYHeadRot();
		st[5] = c.player.getXRot();
		double roll = spring(st, 0, Math.max(-0.45, Math.min(0.45, yawVel * 0.0022)), 60, 7, dt);
		double pitch = spring(st, 2, Math.max(-0.4, Math.min(0.4, pitchVel * 0.0018 + c.moving() * 0.18)), 55, 7, dt);
		Matrix4d xf = c.anchor.head().translate(0, c.anchor.headTop() + 0.2 * s + bob, -0.05)
				.rotateZ(roll).rotateX(-0.28 + pitch).rotateY(c.time * 0.35).scale(s);
		Meshes mesh = c.mesh;
		mesh.lightFrom(c.lightPos);
		double ringGlow = look.glow();
		double cosShine = Math.cos(c.time * 1.1), sinShine = Math.sin(c.time * 1.1);
		mesh.model(model, xf, null, new Meshes.ModelPainter() {
			private final Meshes.ModelPainter base = painter(look, Meshes.PATTERN_ENGRAVED, 1);
			@Override
			public boolean part(Meshes m, Model.Part part) {
				return base.part(m, part);
			}
			@Override
			public int colour(Model.Part part, double ramp) {
				return base.colour(part, ramp);
			}
			@Override
			public double glowAt(Model.Part part, double mask, double ramp, float x, float y, float z) {
				double shine = sweepToward(x, z, cosShine, sinShine) + sweepToward(-x, -z, cosShine, sinShine);
				return Math.min(1, mask * ringGlow * (0.45 + 0.9 * shine));
			}
		});
		mesh.lightFrom(null);
		double glow = look.glow();
		if (glow <= 0) {
			return;
		}
		Fx fx = c.fx;
		Vector3d centre = xf.transformPosition(new Vector3d());
		Vector3d down = c.anchor.headDir(0, -1, 0);
		fx.additive(true);
		int n = 24;
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			Vector3d p = xf.transformPosition(new Vector3d(Math.cos(a) * 0.3, 0, Math.sin(a) * 0.3));
			double pulse = 0.6 + 0.4 * Math.sin(a * 2 - c.time * 1.6);
			fx.glow(p.x, p.y, p.z, 0.11 * s, look.glowColour(i / (double) n) & 0x00FFFFFF | ((int) (60 * glow * pulse) << 24));
		}
		fx.glow(centre.x, centre.y, centre.z, 0.5 * s, withAlpha(look.glowColour(0), 0.1 * glow));
		for (int i = 0; i < 7; i++) {
			double phase = (c.time * 0.32 + i / 7.0) % 1;
			double a = i * 2.39996 + Math.floor(c.time * 0.32 + i / 7.0) * 1.7;
			Vector3d from = xf.transformPosition(new Vector3d(Math.cos(a) * 0.3, 0, Math.sin(a) * 0.3));
			double fall = phase * 0.55 * s;
			double sway = Math.sin(phase * 7 + i) * 0.03 * s;
			double fade = Math.sin(Math.PI * Math.min(1, phase * 1.4)) * (1 - phase);
			fx.light(from.x + down.x * fall + sway, from.y + down.y * fall, from.z + down.z * fall + sway * 0.6, 0.014 * s,
					withAlpha(look.glowColour(i / 7.0), 0.85 * glow * fade), 0.8);
		}
		int shafts = 9;
		for (int i = 0; i < shafts; i++) {
			double a = Math.PI * 2 * i / shafts + c.time * 0.25;
			Vector3d top = xf.transformPosition(new Vector3d(Math.cos(a) * 0.27, 0, Math.sin(a) * 0.27));
			double reach = (0.75 + 0.2 * Math.sin(c.time * 0.9 + i * 1.7)) * s;
			double flicker = 0.55 + 0.45 * Math.sin(c.time * 1.3 + i * 2.3);
			fx.softBeam(top.x, top.y, top.z, top.x + down.x * reach, top.y + down.y * reach, top.z + down.z * reach, 0.05 * s,
					withAlpha(look.glowColour(i / (double) shafts), 0.16 * glow * flicker), withAlpha(look.glowColour(0), 0));
		}
		Vector3d crown = c.anchor.headPoint(0, c.anchor.headTop() + 0.005, 0);
		Vector3d ax = c.anchor.headDir(1, 0, 0);
		Vector3d az = c.anchor.headDir(0, 0, 1);
		fx.softPool(crown.x, crown.y, crown.z, new Vec3(ax.x, ax.y, ax.z), new Vec3(az.x, az.y, az.z), 0.32 * s,
				withAlpha(look.glowColour(0), 0.22 * glow));
		fx.additive(false);
	}
	static int withAlpha(int rgb, double alpha) {
		return Fx.withAlpha(rgb, (float) (255 * Math.max(0, Math.min(1, alpha))));
	}
}
