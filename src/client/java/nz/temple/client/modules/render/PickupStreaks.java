package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class PickupStreaks extends Module implements nz.temple.client.modules.cosmetic.Previewable, nz.temple.client.render.FxSource {
	private static final int MAX_STREAKS = 60;
	private final BoolSetting items;
	private final BoolSetting experience;
	private final ModeSetting who;
	private final NumberSetting duration;
	private final NumberSetting size;
	private final NumberSetting trail;
	private final BoolSetting typeColors;
	private final nz.temple.client.modules.cosmetic.Look look;
	private Vec3 previewTarget;
	private final Map<Integer, Tracked> tracked = new HashMap<>();
	private final List<Streak> streaks = new ArrayList<>();
	private ClientLevel level;
	private record Tracked(double x, double y, double z, boolean xp) {
	}
	private static final class Streak {
		final double fromX, fromY, fromZ;
		final int owner;
		final Vec3 fixedTarget;
		final boolean xp;
		final long born = System.nanoTime();
		final double phase;
		Streak(double x, double y, double z, int owner, boolean xp, double phase) {
			this(x, y, z, owner, xp, phase, null);
		}

		Streak(double x, double y, double z, int owner, boolean xp, double phase, Vec3 fixedTarget) {
			this.fixedTarget = fixedTarget;
			fromX = x;
			fromY = y;
			fromZ = z;
			this.owner = owner;
			this.xp = xp;
			this.phase = phase;
		}
	}
	public PickupStreaks() {
		super("Pickup Streaks", Category.COSMETICS, "A streak of light flies to whoever picks up an item or experience orb.");
		items = add(new BoolSetting("Items", true).describe("Shows a streak when items are picked up."));
		experience = add(new BoolSetting("Experience", true).describe("Shows a streak when experience orbs are picked up."));
		who = add(new ModeSetting("Who", List.of("Self", "Players", "Both"), "Both")
				.describe("Whose pickups show a streak."));
		group();
		duration = add(new NumberSetting("Duration", 0.5, 0.15, 2, 0.05, " s").describe("How long the streak takes to arrive."));
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Streak size."));
		trail = add(new NumberSetting("Trail", 0.6, 0.1, 2, 0.05, " b").describe("Tail length."));
		group();
		typeColors = add(new BoolSetting("Colour by type", true)
				.describe("Gold for items, green for experience. Off uses the colour settings below."));
		look = new nz.temple.client.modules.cosmetic.Look(x -> add(x), "Light");
		on(TickEvent.class, e -> tick());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			tracked.clear();
			streaks.clear();
			return;
		}
		for (Iterator<Map.Entry<Integer, Tracked>> it = tracked.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Tracked> e = it.next();
			if (mc.level.getEntity(e.getKey()) != null) {
				continue;
			}
			Tracked t = e.getValue();
			it.remove();
			Player owner = nearestCollector(mc, t);
			if (owner != null && streaks.size() < MAX_STREAKS) {
				streaks.add(new Streak(t.x(), t.y(), t.z(), owner.getId(), t.xp(), Math.random()));
			}
		}
		tracked.clear();
		for (Entity entity : mc.level.entitiesForRendering()) {
			boolean xp = entity instanceof ExperienceOrb;
			boolean item = entity instanceof ItemEntity;
			if (xp && !experience.get() || item && !items.get() || !xp && !item) {
				continue;
			}
			if (entity.distanceToSqr(mc.player) > 48 * 48) {
				continue;
			}
			tracked.put(entity.getId(), new Tracked(entity.getX(), entity.getY(), entity.getZ(), xp));
		}
	}
	private Player nearestCollector(Minecraft mc, Tracked t) {
		Player best = null;
		double bestSq = 4.2 * 4.2;
		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			if (nz.temple.client.util.Wearer.mine(player) ? who.is("Players") : who.is("Self")) {
				continue;
			}
			double d = player.distanceToSqr(t.x(), t.y(), t.z());
			if (d < bestSq) {
				bestSq = d;
				best = player;
			}
		}
		return best;
	}
	private int color(boolean xp, double offset, double fade) {
		if (!typeColors.get()) {
			return look.color(offset, fade);
		}
		return Fx.withAlpha(xp ? 0xFF6BE38A : 0xFFE9B949, (float) (255 * look.alpha() * Math.max(0, Math.min(1, fade))));
	}
	@Override
	public void preview(Vec3 at) {
		Minecraft mc = Minecraft.getInstance();
		level = mc.level;
		if (mc.player == null) {
			return;
		}
		for (int i = 0; i < 6; i++) {
			double a = i * 1.05;
			streaks.add(new Streak(at.x + Math.cos(a) * 1.5, at.y + 0.3, at.z + Math.sin(a) * 1.5, mc.player.getId(), i % 2 == 0,
					Math.random(), at.add(0, 1.2, 0)));
		}
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null || level != mc.level) {
			streaks.clear();
			return;
		}
		long now = System.nanoTime();
		double life = duration.get() * 1e9;
		double s = size.get();
		for (int i = streaks.size() - 1; i >= 0; i--) {
			Streak st = streaks.get(i);
			double t = (now - st.born) / life;
			Entity owner = mc.level.getEntity(st.owner);
			if (t >= 1.3 || owner == null && st.fixedTarget == null) {
				streaks.remove(i);
				continue;
			}
			Vec3 target = st.fixedTarget != null ? st.fixedTarget : owner.getPosition(partial).add(0, owner.getBbHeight() * 0.55, 0);
			if (t >= 1) {
				arrive(fx, target, (t - 1) / 0.3, st, s);
				continue;
			}
			double eased = 1 - Math.pow(1 - t, 2.4);
			double reach = trail.get() / Math.max(0.001, distance(st, target));
			double fade = Math.min(1, (1 - t) * 3 + 0.35);
			int samples = 18;
			double[] path = new double[samples * 3];
			double[] radius = new double[samples];
			int[] colours = new int[samples];
			double side = (st.phase - 0.5) * 0.9;
			for (int k = 0; k < samples; k++) {
				double w = k / (samples - 1.0);
				double e = Math.max(0, eased - reach * (1 - w));
				double lift = Math.sin(e * Math.PI);
				path[k * 3] = st.fromX + (target.x - st.fromX) * e + lift * side * 0.4;
				path[k * 3 + 1] = st.fromY + (target.y - st.fromY) * e + lift * 0.55;
				path[k * 3 + 2] = st.fromZ + (target.z - st.fromZ) * e - lift * side * 0.4;
				radius[k] = 0.035 * s * Math.pow(w, 1.3);
				colours[k] = color(st.xp, st.phase + w * 0.2, fade * Math.pow(w, 0.8));
			}
			nz.temple.client.modules.cosmetic.SoftTrail.trail(fx, look, path, samples, radius, colours, 1);
			int head = (samples - 1) * 3;
			double hx = path[head];
			double hy = path[head + 1];
			double hz = path[head + 2];
			int c = color(st.xp, st.phase, fade);
			nz.temple.client.modules.cosmetic.SoftTrail.head(fx, look, hx, hy, hz, 0.06 * s, c);
			fx.additive(!look.matte());
			if (st.xp) {
				fx.softRing(hx, hy, hz, 0.16 * s * (1 + 0.25 * Math.sin(t * 30)), Fx.fade(c, 0.6), 0.3);
			} else {
				fx.sparkle(hx, hy, hz, 0.22 * s, Fx.fade(Fx.lerp(c, 0xFFFFFFFF, 0.5), 0.9), t * 9 + st.phase * 6);
			}
			for (int k = 1; k < 6; k++) {
				double at = eased - k * 0.12;
				if (at <= 0) {
					break;
				}
				double lift = Math.sin(at * Math.PI);
				double drift = (eased - at) * 0.25;
				double px = st.fromX + (target.x - st.fromX) * at + lift * side * 0.4 + Math.sin(st.phase * 40 + k) * drift;
				double py = st.fromY + (target.y - st.fromY) * at + lift * 0.55 - drift * 0.6;
				double pz = st.fromZ + (target.z - st.fromZ) * at - lift * side * 0.4 + Math.cos(st.phase * 40 + k) * drift;
				fx.light(px, py, pz, 0.035 * s, color(st.xp, st.phase, fade * (1 - k / 6.0)), 0.8);
			}
			fx.additive(false);
		}
	}
	private void arrive(Fx fx, Vec3 at, double u, Streak st, double s) {
		double fade = (1 - u) * (1 - u);
		int c = color(st.xp, st.phase, fade);
		fx.additive(!look.matte());
		fx.light(at.x, at.y, at.z, 0.35 * s * (1 - u * 0.5), Fx.fade(c, 0.7), 0.6);
		fx.softRing(at.x, at.y, at.z, 0.2 * s + u * 0.5 * s, Fx.fade(c, 0.8), 0.4);
		fx.additive(false);
	}
	private static double distance(Streak st, Vec3 target) {
		double dx = target.x - st.fromX;
		double dy = target.y - st.fromY;
		double dz = target.z - st.fromZ;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
	@Override
	protected void onDisable() {
		tracked.clear();
		streaks.clear();
	}
}
