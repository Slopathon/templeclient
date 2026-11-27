package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.ModelCache;
import nz.temple.cosmetic.Colours;
import nz.temple.cosmetic.Finishes;
import nz.temple.cosmetic.Model;
import org.joml.Matrix4d;
import org.joml.Vector3d;

public final class HatPiece extends AuraPiece {
	private static final double R = 0.66;
	private static final double H = 0.32;
	private final PieceLook look;
	private final nz.temple.setting.NumberSetting hatSize;

	public HatPiece(Host host) {
		super(host, "China hat", false, "A woven conical hat tied under the chin.", true);
		look = new PieceLook(host::add, "Hat", PieceLook.of(Finishes.STRAW, Finishes.LACQUERED, Finishes.GOLD), Finishes.STRAW, 20, this::showsNew);
		hatSize = host.add(new nz.temple.setting.NumberSetting("Hat size", 1, 0.4, 2.5, 0.05, "x")
				.describe("How big the hat is, on top of the module's overall size. It still sits on the head.")
				.visibleWhen(on::get));
	}
	public double hatSize() {
		return hatSize.get();
	}
	@Override
	public void draw(AuraContext c) {
		Model model = c.model("china_hat");
		if (model == null || c.cameraInHead) {
			return;
		}
		double s = c.size * hatSize.get();
		double corner = c.anchor.headHalf() * Math.sqrt(2);
		double seat = corner < R * s ? H * corner / R : H * s;
		double apexY = c.anchor.headTop() + seat + 0.004;
		double moving = c.moving();
		double step = Math.sin(c.player.walkAnimation.position(c.partial) * 0.6662 * 2) * 0.02 * moving;
		double gust = Math.sin(c.time * 2.3) * 0.012 * moving;
		Matrix4d xf = c.anchor.head().translate(0, apexY, 0).rotateX(-(0.09 * moving + step + gust)).scale(s);
		Meshes.ModelPainter painter = new Meshes.ModelPainter() {
			@Override
			public boolean part(Meshes m, Model.Part part) {
				look.apply(m);
				if (part.name().equals("binding")) {
					m.finish(look.is("Gold") ? Finishes.GOLD : Finishes.SILK);
					m.pattern(Meshes.PATTERN_WEAVE, 6);
				} else {
					m.pattern(Meshes.PATTERN_WEAVE, 1.2);
				}
				m.accent(0, look.glowColour(0));
				return true;
			}
			@Override
			public int colour(Model.Part part, double ramp) {
				int c0 = look.colour(ramp);
				return part.name().equals("binding") ? Colours.lerp(c0, 0xFF2A1A10, 0.45) : c0;
			}
			@Override
			public double glow(Model.Part part, double mask) {
				return mask * look.glow();
			}
		};
		c.mesh.lightFrom(c.lightPos);
		c.mesh.model(model, xf, null, painter);
		c.mesh.finish(Finishes.SILK);
		int cord = Colours.lerp(look.colour(0.4), 0xFF2A1A10, 0.5);
		double top = c.anchor.headTop();
		for (int side = -1; side <= 1; side += 2) {
			double[][] local = {{side * 0.294, top - 0.07, -0.02}, {side * 0.297, 0.26, 0.0}, {side * 0.297, 0.04, 0.05}, {side * 0.19, -0.012, 0.11},
					{0, -0.016, 0.14}};
			double[] path = new double[local.length * 3];
			double[] radius = new double[local.length];
			int[] colours = new int[local.length];
			for (int i = 0; i < local.length; i++) {
				Vector3d w = c.anchor.headPoint(local[i][0], local[i][1], local[i][2]);
				path[i * 3] = w.x;
				path[i * 3 + 1] = w.y;
				path[i * 3 + 2] = w.z;
				radius[i] = 0.0035 * c.anchor.px() * 16;
				colours[i] = cord;
			}
			c.mesh.tube(path, local.length, radius, colours, 8);
		}
		c.mesh.lightFrom(null);
	}
}
