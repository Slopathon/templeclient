package nz.temple.client.modules.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.cosmetic.Look;
import nz.temple.client.render.BodyFx;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Projection;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.social.FriendManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LogoutSpots extends Module {
	private static final long CONFIRM_MILLIS = 3000;
	private static final int MAX_SPOTS = 64;
	private final BoolSetting friends;
	private final NumberSetting lifetime;
	private final BoolSetting notify;
	private final BoolSetting notifyRejoin;
	private final BoolSetting tag;
	private final BoolSetting throughWalls;
	private final Look look;
	private final FriendManager friendManager;
	private final Map<UUID, Seen> seen = new HashMap<>();
	private final Map<UUID, Long> vanished = new HashMap<>();
	private final List<Spot> spots = new ArrayList<>();
	private ClientLevel level;
	private record Seen(String name, BodyFx.Body body, float health, float absorption) {
	}
	private record Spot(UUID id, String name, BodyFx.Body body, float health, float absorption, long millis) {
	}

	public LogoutSpots(FriendManager friendManager) {
		super("Logout Spots", Category.RENDER, "Leaves a marker where nearby players log out, showing their name, health and how long ago "
				+ "they left.");
		this.friendManager = friendManager;
		friends = add(new BoolSetting("Friends", true).describe("Includes friends."));
		lifetime = add(new NumberSetting("Keep for", 30, 0, 240, 5, " min")
				.describe("How long a marker stays. It is removed when the player rejoins. 0 keeps it until then."));
		notify = add(new BoolSetting("Notify", true).describe("Notifies you when someone logs out nearby."));
		notifyRejoin = add(new BoolSetting("Notify on return", true).describe("Notifies you when a marked player logs back in."));
		tag = add(new BoolSetting("Tag", true).describe("Shows name, health, distance and time since logout above the marker."));
		throughWalls = add(new BoolSetting("Through walls", true).describe("Shows the marker through terrain."));
		group();
		look = new Look(s -> add(s), "Glass");
		on(TickEvent.class, e -> tick());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || mc.getConnection() == null) {
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			seen.clear();
			vanished.clear();
			spots.clear();
			return;
		}
		long now = System.currentTimeMillis();
		Map<UUID, Player> present = new HashMap<>();
		for (Player p : mc.level.players()) {
			if (p != mc.player && !p.isSpectator()) {
				present.put(p.getUUID(), p);
			}
		}
		for (Player p : present.values()) {
			seen.put(p.getUUID(), new Seen(p.getName().getString(), BodyFx.Body.of(p, 1), p.getHealth(), p.getAbsorptionAmount()));
			vanished.remove(p.getUUID());
		}
		for (Iterator<Map.Entry<UUID, Seen>> it = seen.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Seen> e = it.next();
			UUID id = e.getKey();
			if (present.containsKey(id)) {
				continue;
			}
			long since = vanished.computeIfAbsent(id, k -> now);
			boolean inTab = mc.getConnection().getPlayerInfo(id) != null;
			if (!inTab) {
				Seen s = e.getValue();
				it.remove();
				vanished.remove(id);
				if (friends.get() || !friendManager.isFriend(s.name())) {
					logout(id, s, now);
				}
			} else if (now - since > CONFIRM_MILLIS) {
				it.remove();
				vanished.remove(id);
			}
		}
		for (Iterator<Spot> it = spots.iterator(); it.hasNext(); ) {
			Spot s = it.next();
			if (mc.getConnection().getPlayerInfo(s.id()) != null) {
				it.remove();
				if (notifyRejoin.get() && TempleNzClient.notifications() != null) {
					TempleNzClient.notifications().info(s.name() + " is back", "logged in again after " + ago(now - s.millis()));
				}
			} else if (lifetime.get() > 0 && now - s.millis() > lifetime.get() * 60_000) {
				it.remove();
			}
		}
	}
	private void logout(UUID id, Seen s, long now) {
		spots.removeIf(o -> o.id().equals(id));
		if (spots.size() >= MAX_SPOTS) {
			spots.remove(0);
		}
		spots.add(new Spot(id, s.name(), s.body(), s.health(), s.absorption(), now));
		Minecraft mc = Minecraft.getInstance();
		if (notify.get() && TempleNzClient.notifications() != null && mc.player != null) {
			Vec3 at = s.body().feet();
			TempleNzClient.notifications().info(s.name() + " logged out", String.format(Locale.ROOT, "%.0f m away at %s",
					mc.player.position().distanceTo(at), nz.temple.client.modules.client.StreamerMode.xyz((int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z))));
		}
	}
	private static String ago(long millis) {
		long s = millis / 1000;
		if (s < 60) {
			return s + "s";
		}
		if (s < 3600) {
			return s / 60 + "m";
		}
		return s / 3600 + "h " + (s % 3600) / 60 + "m";
	}
	@Override
	protected void onDisable() {
		spots.clear();
		seen.clear();
		vanished.clear();
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || spots.isEmpty() || mc.level != level) {
			return;
		}
		fx.throughWalls(throughWalls.get());
		double time = System.nanoTime() / 1e9;
		for (Spot s : spots) {
			double age = (System.currentTimeMillis() - s.millis()) / 1000.0;
			BodyFx.statue(fx, s.body(), look, Math.min(1, age / 0.6), time + s.id().hashCode() % 100);
		}
		fx.throughWalls(false);
	}
	public void render(GuiGraphicsExtractor g, DeltaTracker delta, Projection projection, Ui ui) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !tag.get() || spots.isEmpty() || mc.player == null || mc.level != level || !projection.ready()) {
			return;
		}
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		long now = System.currentTimeMillis();
		float uiScale = UiScale.factor();
		ui.begin(g);
		for (Spot s : spots) {
			Vec3 feet = s.body().feet();
			float[] p = projection.toScreen(feet.x, feet.y + s.body().height() + 0.45, feet.z, gw, gh);
			if (p == null) {
				continue;
			}
			double dist = mc.player.position().distanceTo(feet);
			String name = s.name();
			String sub = String.format(Locale.ROOT, "%.1f hp  ·  %.0f m  ·  left %s ago", s.health() + s.absorption(), dist, ago(now - s.millis()));
			float size = 9 * uiScale;
			float small = 7.5f * uiScale;
			float w = Math.max(ui.textWidth(name, Fonts.SEMIBOLD, size), ui.textWidth(sub, Fonts.MEDIUM, small)) + 14 * uiScale;
			float h = 25 * uiScale;
			float x = p[0] - w / 2;
			float y = p[1] - h;
			ui.glass(x, y, w, h, 8 * uiScale);
			ui.textMiddle(name, p[0] - ui.textWidth(name, Fonts.SEMIBOLD, size) / 2, y + 8.5f * uiScale, size, 0xFFFFFFFF, Fonts.SEMIBOLD);
			float frac = Math.max(0, Math.min(1, s.health() / 20f));
			int hp = Ui.lerpColor(0xFFFF453A, 0xFF34C759, frac);
			ui.textMiddle(sub, p[0] - ui.textWidth(sub, Fonts.MEDIUM, small) / 2, y + 18 * uiScale, small, (0xE6 << 24) | (hp & 0xFFFFFF), Fonts.MEDIUM);
		}
	}
}
