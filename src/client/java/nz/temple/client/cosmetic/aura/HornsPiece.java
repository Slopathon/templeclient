package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.ModelCache;
import nz.temple.cosmetic.Finishes;
import nz.temple.cosmetic.Model;
import nz.temple.setting.ModeSetting;
import org.joml.Matrix4d;

import java.util.List;

public final class HornsPiece extends AuraPiece {
	private final ModeSetting style;
	private final PieceLook look;

	public HornsPiece(Host host) {
		super(host, "Horns", false, "Horns growing from the head.", true);
		style = host.add(new ModeSetting("Horn style", List.of("Devil", "Ram", "Dragon"), "Devil")
				.describe("Devil horns are short with forward hooks. Ram horns curl around the ears. Dragon horns are long and swept back.")
				.visibleWhen(on::get));
		look = new PieceLook(host::add, "Horn", PieceLook.of(Finishes.KERATIN, Finishes.OBSIDIAN, Finishes.BONE, Finishes.GOLD,
				Finishes.CRYSTAL), Finishes.KERATIN, this::showsNew);
	}

	@Override
	public void draw(AuraContext c) {
		if (c.cameraInHead) {
			return;
		}
		Model model = c.model(switch (style.get()) {
			case "Ram" -> "horn_ram";
			case "Dragon" -> "horn_dragon";
			default -> "horn_devil";
		});
		if (model == null) {
			return;
		}
		double x = style.is("Ram") ? 0.17 : style.is("Dragon") ? 0.14 : 0.16;
		double z = style.is("Ram") ? 0.0 : style.is("Dragon") ? 0.03 : 0.045;
		double lean = style.is("Ram") ? 0.1 : style.is("Dragon") ? 0.15 : 0.2;
		double top = 8 / 16.0 - 0.005;
		Meshes mesh = c.mesh;
		mesh.lightFrom(c.lightPos);
		Meshes.ModelPainter painter = painter(look, Meshes.PATTERN_HORN, 1);
		double glow = look.glow();
		float[] tip = tip(model);
		for (int side = -1; side <= 1; side += 2) {
			Matrix4d xf = c.anchor.head().translate(side * x, top, z).rotateZ(-side * lean).scale(side * c.size, c.size, c.size);
			mesh.model(model, xf, null, painter);
			if (glow > 0 && !c.far) {
				wisps(c, xf.transformPosition(new org.joml.Vector3d(tip[0], tip[1], tip[2])), side, glow);
			}
		}
		mesh.lightFrom(null);
	}

	private void wisps(AuraContext c, org.joml.Vector3d tip, int side, double glow) {
		nz.temple.client.render.Fx fx = c.fx;
		double s = c.size;
		fx.additive(true);
		for (int k = 0; k < 4; k++) {
			double cycle = c.time * 0.5 + k / 4.0 + (side > 0 ? 0.13 : 0);
			double ph = cycle - Math.floor(cycle);
			double curl = ph * 5 + k * 1.9;
			double rise = ph * 0.32 * s;
			double px = tip.x + Math.cos(curl) * 0.035 * s * ph;
			double pz = tip.z + Math.sin(curl) * 0.035 * s * ph;
			double fade = Math.sin(Math.PI * Math.min(1, ph * 1.5)) * (1 - ph);
			int col = look.glowColour(k * 0.25);
			fx.smoke(px, tip.y + rise, pz, (0.025 + 0.06 * ph) * s, HaloPiece.withAlpha(col, 0.22 * glow * fade), k + side * 3, curl);
			fx.light(px, tip.y + rise, pz, 0.008 * s, HaloPiece.withAlpha(col, 0.6 * glow * fade), 0.8);
		}
		fx.additive(false);
	}
}
