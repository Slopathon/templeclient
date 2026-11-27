package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.ModelCache;
import nz.temple.cosmetic.Colours;
import nz.temple.cosmetic.Finish;
import nz.temple.cosmetic.Finishes;
import nz.temple.cosmetic.Model;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.List;

public final class BladesPiece extends AuraPiece {
	private final ModeSetting style;
	private final NumberSetting count;
	private final PieceLook look;
	public BladesPiece(Host host) {
		super(host, "Orbiting blades", false, "Swords circling the body, leaving a sweep of light.", true);
		style = host.add(new ModeSetting("Blade style", List.of("Sword", "Energy"), "Sword")
				.describe("Sword is a steel longsword with a gilded guard and leather grip. Energy has a crystal blade with glowing edges.")
				.visibleWhen(on::get));
		count = host.add(new NumberSetting("Blades", 3, 1, 6, 1, "").describe("Number of swords.").visibleWhen(on::get));
		look = new PieceLook(host::add, "Blade", PieceLook.of(Finishes.STEEL, Finishes.GOLD, Finishes.OBSIDIAN, Finishes.CRYSTAL),
				Finishes.STEEL, 40, this::showsNew);
	}
	@Override
	public void draw(AuraContext c) {
		Model model = c.model("sword");
		if (model == null) {
			return;
		}
		int n = (int) Math.round(count.get());
		boolean energy = style.is("Energy");
		double s = c.size;
		double cy = c.feet.y + c.player.getBbHeight() * 0.55;
		double k = 1.5 * s;
		double glow = look.glow();
		Finish blade = energy ? Finishes.ENERGY : look.finish();
		int guardTint = look.glowColour(0);
		int[] guard = new int[33];
		for (int i = 0; i <= 32; i++) {
			guard[i] = Finishes.GOLD.tinted(i / 32.0, guardTint);
		}
		int energyColour = Colours.lerp(look.glowColour(0.2), 0xFFFFFFFF, 0.35);
		Meshes.ModelPainter painter = new Meshes.ModelPainter() {
			@Override
			public boolean part(Meshes m, Model.Part part) {
				switch (part.name()) {
					case "blade" -> {
						look.apply(m);
						m.finish(blade);
						m.pattern(Meshes.PATTERN_BRUSHED, 8);
						m.accent(0, look.glowColour(0.3));
					}
					case "grip" -> {
						m.finish(Finishes.LEATHER);
						m.pattern(Meshes.PATTERN_WEAVE, 3);
					}
					case "gem" -> {
						m.finish(Finishes.GEMSTONE);
						m.accentOwnColour(0);
					}
					default -> {
						m.finish(Finishes.GOLD);
						m.pattern(Meshes.PATTERN_ENGRAVED, 2);
						m.accent(0, look.glowColour(0));
					}
				}
				return true;
			}
			@Override
			public int colour(Model.Part part, double ramp) {
				return switch (part.name()) {
					case "blade" -> energy ? energyColour : look.colour(ramp);
					case "grip" -> Colours.lerp(0xFF20140E, 0xFF4A3022, ramp);
					case "gem" -> 0xFFD01A36;
					default -> guard[(int) Math.round(Math.max(0, Math.min(1, ramp)) * 32)];
				};
			}
			@Override
			public double glow(Model.Part part, double mask) {
				return switch (part.name()) {
					case "blade" -> energy ? Math.max(mask, 0.45) * Math.max(0.75, glow) : mask * glow;
					case "gem" -> mask * glow;
					default -> 0;
				};
			}
		};
		c.mesh.lightFrom(c.lightPos);
		for (int i = 0; i < n; i++) {
			double[] now = orbit(c.time, i, n, s);
			Vector3d along = heading(c.time, i, n, s);
			Vector3d out = new Vector3d(now[0], 0, now[2]).normalize();
			Vector3d z = new Vector3d(out).negate();
			Vector3d x = new Vector3d(along).cross(z).normalize();
			z = new Vector3d(x).cross(along).normalize();
			double[] centre = {c.feet.x + now[0], cy + now[1], c.feet.z + now[2]};
			Matrix4d xf = new Matrix4d(x.x, x.y, x.z, 0, along.x, along.y, along.z, 0, z.x, z.y, z.z, 0,
					centre[0] - along.x * 0.3 * k, centre[1] - along.y * 0.3 * k, centre[2] - along.z * 0.3 * k, 1).scale(k);
			c.mesh.model(model, xf, null, painter);
			trail(c, cy, i, n, s, k, energy);
		}
		c.mesh.lightFrom(null);
	}
	private void trail(AuraContext c, double cy, int i, int n, double s, double k, boolean energy) {
		double glow = look.glow();
		double strength = (energy ? 0.6 : 0.3) * Math.max(0.5, glow);
		if (strength <= 0.01) {
			return;
		}
		int steps = 14;
		double[][] inner = new double[steps][];
		double[][] outer = new double[steps][];
		for (int j = 0; j < steps; j++) {
			double tk = c.time - j * 0.026 * c.speed;
			double[] now = orbit(tk, i, n, s);
			Vector3d along = heading(tk, i, n, s);
			double gx = c.feet.x + now[0] - along.x * 0.3 * k, gy = cy + now[1] - along.y * 0.3 * k, gz = c.feet.z + now[2] - along.z * 0.3 * k;
			inner[j] = new double[] {gx + along.x * 0.25 * k, gy + along.y * 0.25 * k, gz + along.z * 0.25 * k};
			outer[j] = new double[] {gx + along.x * 0.6 * k, gy + along.y * 0.6 * k, gz + along.z * 0.6 * k};
		}
		Fx fx = c.fx;
		fx.additive(true);
		for (int j = 0; j < steps - 1; j++) {
			double f0 = 1 - j / (double) (steps - 1);
			double f1 = 1 - (j + 1) / (double) (steps - 1);
			int c0 = HaloPiece.withAlpha(look.glowColour(i / (double) n), strength * f0 * f0);
			int c1 = HaloPiece.withAlpha(look.glowColour(i / (double) n), strength * f1 * f1);
			int e0 = Fx.withAlpha(c0, Fx.alpha(c0) * 0.1f);
			int e1 = Fx.withAlpha(c1, Fx.alpha(c1) * 0.1f);
			fx.quad(inner[j][0], inner[j][1], inner[j][2], e0, outer[j][0], outer[j][1], outer[j][2], c0,
					outer[j + 1][0], outer[j + 1][1], outer[j + 1][2], c1, inner[j + 1][0], inner[j + 1][1], inner[j + 1][2], e1);
		}
		fx.additive(false);
	}
	private static double[] orbit(double t, int i, int n, double s) {
		double a = t * 1.1 + Math.PI * 2 * i / n;
		double r = (1.0 + 0.06 * Math.sin(t * 0.7 + i)) * s;
		return new double[] {Math.cos(a) * r, Math.sin(t * 1.3 + i * 2.1) * 0.2 * s, Math.sin(a) * r};
	}
	private static Vector3d heading(double t, int i, int n, double s) {
		double[] a = orbit(t, i, n, s);
		double[] b = orbit(t + 0.01, i, n, s);
		return new Vector3d(b[0] - a[0], b[1] - a[1], b[2] - a[2]).normalize();
	}
}
