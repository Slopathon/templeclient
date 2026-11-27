package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.StreamerMode;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.ServerLight;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.util.SusZones;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class SusChunkFinder extends Module {
	private static final int RADIUS = 16;
	private static final long TICK_BUDGET_NS = 2_000_000L;
	private static final int MAX_POLLS = 128;
	private static final int MAX_COLUMN = 40;
	private static final float FADE_IN = 0.3f;
	private static final float FADE_OUT = 0.45f;
	private final NumberSetting threshold;
	private final NumberSetting scanSpeed;
	private final BoolSetting amethyst;
	private final BoolSetting kelp;
	private final BoolSetting bamboo;
	private final BoolSetting berries;
	private final BoolSetting vines;
	private final BoolSetting dripstone;
	private final BoolSetting merge;
	private final NumberSetting mergeRadius;
	private final ModeSetting notify;
	private final NumberSetting alertsPerMinute;
	private final NumberSetting renderY;
	private final NumberSetting fillOpacity;
	private final BoolSetting outline;
	private final NumberSetting outlineOpacity;
	private final ColorSetting color;
	private final Map<Long, Score> scores = new HashMap<>();
	private final ArrayDeque<Long> queue = new ArrayDeque<>();
	private final Set<Long> rescans = new LinkedHashSet<>();
	private final Map<Long, Long> dirty = new HashMap<>();
	private final Set<Long> alerted = new HashSet<>();
	private final ArrayDeque<Long> recentAlerts = new ArrayDeque<>();
	private final Map<Long, Float> chunkFade = new HashMap<>();
	private final Map<Long, ZoneFade> zoneFade = new HashMap<>();
	private List<SusZones.Flag> flags = List.of();
	private List<SusZones.Zone> zones = List.of();
	private ClientLevel level;
	private boolean queued;
	private int queueX;
	private int queueZ;
	private int lastRefill;
	private int ticks;
	private long lastFrame;
	private static final class Score {
		int score;
		double hitWeight, hitX, hitZ;
		int hitMinY = Integer.MAX_VALUE;
		final int[] counts = new int[SusZones.Signal.values().length];
		final List<SusZones.Cell> amethyst = new ArrayList<>();
		void hit(SusZones.Signal signal, BlockPos pos) {
			counts[signal.ordinal()]++;
			hitWeight += signal.weight;
			hitX += (pos.getX() + 0.5) * signal.weight;
			hitZ += (pos.getZ() + 0.5) * signal.weight;
			hitMinY = Math.min(hitMinY, pos.getY());
		}
	}
	private static final class ZoneFade {
		double x0, z0, x1, z1;
		float alpha;
	}
	public SusChunkFinder() {
		super("Sus Chunk Finder", Category.RENDER, "Flags chunks where kelp, bamboo, berries, vines or dripstone have grown far longer "
				+ "than usual, and amethyst geodes seen through the block light the server sends. Nearby chunks are merged into zones.");
		threshold = add(new NumberSetting("Threshold", 3, 1, 10, 1, "")
				.describe("Points needed to flag a chunk, in steps of 5. Higher values flag fewer chunks."));
		scanSpeed = add(new NumberSetting("Scan speed", 10, 2, 24, 1, "").describe("Chunks scanned per tick, within a 2 ms limit."));
		group("Signals");
		amethyst = add(new BoolSetting("Amethyst", true).describe("Detects amethyst geodes from the block light the server sends."));
		kelp = add(new BoolSetting("Kelp", true).describe("Kelp columns 14 or more blocks long, or 8 or more and fully grown."));
		bamboo = add(new BoolSetting("Bamboo", true).describe("Bamboo stalks 12 or more blocks tall."));
		berries = add(new BoolSetting("Berries", true).describe("Fully grown sweet berry bushes."));
		vines = add(new BoolSetting("Vines", true).describe("Vines hanging 7 or more blocks, outside lush and dripstone caves."));
		dripstone = add(new BoolSetting("Dripstone", true).describe("Pointed dripstone 5 or more blocks long, outside lush and dripstone caves."));
		group("Zones and alerts");
		merge = add(new BoolSetting("Merge zones", true).describe("Merges nearby flagged chunks into one zone and draws one square for it."));
		mergeRadius = add(new NumberSetting("Merge radius", 3, 1, 8, 1, " chunks").describe("Flagged chunks this close are merged into one zone.")
				.visibleWhen(merge::get));
		notify = add(new ModeSetting("Notifications", List.of("Toast", "Chat", "Off"), "Toast")
				.describe("How a new zone is announced."));
		alertsPerMinute = add(new NumberSetting("Alerts per minute", 6, 1, 30, 1, "").describe("Limits how many alerts are sent. Zones are "
				+ "still drawn.").visibleWhen(() -> !notify.is("Off")));
		group("Marker");
		renderY = add(new NumberSetting("Render Y", 100, -64, 320, 1, "").describe("Height of the flat marker."));
		fillOpacity = add(new NumberSetting("Fill opacity", 90, 0, 255, 1, "").describe("Opacity of the marker fill."));
		outline = add(new BoolSetting("Outline", true).describe("Draws an outline around each marker."));
		outlineOpacity = add(new NumberSetting("Outline opacity", 200, 0, 255, 1, "").describe("Opacity of the marker outline.")
				.visibleWhen(outline::get));
		color = add(new ColorSetting("Colour", 0xFF9F5AFF).describe("Colour of the markers."));
		on(TickEvent.class, e -> tick());
	}
	@Override
	protected void onEnable() {
		clear();
		ServerLight.setEnabled(true);
	}
	@Override
	protected void onDisable() {
		ServerLight.setEnabled(false);
		clear();
		level = null;
	}
	private void clear() {
		scores.clear();
		queue.clear();
		rescans.clear();
		dirty.clear();
		alerted.clear();
		recentAlerts.clear();
		chunkFade.clear();
		zoneFade.clear();
		flags = List.of();
		zones = List.of();
		queued = false;
		lastFrame = 0;
	}
	public void disconnected() {
		if (isEnabled()) {
			clear();
			ServerLight.clear();
			level = null;
		}
	}
	public void chunkLoaded(LevelChunk chunk) {
		if (isEnabled()) {
			rescans.add(ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z()));
		}
	}
	public void chunkUnloaded(LevelChunk chunk) {
		int cx = chunk.getPos().x(), cz = chunk.getPos().z();
		ServerLight.drop(cx, cz);
		if (isEnabled()) {
			long key = ChunkPos.pack(cx, cz);
			scores.remove(key);
			rescans.remove(key);
			dirty.remove(key);
		}
	}
	public void blockChanged(BlockPos pos) {
		if (isEnabled()) {
			dirty.put(ChunkPos.pack(pos), System.currentTimeMillis());
		}
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			clear();
			ServerLight.clear();
		}
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Long, Long>> it = dirty.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, Long> e = it.next();
			if (now - e.getValue() > 600) {
				rescans.add(e.getKey());
				it.remove();
			}
		}
		ServerLight.drainDirty(rescans::add);
		ChunkPos center = player.chunkPosition();
		refill(center);
		long deadline = System.nanoTime() + TICK_BUDGET_NS;
		int budget = (int) Math.round(scanSpeed.get());
		budget -= scanRescans(center, budget, deadline);
		scanQueue(budget, deadline);
		if (++ticks % 10 == 0) {
			rebuild(player);
		}
		if (ticks % 100 == 0) {
			alerted.removeIf(k -> far(k, center, RADIUS + 8));
		}
	}
	private static boolean far(long key, ChunkPos center, int radius) {
		return Math.abs(ChunkPos.getX(key) - center.x()) > radius || Math.abs(ChunkPos.getZ(key) - center.z()) > radius;
	}

	private void refill(ChunkPos center) {
		boolean moved = !queued || Math.max(Math.abs(center.x() - queueX), Math.abs(center.z() - queueZ)) >= 3;
		if (!moved && (!queue.isEmpty() || ticks - lastRefill < 20)) {
			return;
		}
		queued = true;
		queueX = center.x();
		queueZ = center.z();
		lastRefill = ticks;
		queue.clear();
		List<Long> order = new ArrayList<>();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				long key = ChunkPos.pack(center.x() + dx, center.z() + dz);
				if (!scores.containsKey(key) && level.hasChunk(center.x() + dx, center.z() + dz)) {
					order.add(key);
				}
			}
		}
		order.sort(Comparator.comparingLong(k -> {
			long dx = ChunkPos.getX(k) - center.x(), dz = ChunkPos.getZ(k) - center.z();
			return dx * dx + dz * dz;
		}));
		queue.addAll(order);
	}
	private int scanRescans(ChunkPos center, int budget, long deadline) {
		int scanned = 0;
		for (Iterator<Long> it = rescans.iterator(); it.hasNext() && scanned < budget && System.nanoTime() < deadline; ) {
			long key = it.next();
			it.remove();
			int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
			if (!far(key, center, RADIUS) && level.hasChunk(cx, cz)) {
				scores.put(key, scan(level.getChunk(cx, cz)));
				scanned++;
			}
		}
		return scanned;
	}
	private void scanQueue(int budget, long deadline) {
		int scanned = 0, polled = 0;
		while (scanned < budget && polled < MAX_POLLS && !queue.isEmpty() && System.nanoTime() < deadline) {
			polled++;
			long key = queue.pollFirst();
			int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
			if (!scores.containsKey(key) && level.hasChunk(cx, cz)) {
				scores.put(key, scan(level.getChunk(cx, cz)));
				scanned++;
			}
		}
	}
	private static final Predicate<BlockState> PLANTS = SusChunkFinder::isPlant;
	private static final Predicate<BlockState> AMETHYST = SusChunkFinder::isAmethyst;
	private static boolean isPlant(BlockState st) {
		Block b = st.getBlock();
		return b == Blocks.KELP || b == Blocks.KELP_PLANT || b == Blocks.BAMBOO || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.VINE
				|| b == Blocks.POINTED_DRIPSTONE;
	}
	private static boolean isAmethyst(BlockState st) {
		return st.is(Blocks.AMETHYST_BLOCK) || st.is(Blocks.BUDDING_AMETHYST);
	}

	private Score scan(LevelChunk chunk) {
		Score s = new Score();
		if (amethyst.get()) {
			detectAmethyst(chunk, s);
		}
		detectGrowth(chunk, s);
		s.score = SusZones.plantScore(s.counts);
		return s;
	}
	private void detectAmethyst(LevelChunk chunk, Score s) {
		int cx = chunk.getPos().x(), cz = chunk.getPos().z();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		if (ServerLight.hasChunk(cx, cz)) {
			for (BlockPos cell : ServerLight.light5(cx, cz)) {
				if (isAmethystCell(level.getBlockState(cell)) && touchesAmethyst(cell, cursor)) {
					s.amethyst.add(new SusZones.Cell(cell.getX(), cell.getY(), cell.getZ()));
				}
			}
			return;
		}
		Set<Long> seen = new HashSet<>();
		LevelChunkSection[] sections = chunk.getSections();
		int minSection = chunk.getMinY() >> 4;
		BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
		for (int i = 0; i < sections.length; i++) {
			int baseY = (minSection + i) << 4;
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir() || baseY + 15 < ServerLight.MIN_Y - 1 || baseY > ServerLight.MAX_Y + 1
					|| !section.maybeHas(AMETHYST)) {
				continue;
			}
			for (int ly = 0; ly < 16; ly++) {
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						if (!isAmethyst(section.getBlockState(lx, ly, lz))) {
							continue;
						}
						int x = (cx << 4) + lx, y = baseY + ly, z = (cz << 4) + lz;
						for (int dx = -1; dx <= 1; dx++) {
							for (int dy = -1; dy <= 1; dy++) {
								for (int dz = -1; dz <= 1; dz++) {
									int ny = y + dy;
									if ((dx | dy | dz) == 0 || ny < ServerLight.MIN_Y || ny > ServerLight.MAX_Y) {
										continue;
									}
									cell.set(x + dx, ny, z + dz);
									if (isAmethystCell(level.getBlockState(cell)) && level.getBrightness(LightLayer.BLOCK, cell) == 5
											&& seen.add(cell.asLong())) {
										s.amethyst.add(new SusZones.Cell(cell.getX(), ny, cell.getZ()));
									}
								}
							}
						}
					}
				}
			}
		}
	}
	private static boolean isAmethystCell(BlockState st) {
		return st.isAir() || st.is(Blocks.AMETHYST_CLUSTER);
	}
	private boolean touchesAmethyst(BlockPos cell, BlockPos.MutableBlockPos cursor) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if ((dx | dy | dz) != 0 && isAmethyst(level.getBlockState(cursor.set(cell.getX() + dx, cell.getY() + dy, cell.getZ() + dz)))) {
						return true;
					}
				}
			}
		}
		return false;
	}
	private void detectGrowth(LevelChunk chunk, Score s) {
		LevelChunkSection[] sections = chunk.getSections();
		int minSection = chunk.getMinY() >> 4;
		int ox = chunk.getPos().x() << 4, oz = chunk.getPos().z() << 4;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < sections.length; i++) {
			int baseY = (minSection + i) << 4;
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir() || baseY + 15 < ServerLight.MIN_Y || baseY > ServerLight.MAX_Y
					|| !section.maybeHas(PLANTS)) {
				continue;
			}
			for (int ly = 0; ly < 16; ly++) {
				int y = baseY + ly;
				if (y < ServerLight.MIN_Y || y > ServerLight.MAX_Y) {
					continue;
				}
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						BlockState st = section.getBlockState(lx, ly, lz);
						if (isPlant(st)) {
							plant(st, pos.set(ox + lx, y, oz + lz), s);
						}
					}
				}
			}
		}
	}
	private void plant(BlockState st, BlockPos pos, Score s) {
		Block b = st.getBlock();
		if ((b == Blocks.KELP || b == Blocks.KELP_PLANT) && kelp.get()) {
			BlockState above = level.getBlockState(pos.above());
			if (above.is(Blocks.KELP) || above.is(Blocks.KELP_PLANT)) {
				return;
			}
			int length = column(pos, Direction.DOWN, Blocks.KELP, Blocks.KELP_PLANT);
			boolean grown = b == Blocks.KELP && st.hasProperty(BlockStateProperties.AGE_25) && st.getValue(BlockStateProperties.AGE_25) == 25;
			if (grown && length >= 8 || length >= 14) {
				s.hit(SusZones.Signal.KELP, pos);
			}
		} else if (b == Blocks.BAMBOO && bamboo.get()) {
			if (!level.getBlockState(pos.below()).is(Blocks.BAMBOO) && column(pos, Direction.UP, Blocks.BAMBOO) >= 12) {
				s.hit(SusZones.Signal.BAMBOO, pos);
			}
		} else if (b == Blocks.SWEET_BERRY_BUSH && berries.get()) {
			if (st.hasProperty(BlockStateProperties.AGE_3) && st.getValue(BlockStateProperties.AGE_3) == 3) {
				s.hit(SusZones.Signal.BERRIES, pos);
			}
		} else if (b == Blocks.VINE && vines.get()) {
			if (!level.getBlockState(pos.above()).is(Blocks.VINE) && !naturalCave(pos) && column(pos, Direction.DOWN, Blocks.VINE) >= 7) {
				s.hit(SusZones.Signal.VINES, pos);
			}
		} else if (b == Blocks.POINTED_DRIPSTONE && dripstone.get()) {
			if (!level.getBlockState(pos.above()).is(Blocks.POINTED_DRIPSTONE) && level.getBlockState(pos.below()).is(Blocks.POINTED_DRIPSTONE)
					&& !naturalCave(pos) && column(pos, Direction.DOWN, Blocks.POINTED_DRIPSTONE) >= 5) {
				s.hit(SusZones.Signal.DRIPSTONE, pos);
			}
		}
	}
	private boolean naturalCave(BlockPos pos) {
		var biome = level.getBiome(pos);
		return biome.is(Biomes.LUSH_CAVES) || biome.is(Biomes.DRIPSTONE_CAVES);
	}
	private int column(BlockPos start, Direction dir, Block... blocks) {
		BlockPos.MutableBlockPos cursor = start.mutable();
		int length = 1;
		outer:
		while (length < MAX_COLUMN) {
			BlockState st = level.getBlockState(cursor.move(dir));
			for (Block b : blocks) {
				if (st.is(b)) {
					length++;
					continue outer;
				}
			}
			break;
		}
		return length;
	}
	private void rebuild(LocalPlayer player) {
		scores.keySet().removeIf(k -> !level.hasChunk(ChunkPos.getX(k), ChunkPos.getZ(k)));
		int min = SusZones.threshold((int) Math.round(threshold.get()));
		Map<Long, SusZones.Flag> flagged = new HashMap<>();
		Set<SusZones.Cell> cells = new LinkedHashSet<>();
		for (Map.Entry<Long, Score> e : scores.entrySet()) {
			Score s = e.getValue();
			cells.addAll(s.amethyst);
			if (s.score >= min) {
				SusZones.Flag f = flagged.computeIfAbsent(e.getKey(), SusZones.Flag::new);
				f.score = Math.max(f.score, s.score);
				f.hitWeight += s.hitWeight;
				f.hitX += s.hitX;
				f.hitZ += s.hitZ;
				f.hitMinY = Math.min(f.hitMinY, s.hitMinY);
			}
		}
		for (SusZones.Geode geode : SusZones.clusterGeodes(new ArrayList<>(cells))) {
			if (geode.score() < min) {
				continue;
			}
			for (long key : geode.chunks()) {
				SusZones.Flag f = flagged.computeIfAbsent(key, SusZones.Flag::new);
				f.score = Math.max(f.score, geode.score());
			}
			for (SusZones.Cell c : geode.cells()) {
				SusZones.Flag f = flagged.get(c.chunk());
				if (f != null) {
					f.hit(SusZones.Signal.AMETHYST.weight, c.x() + 0.5, c.y(), c.z() + 0.5);
				}
			}
		}
		flags = List.copyOf(flagged.values());
		zones = SusZones.buildZones(flags, merge.get() ? (int) Math.round(mergeRadius.get()) : 0);
		alert(player);
	}
	private void alert(LocalPlayer player) {
		long now = System.currentTimeMillis();
		while (!recentAlerts.isEmpty() && now - recentAlerts.peekFirst() > 60_000) {
			recentAlerts.removeFirst();
		}
		for (SusZones.Zone zone : zones) {
			boolean fresh = true;
			for (long member : zone.members()) {
				if (alerted.contains(member)) {
					fresh = false;
					break;
				}
			}
			alerted.addAll(zone.members());
			if (!fresh || notify.is("Off") || player == null || recentAlerts.size() >= alertsPerMinute.get()) {
				continue;
			}
			recentAlerts.addLast(now);
			int x = (int) Math.round(zone.x());
			int z = (int) Math.round(zone.z());
			int dist = (int) Math.hypot(x - player.getX(), z - player.getZ());
			String message = "Sus zone, " + dist + " m at " + StreamerMode.alert(x, z);
			if (notify.is("Chat")) {
				nz.temple.client.compat.Players.message(player, Component.literal("§8[§bTemple§8] §f" + message));
			} else if (TempleNzClient.notifications() != null) {
				TempleNzClient.notifications().info("Sus Chunk Finder", message);
			}
		}
	}
	public void collect(List<WorldEsp.Box> out) {
		if (!isEnabled() || level == null || level != Minecraft.getInstance().level) {
			return;
		}
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0.016f : Math.min((now - lastFrame) / 1e9f, 0.1f);
		lastFrame = now;
		Set<Long> chunks = new HashSet<>();
		Map<Long, double[]> rects = new HashMap<>();
		if (merge.get()) {
			for (SusZones.Zone zone : zones) {
				if (zone.members().size() == 1) {
					chunks.add(zone.key());
				} else {
					double half = Math.min(48, 16 + (zone.members().size() - 1) * 8) / 2.0;
					rects.put(zone.key(), new double[] {zone.x() - half, zone.z() - half, zone.x() + half, zone.z() + half});
				}
			}
		} else {
			for (SusZones.Flag f : flags) {
				chunks.add(f.key);
			}
		}
		for (Iterator<Map.Entry<Long, Float>> it = chunkFade.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, Float> e = it.next();
			if (!chunks.contains(e.getKey())) {
				float a = e.getValue() - dt / FADE_OUT;
				if (a <= 0) {
					it.remove();
				} else {
					e.setValue(a);
				}
			}
		}
		for (long key : chunks) {
			chunkFade.merge(key, Math.min(1f, dt / FADE_IN), (old, inc) -> Math.min(1f, old + inc));
		}
		for (Iterator<Map.Entry<Long, ZoneFade>> it = zoneFade.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, ZoneFade> e = it.next();
			if (!rects.containsKey(e.getKey())) {
				e.getValue().alpha -= dt / FADE_OUT;
				if (e.getValue().alpha <= 0) {
					it.remove();
				}
			}
		}
		for (Map.Entry<Long, double[]> e : rects.entrySet()) {
			ZoneFade fade = zoneFade.computeIfAbsent(e.getKey(), k -> new ZoneFade());
			double[] r = e.getValue();
			fade.x0 = r[0];
			fade.z0 = r[1];
			fade.x1 = r[2];
			fade.z1 = r[3];
			fade.alpha = Math.min(1f, fade.alpha + dt / FADE_IN);
		}
		double y = renderY.get();
		for (Map.Entry<Long, Float> e : chunkFade.entrySet()) {
			double x0 = ChunkPos.getX(e.getKey()) * 16.0, z0 = ChunkPos.getZ(e.getKey()) * 16.0;
			box(out, x0, z0, x0 + 16, z0 + 16, y, e.getValue());
		}
		for (ZoneFade fade : zoneFade.values()) {
			box(out, fade.x0, fade.z0, fade.x1, fade.z1, y, fade.alpha);
		}
	}
	private void box(List<WorldEsp.Box> out, double x0, double z0, double x1, double z1, double y, float alpha) {
		alpha = Math.max(0f, Math.min(1f, alpha));
		int rgb = color.get() & 0xFFFFFF;
		int fill = Math.round(fillOpacity.getFloat() * alpha);
		int line = outline.get() ? Math.round(outlineOpacity.getFloat() * alpha) : 0;
		if (fill <= 0 && line <= 0) {
			return;
		}
		out.add(new WorldEsp.Box(new AABB(x0, y, z0, x1, y + 0.05, z1), line > 0 ? line << 24 | rgb : 0, fill > 0 ? fill << 24 | rgb : 0,
				line > 0 ? 0.03f : 0));
	}
	public int zoneCount() {
		return zones.size();
	}
}
