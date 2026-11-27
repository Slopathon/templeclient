package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TeleportEffects extends Module implements nz.temple.client.modules.cosmetic.Previewable, nz.temple.client.render.FxSource {
	private static volatile TeleportEffects instance;
	public static boolean replacesPearlParticles() {
		TeleportEffects m = instance;
		return m != null && m.isEnabled();
	}
	private static final int MAX = 24;
	private final ModeSetting style;
	private final ModeSetting who;
	private final NumberSetting trigger;
	private final NumberSetting size;
	private final NumberSetting lifetime;
	private final BoolSetting link;
	private final BoolSetting bodyEcho;
	private final nz.temple.client.render.BodyFx bodies = new nz.temple.client.render.BodyFx();
	private final BoolSetting enderColor;
	private final nz.temple.client.modules.cosmetic.Look look;
	private final Map<Integer, Vec3> lastSeen = new HashMap<>();
	private final List<Warp> warps = new ArrayList<>();
	private ClientLevel level;
	private record Warp(Vec3 from, Vec3 to, long born) {
	}
	static {
		nz.temple.client.util.VanillaFx.pearlParticles = TeleportEffects::replacesPearlParticles;
	}

	public TeleportEffects() {
		super("Teleport Effects", Category.COSMETICS, "Marks where a player teleported from and to, joined by a line. Useful for seeing "
				+ "where someone pearled.");
		style = add(new ModeSetting("Style", List.of("Vortex", "Rings", "Portal"), "Portal")
				.describe("Vortex shows a spinning glass funnel with a spiral. Rings collapses metal rings where they left and opens them "
						+ "where they arrived. Portal places an upright glass oval at each end, facing the other."));
		instance = this;
		who = add(new ModeSetting("Who", List.of("Players", "Self", "Both"), "Players")
				.describe("Whose teleports are marked."));
		group();
		trigger = add(new NumberSetting("Trigger distance", 8, 3, 64, 1, " b")
				.describe("Distance moved in a single tick that counts as a teleport."));
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Effect size."));
		lifetime = add(new NumberSetting("Lifetime", 1.6, 0.4, 6, 0.1, " s").describe("How long the marks last."));
		link = add(new BoolSetting("Link ends", true).describe("Draws a line between the departure and arrival points."));
		bodyEcho = add(new BoolSetting("Body echo", true)
				.describe("Leaves a copy of the player's body that burns away upward where they left, and reassembles piece by piece where "
						+ "they arrive."));
		group();
		enderColor = add(new BoolSetting("Ender purple", true)
				.describe("Uses ender pearl purple. Off uses the colour settings below."));
		look = new nz.temple.client.modules.cosmetic.Look(x -> add(x), "Glass");
		on(TickEvent.class, e -> tick());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			lastSeen.clear();
			warps.clear();
			return;
		}
		double limit = trigger.get() * trigger.get();
		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			if (nz.temple.client.util.Wearer.mine(player) ? who.is("Players") : who.is("Self")) {
				continue;
			}
			Vec3 now = player.position();
			Vec3 previous = lastSeen.put(player.getId(), now);
			if (previous == null) {
				continue;
			}
			if (previous.distanceToSqr(now) >= limit && warps.size() < MAX) {
				warps.add(new Warp(previous, now, System.nanoTime()));
				echo(nz.temple.client.render.BodyFx.Body.of(player, 1), previous, now);
			}
		}
		lastSeen.keySet().removeIf(id -> mc.level.getEntity(id) == null);
	}
	private int color(double offset, double fade) {
		if (enderColor.get()) {
			int ender = Fx.lerp(0xFFB86BFF, 0xFF6E5BFF, (Math.sin(offset * Math.PI * 2) + 1) / 2);
			return Fx.withAlpha(ender, (float) (255 * look.alpha() * Math.max(0, Math.min(1, fade))));
		}
		return look.color(offset, fade);
	}
	@Override
	public void preview(Vec3 at) {
		Minecraft mc = Minecraft.getInstance();
		level = mc.level;
		warps.add(new Warp(at.add(-2.5, 0, 0), at.add(2.5, 0, 1), System.nanoTime()));
		double yaw = mc.player == null ? 0 : mc.player.getYRot() + 180;
		echo(nz.temple.client.render.BodyFx.Body.standing(at, yaw), at.add(-2.5, 0, 0), at.add(2.5, 0, 1));
	}
	private void echo(nz.temple.client.render.BodyFx.Body body, Vec3 from, Vec3 to) {
		if (!bodyEcho.get()) {
			return;
		}
		var left = new nz.temple.client.render.BodyFx.Body(from, body.yaw(), body.height(), body.width(), body.humanoid(), 0);
		var arrived = new nz.temple.client.render.BodyFx.Body(to, body.yaw(), body.height(), body.width(), body.humanoid(), 0);
		bodies.breakUp(left, look, 220, 0.5, "Everywhere", nz.temple.client.render.BodyFx.Breakup.DISSOLVE);
		bodies.assemble(arrived, look, 220, 0.45);
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || level != mc.level) {
			warps.clear();
			bodies.clear();
			return;
		}
		long now = System.nanoTime();
		double life = lifetime.get() * 1e9;
		warps.removeIf(w -> now - w.born() > life);
		nz.temple.client.render.Meshes mesh = fx.mesh();
		bodies.emit(fx);
		double s = size.get();
		double time = now / 1e9;
		for (Warp w : warps) {
			double t = (now - w.born()) / life;
			end(fx, mesh, w.from(), w.to(), t, s, time, true);
			end(fx, mesh, w.to(), w.from(), t, s, time, false);
			if (link.get()) {
				int samples = 24;
				double[] path = new double[samples * 3];
				double[] radius = new double[samples];
				int[] colours = new int[samples];
				Vec3 from = w.from().add(0, 1, 0);
				Vec3 to = w.to().add(0, 1, 0);
				double arc = Math.min(4, from.distanceTo(to) * 0.25);
				double reach = Math.min(1, t * 4);
				for (int k = 0; k < samples; k++) {
					double u = k / (samples - 1.0) * reach;
					path[k * 3] = from.x + (to.x - from.x) * u;
					path[k * 3 + 1] = from.y + (to.y - from.y) * u + Math.sin(u * Math.PI) * arc;
					path[k * 3 + 2] = from.z + (to.z - from.z) * u;
					radius[k] = 0.03 * s;
					colours[k] = color(u, (1 - t) * 0.8);
				}
				look.apply(mesh, 0.5f);
				mesh.tube(path, samples, radius, colours, 5);
			}
		}
	}
	private void end(Fx fx, nz.temple.client.render.Meshes mesh, Vec3 at, Vec3 other, double t, double s, double time,
			boolean departure) {
		double open = departure ? 1 - t : Math.min(1, t * 3) * (1 - t * 0.3);
		double fade = 1 - t;
		switch (style.get()) {
			case "Rings" -> {
				look.apply(mesh, 0.3f);
				for (int i = 0; i < 4; i++) {
					double y = at.y + 0.15 + i * 0.5 * s;
					double r = (1.0 - i * 0.16) * s * (0.15 + 0.85 * open);
					org.joml.Quaternionf tilt = new org.joml.Quaternionf().rotationY((float) (time * (departure ? -1.5 : 1.5) + i))
							.rotateX((float) (Math.sin(time * 2 + i) * 0.15));
					int index = i;
					mesh.torus(at.x, y, at.z, tilt, r, 0.04 * s, 0, 40, (u, v) -> color(u * 0.5 + index * 0.15, fade));
				}
			}
			case "Vortex" -> {
				double spin = time * (departure ? -3 : 3);
				double h = 2.2 * s;
				mesh.material(nz.temple.client.render.Meshes.Material.GLASS);
				mesh.emission(0.15f);
				mesh.surface((u, v, o) -> {
					double a = u * Math.PI * 2 + v * 2.5 + spin;
					double r = (0.15 + v * v * 1.1) * s * (0.3 + 0.7 * open);
					o[0] = at.x + Math.cos(a) * r;
					o[1] = at.y + v * h;
					o[2] = at.z + Math.sin(a) * r;
					o[3] = Math.cos(a);
					o[4] = -v * 0.8;
					o[5] = Math.sin(a);
				}, 28, 8, 0, (u, v) -> color(v * 0.5, fade * (0.25 + 0.5 * v)));
				int samples = 30;
				double[] path = new double[samples * 3];
				double[] radius = new double[samples];
				int[] colours = new int[samples];
				for (int k = 0; k < samples; k++) {
					double v = k / (samples - 1.0);
					double a = v * Math.PI * 6 + spin * 1.5;
					double r = (0.15 + v * v * 1.1) * s * (0.3 + 0.7 * open);
					path[k * 3] = at.x + Math.cos(a) * r;
					path[k * 3 + 1] = at.y + v * h;
					path[k * 3 + 2] = at.z + Math.sin(a) * r;
					radius[k] = 0.03 * s;
					colours[k] = color(v, fade);
				}
				look.apply(mesh, 0.6f);
				mesh.tube(path, samples, radius, colours, 5);
			}
			default -> {
				double dx = other.x - at.x;
				double dz = other.z - at.z;
				float yaw = (float) Math.atan2(dx, dz);
				org.joml.Quaternionf face = new org.joml.Quaternionf().rotationY(yaw).rotateX((float) (Math.PI / 2));
				double r = 0.8 * s * open;
				if (r < 0.03) {
					return;
				}
				double cy = at.y + 1.05 * s;
				mesh.material(nz.temple.client.render.Meshes.Material.GLASS);
				mesh.emission(0.25f);
				org.joml.Quaternionf upright = new org.joml.Quaternionf().rotationY(yaw);
				mesh.ellipsoid(at.x, cy, at.z, r * 0.62, r * 1.15, 0.05 * s, upright, color(time * 0.2, fade * 0.8), 14);
				look.apply(mesh, 0.35f);
				org.joml.Quaternionf rim = new org.joml.Quaternionf(upright).rotateX((float) (Math.PI / 2));
				mesh.surface((u, v, o) -> {
					double a = u * Math.PI * 2;
					double b = v * Math.PI * 2;
					double tube = 0.05 * s;
					double ex = Math.cos(a) * (r * 0.62 + tube * Math.cos(b));
					double ey = Math.sin(a) * (r * 1.15 + tube * Math.cos(b));
					double ez = tube * Math.sin(b);
					org.joml.Vector3f p = upright.transform(new org.joml.Vector3f((float) ex, (float) ey, (float) ez));
					org.joml.Vector3f n = upright.transform(new org.joml.Vector3f(
							(float) (Math.cos(a) * Math.cos(b)), (float) (Math.sin(a) * Math.cos(b)), (float) Math.sin(b)));
					o[0] = at.x + p.x;
					o[1] = cy + p.y;
					o[2] = at.z + p.z;
					o[3] = n.x;
					o[4] = n.y;
					o[5] = n.z;
				}, 40, 6, 0, (u, v) -> color(u, fade));
				if (look.bloom() > 0.2f) {
					fx.additive(true);
					fx.glow(at.x, cy, at.z, r * 1.4, color(0.3, fade * 0.3 * look.bloom()));
					fx.additive(false);
				}
			}
		}
	}
	@Override
	protected void onDisable() {
		warps.clear();
		lastSeen.clear();
	}
}
