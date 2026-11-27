package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import nz.temple.client.TempleNzClient;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ActionSetting;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.TextSetting;
import nz.temple.util.DonutRegions;

import java.util.List;
import java.util.Locale;

public final class DonutRegion extends Module {
	private final ModeSetting display;
	private final NumberSetting mapSize;
	private final BoolSetting notifyChanges;
	private final NumberSetting target;
	private final NumberSetting retryDelay;
	private final BoolSetting arrivalSound;
	private final TextSetting[] regionArgs = new TextSetting[DonutRegions.REGIONS.length];
	private String region = "Unmapped";
	private int cell;
	private double px;
	private double pz;
	private String lastAnnouncement;
	private boolean rtpActive;
	private int attempts;
	private boolean menuClosedSinceStart;
	private boolean hopAway;
	private long lastCommand;
	public DonutRegion() {
		super("Donut Region", Category.PLAYER,
				"Which DonutSMP region and Goliath server your Overworld coordinates are in, shown on the HUD, and an RTP that "
						+ "keeps teleporting until you land on the server you picked. " + DonutRegions.MAP_NOTE);
		display = add(new ModeSetting("Display", List.of("Map", "Text"), "Map")
				.describe("Map shows the region map with your current server outlined and your position marked. Text shows the region and "
						+ "server name, for example \"NA East · Goliath 41\"."));
		mapSize = add(new NumberSetting("Map size", 90, 60, 180, 5, "")
				.describe("Width of the map. You can also resize it in Edit HUD.")
				.visibleWhen(() -> display.is("Map")));
		notifyChanges = add(new BoolSetting("Notify changes", true)
				.describe("Shows a notification when you move to a different server or leave the mapped area."));
		group();
		target = add(new NumberSetting("Target server", 5, 1, 113, 1, "")
				.describe("The Goliath server number RTP to server should get you to, as numbered on the region map (Goliath 5 is the "
						+ "centre, around 0,0). The region is chosen automatically. Numbers without a server are rejected."));
		add(new ActionSetting("RTP to server", () -> rtpActive ? "Stop" : "Start", this::toggleRtp)
				.describe("Sends /rtp for the target server's region. After each miss it switches to another region and back, since /rtp "
						+ "within one region keeps you on the same server. Repeats until you reach the target; press again or open the "
						+ "menu to stop."));
		retryDelay = add(new NumberSetting("Retry delay", 12, 4, 60, 1, " s")
				.describe("Seconds between /rtp commands. Keep it above the server's RTP cooldown and countdown."));
		arrivalSound = add(new BoolSetting("Arrival sound", true)
				.describe("Plays a sound when you arrive on the target server."));
		group();
		for (int i = 0; i < DonutRegions.REGIONS.length; i++) {
			String name = DonutRegions.REGIONS[i];
			regionArgs[i] = add(new TextSetting(name + " argument", name.toLowerCase(Locale.ROOT), 40, "region name")
					.describe("What follows /rtp for " + name + ". Change it if the server uses a different name for the region."));
		}
		on(TickEvent.class, event -> tick());
	}
	@Override
	public String displayName() {
		return cell == 0 ? "Donut Region · " + region : "Donut Region · " + region + " · " + DonutRegions.server(cell);
	}

	public boolean showMap() {
		return display.is("Map");
	}
	public float mapWidth() {
		return mapSize.getFloat();
	}

	public int cell() {
		return cell;
	}

	public String region() {
		return region;
	}
	public double playerX() {
		return px;
	}
	public double playerZ() {
		return pz;
	}

	public int targetGoliath() {
		return (int) Math.round(target.get());
	}

	public int targetCell() {
		return DonutRegions.cellOf(targetGoliath());
	}

	public boolean rtpActive() {
		return rtpActive;
	}
	public int attempts() {
		return attempts;
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (mc.getConnection() == null) {
			rtpActive = false;
		}
		if (player == null || mc.level == null) {
			region = "Unmapped";
			cell = 0;
			lastAnnouncement = null;
			return;
		}
		boolean overworld = mc.level.dimension() == Level.OVERWORLD;
		DonutRegions.Result result = overworld ? DonutRegions.locate(player.getX(), player.getZ()) : null;
		px = player.getX();
		pz = player.getZ();
		region = result == null ? "Unmapped" : result.region();
		cell = result == null ? 0 : result.cell();
		String announcement = region + ':' + cell;
		if (!announcement.equals(lastAnnouncement)) {
			lastAnnouncement = announcement;
			if (notifyChanges.get() && TempleNzClient.notifications() != null) {
				String detail = cell == 0 ? "Outside the published Overworld grid" : region + " · " + DonutRegions.server(cell);
				TempleNzClient.notifications().info("Donut Region", detail);
			}
		}
		if (rtpActive) {
			rtpTick(mc, overworld);
		}
	}
	private void toggleRtp() {
		if (rtpActive) {
			stopRtp("Stopped", "RTP to Goliath " + targetGoliath() + " cancelled");
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) {
			info("RTP to server", "join DonutSMP first");
			return;
		}
		if (targetCell() == 0) {
			info("RTP to server", "there's no Goliath " + targetGoliath() + " on the map, pick another");
			return;
		}
		if (!isEnabled()) {
			setEnabled(true);
		}
		rtpActive = true;
		attempts = 0;
		menuClosedSinceStart = false;
		hopAway = region.equals(DonutRegions.regionOf(targetCell()));
		lastCommand = 0;
		info("RTP to server", "heading for " + DonutRegions.server(targetCell()) + " (" + DonutRegions.regionOf(targetCell()) + ")");
	}
	private void rtpTick(Minecraft mc, boolean overworld) {
		int goal = targetCell();
		if (goal == 0) {
			stopRtp("Stopped", "there's no Goliath " + targetGoliath() + " on the map");
			return;
		}
		var screen = mc.gui.screen();
		boolean menuOpen = screen instanceof nz.temple.client.gui.TempleScreen;
		if (!menuOpen) {
			menuClosedSinceStart = true;
		} else if (menuClosedSinceStart) {
			stopRtp("Stopped", "RTP to " + DonutRegions.server(goal) + " stopped: menu opened");
			return;
		}
		if (overworld && cell == goal) {
			stopRtp("Arrived", "landed on " + DonutRegions.server(goal) + " after " + attempts + (attempts == 1 ? " teleport" : " teleports"));
			if (arrivalSound.get()) {
				mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
						net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.2f, 0.8f));
			}
			return;
		}
		long now = System.currentTimeMillis();
		if (lastCommand != 0 && now - lastCommand < (long) (retryDelay.get() * 1000)) {
			return;
		}
		String targetRegion = DonutRegions.regionOf(goal);
		String regionName = hopAway ? otherRegion(targetRegion) : targetRegion;
		String arg = argumentFor(regionName);
		if (mc.getConnection() == null || arg.isBlank()) {
			stopRtp("RTP to server", "no /rtp argument set for " + regionName);
			return;
		}
		mc.getConnection().sendCommand("rtp " + arg.trim());
		if (!hopAway) {
			attempts++;
		}
		hopAway = !hopAway;
		lastCommand = now;
	}
	private String otherRegion(String region) {
		List<String> others = new java.util.ArrayList<>();
		for (String r : DonutRegions.REGIONS) {
			if (!r.equals(region) && !argumentFor(r).isBlank()) {
				others.add(r);
			}
		}
		return others.isEmpty() ? region : others.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(others.size()));
	}
	private String argumentFor(String regionName) {
		for (int i = 0; i < DonutRegions.REGIONS.length; i++) {
			if (DonutRegions.REGIONS[i].equals(regionName)) {
				return regionArgs[i].get();
			}
		}
		return "";
	}
	private void stopRtp(String title, String detail) {
		rtpActive = false;
		info(title, detail);
	}

	private static void info(String title, String detail) {
		if (TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().info(title, detail);
		}
	}
	@Override
	protected void onEnable() {
		lastAnnouncement = null;
	}

	@Override
	protected void onDisable() {
		region = "Unmapped";
		cell = 0;
		lastAnnouncement = null;
		rtpActive = false;
	}
}
