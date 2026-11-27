package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.ModelCache;
import nz.temple.cosmetic.Colours;
import nz.temple.cosmetic.Finishes;
import nz.temple.cosmetic.Model;
import nz.temple.setting.ModeSetting;
import org.joml.Matrix4d;

import java.util.List;

public final class CrownPiece extends AuraPiece {
	private static final int RUBY = 0xFFD01A36;
	private static final int SAPPHIRE = 0xFF2457E8;
	private static final int EMERALD = 0xFF12A85C;
	private static final int DIAMOND = 0xFFE8F4FF;
	private final ModeSetting style;
	private final PieceLook look;
	public CrownPiece(Host host) {
		super(host, "Crown", false, "A crown seated on the head, with gems, engraving and pearls.", true);
		style = host.add(new ModeSetting("Crown style", List.of("Royal", "Spiked", "Circlet"), "Royal")
				.describe("Royal has crosses and fleurs-de-lis, crossed arches over velvet, an orb and an ermine trim. Spiked has tall "
						+ "spires tipped with pearls. Circlet is a twisted band of gems worn round the brow with a teardrop at the front.")
				.visibleWhen(on::get));
		look = new PieceLook(host::add, "Crown", PieceLook.of(Finishes.GOLD, Finishes.SILVER, Finishes.OBSIDIAN, Finishes.CRYSTAL),
				Finishes.GOLD, 50, this::showsNew);
	}
	@Override
	public void draw(AuraContext c) {
		if (c.cameraInHead) {
			return;
		}
		Model model = c.model(switch (style.get()) {
			case "Spiked" -> "crown_spiked";
			case "Circlet" -> "crown_circlet";
			default -> "crown_royal";
		});
		if (model == null) {
			return;
		}
		double fit = c.anchor.helmet() ? (5 / 16.0 + 0.012) / (4.5 / 16.0 + 0.012) : 1;
		Matrix4d xf = c.anchor.head().translate(0, c.anchor.headTop(), 0).scale(fit, c.size, fit);
		double glow = look.glow();
		double cosShimmer = Math.cos(c.time * 0.8), sinShimmer = Math.sin(c.time * 0.8);
		Meshes.ModelPainter painter = new Meshes.ModelPainter() {
			@Override
			public boolean part(Meshes m, Model.Part part) {
				switch (part.name()) {
					case "gem" -> {
						m.finish(Finishes.GEMSTONE);
						m.accentOwnColour(0);
					}
					case "pearl" -> m.finish(Finishes.PEARL);
					case "velvet" -> {
						m.finish(Finishes.VELVET);
						m.pattern(Meshes.PATTERN_GRAIN, 0.5);
					}
					case "ermine" -> {
						m.finish(Finishes.ERMINE);
						m.pattern(Meshes.PATTERN_FUR, 0.6);
					}
					default -> {
						look.apply(m);
						m.pattern(Meshes.PATTERN_ENGRAVED, 1);
						m.accent(0, look.glowColour(0));
					}
				}
				return true;
			}
			@Override
			public int colour(Model.Part part, double ramp) {
				return switch (part.name()) {
					case "gem" -> ramp < 0.25 ? RUBY : ramp < 0.55 ? SAPPHIRE : ramp < 0.85 ? EMERALD : DIAMOND;
					case "pearl" -> Colours.lerp(0xFFE9E1D0, 0xFFFFFCF4, ramp);
					case "velvet" -> Colours.lerp(0xFF3E0610, 0xFF9A1830, ramp);
					case "ermine" -> ramp < 0.1 ? 0xFF141113 : Colours.lerp(0xFFE6E2DA, 0xFFFFFFFF, ramp);
					default -> look.colour(ramp);
				};
			}
			@Override
			public double glow(Model.Part part, double mask) {
				return switch (part.name()) {
					case "gem" -> mask * Math.min(1, glow * 1.2);
					case "metal" -> mask * glow;
					default -> 0;
				};
			}
			@Override
			public double glowAt(Model.Part part, double mask, double ramp, float x, float y, float z) {
				return switch (part.name()) {

					case "gem" -> mask * Math.min(1, glow * 1.2) * (0.7 + 0.3 * Math.sin(c.time * 1.7 + x * 23 + z * 17));
					case "metal" -> Math.min(1, mask * glow * (0.5 + 0.9 * sweepToward(x, z, cosShimmer, sinShimmer)));
					default -> 0;
				};
			}
		};
		c.mesh.lightFrom(c.lightPos);
		c.mesh.model(model, xf, null, painter);
		c.mesh.lightFrom(null);
		if (!c.far) {
			glints(c, model, xf);
		}
	}
	private void glints(AuraContext c, Model model, Matrix4d xf) {
		int[] gems = partVertices(model, "gem");
		if (gems.length == 0) {
			return;
		}
		nz.temple.client.render.Fx fx = c.fx;
		fx.additive(true);
		for (int k = 0; k < 2; k++) {
			double cycle = c.time * (0.55 + 0.15 * k) + k * 0.5;
			long n = (long) Math.floor(cycle);
			double ph = cycle - n;
			double flash = Math.pow(Math.sin(Math.PI * ph), 8);
			if (flash < 0.02) {
				continue;
			}
			long h = (n * 0x9E3779B97F4A7C15L + k * 0x632BE59BD9B4E019L) >>> 33;
			int v = gems[(int) (h % gems.length)];
			org.joml.Vector3d p = xf.transformPosition(new org.joml.Vector3d(model.x(v), model.y(v), model.z(v)));
			fx.star(p.x, p.y, p.z, 0.06 * c.size * flash, nz.temple.client.render.Fx.withAlpha(0xFFFFFFFF, (float) (230 * flash)), c.time * 0.8 + k);
		}
		fx.additive(false);
	}
}
