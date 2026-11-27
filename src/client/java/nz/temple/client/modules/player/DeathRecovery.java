package nz.temple.client.modules.player;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.modules.cosmetic.Look;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.client.util.DeathLog;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;
import java.util.Locale;

public final class DeathRecovery extends Module {
	private static final double DESPAWN_SECONDS = 300;
	private final BoolSetting hud;
	private final BoolSetting marker;
	private final BoolSetting itemBeams;
	private final BoolSetting chat;
	private final BoolSetting clearOnPickup;
	private final NumberSetting keepMinutes;
	private final Look look;
	private DeathLog.Death tracked;
	private double loadedSeconds;
	private long lastTick = System.nanoTime();
	private boolean visited;
	public DeathRecovery() {
		super("Death Recovery", Category.PLAYER, "Shows where you died, how long until your items despawn, and the way back, including from "
				+ "another dimension. Deaths are always logged, even with this off.");
		hud = add(new BoolSetting("On-screen guide", true).describe("An arrow with distance and time remaining at the top of the screen."));
		marker = add(new BoolSetting("Death marker", true).describe("A tall marker at your death location, visible from far away."));
		itemBeams = add(new BoolSetting("Item beams", true).describe("A beam above each of your dropped items near your death location."));
		chat = add(new BoolSetting("Chat message", true).describe("Sends your death coordinates in chat (only visible to you)."));
		clearOnPickup = add(new BoolSetting("Clear when collected", true)
				.describe("Stops guiding once you reach the location and no items are left."));
		keepMinutes = add(new NumberSetting("Keep for", 30, 5, 240, 5, " min").describe("Stops guiding after this long."));
		group();
		look = new Look(s -> add(s), "Glass");
		on(TickEvent.class, e -> tick());
		DeathLog.onDeath(death -> {
			tracked = death;
			loadedSeconds = 0;
			visited = false;
			if (chat.get() && Minecraft.getInstance().player != null) {
				nz.temple.client.compat.Players.message(Minecraft.getInstance().player, Component.literal(String.format(Locale.ROOT,
						"§6Temple §7» You died at §f%s §7(%s)", nz.temple.client.modules.client.StreamerMode.xyz((int) Math.floor(death.x()),
						(int) Math.floor(death.y()), (int) Math.floor(death.z())), dimensionName(death.dimension()))));
			}
		});
	}
	private DeathLog.Death current() {
		if (tracked != null && !tracked.server().equals(DeathLog.serverId())) {
			tracked = null;
		}
		if (tracked == null) {
			DeathLog.Death latest = DeathLog.latestHere();
			if (latest != null) {
				tracked = latest;
				loadedSeconds = 0;
			}
		}
		if (tracked != null && System.currentTimeMillis() - tracked.timeMillis() > keepMinutes.get() * 60_000) {
			return null;
		}
		return tracked;
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		long now = System.nanoTime();
		double dt = Math.min(1, (now - lastTick) / 1e9);
		lastTick = now;
		DeathLog.Death death = current();
		if (player == null || mc.level == null || death == null || player.isDeadOrDying()) {
			return;
		}
		boolean sameDim = mc.level.dimension().identifier().toString().equals(death.dimension());
		BlockPos spot = BlockPos.containing(death.x(), death.y(), death.z());
		if (sameDim && mc.level.hasChunk(spot.getX() >> 4, spot.getZ() >> 4)) {
			loadedSeconds += dt;
		}
		if (sameDim && player.distanceToSqr(death.x(), death.y(), death.z()) < 3 * 3) {
			visited = true;
		}
		if (clearOnPickup.get() && visited && sameDim && items(mc, death).isEmpty()) {
			DeathLog.forget(death);
			tracked = null;
			if (TempleNzClient.notifications() != null) {
				TempleNzClient.notifications().info("Death Recovery", "Everything collected.");
			}
		}
	}
	private static List<ItemEntity> items(Minecraft mc, DeathLog.Death death) {
		AABB area = new AABB(death.x() - 12, death.y() - 12, death.z() - 12, death.x() + 12, death.y() + 12, death.z() + 12);
		return mc.level.getEntitiesOfClass(ItemEntity.class, area, e -> e.isAlive());
	}
	private static Vec3 target(Minecraft mc, DeathLog.Death death) {
		String here = mc.level.dimension().identifier().toString();
		if (here.equals(death.dimension())) {
			return new Vec3(death.x(), death.y(), death.z());
		}
		if (here.equals("minecraft:the_nether") && death.dimension().equals("minecraft:overworld")) {
			return new Vec3(death.x() / 8, mc.player.getY(), death.z() / 8);
		}
		if (here.equals("minecraft:overworld") && death.dimension().equals("minecraft:the_nether")) {
			return new Vec3(death.x() * 8, mc.player.getY(), death.z() * 8);
		}
		return null;
	}
	private static String dimensionName(String id) {
		return switch (id) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> id;
		};
	}
	private double secondsLeft(DeathLog.Death death) {
		return Math.max(0, DESPAWN_SECONDS - loadedSeconds);
	}

	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		DeathLog.Death death = current();
		if (!isEnabled() || mc.level == null || mc.player == null || death == null) {
			return;
		}
		Vec3 at = target(mc, death);
		if (at == null) {
			return;
		}
		boolean sameDim = mc.level.dimension().identifier().toString().equals(death.dimension());
		Vec3 eye = mc.player.getEyePosition();
		double flat = Math.sqrt(Math.pow(at.x - eye.x, 2) + Math.pow(at.z - eye.z, 2));
		double reach = Math.max(32, mc.options.getEffectiveRenderDistance() * 16 - 24);
		if (flat > reach) {
			double k = reach / flat;
			at = new Vec3(eye.x + (at.x - eye.x) * k, mc.player.getY(), eye.z + (at.z - eye.z) * k);
			sameDim = false;
		}
		Meshes mesh = fx.mesh();
		double t = System.nanoTime() / 1e9;
		if (marker.get()) {
			double groundY = sameDim ? at.y : mc.player.getY() - 1;
			fx.throughWalls(true);
			double r = 0.82 + Math.sin(t * 2) * 0.04;
			fx.ring(at.x, groundY + 0.05, at.z, r - 0.055, r + 0.055, look.color(0, 0.9), look.color(0.45, 0.2), 40);
			double crystalY = groundY + 2.2 + Math.sin(t * 1.8) * 0.15;
			crystal(fx, at.x, crystalY, at.z, t * 1.5);
			fx.additive(true);
			fx.glow(at.x, crystalY, at.z, 0.75, look.color(0.2, 0.28));
			fx.additive(false);
			fx.throughWalls(false);
		}
		if (itemBeams.get() && mc.level.dimension().identifier().toString().equals(death.dimension())) {
			look.apply(mesh, 0.7f);
			for (ItemEntity item : items(mc, death)) {
				Vec3 p = item.getPosition(partial);
				mesh.tube(new double[] {p.x, p.y + 0.2, p.z, p.x, p.y + 3.5, p.z}, 2, new double[] {0.04, 0.005},
						new int[] {look.color(0.4, 0.9), look.color(0.4, 0)}, 6);
			}
		}
	}
	private void crystal(Fx fx, double x, double y, double z, double spin) {
		double radius = 0.28;
		double top = y + 0.52;
		double bottom = y - 0.52;
		for (int i = 0; i < 6; i++) {
			double a0 = spin + Math.PI * 2 * i / 6;
			double a1 = spin + Math.PI * 2 * (i + 1) / 6;
			double x0 = x + Math.cos(a0) * radius, z0 = z + Math.sin(a0) * radius;
			double x1 = x + Math.cos(a1) * radius, z1 = z + Math.sin(a1) * radius;
			int c0 = look.color(i / 6.0, 0.95);
			int c1 = look.color((i + 1) / 6.0, 0.95);
			fx.quad(x, top, z, c0, x0, y, z0, c0, x1, y, z1, c1, x, top, z, c1);
			fx.quad(x, bottom, z, c1, x1, y, z1, c1, x0, y, z0, c0, x, bottom, z, c0);
		}
	}
	public void render(GuiGraphicsExtractor g, DeltaTracker delta, Ui ui) {
		Minecraft mc = Minecraft.getInstance();
		DeathLog.Death death = current();
		if (!isEnabled() || !hud.get() || mc.level == null || mc.player == null || death == null || mc.gui.screen() != null) {
			return;
		}
		Vec3 at = target(mc, death);
		boolean sameDim = mc.level.dimension().identifier().toString().equals(death.dimension());
		String where = nz.temple.client.modules.client.StreamerMode.xyz((int) Math.floor(death.x()), (int) Math.floor(death.y()), (int) Math.floor(death.z()));
		String main;
		String sub;
		if (at == null) {
			main = "Died in the " + dimensionName(death.dimension());
			sub = where;
		} else {
			double dist = Math.sqrt(Math.pow(at.x - mc.player.getX(), 2) + Math.pow(at.z - mc.player.getZ(), 2));
			main = String.format(Locale.ROOT, "%.0f m", dist);
			sub = sameDim ? where : String.format(Locale.ROOT, "%s %s → go to %d, %d here", dimensionName(death.dimension()), where,
					(int) Math.floor(at.x), (int) Math.floor(at.z));
		}
		double left = secondsLeft(death);
		String timer = left <= 0 ? "items gone" : String.format(Locale.ROOT, "%d:%02d left", (int) left / 60, (int) left % 60);
		ui.begin(g);
		float k = UiScale.factor();
		ui.transform(0, 0, k, k);
		float nameW = ui.textWidth(main, Fonts.SEMIBOLD, 11);
		float subW = ui.textWidth(sub, Fonts.MEDIUM, 8);
		float timerW = ui.textWidth(timer, Fonts.SEMIBOLD, 8.5f);
		float w = Math.max(nameW + timerW + 58, subW + 40);
		float h = 30;
		float x = (g.guiWidth() / k - w) / 2;
		float y = 34;
		ui.glass(x, y, w, h, 12);
		float cx = x + 16;
		float cy = y + h / 2;
		if (at != null) {
			double yaw = Math.toRadians(mc.player.getYRot());
			double dx = at.x - mc.player.getX();
			double dz = at.z - mc.player.getZ();
			double ahead = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
			double side = dx * -Math.cos(yaw) + dz * -Math.sin(yaw);
			double bearing = Math.atan2(side, ahead);
			float r = 10;
			ui.fill(cx - r, cy - r, r * 2, r * 2, r, 0x52000000);
			ui.ring(cx - r, cy - r, r * 2, r * 2, r, 0.9f, (0xB0 << 24) | (HudModule.GOLD & 0xFFFFFF));
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(ui.mapX(cx), ui.mapY(cy));
			pose.rotate((float) (bearing - Math.PI / 2));
			nz.temple.client.render.Icons.draw(g, nz.temple.client.render.Icons.Icon.CHEVRON_RIGHT, 1.2f * k, 0, 21 * k, HudModule.GOLD);
			pose.popMatrix();
		}
		ui.textMiddle(main, x + 30, y + 10, 11, 0xFFFFFFFF, Fonts.SEMIBOLD);
		ui.textMiddle(sub, x + 30, y + 21, 8, 0xB0FFFFFF, Fonts.MEDIUM);
		int timerColour = left <= 0 ? 0xFFFF6B6B : left < 60 ? 0xFFFFB340 : 0xFFE9E9E9;
		ui.fill(x + w - timerW - 18, y + 5, timerW + 10, 11, 5.5f, 0x40000000);
		ui.textMiddle(timer, x + w - timerW - 13, y + 10.5f, 8.5f, timerColour, Fonts.SEMIBOLD);
		ui.resetTransform();
		ui.alpha(1);
	}
}
