package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.TempleNzClient;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.Theme;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class CrystalChams extends Module {
	private static CrystalChams instance;
	private static final float SIN_45 = (float) Math.sin(Math.PI / 4);
	private final ModeSetting model;
	private final ModeSetting colors;
	private final ColorSetting color;
	private final NumberSetting opacity;
	private final BoolSetting glow;
	private final BoolSetting rings;
	private final NumberSetting size;
	private final NumberSetting spin;
	private final NumberSetting bounce;
	private final ModeSetting base;
	public CrystalChams() {
		super("Crystal Chams", Category.COSMETICS, "Changes the colour, size, spin and bounce of end crystals, or replaces them with a Gem, "
				+ "Orb or Prism model.");
		instance = this;
		model = add(new ModeSetting("Model", List.of("Vanilla", "Gem", "Orb", "Prism"), "Vanilla")
				.describe("Vanilla recolours Minecraft's crystal. Gem is a faceted glass crystal with a bright core. Orb is a glass sphere "
						+ "with a pulsing core and a ring. Prism is nested glass and gold cubes around a core, based on the vanilla "
						+ "crystal."));
		colors = add(new ModeSetting("Color mode", List.of("Theme", "Custom", "Rainbow", "Original"), "Theme")
				.describe("Theme uses the HUD accent. Custom uses the colour below. Rainbow cycles through colours. Original keeps the "
						+ "vanilla colours."));
		color = add(new ColorSetting("Color", 0xFFE9B949).themed().describe("Crystal tint.").visibleWhen(() -> colors.is("Custom")));
		opacity = add(new NumberSetting("Opacity", 100, 10, 100, 5, "%")
				.describe("Opacity. Below 100 the crystal is see-through."));
		glow = add(new BoolSetting("Glow", true).describe("Renders at full brightness so crystals are visible in the dark."));
		rings = add(new BoolSetting("Rings", true).describe("Thin rings orbiting the crystal.").visibleWhen(() -> !model.is("Vanilla")));
		group();
		size = add(new NumberSetting("Size", 100, 20, 300, 5, "%").describe("Crystal size. The hitbox is unchanged."));
		spin = add(new NumberSetting("Spin rate", 100, 0, 600, 10, "%").describe("Rotation speed. 0 stops it."));
		bounce = add(new NumberSetting("Bounce", 100, 0, 300, 10, "%").describe("Bob height. 0 keeps it still."));
		base = add(new ModeSetting("Base", List.of("Default", "Always", "Never"), "Default")
				.describe("Whether to show a base plate under the crystal."));
	}
	public static CrystalChams active() {
		CrystalChams c = TempleNzClient.modules() != null ? TempleNzClient.modules().get(CrystalChams.class) : instance;
		return c != null && c.isEnabled() ? c : null;
	}
	public boolean recolors() {
		return !colors.is("Original") || opacity.get() < 100 || glow.get();
	}
	public boolean replacesModel() {
		return !model.is("Vanilla");
	}
	public int tint(int entityId) {
		int rgb = switch (colors.get()) {
			case "Custom" -> color.get();
			case "Rainbow" -> Fx.hue(Theme.time() * 0.12 + entityId * 0.13);
			case "Original" -> 0xFFFFFFFF;
			default -> Theme.color(entityId * 0.1);
		};
		return Fx.withAlpha(rgb, 255 * opacity.getFloat() / 100f);
	}
	public boolean translucent() {
		return opacity.get() < 100;
	}
	public boolean glow() {
		return glow.get();
	}
	public float size() {
		return size.getFloat() / 100f;
	}
	public float spin() {
		return spin.getFloat() / 100f;
	}
	public float bounce() {
		return bounce.getFloat() / 100f;
	}
	public Boolean showBase() {
		return switch (base.get()) {
			case "Always" -> Boolean.TRUE;
			case "Never" -> Boolean.FALSE;
			default -> null;
		};
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || model.is("Vanilla") || mc.level == null || mc.player == null) {
			return;
		}
		Meshes mesh = fx.mesh();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof EndCrystal crystal) || e.distanceToSqr(mc.player) > 96 * 96) {
				continue;
			}
			Vec3 p = crystal.getPosition(partial);
			float age = crystal.time + partial;
			double hh = Math.sin(age * 0.2) / 2 + 0.5;
			double bob = (hh * hh + hh) * 0.4 * bounce();
			double s = size();
			double cy = p.y + 1.2 + bob;
			double turn = Math.toRadians(age * 3 * spin());
			int id = (int) (p.x * 7 + p.z * 13);
			int tint = colors.is("Original") ? 0xFFC76BFF : tint(id) | 0xFF000000;
			float alpha = 255 * opacity.getFloat() / 100f;
			if (Boolean.TRUE.equals(showBase())) {
				mesh.bloom(false, 0);
				mesh.material(Meshes.Material.MATTE);
				mesh.emission(0.02f);
				mesh.box(p.x, p.y + 0.125, p.z, null, 0.375, 0.125, 0.375, 0xFF26222E);
				mesh.material(Meshes.Material.METAL);
				mesh.emission(0.2f);
				mesh.torus(p.x, p.y + 0.255, p.z, null, 0.27, 0.012, Fx.withAlpha(tint, alpha), 32, null);
			}
			switch (model.get()) {
				case "Gem" -> gem(mesh, p.x, cy, p.z, s, turn, tint, alpha);
				case "Orb" -> orb(mesh, p.x, cy, p.z, s, turn, age, tint, alpha);
				default -> prism(mesh, p.x, cy, p.z, s, turn, tint, alpha);
			}
			if (rings.get()) {
				mesh.material(Meshes.Material.NEON);
				mesh.emission(0.3f);
				for (int r = 0; r < 2; r++) {
					Quaternionf q = new Quaternionf().rotationY((float) (turn * (r == 0 ? 1.6 : -1.2) + r)).rotateX((float) (0.5 + r * 0.6));
					mesh.torus(p.x, cy, p.z, q, (0.62 + r * 0.1) * s, 0.008 * s, Fx.withAlpha(Fx.lerp(tint, 0xFFFFFFFF, 0.3), alpha * 0.9f), 48, null);
				}
			}
			if (glow.get()) {
				fx.glow(p.x, cy, p.z, 0.9 * s, Fx.withAlpha(tint, alpha * 0.35f));
			}
		}
	}
	private void gem(Meshes mesh, double x, double y, double z, double s, double turn, int tint, float alpha) {
		mesh.bloom(false, 0);
		mesh.material(Meshes.Material.GLASS);
		mesh.emission(glow.get() ? 0.25f : 0.08f);
		bipyramid(mesh, x, y, z, 0.36 * s, 0.6 * s, 0.22 * s, turn, Fx.withAlpha(tint, alpha * 0.85f));
		mesh.material(Meshes.Material.NEON);
		mesh.emission(glow.get() ? 0.6f : 0.3f);
		bipyramid(mesh, x, y, z, 0.13 * s, 0.24 * s, 0.07 * s, -turn * 1.7, Fx.withAlpha(Fx.lerp(tint, 0xFFFFFFFF, 0.35), alpha));
	}
	private void orb(Meshes mesh, double x, double y, double z, double s, double turn, double age, int tint, float alpha) {
		double pulse = 0.85 + 0.15 * Math.sin(age * 0.25);
		mesh.bloom(false, 0);
		mesh.material(Meshes.Material.NEON);
		mesh.emission(glow.get() ? 0.6f : 0.3f);
		mesh.sphere(x, y, z, 0.15 * s * pulse, Fx.withAlpha(Fx.lerp(tint, 0xFFFFFFFF, 0.3), alpha), 14);
		mesh.material(Meshes.Material.GLASS);
		mesh.emission(glow.get() ? 0.2f : 0.06f);
		mesh.sphere(x, y, z, 0.42 * s, Fx.withAlpha(tint, alpha * 0.7f), 20);
		mesh.material(Meshes.Material.METAL);
		mesh.emission(0.15f);
		Quaternionf q = new Quaternionf().rotationY((float) turn).rotateX(0.35f);
		mesh.torus(x, y, z, q, 0.48 * s, 0.03 * s, Fx.withAlpha(Fx.lerp(tint, 0xFFE9B949, 0.4), alpha), 40, null);
	}
	private void prism(Meshes mesh, double x, double y, double z, double s, double turn, int tint, float alpha) {
		Quaternionf outer = new Quaternionf().rotationY((float) turn).rotateAxis((float) (Math.PI / 3), SIN_45, 0, SIN_45);
		Quaternionf inner = new Quaternionf().setAngleAxis((float) (Math.PI / 3), SIN_45, 0, SIN_45).rotateY((float) turn * 1.4f);
		Quaternionf core = new Quaternionf(outer).mul(inner);
		double h = 0.3 * s;
		mesh.bloom(false, 0);
		mesh.material(Meshes.Material.NEON);
		mesh.emission(glow.get() ? 0.6f : 0.3f);
		mesh.box(x, y, z, core, h * 0.42, h * 0.42, h * 0.42, Fx.withAlpha(Fx.lerp(tint, 0xFFFFFFFF, 0.25), alpha));
		mesh.material(Meshes.Material.GLASS);
		mesh.emission(glow.get() ? 0.2f : 0.06f);
		mesh.box(x, y, z, core, h * 0.75, h * 0.75, h * 0.75, Fx.withAlpha(tint, alpha * 0.55f));
		mesh.box(x, y, z, outer, h, h, h, Fx.withAlpha(tint, alpha * 0.5f));
		mesh.material(Meshes.Material.METAL);
		mesh.emission(0.2f);
		int edge = Fx.withAlpha(Fx.lerp(tint, 0xFFE9B949, 0.55), alpha);
		double t = 0.022 * s;
		for (int axis = 0; axis < 3; axis++) {
			for (int a = -1; a <= 1; a += 2) {
				for (int b = -1; b <= 1; b += 2) {
					float[] c = new float[3];
					float[] half = new float[3];
					c[(axis + 1) % 3] = (float) (a * h);
					c[(axis + 2) % 3] = (float) (b * h);
					half[axis] = (float) (h + t);
					half[(axis + 1) % 3] = (float) t;
					half[(axis + 2) % 3] = (float) t;
					Vector3f at = outer.transform(new Vector3f(c[0], c[1], c[2]));
					mesh.box(x + at.x, y + at.y, z + at.z, outer, half[0], half[1], half[2], edge);
				}
			}
		}
	}
	private static void bipyramid(Meshes mesh, double x, double y, double z, double r, double top, double bottom, double turn, int colour) {
		int sides = 8;
		double[] apex = {x, y + top, z};
		double[] foot = {x, y - bottom, z};
		double[] centre = {x, y, z};
		for (int i = 0; i < sides; i++) {
			double a0 = turn + i * Math.PI * 2 / sides;
			double a1 = turn + (i + 1) * Math.PI * 2 / sides;
			double[] p0 = {x + Math.cos(a0) * r, y, z + Math.sin(a0) * r};
			double[] p1 = {x + Math.cos(a1) * r, y, z + Math.sin(a1) * r};
			facet(mesh, centre, apex, p1, p0, colour);
			facet(mesh, centre, foot, p0, p1, colour);
		}
	}
	private static void facet(Meshes mesh, double[] centre, double[] a, double[] b, double[] c, int colour) {
		double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
		double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-12) {
			return;
		}
		nx /= len;
		ny /= len;
		nz /= len;
		double ox = (a[0] + b[0] + c[0]) / 3 - centre[0], oy = (a[1] + b[1] + c[1]) / 3 - centre[1], oz = (a[2] + b[2] + c[2]) / 3 - centre[2];
		if (nx * ox + ny * oy + nz * oz < 0) {
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}
		double[] pa = {a[0], a[1], a[2], nx, ny, nz};
		double[] pb = {b[0], b[1], b[2], nx, ny, nz};
		double[] pc = {c[0], c[1], c[2], nx, ny, nz};
		mesh.quad(pa, pb, pc, pc, colour, colour, colour, colour);
	}
}
