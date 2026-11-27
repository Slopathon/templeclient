package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.ModelCache;
import nz.temple.cosmetic.Finishes;
import nz.temple.cosmetic.Model;
import org.joml.Matrix4d;
import org.joml.Vector3d;

public final class CrystalsPiece extends AuraPiece {
	private final PieceLook look;
	public CrystalsPiece(Host host) {
		super(host, "Crystals", false, "Crystal clusters circling the head.", true);
		look = new PieceLook(host::add, "Crystal", PieceLook.of(Finishes.QUARTZ, Finishes.AMETHYST, Finishes.CRYSTAL), Finishes.QUARTZ, 35,
				this::showsNew);
	}
	@Override
	public void draw(AuraContext c) {
		Model model = c.model("crystal_cluster");
		if (model == null) {
			return;
		}
		double s = c.size;
		int count = 4;
		double radius = 0.62 * s;
		double y = c.player.getBbHeight() + 0.12 * s;
		Meshes.ModelPainter painter = painter(look, Meshes.PATTERN_FRACTURE, 6);
		c.mesh.lightFrom(c.lightPos);
		double glow = look.glow();
		for (int i = 0; i < count; i++) {
			double t = c.time;
			double[] at = orbit(c, t, i, count, radius, y, s);
			Matrix4d xf = new Matrix4d().translation(at[0], at[1], at[2])
					.rotateY(t * 0.6 + i * 1.7).rotateX(0.15 * Math.sin(t * 0.5 + i)).rotateZ(0.12 * Math.cos(t * 0.4 + i * 2))
					.translate(0, -0.16 * s, 0).scale(s * (1.25 + 0.2 * ((i * 7) % 3)));
			c.mesh.model(model, xf, null, painter);
			if (glow > 0) {
				int steps = 10;
				double[] path = new double[steps * 3];
				for (int k = 0; k < steps; k++) {
					double[] p = orbit(c, t - (steps - 1 - k) * 0.06, i, count, radius, y, s);
					path[k * 3] = p[0];
					path[k * 3 + 1] = p[1];
					path[k * 3 + 2] = p[2];
				}
				Fx fx = c.fx;
				fx.additive(true);
				fx.ribbon(path, steps, 0.07 * s, 0.005 * s, HaloPiece.withAlpha(look.glowColour(i / (double) count), 0.35 * glow),
						HaloPiece.withAlpha(look.glowColour(0), 0), 0.4);
				Vector3d core = xf.transformPosition(new Vector3d(0, 0.1, 0));
				fx.glow(core.x, core.y, core.z, 0.12 * s, HaloPiece.withAlpha(look.glowColour(i / (double) count), 0.18 * glow));
				fx.additive(false);
			}
		}
		c.mesh.lightFrom(null);
	}
	private static double[] orbit(AuraContext c, double t, int i, int count, double radius, double y, double s) {
		double a = t * 0.8 + Math.PI * 2 * i / count;
		double bob = Math.sin(t * 1.6 + i * 1.3) * 0.07 * s;
		return new double[] {c.feet.x + Math.cos(a) * radius, c.feet.y + y + bob, c.feet.z + Math.sin(a) * radius};
	}
}
