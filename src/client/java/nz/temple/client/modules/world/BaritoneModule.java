package nz.temple.client.modules.world;

import net.minecraft.client.Minecraft;
import nz.temple.client.TempleNzClient;
import nz.temple.client.util.BaritoneBridge;
import nz.temple.client.util.BlockCatalog;
import nz.temple.client.util.Waypoints;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ActionSetting;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.EntitySelection;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.TextSetting;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class BaritoneModule extends Module {
	private final ModeSetting task;
	private final NumberSetting x;
	private final NumberSetting y;
	private final NumberSetting z;
	private final BoolSetting anyHeight;
	private final TextSetting waypoint;
	private final EntitySelection blocks;
	private final NumberSetting amount;
	private final TextSetting player;
	private final NumberSetting distance;
	private final NumberSetting farmRange;
	private final BoolSetting humanTurns;
	private final NumberSetting turnSpeed;
	private final NumberSetting jitter;
	private final BoolSetting freeLook;
	private final BoolSetting breakBlocks;
	private final BoolSetting placeBlocks;
	private final BoolSetting sprint;
	private final BoolSetting parkour;
	private final BoolSetting inventory;
	private final NumberSetting fall;
	private final BoolSetting showPath;
	private final BoolSetting chat;
	private int running;
	private int idle;
	public BaritoneModule() {
		super("Baritone", Category.WORLD, "Walks, mines and builds its way to a goal for you with Baritone, the pathfinding mod. "
				+ "Pick a task and turn this on; it turns off when the task is done.");
		task = add(new ModeSetting("Task", List.of("Go to", "Go to waypoint", "Mine", "Follow player", "Explore", "Farm", "Travel"), "Go to")
				.describe("What to do when turned on. Go to walks to a position. Go to waypoint walks to one of your waypoints. Mine "
						+ "finds and digs the chosen blocks. Follow player stays with a player. Explore walks out to unseen chunks. Farm "
						+ "harvests and replants crops nearby. Travel walks a set distance the way you are facing."));
		x = add(new NumberSetting("X", 0, -30_000_000, 30_000_000, 1, "").describe("Where to go.").visibleWhen(() -> task.is("Go to")));
		y = add(new NumberSetting("Y", 64, -64, 320, 1, "").describe("Where to go.").visibleWhen(() -> task.is("Go to") && !anyHeightSet()));
		z = add(new NumberSetting("Z", 0, -30_000_000, 30_000_000, 1, "").describe("Where to go.").visibleWhen(() -> task.is("Go to")));
		anyHeight = add(new BoolSetting("Any height", true).describe("Goes to X and Z at whatever height the ground is.")
				.visibleWhen(() -> task.is("Go to")));
		add(new ActionSetting("Use my position", "Here", this::here).describe("Fills in X, Y and Z with where you stand.")
				.visibleWhen(() -> task.is("Go to")));
		waypoint = add(new TextSetting("Waypoint", "", 32, "Waypoint name").describe("The waypoint to go to, by name.")
				.visibleWhen(() -> task.is("Go to waypoint")));
		blocks = add(BlockCatalog.selection("Blocks", "diamond_ore, deepslate_diamond_ore").describe("The blocks to mine.")
				.visibleWhen(() -> task.is("Mine")));
		amount = add(new NumberSetting("Amount", 0, 0, 2304, 1, "").describe("Stops after mining this many. 0 keeps going.")
				.visibleWhen(() -> task.is("Mine")));
		player = add(new TextSetting("Player", "", 16, "Player name").describe("The player to follow.")
				.visibleWhen(() -> task.is("Follow player")));
		distance = add(new NumberSetting("Distance", 1000, 16, 100_000, 16, " b").describe("How far to travel.")
				.visibleWhen(() -> task.is("Travel")));
		farmRange = add(new NumberSetting("Range", 32, 8, 128, 4, " b").describe("How far from where you start to farm.")
				.visibleWhen(() -> task.is("Farm")));
		group("Looking");
		humanTurns = add(new BoolSetting("Turn like a person", true)
				.describe("Turns toward each target at a human speed, easing in at the end and never exactly the same way twice, "
						+ "instead of snapping. Slower, and much harder for aim checks to tell apart from a player."));
		turnSpeed = add(new NumberSetting("Turn speed", 25, 5, 90, 1, "°/t").describe("The fastest it turns, in degrees a tick.")
				.visibleWhen(humanTurns::get));
		freeLook = add(new BoolSetting("Free look", false)
				.describe("Keeps your view your own while Baritone turns only on the server. Between targets the server then sees "
						+ "you snap back to where your view points, which aim checks can catch; off, your view turns with it."));
		jitter = add(new NumberSetting("Look jitter", 0.1, 0, 2, 0.05, "°").describe("A tiny random wobble in where it looks."));
		group("Pathing");
		breakBlocks = add(new BoolSetting("Break blocks", true).describe("Lets it dig through blocks in the way."));
		placeBlocks = add(new BoolSetting("Place blocks", true).describe("Lets it bridge and pillar with blocks from your hotbar."));
		sprint = add(new BoolSetting("Sprint", true).describe("Sprints where it can."));
		parkour = add(new BoolSetting("Parkour", false).describe("Lets it jump gaps. Faster, but it can overshoot."));
		inventory = add(new BoolSetting("Use inventory", false).describe("Lets it move tools and blocks from your inventory to the hotbar."));
		fall = add(new NumberSetting("Max fall", 3, 2, 20, 1, " b").describe("The highest drop it will take without water. Over 3 hurts."));
		showPath = add(new BoolSetting("Show path", true).describe("Draws the path and the goal."));
		chat = add(new BoolSetting("Chat commands", false).describe("Lets you also control Baritone by typing #commands in chat."));
		add(new ActionSetting("Install Baritone", "Install", this::install)
				.describe("Downloads the official Baritone for your Minecraft version into your mods folder. Restart the game afterwards.")
				.visibleWhen(() -> !BaritoneBridge.installed()));
		add(new ActionSetting("Stop", "Stop", () -> {
			BaritoneBridge.stop();
			setEnabled(false);
		}).describe("Stops whatever Baritone is doing."));
		for (var s : List.of(humanTurns, turnSpeed, freeLook, jitter, breakBlocks, placeBlocks, sprint, parkour, inventory, fall, showPath, chat)) {
			s.addListener(this::applySettings);
		}
		on(TickEvent.class, e -> tick());
	}
	private boolean anyHeightSet() {
		return anyHeight != null && anyHeight.get();
	}
	private void here() {
		var p = Minecraft.getInstance().player;
		if (p != null) {
			x.set((double) p.getBlockX());
			y.set((double) p.getBlockY());
			z.set((double) p.getBlockZ());
		}
	}
	private void install() {
		if (BaritoneBridge.installed()) {
			notify("Baritone is already installed.");
			return;
		}
		notify("Downloading Baritone…");
		BaritoneBridge.install().thenAccept(error -> Minecraft.getInstance().execute(() ->
				notify(error == null ? "Baritone is installed. Restart Minecraft to start using it." : error)));
	}
	private static void notify(String text) {
		if (TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().info("Baritone", text);
		}
	}
	private void applySettings() {
		BaritoneBridge.setHumanize(isEnabled() && humanTurns.get(), turnSpeed.getFloat());
		if (!BaritoneBridge.installed()) {
			return;
		}
		BaritoneBridge.set("randomLooking", jitter.get());
		BaritoneBridge.set("randomLooking113", 0);
		BaritoneBridge.set("allowBreak", breakBlocks.get());
		BaritoneBridge.set("allowPlace", placeBlocks.get());
		BaritoneBridge.set("allowSprint", sprint.get());
		BaritoneBridge.set("allowParkour", parkour.get());
		BaritoneBridge.set("allowInventory", inventory.get());
		BaritoneBridge.set("maxFallHeightNoWater", fall.get());
		BaritoneBridge.set("renderPath", showPath.get());
		BaritoneBridge.set("renderGoal", showPath.get());
		BaritoneBridge.set("chatControl", chat.get());
		BaritoneBridge.set("freeLook", freeLook.get());
		BaritoneBridge.set("blockFreeLook", freeLook.get());
		BaritoneBridge.set("elytraFreeLook", freeLook.get());
		BaritoneBridge.set("antiCheatCompatibility", true);
	}
	@Override
	protected void onEnable() {
		running = 0;
		idle = 0;
		if (!BaritoneBridge.installed()) {
			notify(BaritoneBridge.supported() ? "Baritone is not installed. Press Install in this module, then restart."
					: "Baritone has no build for this Minecraft version yet.");
			setEnabled(false);
			return;
		}
		applySettings();
		String command = command();
		if (command == null) {
			setEnabled(false);
			return;
		}
		if (!BaritoneBridge.command(command)) {
			notify("Baritone could not start that task.");
			setEnabled(false);
		}
	}
	private String command() {
		return switch (task.get()) {
			case "Go to" -> anyHeight.get() ? "goto " + x.get().intValue() + " " + z.get().intValue()
					: "goto " + x.get().intValue() + " " + y.get().intValue() + " " + z.get().intValue();
			case "Go to waypoint" -> {
				Waypoints.Waypoint w = waypoint.get().isBlank() ? null : Waypoints.find(waypoint.get());
				if (w == null) {
					notify("No waypoint called \"" + waypoint.get() + "\" here.");
					yield null;
				}
				yield "goto " + w.x + " " + w.y + " " + w.z;
			}
			case "Mine" -> {
				if (blocks.get().isEmpty()) {
					notify("Choose blocks to mine first.");
					yield null;
				}
				String list = blocks.get().stream().map(id -> id.replace("minecraft:", "")).collect(Collectors.joining(" "));
				yield "mine " + (amount.get().intValue() > 0 ? amount.get().intValue() + " " : "") + list;
			}
			case "Follow player" -> {
				if (player.get().isBlank()) {
					notify("Type the name of the player to follow first.");
					yield null;
				}
				yield "follow players " + player.get().trim();
			}
			case "Explore" -> "explore";
			case "Farm" -> "farm " + farmRange.get().intValue();
			case "Travel" -> "thisway " + distance.get().intValue();
			default -> null;
		};
	}
	@Override
	protected void onDisable() {
		BaritoneBridge.setHumanize(false, turnSpeed.getFloat());
		if (BaritoneBridge.installed() && running > 0) {
			BaritoneBridge.stop();
		}
	}
	private void tick() {
		running++;
		if (running > 20 && !BaritoneBridge.busy()) {
			if (++idle > 20) {
				notify(task.get().toLowerCase(Locale.ROOT).startsWith("go to") ? "Arrived." : "Baritone finished.");
				running = 0;
				setEnabled(false);
			}
		} else {
			idle = 0;
		}
	}
}
