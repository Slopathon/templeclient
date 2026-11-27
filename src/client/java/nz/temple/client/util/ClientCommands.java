package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import nz.temple.client.gui.MapScreen;
import nz.temple.client.gui.PlayerHistoryScreen;
import nz.temple.client.modules.client.StreamerMode;
import nz.temple.client.modules.movement.ElytraAutopilot;
import nz.temple.client.modules.render.WaypointsModule;
import nz.temple.command.CommandManager;

import java.util.Arrays;

public final class ClientCommands {
	private ClientCommands() {
	}

	public static void register(CommandManager commands) {
		commands.register("wp add [name] | del <name> | list – waypoints on this server", (parts, out) -> {
			String sub = parts.length > 1 ? parts[1].toLowerCase(java.util.Locale.ROOT) : "list";
			String rest = parts.length > 2 ? String.join(" ", Arrays.copyOfRange(parts, 2, parts.length)) : "";
			switch (sub) {
				case "add", "set", "here" -> {
					Waypoints.Waypoint w = Waypoints.addHere(rest);
					if (w == null) {
						out.accept("Join a world first.");
					} else {
						WaypointsModule.added(w);
					}
				}
				case "del", "delete", "remove", "rm" -> {
					Waypoints.Waypoint w = Waypoints.find(rest);
					if (w == null) {
						out.accept("No waypoint called \"" + rest + "\".");
					} else {
						Waypoints.remove(w);
						out.accept("Deleted waypoint \"" + w.name + "\".");
					}
				}
				default -> {
					var here = Waypoints.here();
					if (here.isEmpty()) {
						out.accept("No waypoints on this server yet. .wp add <name> adds one where you stand.");
					}
					for (Waypoints.Waypoint w : here) {
						out.accept(w.name + ": " + StreamerMode.xyz(w.x, w.y, w.z) + " (" + w.dimension + ")");
					}
				}
			}
		}, "wp", "waypoint", "waypoints");

		commands.register("fly <x> <z> [nether] | <waypoint> | stop – Elytra Autopilot", (parts, out) -> {
			Minecraft mc = Minecraft.getInstance();
			if (parts.length < 2) {
				out.accept("Usage: .fly <x> <z> [nether|overworld], .fly <waypoint name> or .fly stop");
				return;
			}
			if (parts[1].equalsIgnoreCase("stop")) {
				var pilot = nz.temple.client.TempleNzClient.modules().get(ElytraAutopilot.class);
				if (pilot != null) {
					pilot.setEnabled(false);
				}
				out.accept("Elytra Autopilot stopped.");
				return;
			}
			try {
				int x = (int) Math.floor(Double.parseDouble(parts[1]));
				int z = (int) Math.floor(Double.parseDouble(parts[2]));
				String dim = ServerKey.dimension();
				if (parts.length > 3) {
					String d = parts[3].toLowerCase(java.util.Locale.ROOT);
					dim = d.startsWith("n") ? "the_nether" : d.startsWith("e") ? "the_end" : "overworld";
				}
				ElytraAutopilot.flyTo(x, z, dim == null ? "overworld" : dim, null);
				return;
			} catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
			}
			Waypoints.Waypoint w = Waypoints.find(String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)));
			if (w == null) {
				out.accept("No waypoint called that. Usage: .fly <x> <z> [nether] or .fly <waypoint>");
			} else if (mc.player != null) {
				ElytraAutopilot.flyTo(w.x, w.z, w.dimension, w.name);
			}
		}, "fly", "goto");

		commands.register("map – open the world map", (parts, out) -> {
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> mc.execute(() -> mc.gui.setScreen(new MapScreen(null))));
		}, "map");

		commands.register("history – everyone you have been near", (parts, out) -> {
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> mc.execute(() -> mc.gui.setScreen(new PlayerHistoryScreen(null))));
		}, "history", "players");
	}
}
