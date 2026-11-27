package nz.temple.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import nz.temple.TempleNz;
import nz.temple.client.TempleNzClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Waypoints {
	private static final int[] PALETTE = {0xFFFFD60A, 0xFF64D2FF, 0xFF30D158, 0xFFFF6482, 0xFFBF5AF2, 0xFFFF9F0A, 0xFFFFFFFF};
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final class Waypoint {
		public String name;
		public int x;
		public int y;
		public int z;
		public String dimension;
		public String server;
		public int color;
		public boolean visible = true;
		public long created;
		public double[] in(String dim) {
			if (dimension.equals(dim)) {
				return new double[] {x + 0.5, y, z + 0.5};
			}
			double f = ServerKey.scale(dim) / ServerKey.scale(dimension);
			return new double[] {x * f + 0.5, y, z * f + 0.5};
		}
		public boolean showsIn(String dim) {
			return dimension.equals(dim) || pair(dimension, dim);
		}
	}
	private static final List<Waypoint> ALL = new CopyOnWriteArrayList<>();
	private static boolean loaded;

	private Waypoints() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(TempleNz.MOD_ID).resolve("waypoints.json");
	}
	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try {
			List<Waypoint> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Waypoint>>() { }.getType());
			if (read != null) {
				read.removeIf(w -> w == null || w.name == null || w.dimension == null || w.server == null);
				ALL.addAll(read);
			}
		} catch (IOException | RuntimeException e) {
			TempleNzClient.LOGGER.warn("Could not read {}", file, e);
		}
	}
	public static void save() {
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("waypoints.json.tmp");
			Files.writeString(tmp, GSON.toJson(new ArrayList<>(ALL)), StandardCharsets.UTF_8);
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			TempleNzClient.LOGGER.warn("Could not write {}", file, e);
		}
	}
	public static List<Waypoint> here() {
		load();
		String server = ServerKey.current();
		List<Waypoint> out = new ArrayList<>();
		if (server != null) {
			for (Waypoint w : ALL) {
				if (w.server.equals(server)) {
					out.add(w);
				}
			}
		}
		return out;
	}
	public static List<Waypoint> visibleIn(String dim) {
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : here()) {
			if (w.visible && w.showsIn(dim)) {
				out.add(w);
			}
		}
		return out;
	}
	public static Waypoint add(String name, int x, int y, int z) {
		load();
		String server = ServerKey.current();
		String dim = ServerKey.dimension();
		if (server == null || dim == null) {
			return null;
		}
		Waypoint w = new Waypoint();
		List<Waypoint> here = here();
		w.name = name == null || name.isBlank() ? uniqueName(here) : name.trim();
		w.x = x;
		w.y = y;
		w.z = z;
		w.dimension = dim;
		w.server = server;
		w.color = PALETTE[here.size() % PALETTE.length];
		w.created = System.currentTimeMillis();
		ALL.add(w);
		save();
		return w;
	}
	public static Waypoint addHere(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return null;
		}
		return add(name, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
	}
	public static void remove(Waypoint w) {
		if (ALL.remove(w)) {
			save();
		}
	}
	public static Waypoint find(String name) {
		for (Waypoint w : here()) {
			if (w.name.equalsIgnoreCase(name.trim())) {
				return w;
			}
		}
		return null;
	}
	private static String uniqueName(List<Waypoint> here) {
		for (int n = here.size() + 1; ; n++) {
			String name = "Waypoint " + n;
			if (here.stream().noneMatch(w -> w.name.equalsIgnoreCase(name))) {
				return name;
			}
		}
	}
	private static boolean pair(String a, String b) {
		return a.equals("overworld") && b.equals("the_nether") || a.equals("the_nether") && b.equals("overworld");
	}
	public static String coords(Waypoint w) {
		return String.format(Locale.ROOT, "%d %d %d", w.x, w.y, w.z);
	}
}
