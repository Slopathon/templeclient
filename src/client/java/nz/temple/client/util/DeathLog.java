package nz.temple.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class DeathLog {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/deaths");
	private static final int KEEP = 50;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public record Death(double x, double y, double z, String dimension, String server, long timeMillis, int itemCount) {
	}
	private static final List<Death> deaths = new ArrayList<>();
	private static final List<Consumer<Death>> listeners = new ArrayList<>();
	private static boolean wasDead;
	private static boolean loaded;
	private DeathLog() {
	}

	public static void register() {
		load();
		ClientTickEvents.END_CLIENT_TICK.register(DeathLog::tick);
	}
	public static void onDeath(Consumer<Death> listener) {
		listeners.add(listener);
	}
	public static synchronized List<Death> all() {
		return List.copyOf(deaths);
	}
	public static synchronized List<Death> here() {
		String server = serverId();
		List<Death> out = new ArrayList<>();
		for (Death d : deaths) {
			if (d.server().equals(server)) {
				out.add(d);
			}
		}
		return out;
	}
	public static synchronized Death latestHere() {
		String server = serverId();
		for (int i = deaths.size() - 1; i >= 0; i--) {
			Death d = deaths.get(i);
			if (d.server().equals(server)) {
				return d;
			}
		}
		return null;
	}
	public static synchronized void forget(Death death) {
		if (deaths.remove(death)) {
			save();
		}
	}
	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			wasDead = false;
			return;
		}
		boolean dead = player.isDeadOrDying() || player.getHealth() <= 0;
		if (dead && !wasDead) {
			int items = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (!player.getInventory().getItem(i).isEmpty()) {
					items++;
				}
			}
			Death death = new Death(player.getX(), player.getY(), player.getZ(), mc.level.dimension().identifier().toString(),
					serverId(), System.currentTimeMillis(), items);
			synchronized (DeathLog.class) {
				deaths.add(death);
				while (deaths.size() > KEEP) {
					deaths.remove(0);
				}
				save();
			}
			for (Consumer<Death> listener : listeners) {
				try {
					listener.accept(death);
				} catch (RuntimeException e) {
					LOGGER.warn("Death listener failed", e);
				}
			}
		}
		wasDead = dead;
	}
	public static String serverId() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getSingleplayerServer() != null) {
			return "singleplayer:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		}
		var data = mc.getCurrentServer();
		return data == null ? "unknown" : data.ip;
	}
	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(TempleNz.MOD_ID).resolve("deaths.json");
	}
	private static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			if (Files.exists(file())) {
				List<Death> read = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), new TypeToken<List<Death>>() {
				}.getType());
				if (read != null) {
					read.removeIf(d -> d == null || d.server() == null || d.dimension() == null);
					deaths.addAll(read);
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Could not read the death log; starting a new one", e);
		}
	}
	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(deaths), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warn("Could not save the death log", e);
		}
	}
}
