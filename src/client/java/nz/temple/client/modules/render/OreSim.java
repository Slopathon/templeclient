package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.AABB;
import nz.temple.client.TempleNzClient;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.OreSimulator;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.TextSetting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OreSim extends Module {
	private static final Map<String, Integer> COLOURS = new LinkedHashMap<>();
	static {
		COLOURS.put("diamond", 0x5AE0F0);
		COLOURS.put("ancient_debris", 0x8A5A44);
		COLOURS.put("emerald", 0x34D26A);
		COLOURS.put("gold", 0xFFD23F);
		COLOURS.put("iron", 0xD8A077);
		COLOURS.put("redstone", 0xFF453A);
		COLOURS.put("lapis", 0x2E6BFF);
		COLOURS.put("copper", 0xE07A45);
		COLOURS.put("coal", 0x3A3A3A);
		COLOURS.put("quartz", 0xF2EFE6);
	}
	private final TextSetting seed;
	private final NumberSetting radius;
	private final BoolSetting hiddenOnly;
	private final NumberSetting maxBoxes;
	private final Map<String, BoolSetting> ores = new LinkedHashMap<>();
	private final OreSimulator simulator = new OreSimulator();
	private final List<WorldEsp.Box> boxes = new ArrayList<>();
	private int refresh;
	private long lastWarned;
	public OreSim() {
		super("OreSim", Category.RENDER, "Shows ores predicted from the world seed by running the game's ore generation for nearby chunks. "
				+ "Reveals ores a server hides, if it uses vanilla generation.");
		seed = add(new TextSetting("Seed", "", 24, "Auto in singleplayer")
				.describe("The world seed. Click and type it, or paste with Ctrl+V. If empty, singleplayer worlds use their own seed."));
		radius = add(new NumberSetting("Radius", 6, 1, 16, 1, " chunks").describe("Number of chunks around you to simulate."));
		hiddenOnly = add(new BoolSetting("Hidden only", false)
				.describe("Only shows ores the client does not see as ore, meaning ones an anti-xray server is hiding."));
		maxBoxes = add(new NumberSetting("Max shown", 1500, 100, 6000, 100, "").describe("Maximum ore blocks drawn at once."));
		group();
		for (String ore : COLOURS.keySet()) {
			boolean on = ore.equals("diamond") || ore.equals("ancient_debris") || ore.equals("emerald") || ore.equals("gold");
			String label = ore.equals("ancient_debris") ? "Ancient debris" : Character.toUpperCase(ore.charAt(0)) + ore.substring(1);
			ores.put(ore, add(new BoolSetting(label, on).describe("Show " + label.toLowerCase() + ".")));
		}
		on(TickEvent.class, e -> tick());
	}
	@Override
	protected void onDisable() {
		simulator.clear();
		boxes.clear();
	}
	private Long resolveSeed(Minecraft mc) {
		String text = seed.get().trim();
		if (!text.isEmpty()) {
			try {
				return Long.parseLong(text);
			} catch (NumberFormatException e) {
				return (long) text.hashCode();
			}
		}
		var server = mc.getSingleplayerServer();
		if (server != null) {
			var level = server.getLevel(mc.level.dimension());
			return level != null ? level.getSeed() : server.overworld().getSeed();
		}
		return null;
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		Long worldSeed = resolveSeed(mc);
		if (worldSeed == null) {
			long now = System.currentTimeMillis();
			if (now - lastWarned > 30_000 && TempleNzClient.notifications() != null) {
				lastWarned = now;
				TempleNzClient.notifications().info("OreSim", "Type the server's seed into the Seed setting.");
			}
			boxes.clear();
			return;
		}
		if (!OreSimulator.supported(mc.level.dimension())) {
			boxes.clear();
			return;
		}
		simulator.configure(worldSeed, mc.level.dimension());
		int r = (int) Math.round(radius.get());
		simulator.tick(mc.level, player.chunkPosition(), r, 2);
		if (--refresh > 0) {
			return;
		}
		refresh = 10;
		rebuild(mc, player);
	}
	private void rebuild(Minecraft mc, LocalPlayer player) {
		List<WorldEsp.Box> next = new ArrayList<>();
		int limit = (int) Math.round(maxBoxes.get());
		BlockPos center = player.blockPosition();
		List<OreSimulator.Ore> all = new ArrayList<>();
		for (List<OreSimulator.Ore> list : simulator.results().values()) {
			all.addAll(list);
		}
		all.sort((a, b) -> Double.compare(a.pos().distSqr(center), b.pos().distSqr(center)));
		for (OreSimulator.Ore ore : all) {
			if (next.size() >= limit) {
				break;
			}
			String family = family(ore.block());
			BoolSetting toggle = family == null ? null : ores.get(family);
			if (toggle == null || !toggle.get()) {
				continue;
			}
			var state = mc.level.getBlockState(ore.pos());
			if (state.isAir()) {
				continue;
			}
			boolean visible = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().equals(ore.block());
			if (hiddenOnly.get() && visible) {
				continue;
			}
			int rgb = COLOURS.get(family);
			next.add(new WorldEsp.Box(new AABB(ore.pos()).deflate(0.02), 0xFF000000 | rgb, (visible ? 0x30 : 0x60) << 24 | rgb, 0.006f));
		}
		synchronized (boxes) {
			boxes.clear();
			boxes.addAll(next);
		}
	}
	private static String family(String block) {
		if (block.equals("ancient_debris")) {
			return "ancient_debris";
		}
		for (String key : COLOURS.keySet()) {
			if (block.contains(key)) {
				return key;
			}
		}
		return null;
	}
	public String verify() {
		Minecraft mc = Minecraft.getInstance();
		int predicted = 0;
		int matched = 0;
		int stone = 0;
		for (List<OreSimulator.Ore> list : simulator.results().values()) {
			for (OreSimulator.Ore ore : list) {
				predicted++;
				String actual = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(ore.pos()).getBlock()).getPath();
				if (actual.equals(ore.block())) {
					matched++;
				} else if (actual.contains("stone") || actual.contains("deepslate") || actual.contains("netherrack")) {
					stone++;
				}
			}
		}
		java.util.Set<BlockPos> predictedAt = new java.util.HashSet<>();
		for (List<OreSimulator.Ore> list : simulator.results().values()) {
			for (OreSimulator.Ore ore : list) {
				predictedAt.add(ore.pos());
			}
		}
		int real = 0;
		int found = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (long key : simulator.results().keySet()) {
			int cx = net.minecraft.world.level.ChunkPos.getX(key);
			int cz = net.minecraft.world.level.ChunkPos.getZ(key);
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					for (int y = mc.level.getMinY(); y < 80; y++) {
						cursor.set((cx << 4) + x, y, (cz << 4) + z);
						String id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(cursor).getBlock()).getPath();
						if (id.endsWith("_ore")) {
							real++;
							if (predictedAt.contains(cursor)) {
								found++;
							}
						}
					}
				}
			}
		}
		return "chunks=" + simulator.results().size() + " realOres=" + real + " realFound=" + found + " predicted=" + predicted + " matched=" + matched + " stoneInstead=" + stone
				+ " status=" + simulator.status();
	}
	public void collect(List<WorldEsp.Box> out) {
		if (isEnabled()) {
			synchronized (boxes) {
				out.addAll(boxes);
			}
		}
	}
}
