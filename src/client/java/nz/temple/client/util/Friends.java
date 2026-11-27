package nz.temple.client.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import nz.temple.TempleNz;
import nz.temple.client.TempleNzClient;
import nz.temple.social.FriendManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Friends {
	private static final Gson GSON = new Gson();
	private static FriendManager manager;
	private static volatile int tint = 0xFF4DA3FF;
	private Friends() {
	}

	public static void attach(FriendManager friends) {
		manager = friends;
		Path file = file();
		if (Files.exists(file)) {
			try {
				List<String> names = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<String>>() { }.getType());
				if (names != null) {
					friends.load(names);
				}
			} catch (IOException | RuntimeException e) {
				TempleNzClient.LOGGER.warn("Could not read {}", file, e);
			}
		}
		friends.setOnChange(Friends::save);
	}
	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(TempleNz.MOD_ID).resolve("friends.json");
	}
	private static void save() {
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(new ArrayList<>(manager.all())), StandardCharsets.UTF_8);
		} catch (IOException e) {
			TempleNzClient.LOGGER.warn("Could not write {}", file, e);
		}
	}
	public static FriendManager manager() {
		return manager;
	}
	public static boolean isFriend(Entity entity) {
		return manager != null && entity instanceof Player p && manager.isFriend(p.getGameProfile().name());
	}
	/** Another player on your friends list (never you): combat modules leave them alone. */
	public static boolean isProtected(Entity entity) {
		return entity instanceof Player p && p != net.minecraft.client.Minecraft.getInstance().player && isFriend(p);
	}
	/** Friends within {@code range} of {@code from}, which explosive modules avoid hurting. */
	public static java.util.List<Player> explosionSensitive(net.minecraft.client.Minecraft mc, net.minecraft.world.phys.Vec3 from,
			double range) {
		java.util.List<Player> out = new java.util.ArrayList<>();
		if (mc.level == null) {
			return out;
		}
		for (Player p : mc.level.players()) {
			if (p.isAlive() && p.position().distanceToSqr(from) <= range * range && isProtected(p)) {
				out.add(p);
			}
		}
		return out;
	}
	public static int tintFor(Entity entity) {
		return tint != 0 && isFriend(entity) ? tint : 0;
	}
	public static void setTint(int argb) {
		tint = argb;
	}
}
