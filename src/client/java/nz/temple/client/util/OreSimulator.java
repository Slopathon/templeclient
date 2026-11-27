package nz.temple.client.util;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
//? if <26.3 {
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
//?}
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Predicts ore positions by running the game's own ore placement for a seed, against a copy of the chunks the client
 * has loaded. Feature order, random seeding and placement rules all come from vanilla, so for an unmodified
 * generator the prediction matches what the server generated, which reveals ores an anti-xray server hides.
 */
public final class OreSimulator {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/oresim");

	/** A predicted ore block. */
	public record Ore(BlockPos pos, String block) {
	}

	/** One ore feature as vanilla places it: which feature, its index within its step, and the step. */
	private record OreFeatureRef(PlacedFeature feature, int index, int step) {
	}

	private record Setup(ChunkGenerator generator, List<OreFeatureRef> ores, HolderLookup.Provider lookup) {
	}

	/** Blocks copied from the client on the main thread, simulated on the worker. */
	private record Snapshot(ChunkPos pos, Map<Long, LevelChunkSection[]> sections, Map<Long, int[]> heights) {
	}

	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Temple ore simulator");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	private final Map<Long, List<Ore>> results = new ConcurrentHashMap<>();
	private final Set<Long> queued = ConcurrentHashMap.newKeySet();
	private final Map<ResourceKey<Level>, Setup> setups = new ConcurrentHashMap<>();
	private volatile HolderLookup.Provider lookup;
	private volatile String status = "";
	private long seed;
	private ResourceKey<Level> dimension;
	private int generation;

	public String status() {
		return status;
	}

	/** Forgets everything when the seed or dimension changes. */
	public void configure(long seed, ResourceKey<Level> dimension) {
		if (seed != this.seed || !dimension.equals(this.dimension)) {
			this.seed = seed;
			this.dimension = dimension;
			generation++;
			results.clear();
			queued.clear();
		}
	}

	public void clear() {
		generation++;
		results.clear();
		queued.clear();
	}

	public Map<Long, List<Ore>> results() {
		return results;
	}

	/** Queues loaded chunks within {@code radius} of the player that have not been simulated yet. */
	public void tick(ClientLevel level, ChunkPos center, int radius, int perTick) {
		if (dimension == null || !supported(dimension)) {
			return;
		}
		int started = 0;
		for (int r = 0; r <= radius && started < perTick; r++) {
			for (int dx = -r; dx <= r && started < perTick; dx++) {
				for (int dz = -r; dz <= r && started < perTick; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int cx = center.x() + dx;
					int cz = center.z() + dz;
					long key = ChunkPos.pack(cx, cz);
					if (results.containsKey(key) || queued.contains(key) || !loaded(level, cx, cz)) {
						continue;
					}
					Snapshot snapshot = snapshot(level, cx, cz);
					queued.add(key);
					int gen = generation;
					long s = seed;
					ResourceKey<Level> dim = dimension;
					PalettedContainerFactory factory = level.palettedContainerFactory();
					worker.execute(() -> run(snapshot, s, dim, gen, factory, level));
					started++;
				}
			}
		}
		// Drop results far outside the radius, so memory stays bounded while travelling.
		results.keySet().removeIf(k -> Math.max(Math.abs(ChunkPos.getX(k) - center.x()), Math.abs(ChunkPos.getZ(k) - center.z())) > radius + 4);
	}

	public static boolean supported(ResourceKey<Level> dimension) {
		return dimension.equals(Level.OVERWORLD) || dimension.equals(Level.NETHER);
	}

	private static boolean loaded(ClientLevel level, int cx, int cz) {
		return level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) != null;
	}

	/** Copies the chunk and its neighbours (ore veins spill across chunk borders). Main thread only. */
	private static Snapshot snapshot(ClientLevel level, int cx, int cz) {
		Map<Long, LevelChunkSection[]> sections = new HashMap<>();
		Map<Long, int[]> heights = new HashMap<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] source = chunk.getSections();
				LevelChunkSection[] copy = new LevelChunkSection[source.length];
				for (int i = 0; i < source.length; i++) {
					copy[i] = source[i].copy();
				}
				long key = ChunkPos.pack(cx + dx, cz + dz);
				sections.put(key, copy);
				int[] h = new int[256];
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						h[x + z * 16] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
					}
				}
				heights.put(key, h);
			}
		}
		return new Snapshot(new ChunkPos(cx, cz), sections, heights);
	}

	private void run(Snapshot snapshot, long seed, ResourceKey<Level> dim, int gen, PalettedContainerFactory factory, ClientLevel client) {
		try {
			Setup setup = setup(dim);
			if (setup == null || gen != generation) {
				return;
			}
			List<Ore> found = simulate(snapshot, seed, setup, factory, client);
			if (gen == generation) {
				results.put(ChunkPos.pack(snapshot.pos().x(), snapshot.pos().z()), found);
			}
		} catch (Throwable t) {
			LOGGER.warn("Ore simulation failed for chunk {}", snapshot.pos(), t);
			status = "Simulation error: " + t.getClass().getSimpleName();
			if (gen == generation) {
				results.put(ChunkPos.pack(snapshot.pos().x(), snapshot.pos().z()), List.of());
			}
		} finally {
			queued.remove(ChunkPos.pack(snapshot.pos().x(), snapshot.pos().z()));
		}
	}

	private Setup setup(ResourceKey<Level> dim) {
		Setup existing = setups.get(dim);
		if (existing != null) {
			return existing;
		}
		status = "Building world generation data…";
		if (lookup == null) {
			//? if >=26.3 {
			/*lookup = VanillaRegistries.createWorldLookup();
			*///?} else {
			lookup = VanillaRegistries.createLookup();
			//?}
		}
		boolean nether = dim.equals(Level.NETHER);
		var presets = lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
		BiomeSource source = MultiNoiseBiomeSource.createFromPreset(presets.getOrThrow(
				nether ? MultiNoiseBiomeSourceParameterLists.NETHER : MultiNoiseBiomeSourceParameterLists.OVERWORLD));
		Holder<NoiseGeneratorSettings> noise = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
				.getOrThrow(nether ? NoiseGeneratorSettings.NETHER : NoiseGeneratorSettings.OVERWORLD);
		ChunkGenerator generator = new NoiseBasedChunkGenerator(source, noise);
		// The same ordering the server builds, so every feature gets the index it had during generation.
		List<FeatureSorter.StepFeatureData> steps = FeatureSorter.buildFeaturesPerStep(List.copyOf(source.possibleBiomes()),
				biome -> biome.value().getGenerationSettings().features(), true);
		List<OreFeatureRef> ores = new ArrayList<>();
		for (int step = 0; step < steps.size(); step++) {
			FeatureSorter.StepFeatureData data = steps.get(step);
			for (PlacedFeature feature : data.features()) {
				var configured = feature.feature().value();
				// 26.3 folded the configuration into the feature: an ore feature holds its own targets.
				//? if >=26.3 {
				/*if (!(configured instanceof net.minecraft.world.level.levelgen.feature.AbstractOreFeature ore)
						|| !isOre(ore.targetStates().stream().map(t -> t.state()).toList())) {
					continue;
				}
				*///?} else {
				if (!(configured.feature() instanceof OreFeature) && !(configured.feature() instanceof ScatteredOreFeature)) {
					continue;
				}
				if (!(configured.config() instanceof OreConfiguration config)
						|| !isOre(config.targetStates.stream().map(t -> t.state).toList())) {
					continue;
				}
				//?}
				ores.add(new OreFeatureRef(feature, data.indexMapping().applyAsInt(feature), step));
			}
		}
		Setup setup = new Setup(generator, ores, lookup);
		setups.put(dim, setup);
		status = "";
		return setup;
	}

	/** Only real ores: vanilla also uses ore features for dirt, gravel, granite and the like. */
	private static boolean isOre(List<BlockState> targets) {
		for (var target : targets) {
			String id = BuiltInRegistries.BLOCK.getKey(target.getBlock()).getPath();
			if (id.endsWith("_ore") || id.equals("ancient_debris")) {
				return true;
			}
		}
		return false;
	}

	private List<Ore> simulate(Snapshot snapshot, long seed, Setup setup, PalettedContainerFactory factory, ClientLevel client) {
		Map<Long, ProtoChunk> chunks = new HashMap<>();
		Map<Long, LevelChunkSection[]> originals = new HashMap<>();
		for (var entry : snapshot.sections().entrySet()) {
			LevelChunkSection[] original = entry.getValue();
			// Start from bare rock: every ore the client can see goes back to the stone it replaced. That is what
			// the generator saw, and it is also what an anti-xray server sends, so both cases simulate the same.
			for (LevelChunkSection section : original) {
				unore(section);
			}
			LevelChunkSection[] working = new LevelChunkSection[original.length];
			for (int i = 0; i < original.length; i++) {
				working[i] = original[i].copy();
			}
			originals.put(entry.getKey(), original);
			ChunkPos pos = new ChunkPos(ChunkPos.getX(entry.getKey()), ChunkPos.getZ(entry.getKey()));
			chunks.put(entry.getKey(), new ProtoChunk(pos, UpgradeData.EMPTY, working, new ProtoChunkTicks<>(), new ProtoChunkTicks<>(),
					client, factory, null));
		}
		Map<ResourceKey<Biome>, Holder<Biome>> biomeCache = new HashMap<>();
		var biomes = setup.lookup().lookupOrThrow(Registries.BIOME);
		WorldGenLevel level = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[] {WorldGenLevel.class},
				new FakeLevel(chunks, snapshot.heights(), client, seed, key -> biomeCache.computeIfAbsent(key,
						k -> biomes.get(k).map(h -> (Holder<Biome>) h).orElse(null))));
		int cx = snapshot.pos().x();
		int cz = snapshot.pos().z();
		WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
		long decoration = random.setDecorationSeed(seed, cx << 4, cz << 4);
		BlockPos origin = new BlockPos(cx << 4, setup.generator().getMinY(), cz << 4);
		for (OreFeatureRef ore : setup.ores()) {
			random.setFeatureSeed(decoration, ore.index(), ore.step());
			try {
				//? if >=26.3 {
				/*ore.feature().place(level, setup.generator(), random, origin);
				*///?} else {
				ore.feature().placeWithBiomeCheck(level, setup.generator(), random, origin);
				//?}
			} catch (RuntimeException e) {
				// One unusual feature should not lose the whole chunk.
			}
		}
		List<Ore> found = new ArrayList<>();
		int minSection = client.getMinSectionY();
		for (var entry : chunks.entrySet()) {
			LevelChunkSection[] before = originals.get(entry.getKey());
			LevelChunkSection[] after = entry.getValue().getSections();
			int baseX = ChunkPos.getX(entry.getKey()) << 4;
			int baseZ = ChunkPos.getZ(entry.getKey()) << 4;
			for (int s = 0; s < after.length; s++) {
				if (after[s].hasOnlyAir()) {
					continue;
				}
				for (int y = 0; y < 16; y++) {
					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							BlockState now = after[s].getBlockState(x, y, z);
							if (now != before[s].getBlockState(x, y, z)) {
								found.add(new Ore(new BlockPos(baseX + x, ((minSection + s) << 4) + y, baseZ + z),
										BuiltInRegistries.BLOCK.getKey(now.getBlock()).getPath()));
							}
						}
					}
				}
			}
		}
		return found;
	}

	private static void unore(LevelChunkSection section) {
		if (section.hasOnlyAir() || !section.getStates().maybeHas(OreSimulator::isOreBlock)) {
			return;
		}
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					BlockState state = section.getBlockState(x, y, z);
					if (isOreBlock(state)) {
						section.setBlockState(x, y, z, hostOf(state), false);
					}
				}
			}
		}
	}

	private static boolean isOreBlock(BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return id.endsWith("_ore") || id.equals("ancient_debris");
	}

	private static BlockState hostOf(BlockState ore) {
		String id = BuiltInRegistries.BLOCK.getKey(ore.getBlock()).getPath();
		if (id.startsWith("deepslate_")) {
			return Blocks.DEEPSLATE.defaultBlockState();
		}
		if (id.startsWith("nether_") || id.equals("ancient_debris")) {
			return Blocks.NETHERRACK.defaultBlockState();
		}
		return Blocks.STONE.defaultBlockState();
	}

	/** Just enough of a world for ore placement to run against the copied chunks. */
	private static final class FakeLevel implements InvocationHandler {
		private final Map<Long, ProtoChunk> chunks;
		private final Map<Long, int[]> heights;
		private final ClientLevel client;
		private final long seed;
		private final java.util.function.Function<ResourceKey<Biome>, Holder<Biome>> biomes;
		private final Set<String> unknown = new HashSet<>();

		FakeLevel(Map<Long, ProtoChunk> chunks, Map<Long, int[]> heights, ClientLevel client, long seed,
				java.util.function.Function<ResourceKey<Biome>, Holder<Biome>> biomes) {
			this.chunks = chunks;
			this.heights = heights;
			this.client = client;
			this.seed = seed;
			this.biomes = biomes;
		}

		private ProtoChunk chunkAt(int blockX, int blockZ) {
			return chunks.get(ChunkPos.pack(blockX >> 4, blockZ >> 4));
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String name = method.getName();
			int n = args == null ? 0 : args.length;
			switch (name) {
				case "getBlockState" -> {
					BlockPos pos = (BlockPos) args[0];
					ProtoChunk chunk = chunkAt(pos.getX(), pos.getZ());
					return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
				}
				case "getFluidState" -> {
					BlockPos pos = (BlockPos) args[0];
					ProtoChunk chunk = chunkAt(pos.getX(), pos.getZ());
					return chunk == null ? Blocks.AIR.defaultBlockState().getFluidState() : (FluidState) chunk.getFluidState(pos);
				}
				case "setBlock" -> {
					BlockPos pos = (BlockPos) args[0];
					ProtoChunk chunk = chunkAt(pos.getX(), pos.getZ());
					if (chunk == null) {
						return false;
					}
					chunk.setBlockState(pos, (BlockState) args[1], 0);
					return true;
				}
				case "getChunk" -> {
					if (n >= 2 && args[0] instanceof Integer x && args[1] instanceof Integer z) {
						ProtoChunk chunk = chunks.get(ChunkPos.pack(x, z));
						return chunk != null ? chunk : empty(x, z);
					}
					if (n == 1 && args[0] instanceof BlockPos pos) {
						ProtoChunk chunk = chunkAt(pos.getX(), pos.getZ());
						return chunk != null ? chunk : empty(pos.getX() >> 4, pos.getZ() >> 4);
					}
					return null;
				}
				case "getHeight" -> {
					if (n == 0) {
						return client.getHeight();
					}
					int x = (Integer) args[1];
					int z = (Integer) args[2];
					int[] h = heights.get(ChunkPos.pack(x >> 4, z >> 4));
					return h == null ? client.getMinY() : h[(x & 15) + (z & 15) * 16];
				}
				case "getMinY" -> {
					return client.getMinY();
				}
				case "getSeed" -> {
					return seed;
				}
				case "ensureCanWrite" -> {
					return true;
				}
				case "getBiome" -> {
					BlockPos pos = (BlockPos) args[0];
					Holder<Biome> clientBiome = client.getBiome(pos);
					Holder<Biome> mapped = clientBiome.unwrapKey().map(biomes).orElse(null);
					return mapped != null ? mapped : clientBiome;
				}
				case "hashCode" -> {
					return System.identityHashCode(proxy);
				}
				case "equals" -> {
					return proxy == args[0];
				}
				case "toString" -> {
					return "TempleOreSimLevel";
				}
				default -> {
				}
			}
			if (method.isDefault()) {
				return InvocationHandler.invokeDefault(proxy, method, args);
			}
			if (unknown.add(name)) {
				LOGGER.debug("Ore simulation level has no answer for {}", name);
			}
			Class<?> type = method.getReturnType();
			if (type == boolean.class) {
				return false;
			}
			if (type == int.class || type == long.class || type == float.class || type == double.class || type == short.class
					|| type == byte.class) {
				return 0;
			}
			return null;
		}

		private final Map<Long, ProtoChunk> empties = new HashMap<>();

		/** An all-air chunk for anywhere the client has nothing loaded, so writes there are simply ignored. */
		private ChunkAccess empty(int x, int z) {
			return empties.computeIfAbsent(ChunkPos.pack(x, z), k -> new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, client,
					client.palettedContainerFactory(), null));
		}
	}
}
