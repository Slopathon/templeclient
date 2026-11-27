package nz.temple.client.modules.client;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import nz.temple.client.TempleNzClient;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.TextSetting;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StreamerMode extends Module {
	private static StreamerMode instance;
	private static final Pattern NUMBER = Pattern.compile("-?\\d{2,8}");
	private final ModeSetting coords;
	private final BoolSetting f3;
	private final BoolSetting alerts;
	private final BoolSetting chat;
	private final NumberSetting chatSlack;
	private final BoolSetting server;
	private final BoolSetting name;
	private final TextSetting alias;
	private int offsetX;
	private int offsetZ;
	public StreamerMode() {
		super("Streamer Mode", Category.CLIENT, "Hides your location for streams and screenshots: coordinates in the HUD, F3, map and "
				+ "waypoints, chat messages that would reveal your position, the server address, and optionally your name.");
		instance = this;
		coords = add(new ModeSetting("Coordinates", List.of("Hidden", "Fake offset", "Shown"), "Hidden")
				.describe("Hidden replaces coordinates with dashes. Fake offset shows coordinates shifted by a random amount that changes "
						+ "each time Streamer Mode is enabled. Shown leaves them visible."));
		f3 = add(new BoolSetting("Hide F3 position", true)
				.describe("Removes position, chunk and targeted block lines from the F3 screen."));
		alerts = add(new BoolSetting("Hide in alerts", true)
				.describe("Replaces coordinates in alerts and messages (stash finds, Chunk Finder, Sus Chunk Finder, pearl landings, copied coordinates, "
						+ "disconnect reasons) with \"hidden by Streamer Mode\". Turn off to show them."));
		group();
		chat = add(new BoolSetting("Block coords in chat", true)
				.describe("Blocks chat messages and commands that contain your coordinates (Overworld or Nether)."));
		chatSlack = add(new NumberSetting("Counts as yours within", 200, 10, 2000, 10, " b")
				.describe("How close a number must be to your real X or Z to count as your coordinates.")
				.visibleWhen(chat::get));
		group();
		server = add(new BoolSetting("Hide server address", true)
				.describe("Shows \"hidden\" instead of server addresses in Temple's server list, map and player history."));
		name = add(new BoolSetting("Hide my name", false)
				.describe("Replaces your name in chat, the tab list, sidebar, nametags and HUD."));
		alias = add(new TextSetting("Shown name", "Player", 16, "Player").describe("The name shown in place of yours.")
				.visibleWhen(name::get));
		ClientSendMessageEvents.ALLOW_CHAT.register(message -> allow(message));
		ClientSendMessageEvents.ALLOW_COMMAND.register(command -> allow("/" + command));
	}
	@Override
	protected void onEnable() {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		offsetX = (r.nextBoolean() ? 1 : -1) * r.nextInt(20_000, 400_000);
		offsetZ = (r.nextBoolean() ? 1 : -1) * r.nextInt(20_000, 400_000);
	}
	private static boolean on() {
		return instance != null && instance.isEnabled();
	}

	public static boolean hidesCoords() {
		return on() && !instance.coords.is("Shown");
	}
	public static boolean hidesF3() {
		return on() && instance.f3.get();
	}

	public static String xyz(int x, int y, int z) {
		if (!hidesCoords()) {
			return x + ", " + y + ", " + z;
		}
		if (instance.coords.is("Fake offset")) {
			return (x + instance.offsetX) + ", " + y + ", " + (z + instance.offsetZ);
		}
		return "-, -, -";
	}
	public static String xz(int x, int z) {
		if (!hidesCoords()) {
			return x + ", " + z;
		}
		if (instance.coords.is("Fake offset")) {
			return (x + instance.offsetX) + ", " + (z + instance.offsetZ);
		}
		return "-, -";
	}
	public static String x(int x) {
		return !hidesCoords() ? Integer.toString(x) : instance.coords.is("Fake offset") ? Integer.toString(x + instance.offsetX) : "-";
	}
	public static String z(int z) {
		return !hidesCoords() ? Integer.toString(z) : instance.coords.is("Fake offset") ? Integer.toString(z + instance.offsetZ) : "-";
	}

	public static String alert(int x, int y, int z) {
		return hidesAlerts() ? HIDDEN : x + ", " + y + ", " + z;
	}
	public static String alert(int x, int z) {
		return hidesAlerts() ? HIDDEN : x + ", " + z;
	}
	public static final String HIDDEN = "hidden by Streamer Mode";
	public static boolean hidesAlerts() {
		return on() && instance.alerts.get();
	}

	public static String replaceName(String text) {
		if (!on() || !instance.name.get() || instance.alias.get().isBlank() || text == null || text.isEmpty()) {
			return text;
		}
		var user = Minecraft.getInstance().getUser();
		String real = user == null ? null : user.getName();
		return real == null || real.isEmpty() || !text.contains(real) ? text : text.replace(real, instance.alias.get());
	}
	public static String serverAddress(String address) {
		return on() && instance.server.get() ? "hidden" : address;
	}

	public static String myName(String real) {
		return on() && instance.name.get() && !instance.alias.get().isBlank() ? instance.alias.get() : real;
	}
	private boolean allow(String message) {
		if (!isEnabled() || !chat.get() || message.startsWith(".")) {
			return true;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !leaks(message, player)) {
			return true;
		}
		String shown = message.length() > 40 ? message.substring(0, 40) + "…" : message;
		nz.temple.client.compat.Players.message(player, net.minecraft.network.chat.Component.literal(
				"§8[§bTemple§8] §fStreamer Mode §7stopped a message with your coordinates: §f" + shown));
		if (TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().error("Message not sent", "it contained your coordinates");
		}
		return false;
	}
	private boolean leaks(String message, LocalPlayer player) {
		double slack = chatSlack.get();
		boolean nether = player.level().dimension() == net.minecraft.world.level.Level.NETHER;
		double[] xs = {player.getX(), nether ? player.getX() * 8 : player.getX() / 8};
		double[] zs = {player.getZ(), nether ? player.getZ() * 8 : player.getZ() / 8};
		Matcher m = NUMBER.matcher(message.toLowerCase(Locale.ROOT).replace(",", " "));
		while (m.find()) {
			long n;
			try {
				n = Long.parseLong(m.group());
			} catch (NumberFormatException e) {
				continue;
			}
			if (Math.abs(n) < 100 && Math.abs(player.getX()) > 300 && Math.abs(player.getZ()) > 300) {
				continue;
			}
			for (int i = 0; i < 2; i++) {
				if (Math.abs(n - xs[i]) <= slack * (i == 0 ? 1 : 0.125) || Math.abs(n - zs[i]) <= slack * (i == 0 ? 1 : 0.125)) {
					return true;
				}
			}
		}
		return false;
	}
}
