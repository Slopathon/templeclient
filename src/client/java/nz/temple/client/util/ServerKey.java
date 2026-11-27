package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.util.Locale;

public final class ServerKey {
	private ServerKey() {
	}
	public static String current() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		if (mc.getSingleplayerServer() != null) {
			var root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			var name = root.getFileName() == null ? "world" : root.getFileName().toString();
			if (name.equals(".") && root.getParent() != null && root.getParent().getFileName() != null) {
				name = root.getParent().getFileName().toString();
			}
			return "sp_" + safe(name);
		}
		ServerData server = mc.getCurrentServer();
		return server == null ? "unknown" : "mp_" + safe(server.ip);
	}
	public static String dimension(ResourceKey<Level> dimension) {
		var id = dimension.identifier();
		return id.getNamespace().equals("minecraft") ? id.getPath() : safe(id.getNamespace() + "_" + id.getPath());
	}
	public static String dimension() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? null : dimension(mc.level.dimension());
	}
	public static boolean isNether(String dimension) {
		return "the_nether".equals(dimension);
	}
	public static double scale(String dimension) {
		return isNether(dimension) ? 0.125 : 1;
	}
	private static String safe(String s) {
		return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}
}
