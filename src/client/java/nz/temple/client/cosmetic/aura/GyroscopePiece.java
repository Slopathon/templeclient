package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.ModelCache;
import nz.temple.cosmetic.Finishes;
import nz.temple.cosmetic.Model;
import nz.temple.setting.NumberSetting;
import org.joml.Matrix4d;
import org.joml.Vector3d;

public final class GyroscopePiece extends AuraPiece {
	private final NumberSetting rings;
	private final PieceLook look;
	public GyroscopePiece(Host host) {
		super(host, "Gyroscope", false, "Nested rings turning on different axes around the body.", true);
		rings = host.add(new NumberSetting("Rings", 3, 1, 5, 1, "").describe("Number of rings.").visibleWhen(on::get));
		look = new PieceLook(host::add, "Gyroscope", PieceLook.of(Finishes.BRASS, Finishes.SILVER, Finishes.CRYSTAL), Finishes.BRASS, 70,
				this::showsNew);
	}
	@Override
	public void draw(AuraContext c) {
		Model model = c.model("gyro_ring");
		if (model == null) {
			return;
		}
		double s = c.size;
		double t = c.time;
		int n = (int) Math.round(rings.get());
		double cx = c.feet.x, cy = c.feet.y + c.player.getBbHeight() * 0.55, cz = c.feet.z;
		Meshes.ModelPainter painter = painter(look, Meshes.PATTERN_ENGRAVED, 1);
		c.mesh.lightFrom(c.lightPos);
		double glow = look.glow();
		Fx fx = c.fx;
		for (int i = 0; i < n; i++) {
			double r = (0.75 + i * 0.16) * s;
			Matrix4d xf = new Matrix4d().translation(cx, cy, cz)
					.rotateY(t * (0.7 + i * 0.35))
					.rotateX(0.6 + i * 0.9 + Math.sin(t * 0.5 + i) * 0.4)
					.rotateZ(t * 0.3 * (i % 2 == 0 ? 1 : -1))
					.scale(r);
			double phase = t * (1 + i * 0.35) * 1.4;
			double cosPhase = Math.cos(phase), sinPhase = Math.sin(phase);
			c.mesh.model(model, xf, null, new Meshes.ModelPainter() {
				@Override
				public boolean part(Meshes m, Model.Part part) {
					return painter.part(m, part);
				}
				@Override
				public int colour(Model.Part part, double ramp) {
					return painter.colour(part, ramp);
				}
				@Override
				public double glowAt(Model.Part part, double mask, double ramp, float x, float y, float z) {

					return Math.min(1, mask * glow * (0.35 + 0.95 * sweepToward(x, z, cosPhase, sinPhase)));
				}
			});
			double a = t * (1.6 + i * 0.4);
			Vector3d bead = xf.transformPosition(new Vector3d(Math.cos(a) * 1.04, 0, Math.sin(a) * 1.04));
			look.apply(c.mesh);
			c.mesh.accent(glow, look.glowColour(i * 0.2));
			c.mesh.sphere(bead.x, bead.y, bead.z, 0.035 * s, look.colour(0.8), 10);
			if (glow > 0) {
				fx.additive(true);
				fx.glow(bead.x, bead.y, bead.z, 0.18 * s, HaloPiece.withAlpha(look.glowColour(i * 0.2), 0.35 * glow));
				fx.additive(false);
			}
		}
		c.mesh.lightFrom(null);
	}
}
