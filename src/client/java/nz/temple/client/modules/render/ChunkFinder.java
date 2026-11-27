package nz.temple.client.modules.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3x2fStack;
import nz.temple.client.TempleNzClient;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Projection;
import nz.temple.client.render.UiScale;
import nz.temple.client.render.WorldEsp;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public final class ChunkFinder extends Module {
	private static final int PLACED = 0;
	private static final int BASE = 1;
	private static final int FARM = 2;
	private static final int GEODE = 3;
	private static final String[] KIND_NAMES = {"Placed", "Base", "Farm", "Geode"};
	private static final double BUDDING_CHANCE = 0.083;
	private final BoolSetting placed;
	private final NumberSetting deepslateMin;
	private final NumberSetting rotatedMin;
	private final NumberSetting cobbledMin;
	private final NumberSetting endStoneMin;
	private final BoolSetting ignoreExposed;
	private final BoolSetting base;
	private final NumberSetting baseMin;
	private final BoolSetting detCobble;
	private final BoolSetting detMasonry;
	private final BoolSetting detObsidian;
	private final BoolSetting detPistons;
	private final BoolSetting detStairs;
	private final BoolSetting detShafts;
	private final BoolSetting detRooms;
	private final BoolSetting detVeins;
	private final BoolSetting detStorage;
	private final BoolSetting detItems;
	private final BoolSetting detMobs;
	private final BoolSetting farm;
	private final NumberSetting farmMin;
	private final BoolSetting farmRedstone;
	private final BoolSetting farmBees;
	private final BoolSetting farmFloors;
	private final BoolSetting farmPlants;
	private final BoolSetting farmWorkshops;
	private final BoolSetting geode;
	private final NumberSetting geodeSensitivity;
	private final ModeSetting display;
	private final NumberSetting tintOpacity;
	private final ModeSetting heightMode;
	private final NumberSetting renderY;
	private final NumberSetting thickness;
	private final NumberSetting maxChunks;
	private final BoolSetting beacons;
	private final BoolSetting labels;
	private final BoolSetting highlightBlocks;
	private final NumberSetting maxBlocks;
	private final ColorSetting placedColor;
	private final ColorSetting baseColor;
	private final ColorSetting farmColor;
	private final ColorSetting geodeColor;
	private final NumberSetting threads;
	private final NumberSetting perTick;
	private final BoolSetting chatAlerts;
	private final BoolSetting toastAlerts;
	private final BoolSetting sound;
	private final NumberSetting alertsPerMinute;
	private final Map<Long, Result> results = new HashMap<>();
	private final Set<Long> pending = new LinkedHashSet<>();
	private final Map<Long, Long> dirty = new HashMap<>();
	private final Set<Long> alerted = new HashSet<>();
	private final Map<Long, int[]> entityTally = new HashMap<>();
	private final ConcurrentLinkedQueue<Result> finished = new ConcurrentLinkedQueue<>();
	private final ArrayDeque<Long> recentAlerts = new ArrayDeque<>();
	private AtomicInteger inFlight = new AtomicInteger();
	private volatile long lastFinished;
	private static final long STALL_MS = 30_000;
	private final List<WorldEsp.Box> boxes = new ArrayList<>();
	private final List<Result> shown = new ArrayList<>();
	private ExecutorService pool;
	private ClientLevel level;
	private volatile int generation;
	private int ticks;
	private boolean seedLoaded;
	private record Snapshot(int generation, int cx, int cz, int minY, LevelChunkSection[] sections, boolean end,
			int storage, int deepStorage, int spawners, int bells, int hives, int nests, int bees, int workshops,
			int[] entities) {
	}
	private record Mark(int x, int y, int z, int kind) {
	}

	private record Result(int generation, long key, int cx, int cz, int flags, int baseScore, int farmChance,
			String summary, List<Mark> marks) {
		boolean has(int kind) {
			return (flags & (1 << kind)) != 0;
		}
	}

	public ChunkFinder() {
		super("Chunk Finder", Category.RENDER, "Scans loaded chunks in the background for signs of players: placed blocks, hidden bases, "
				+ "farms and mined geodes. Ignores structures that would give false results.");
		placed = add(new BoolSetting("Placed blocks", true)
				.describe("Flags blocks that do not generate where they are found: deepslate above Y 8, sideways deepslate, cobbled "
						+ "deepslate above Y 0, end stone outside the End."));
		deepslateMin = add(new NumberSetting("Deepslate min", 1, 1, 16, 1, "").describe("Deepslate blocks above Y 8 needed to flag a chunk.")
				.visibleWhen(placed::get));
		rotatedMin = add(new NumberSetting("Rotated min", 2, 1, 16, 1, "").describe("Sideways deepslate blocks needed to flag a chunk. "
				+ "Natural deepslate is always upright.")
				.visibleWhen(placed::get));
		cobbledMin = add(new NumberSetting("Cobbled min", 4, 1, 32, 1, "").describe("Cobbled deepslate blocks above Y 0 needed to flag a "
				+ "chunk.")
				.visibleWhen(placed::get));
		endStoneMin = add(new NumberSetting("End stone min", 2, 1, 16, 1, "").describe("End stone blocks outside the End needed to flag a "
				+ "chunk.")
				.visibleWhen(placed::get));
		ignoreExposed = add(new BoolSetting("Ignore exposed", true)
				.describe("Ignores placed blocks touching air or fluid, so surface builds and cave walls are not counted.")
				.visibleWhen(placed::get));
		group();
		base = add(new BoolSetting("Base signals", true)
				.describe("Scores underground signs of a base. Each detector below adds points; the chunk is flagged at the minimum score."));
		baseMin = add(new NumberSetting("Base min score", 8, 2, 40, 1, " pts").describe("Combined score needed to flag a base.")
				.visibleWhen(base::get));
		detCobble = add(new BoolSetting("Cobble lines", true).describe("Rows of cobblestone below y 0 (dungeons are excluded).").visibleWhen(base::get));
		detMasonry = add(new BoolSetting("Deepslate masonry", true).describe("Polished, brick, tile or chiselled deepslate below y 0 (ancient cities excluded).").visibleWhen(base::get));
		detObsidian = add(new BoolSetting("Obsidian", true).describe("Obsidian below y 20.").visibleWhen(base::get));
		detPistons = add(new BoolSetting("Pistons", true).describe("Pistons below Y 40.").visibleWhen(base::get));
		detStairs = add(new BoolSetting("Stairs", true).describe("Stair blocks below y 0.").visibleWhen(base::get));
		detShafts = add(new BoolSetting("Mined shafts", true).describe("1x1 vertical tunnels at least 8 deep below y 0, walled on all sides.").visibleWhen(base::get));
		detRooms = add(new BoolSetting("Flat rooms", true).describe("Several 2 to 4 block high spaces sharing a flat floor below Y 0, "
				+ "typical of dug-out rooms.").visibleWhen(base::get));
		detVeins = add(new BoolSetting("Stacked ore", false).describe("Four ores stacked in one column. Weak on its own and prone to false "
				+ "positives.").visibleWhen(base::get));
		detStorage = add(new BoolSetting("Storage", true).describe("Chests, barrels, shulker boxes and hoppers, weighted more below Y 0.").visibleWhen(base::get));
		detItems = add(new BoolSetting("Dropped items", true).describe("Item entities lying below y 0.").visibleWhen(base::get));
		detMobs = add(new BoolSetting("Mob clusters", true).describe("20 or more mobs in one chunk (spawner farms), and polar bears "
				+ "underground.").visibleWhen(base::get));
		group();
		farm = add(new BoolSetting("Farm signals", true)
				.describe("Estimates the chance of a base from redstone, bee farms, placed floors, underground plants and workshops."));
		farmMin = add(new NumberSetting("Farm min chance", 50, 25, 99, 1, "%").describe("Base chance needed to flag.")
				.visibleWhen(farm::get));
		farmRedstone = add(new BoolSetting("Repeaters", true).describe("Repeaters. Powered repeaters count most.").visibleWhen(farm::get));
		farmBees = add(new BoolSetting("Bee farms", true).describe("Crafted beehives with bees. Natural bee nests count much less.").visibleWhen(farm::get));
		farmFloors = add(new BoolSetting("Placed floors", true).describe("Cobbled deepslate spread between Y 0 and 20.").visibleWhen(farm::get));
		farmPlants = add(new BoolSetting("Underground plants", true).describe("Flowers below Y 40 and vines below Y 50, where they do not "
				+ "grow naturally.").visibleWhen(farm::get));
		farmWorkshops = add(new BoolSetting("Workshops", true).describe("Furnaces, anvils, enchanting tables and crafting tables (villages "
				+ "excluded).").visibleWhen(farm::get));
		group();
		geode = add(new BoolSetting("Mined geodes", true)
				.describe("Geodes with lots of amethyst but no budding amethyst left. Natural geodes almost always have some."));
		geodeSensitivity = add(new NumberSetting("Geode sensitivity", 5, 1, 10, 1, "")
				.describe("Higher values flag smaller geodes, with more false positives. At 5, about 1 in 15 natural geodes is flagged.")
				.visibleWhen(geode::get));
		group();
		display = add(new ModeSetting("Display", List.of("Color only", "Detailed"), "Color only")
				.describe("Color only tints each flagged chunk. Detailed adds an outlined slab, labels with the evidence, and the blocks "
						+ "that triggered it."));
		tintOpacity = add(new NumberSetting("Tint opacity", 30, 5, 90, 1, "%").describe("Tint strength for flagged chunks.")
				.visibleWhen(() -> display.is("Color only")));
		heightMode = add(new ModeSetting("Height", List.of("Feet", "Fixed", "Column"), "Feet")
				.describe("Feet draws a flat marker at your height. Fixed draws it at the height below. Column outlines the full chunk "
						+ "column."));
		renderY = add(new NumberSetting("Render Y", 64, -64, 320, 1, "").describe("Height of the flat marker in Fixed mode.")
				.visibleWhen(() -> heightMode.is("Fixed")));
		thickness = add(new NumberSetting("Thickness", 0.15, 0.02, 2, 0.01, " b").describe("Thickness of the flat marker.")
				.visibleWhen(() -> !heightMode.is("Column")));
		maxChunks = add(new NumberSetting("Max chunks", 96, 8, 400, 8, "").describe("Maximum flagged chunks drawn, nearest first. The rest "
				+ "are still remembered."));
		beacons = add(new BoolSetting("Beacons", false)
				.describe("A beam from bedrock to the sky in each flagged chunk. Off by default since the flat marker is clearer up close."));
		labels = add(new BoolSetting("Labels", true).describe("Text above each chunk showing its evidence and distance.")
				.visibleWhen(() -> display.is("Detailed")));
		highlightBlocks = add(new BoolSetting("Highlight blocks", true).describe("Outlines the individual blocks that triggered a flag."));
		maxBlocks = add(new NumberSetting("Max blocks", 200, 20, 1000, 20, "").describe("Maximum number of outlined blocks.")
				.visibleWhen(highlightBlocks::get));
		placedColor = add(new ColorSetting("Placed colour", 0xFFFFD60A).describe("Colour for chunks with placed blocks (yellow by default)."));
		baseColor = add(new ColorSetting("Base colour", 0xFFFF3B30).describe("Colour for chunks that look like a base (red by default, so bases stand out)."));
		farmColor = add(new ColorSetting("Farm colour", 0xFF34C759).describe("Colour for farm chunks (green by default)."));
		geodeColor = add(new ColorSetting("Geode colour", 0xFFAF52DE).describe("Colour for mined-geode chunks (purple by default)."));
		group();
		threads = add(new NumberSetting("Threads", 2, 1, 4, 1, "").describe("Worker threads used for scanning. Takes effect the next time "
				+ "the module is turned on."));
		perTick = add(new NumberSetting("Chunks per tick", 3, 1, 12, 1, "").describe("Chunks queued for scanning each tick. Higher scans "
				+ "faster when you arrive somewhere."));
		chatAlerts = add(new BoolSetting("Chat alerts", true).describe("Sends each new find to chat with coordinates."));
		toastAlerts = add(new BoolSetting("Toast alerts", true).describe("Shows a notification for each new find."));
		sound = add(new BoolSetting("Sound", true).describe("Plays a sound for each new find."));
		alertsPerMinute = add(new NumberSetting("Alerts per minute", 6, 1, 30, 1, "").describe("Limits how many alerts are sent. Finds are "
				+ "still marked."));
		on(TickEvent.class, e -> tick());
	}
	@Override
	protected void onEnable() {
		seedLoaded = false;
	}
	@Override
	protected void onDisable() {
		stopPool();
		generation++;
		clear();
	}
	private void stopPool() {
		if (pool != null) {
			pool.shutdownNow();
			pool = null;
		}
		inFlight = new AtomicInteger();
	}

	private void clear() {
		results.clear();
		pending.clear();
		dirty.clear();
		alerted.clear();
		entityTally.clear();
		finished.clear();
		boxes.clear();
		shown.clear();
	}
	public void chunkLoaded(LevelChunk chunk) {
		if (isEnabled()) {
			pending.add(ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z()));
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
			generation++;
			clear();
			seedLoaded = false;
		}
		if (pool != null && inFlight.get() > 0 && System.currentTimeMillis() - lastFinished > STALL_MS) {
			TempleNzClient.LOGGER.warn("Chunk Finder workers stopped finishing scans ({} in flight, {} pending); restarting them",
					inFlight.get(), pending.size());
			stopPool();
		}
		if (pool == null) {
			lastFinished = System.currentTimeMillis();
			int n = (int) Math.round(threads.get());
			pool = Executors.newFixedThreadPool(n, r -> {
				Thread t = new Thread(r, "Temple ChunkFinder");
				t.setDaemon(true);
				t.setPriority(Thread.NORM_PRIORITY - 1);
				return t;
			});
		}
		int view = mc.options.getEffectiveRenderDistance();
		ChunkPos center = player.chunkPosition();
		if (!seedLoaded) {
			for (int dx = -view; dx <= view; dx++) {
				for (int dz = -view; dz <= view; dz++) {
					if (mc.level.hasChunk(center.x() + dx, center.z() + dz)) {
						pending.add(ChunkPos.pack(center.x() + dx, center.z() + dz));
					}
				}
			}
			seedLoaded = true;
		}
		if (++ticks % 20 == 0) {
			tallyEntities(mc.level);
		}
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Long, Long>> it = dirty.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, Long> e = it.next();
			if (now - e.getValue() > 1200) {
				pending.add(e.getKey());
				it.remove();
			}
		}
		boolean changed = drainFinished();
		submitNearest(mc.level, center);
		if (ticks % 100 == 0) {
			int keep = view + 4;
			changed |= results.keySet().removeIf(k -> far(k, center, keep));
			pending.removeIf(k -> far(k, center, keep));
			alerted.removeIf(k -> far(k, center, keep + 8));
		}
		if (changed || ticks % 10 == 0) {
			rebuild(player);
		}
	}
	private static boolean far(long key, ChunkPos center, int radius) {
		return Math.abs(ChunkPos.getX(key) - center.x()) > radius || Math.abs(ChunkPos.getZ(key) - center.z()) > radius;
	}
	private void tallyEntities(ClientLevel lvl) {
		entityTally.clear();
		for (Entity entity : lvl.entitiesForRendering()) {
			int[] t = entityTally.computeIfAbsent(ChunkPos.pack(entity.blockPosition()), k -> new int[3]);
			if (entity instanceof ItemEntity && entity.getY() < 0) {
				t[0]++;
			} else if (entity instanceof PolarBear && entity.getY() < 50) {
				t[1]++;
			} else if (entity instanceof LivingEntity && !(entity instanceof Player)) {
				t[2]++;
			}
		}
	}
	private void submitNearest(ClientLevel lvl, ChunkPos center) {
		int budget = (int) Math.round(perTick.get());
		int maxInFlight = (int) Math.round(threads.get()) * 6;
		while (budget-- > 0 && !pending.isEmpty() && inFlight.get() < maxInFlight) {
			long best = 0;
			long bestDist = Long.MAX_VALUE;
			int looked = 0;
			for (long key : pending) {
				long dx = ChunkPos.getX(key) - center.x();
				long dz = ChunkPos.getZ(key) - center.z();
				long d = dx * dx + dz * dz;
				if (d < bestDist) {
					bestDist = d;
					best = key;
				}
				if (++looked > 512) {
					break;
				}
			}
			pending.remove(best);
			int cx = ChunkPos.getX(best);
			int cz = ChunkPos.getZ(best);
			if (!lvl.hasChunk(cx, cz)) {
				continue;
			}
			Snapshot snapshot = snapshot(lvl, lvl.getChunk(cx, cz));
			AtomicInteger counter = inFlight;
			counter.incrementAndGet();
			pool.execute(() -> {
				try {
					if (snapshot.generation() == generation) {
						finished.add(analyze(snapshot));
					}
				} catch (RuntimeException e) {
					TempleNzClient.LOGGER.warn("Chunk Finder could not analyse chunk {}, {}", snapshot.cx(), snapshot.cz(), e);
				} finally {
					lastFinished = System.currentTimeMillis();
					counter.decrementAndGet();
				}
			});
		}
	}
	private Snapshot snapshot(ClientLevel lvl, LevelChunk chunk) {
		LevelChunkSection[] source = chunk.getSections();
		LevelChunkSection[] copy = new LevelChunkSection[source.length];
		for (int i = 0; i < source.length; i++) {
			if (source[i] != null && !source[i].hasOnlyAir()) {
				copy[i] = source[i].copy();
			}
		}
		int storage = 0, deepStorage = 0, spawners = 0, bells = 0, hives = 0, nests = 0, bees = 0, workshops = 0;
		for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
			Block block = chunk.getBlockState(e.getKey()).getBlock();
			String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
			if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL || id.endsWith("shulker_box") || block == Blocks.HOPPER) {
				storage++;
				if (e.getKey().getY() < 0) {
					deepStorage++;
				}
			} else if (block == Blocks.SPAWNER || block == Blocks.TRIAL_SPAWNER) {
				spawners++;
			} else if (block == Blocks.BELL) {
				bells++;
			} else if (block == Blocks.BEEHIVE || block == Blocks.BEE_NEST) {
				if (block == Blocks.BEEHIVE) {
					hives++;
				} else {
					nests++;
				}
				if (e.getValue() instanceof BeehiveBlockEntity hive) {
					bees += hive.getOccupantCount();
				}
			} else if (block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER
					|| block == Blocks.ENCHANTING_TABLE || block == Blocks.BREWING_STAND) {
				workshops++;
			}
		}
		int[] entities = entityTally.getOrDefault(ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z()), new int[3]).clone();
		return new Snapshot(generation, chunk.getPos().x(), chunk.getPos().z(), chunk.getMinY(), copy, lvl.dimension() == Level.END,
				storage, deepStorage, spawners, bells, hives, nests, bees, workshops, entities);
	}
	private static BlockState state(Snapshot s, int lx, int y, int lz) {
		int index = (y - s.minY()) >> 4;
		if (index < 0 || index >= s.sections().length) {
			return Blocks.AIR.defaultBlockState();
		}
		LevelChunkSection section = s.sections()[index];
		return section == null ? Blocks.AIR.defaultBlockState() : section.getBlockState(lx, (y - s.minY()) & 15, lz);
	}
	private static boolean exposed(Snapshot s, int lx, int y, int lz) {
		for (Direction d : Direction.values()) {
			int nx = lx + d.getStepX();
			int nz = lz + d.getStepZ();
			if (nx < 0 || nx > 15 || nz < 0 || nz > 15) {
				continue;
			}
			BlockState n = state(s, nx, y + d.getStepY(), nz);
			if (n.isAir() || !n.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}
	private static boolean solid(Snapshot s, int lx, int y, int lz) {
		if (lx < 0 || lx > 15 || lz < 0 || lz > 15) {
			return true;
		}
		BlockState st = state(s, lx, y, lz);
		return !st.isAir() && st.getFluidState().isEmpty();
	}

	private static final Predicate<BlockState> SURFACE_INTEREST = st -> st.is(Blocks.DEEPSLATE) || st.is(Blocks.COBBLED_DEEPSLATE)
			|| st.is(Blocks.END_STONE) || st.is(Blocks.REPEATER) || st.is(Blocks.PISTON) || st.is(Blocks.STICKY_PISTON)
			|| st.is(Blocks.OBSIDIAN) || st.is(Blocks.CRAFTING_TABLE) || st.is(Blocks.ANVIL) || st.is(Blocks.VINE)
			|| st.is(Blocks.TUFF_BRICKS) || st.is(BlockTags.FLOWERS);
	private Result analyze(Snapshot s) {
		boolean doPlaced = placed.get();
		boolean doBase = base.get();
		boolean doFarm = farm.get();
		boolean doGeode = geode.get();
		int deepslate = 0, rotated = 0, cobbled = 0, endStone = 0, trial = 0, ancient = 0;
		int cobbleRuns = 0, masonry = 0, obsidian = 0, pistons = 0, stairs = 0;
		int repeaters = 0, powered = 0, floor = 0, flowers = 0, vines = 0, tables = 0;
		int amethyst = 0, budding = 0, buds = 0, calcite = 0, basalt = 0;
		long sumX = 0, sumZ = 0;
		List<Mark> marks = new ArrayList<>();
		int ox = s.cx() * 16;
		int oz = s.cz() * 16;
		LevelChunkSection[] sections = s.sections();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null) {
				continue;
			}
			int baseY = s.minY() + i * 16;
			if (baseY >= 64 && !section.maybeHas(SURFACE_INTEREST)) {
				continue;
			}
			for (int ly = 0; ly < 16; ly++) {
				int y = baseY + ly;
				for (int lx = 0; lx < 16; lx++) {
					for (int lz = 0; lz < 16; lz++) {
						BlockState st = section.getBlockState(lx, ly, lz);
						if (st.isAir()) {
							continue;
						}
						Block b = st.getBlock();
						String id = null;
						if (b == Blocks.DEEPSLATE && st.hasProperty(BlockStateProperties.AXIS)) {
							boolean upright = st.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
							if (doPlaced && (!upright || y > 8) && !(ignoreExposed.get() && exposed(s, lx, y, lz))) {
								if (upright) {
									deepslate++;
								} else {
									rotated++;
								}
								marks.add(new Mark(ox + lx, y, oz + lz, PLACED));
							}
						} else if (b == Blocks.COBBLED_DEEPSLATE) {
							if (y >= 0 && y <= 20) {
								floor++;
							}
							if (doPlaced && y >= 0 && !(ignoreExposed.get() && exposed(s, lx, y, lz))) {
								cobbled++;
								marks.add(new Mark(ox + lx, y, oz + lz, PLACED));
							}
							if (y < 0 && lx < 15 && state(s, lx + 1, y, lz).is(b)) {
								cobbleRuns++;
							}
						} else if (b == Blocks.END_STONE) {
							if (doPlaced && !s.end() && !(ignoreExposed.get() && exposed(s, lx, y, lz))) {
								endStone++;
								marks.add(new Mark(ox + lx, y, oz + lz, PLACED));
							}
						} else if (b == Blocks.REINFORCED_DEEPSLATE || b == Blocks.SCULK_SHRIEKER) {
							ancient++;
						} else if (b == Blocks.TUFF_BRICKS || b == Blocks.CHISELED_TUFF || b == Blocks.POLISHED_TUFF
								|| (id = BuiltInRegistries.BLOCK.getKey(b).getPath()).startsWith("waxed_") && id.contains("copper")) {
							trial++;
						} else if (b == Blocks.COBBLESTONE || b == Blocks.MOSSY_COBBLESTONE) {
							if (y < 0 && lx < 15 && state(s, lx + 1, y, lz).is(b)) {
								cobbleRuns++;
							}
						} else if (b == Blocks.POLISHED_DEEPSLATE || b == Blocks.DEEPSLATE_BRICKS || b == Blocks.DEEPSLATE_TILES
								|| b == Blocks.CHISELED_DEEPSLATE) {
							if (y < 0) {
								masonry++;
							}
						} else if (b == Blocks.OBSIDIAN) {
							if (y < 20) {
								obsidian++;
							}
						} else if (b == Blocks.PISTON || b == Blocks.STICKY_PISTON || b == Blocks.PISTON_HEAD || b == Blocks.MOVING_PISTON) {
							if (y < 40) {
								pistons++;
							}
						} else if (b == Blocks.REPEATER) {
							repeaters++;
							if (st.getValue(BlockStateProperties.POWERED)) {
								powered++;
							}
						} else if (b == Blocks.VINE) {
							if (y < 50) {
								vines++;
							}
						} else if (b == Blocks.CRAFTING_TABLE || b == Blocks.ANVIL || b == Blocks.CHIPPED_ANVIL || b == Blocks.DAMAGED_ANVIL) {
							tables++;
						} else if (b == Blocks.AMETHYST_BLOCK) {
							amethyst++;
							sumX += lx;
							sumZ += lz;
						} else if (b == Blocks.BUDDING_AMETHYST) {
							budding++;
						} else if (b == Blocks.AMETHYST_CLUSTER || b == Blocks.LARGE_AMETHYST_BUD || b == Blocks.MEDIUM_AMETHYST_BUD
								|| b == Blocks.SMALL_AMETHYST_BUD) {
							buds++;
						} else if (b == Blocks.CALCITE) {
							calcite++;
						} else if (b == Blocks.SMOOTH_BASALT) {
							basalt++;
						} else if (y < 0 && st.is(BlockTags.STAIRS)) {
							stairs++;
						} else if (y < 40 && st.is(BlockTags.FLOWERS)
								&& !BuiltInRegistries.BLOCK.getKey(b).getPath().contains("azalea")) {
							flowers++;
						}
					}
				}
			}
		}
		boolean trialChamber = trial >= 40;
		boolean ancientCity = ancient > 0;
		boolean dungeon = s.spawners() > 0;
		boolean village = s.bells() > 0;
		int flags = 0;
		List<String> parts = new ArrayList<>();

		if (doPlaced && !trialChamber) {
			List<String> why = new ArrayList<>();
			if (deepslate >= deepslateMin.get()) why.add(deepslate + " deepslate");
			if (rotated >= rotatedMin.get()) why.add(rotated + " sideways deepslate");
			if (!ancientCity && cobbled >= cobbledMin.get()) why.add(cobbled + " cobbled deepslate");
			if (endStone >= endStoneMin.get()) why.add(endStone + " end stone");
			if (!why.isEmpty()) {
				flags |= 1 << PLACED;
				parts.add(String.join(", ", why));
			}
		}
		if ((flags & (1 << PLACED)) == 0) {
			marks.removeIf(m -> m.kind() == PLACED);
		}
		int baseScore = 0;
		if (doBase && !trialChamber) {
			List<String> tags = new ArrayList<>();
			int shafts = 0, rooms = 0, veins = 0;
			if (detShafts.get() || detRooms.get() || detVeins.get()) {
				int[] floors = new int[Math.max(1, -s.minY())];
				for (int lx = 0; lx < 16; lx++) {
					for (int lz = 0; lz < 16; lz++) {
						int run = 0, walled = 0, ore = 0;
						for (int y = s.minY(); y <= 0; y++) {
							BlockState st = y < 0 ? state(s, lx, y, lz) : Blocks.STONE.defaultBlockState();
							if (st.isAir()) {
								run++;
								if (solid(s, lx + 1, y, lz) && solid(s, lx - 1, y, lz) && solid(s, lx, y, lz + 1) && solid(s, lx, y, lz - 1)) {
									walled++;
								}
								ore = 0;
								continue;
							}
							if (run >= 8 && walled >= run * 0.8) {
								shafts++;
							}
							if (run >= 2 && run <= 4 && y - run - 1 >= s.minY() && solid(s, lx, y - run - 1, lz)) {
								int floorY = y - run - 1 - s.minY();
								if (floorY < floors.length) {
									floors[floorY]++;
								}
							}
							run = 0;
							walled = 0;
							String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
							ore = id.endsWith("_ore") || st.is(Blocks.ANCIENT_DEBRIS) ? ore + 1 : 0;
							if (ore == 4) {
								veins++;
							}
						}
					}
				}
				for (int f : floors) {
					rooms = Math.max(rooms, f);
				}
			}
			if (detCobble.get() && !dungeon && cobbleRuns >= 4) {
				baseScore += tag(tags, "cobble lines", cobbleRuns, Math.min(cobbleRuns / 2, 6));
			}
			if (detMasonry.get() && !ancientCity && masonry >= 4) {
				baseScore += tag(tags, "deepslate masonry", masonry, Math.min(masonry / 2, 6));
			}
			if (detObsidian.get() && obsidian >= 4) {
				baseScore += tag(tags, "obsidian", obsidian, Math.min(obsidian / 2, 6));
			}
			if (detPistons.get() && pistons >= 2) {
				baseScore += tag(tags, "pistons", pistons, Math.min(pistons * 2, 8));
			}
			if (detStairs.get() && !ancientCity && stairs >= 4) {
				baseScore += tag(tags, "stairs", stairs, Math.min(stairs / 2, 6));
			}
			if (detShafts.get() && shafts > 0) {
				baseScore += tag(tags, "mined shafts", shafts, Math.min(shafts * 3, 9));
			}
			if (detRooms.get() && rooms >= 24) {
				baseScore += tag(tags, "flat room cells", rooms, Math.min(rooms / 8, 8));
			}
			if (detVeins.get() && veins >= 2) {
				baseScore += tag(tags, "stacked ore", veins, Math.min(veins, 4));
			}
			if (detStorage.get() && s.storage() >= 3) {
				int deep = dungeon ? 0 : s.deepStorage();
				baseScore += tag(tags, "storage", s.storage(), Math.min(s.storage() + deep * 2, 14));
			}
			if (detItems.get() && s.entities()[0] >= 2) {
				baseScore += tag(tags, "deep items", s.entities()[0], Math.min(s.entities()[0] * 2, 10));
			}
			if (detMobs.get() && s.entities()[2] >= 20) {
				baseScore += tag(tags, "mobs", s.entities()[2], Math.min(s.entities()[2] / 5, 10));
			}
			if (detMobs.get() && s.entities()[1] > 0) {
				baseScore += tag(tags, "underground polar bears", s.entities()[1], 4);
			}
			if (baseScore >= baseMin.get()) {
				flags |= 1 << BASE;
				parts.add("base " + baseScore + " (" + String.join(", ", tags) + ")");
			}
		}
		int farmChance = 0;
		if (doFarm && !trialChamber) {
			int score = 0;
			if (farmRedstone.get()) score += Math.min(90, repeaters * 12 + powered * 20);
			if (farmBees.get()) score += Math.min(80, s.hives() * 20 + s.bees() * 6 + (s.nests() > 1 ? s.nests() * 4 : 0));
			if (farmFloors.get() && !ancientCity) score += Math.min(60, floor / 2);
			if (farmPlants.get()) score += Math.min(60, flowers * 10) + Math.min(40, vines / 3);
			if (farmWorkshops.get() && !village) score += Math.min(90, (s.workshops() + tables) * 15);
			if (score > 0) {
				farmChance = Math.max(20, Math.min(99, 20 + score / 2));
				if (farmChance >= farmMin.get()) {
					flags |= 1 << FARM;
					parts.add("farm " + farmChance + "%");
				}
			}
		}
		if (doGeode && budding == 0 && buds <= 2 && amethyst > 0) {
			double sens = geodeSensitivity.get();
			int minAmethyst = (int) Math.round(44 - 3.4 * (sens - 1));
			double cx = (double) sumX / amethyst;
			double cz = (double) sumZ / amethyst;
			if (amethyst >= minAmethyst && calcite >= 16 - (sens - 1) && basalt > 0 && cx >= 3 && cx <= 12 && cz >= 3 && cz <= 12) {
				flags |= 1 << GEODE;
				double odds = Math.pow(1 - BUDDING_CHANCE, amethyst) * 100;
				parts.add(String.format(Locale.ROOT, "mined geode (%d amethyst, natural odds %.1f%%)", amethyst, odds));
			}
		}
		return new Result(s.generation(), ChunkPos.pack(s.cx(), s.cz()), s.cx(), s.cz(), flags, baseScore, farmChance,
				String.join(" · ", parts), marks.size() > 64 ? List.copyOf(marks.subList(0, 64)) : List.copyOf(marks));
	}
	private static int tag(List<String> tags, String name, int count, int points) {
		tags.add(count + " " + name);
		return points;
	}
	private boolean drainFinished() {
		boolean changed = false;
		Result r;
		while ((r = finished.poll()) != null) {
			if (r.generation() != generation) {
				continue;
			}
			if (r.flags() == 0) {
				changed |= results.remove(r.key()) != null;
				continue;
			}
			Result previous = results.put(r.key(), r);
			changed = true;
			if (previous == null || (previous.flags() | r.flags()) != previous.flags()) {
				alert(r);
			}
		}
		return changed;
	}
	private void alert(Result r) {
		if (!alerted.add(r.key()) && r.flags() == 0) {
			return;
		}
		long now = System.currentTimeMillis();
		while (!recentAlerts.isEmpty() && now - recentAlerts.peekFirst() > 60_000) {
			recentAlerts.removeFirst();
		}
		if (recentAlerts.size() >= alertsPerMinute.get()) {
			return;
		}
		recentAlerts.addLast(now);
		Minecraft mc = Minecraft.getInstance();
		String kinds = kinds(r);
		int x = r.cx() * 16 + 8;
		int z = r.cz() * 16 + 8;
		if (chatAlerts.get() && mc.player != null) {
			nz.temple.client.compat.Players.message(mc.player, Component.literal(String.format(Locale.ROOT,
					"§8[§bTemple§8] §f%s chunk §7at §f%s§7: %s", kinds, nz.temple.client.modules.client.StreamerMode.alert(x, z), r.summary())));
		}
		if (toastAlerts.get() && TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().info(kinds + " chunk", nz.temple.client.modules.client.StreamerMode.alert(x, z));
		}
		if (sound.get()) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5f));
		}
	}
	private static String kinds(Result r) {
		List<String> names = new ArrayList<>();
		for (int k = 0; k < KIND_NAMES.length; k++) {
			if (r.has(k)) {
				names.add(KIND_NAMES[k]);
			}
		}
		return String.join(" + ", names);
	}
	private int colorFor(Result r) {
		if (r.has(BASE)) return baseColor.get();
		if (r.has(GEODE)) return geodeColor.get();
		if (r.has(PLACED)) return placedColor.get();
		return farmColor.get();
	}

	private void rebuild(LocalPlayer player) {
		boxes.clear();
		shown.clear();
		ChunkPos center = player.chunkPosition();
		List<Result> sorted = new ArrayList<>(results.values());
		sorted.sort((a, b) -> Long.compare(dist(a, center), dist(b, center)));
		int limit = (int) Math.round(maxChunks.get());
		int blockBudget = highlightBlocks.get() ? (int) Math.round(maxBlocks.get()) : 0;
		double minY = player.level().getMinY();
		double maxY = player.level().getMaxY();
		for (Result r : sorted) {
			if (shown.size() >= limit) {
				break;
			}
			shown.add(r);
			int rgb = colorFor(r) & 0xFFFFFF;
			double x0 = r.cx() * 16, z0 = r.cz() * 16;
			if (display.is("Color only")) {
				int alpha = Math.round(tintOpacity.getFloat() / 100f * 255) << 24;
				double y = heightMode.is("Fixed") ? renderY.get() : Math.floor(player.getY());
				if (heightMode.is("Column")) {
					boxes.add(new WorldEsp.Box(new AABB(x0, minY, z0, x0 + 16, maxY, z0 + 16), 0, alpha | rgb, 0));
				} else {
					boxes.add(new WorldEsp.Box(new AABB(x0, y, z0, x0 + 16, y + 0.02, z0 + 16), 0, alpha | rgb, 0));
				}
				continue;
			}
			if (heightMode.is("Column")) {
				boxes.add(new WorldEsp.Box(new AABB(x0, minY, z0, x0 + 16, maxY, z0 + 16), 0xB0000000 | rgb, 0, 0.03f));
			} else {
				double y = heightMode.is("Feet") ? Math.floor(player.getY()) : renderY.get();
				boxes.add(new WorldEsp.Box(new AABB(x0, y, z0, x0 + 16, y + thickness.get(), z0 + 16), 0xE0000000 | rgb, 0x30000000 | rgb, 0.03f));
			}
			for (Mark m : r.marks()) {
				if (blockBudget-- <= 0) {
					break;
				}
				boxes.add(new WorldEsp.Box(new AABB(m.x(), m.y(), m.z(), m.x() + 1, m.y() + 1, m.z() + 1).deflate(0.02),
						0xF0000000 | rgb, 0x28000000 | rgb, 0.012f));
			}
		}
	}
	private static long dist(Result r, ChunkPos center) {
		long dx = r.cx() - center.x();
		long dz = r.cz() - center.z();
		return dx * dx + dz * dz;
	}
	@Override
	public String info() {
		return results.isEmpty() ? null : Integer.toString(results.size());
	}
	public void collect(List<WorldEsp.Box> out) {
		if (isEnabled() && level == Minecraft.getInstance().level) {
			out.addAll(boxes);
		}
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !beacons.get() || display.is("Color only") || mc.player == null || level != mc.level) {
			return;
		}
		fx.throughWalls(true);
		double minY = level.getMinY();
		double maxY = level.getMaxY();
		double pulse = 0.75 + 0.25 * Math.sin(System.nanoTime() / 1e9 * 2.2);
		for (Result r : shown) {
			double cx = r.cx() * 16 + 8, cz = r.cz() * 16 + 8;
			int c = Fx.withAlpha(colorFor(r), (float) (150 * pulse));
			fx.softBeam(cx, minY, cz, cx, maxY, cz, 1.4, c, c);
			fx.softBeam(cx, minY, cz, cx, maxY, cz, 0.35, Fx.withAlpha(0xFFFFFFFF, (float) (90 * pulse)), Fx.withAlpha(0xFFFFFFFF, (float) (90 * pulse)));
		}
	}
	public void render(GuiGraphicsExtractor g, DeltaTracker delta, Projection projection) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !labels.get() || display.is("Color only") || mc.player == null || level != mc.level || !projection.ready()) {
			return;
		}
		double y = heightMode.is("Fixed") ? renderY.get() + 1 : mc.player.getY() + 1.5;
		float uiScale = UiScale.factor();
		var font = mc.font;
		for (Result r : shown) {
			double cx = r.cx() * 16 + 8, cz = r.cz() * 16 + 8;
			float[] s = projection.toScreen(cx, y, cz, g.guiWidth(), g.guiHeight());
			if (s == null || s[0] < -60 || s[1] < -20 || s[0] > g.guiWidth() + 60 || s[1] > g.guiHeight() + 20) {
				continue;
			}
			double d = Math.sqrt(mc.player.distanceToSqr(cx, mc.player.getY(), cz));
			String title = kinds(r) + String.format(Locale.ROOT, "  %.0fm", d);
			Component text = Fonts.text(title, Fonts.SEMIBOLD);
			float scale = 0.55f * uiScale;
			float w = font.width(text) * scale;
			int color = colorFor(r);
			Draw2D.rect(g, s[0] - w / 2 - 5 * uiScale, s[1] - 3 * uiScale, s[0] + w / 2 + 5 * uiScale, s[1] + 8 * uiScale, 0xC00B0D12);
			Draw2D.rect(g, s[0] - w / 2 - 5 * uiScale, s[1] - 3 * uiScale, s[0] - w / 2 - 4 * uiScale, s[1] + 8 * uiScale, color);
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(s[0] - w / 2, s[1] - 0.5f * uiScale);
			pose.scale(scale, scale);
			g.text(font, text, 0, 0, 0xFFFFFFFF, false);
			pose.popMatrix();
		}
	}
}
