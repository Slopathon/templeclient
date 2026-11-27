package nz.temple.client.render.grass;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;
import nz.temple.client.modules.render.RealisticGrass;
import nz.temple.client.modules.render.WetGround;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GrassRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/grass");
	private static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_TEX_COLOR;
	private static final int MAX_PUSHERS = 12;
	private static final int CONFIG_BYTES = 16 * 8 + 16 * MAX_PUSHERS * 2;
	private static final ByteBuffer CONFIG = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private static final int TILE = 8;
	private static final int BLADES = 1300;
	private static final float[] SHARE = {1, 0.22f, 0.07f, 0.03f};
	private static final int[] RINGS = {1, 3, 5};
	private static final float[] THIN = {3, 8, 16, 24, 34, 40};

	private static final float WIDTH = 0.014f;
	private static final int PLANT_TILES = 6;
	private static final int TUFTS_ONLY = 4;
	private static final int FACINGS = 32;
	private static final int LAWN = 0, PLANT = 1;
	private static final double TRAIL_SECONDS = 1.6;
	private static final class Mesh {
		GpuBuffer buffer;
		int quads;
		int groundQuads;
		int lod = -1;
		int version = -1;
		long hash;
		float minY;
		float maxY;
		double checkedAt;
		boolean building;
		void close() {
			if (buffer != null) {
				buffer.close();
				buffer = null;
			}
		}
	}
	private record Snapshot(int cx, int cz, int lod, int[] cells, int count, long hash, RealisticGrass.Look look, int generation) {
	}
	private record Built(Snapshot from, ByteBufferBuilder bytes, MeshData data, int quads, int groundQuads, float minY, float maxY) {
		void close() {
			if (data != null) {
				data.close();
			}
			if (bytes != null) {
				bytes.close();
			}
		}
	}

	private record Trail(double x, double y, double z, float radius, double time, float dirX, float dirZ) {
	}
	private static final Map<Long, Mesh> meshes = new HashMap<>();
	private static final ConcurrentLinkedQueue<Built> finished = new ConcurrentLinkedQueue<>();
	private static final ArrayDeque<Trail> trails = new ArrayDeque<>();
	private static final java.util.Set<Long> changed = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static ExecutorService urgentWorker;
	private static final Map<Integer, double[]> trailFrom = new HashMap<>();
	private static ExecutorService worker;
	private static ClientLevel level;
	private static volatile int generation;
	private static int inFlight;
	private static RenderPipeline pipeline;
	private static GpuBuffer config;
	private static boolean failed;
	private GrassRenderer() {
	}
	public static void register() {
		LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(ctx -> nz.temple.client.compat.Gpu.outsidePass(() -> draw(ctx)));
	}
	public static RenderPipeline pipeline() {
		if (pipeline == null) {
			pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/grass"))
					.withVertexShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/grass"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/grass"))
					.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler2").build())
					.withBindGroupLayout(BindGroupLayout.builder().withUniform("GrassConfig", UniformType.UNIFORM_BUFFER).build())
					.withVertexBinding(0, FORMAT)
					.withPrimitiveTopology(PrimitiveTopology.QUADS)
					.withColorTargetState(new ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))

					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
					.withCull(false)
					.build());
		}
		return pipeline;
	}
	private static void draw(LevelTerrainRenderContext context) {
		RealisticGrass.Look look = RealisticGrass.look();
		Minecraft mc = Minecraft.getInstance();
		if (failed || look == null || mc.level == null || mc.player == null) {
			if (level != null) {
				reset();
			}
			return;
		}
		try {
			if (mc.level != level) {
				reset();
				level = mc.level;
			}
			var camera = context.levelState().cameraRenderState;
			if (camera.pos == null || camera.viewRotationMatrix == null) {
				return;
			}
			double now = System.nanoTime() / 1e9;
			collect();
			schedule(look, camera.pos.x, camera.pos.z, now);
			render(context, mc, look, camera.pos.x, camera.pos.y, camera.pos.z, now);
		} catch (RuntimeException e) {
			failed = true;
			reset();
			LOGGER.error("Realistic grass disabled", e);
		}
	}
	public static void blockChanged(BlockPos pos) {
		if (level == null) {
			return;
		}
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				changed.add(key(Math.floorDiv(pos.getX() + dx, TILE), Math.floorDiv(pos.getZ() + dz, TILE)));
			}
		}
	}
	private static void reset() {
		changed.clear();
		generation++;
		for (Mesh m : meshes.values()) {
			m.close();
		}
		meshes.clear();
		trails.clear();
		trailFrom.clear();
		level = null;
		for (Built b = finished.poll(); b != null; b = finished.poll()) {
			b.close();
			inFlight--;
		}
	}
	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}
	private static void collect() {
		for (Built b = finished.poll(); b != null; b = finished.poll()) {
			inFlight--;
			Snapshot s = b.from();
			Mesh m = meshes.get(key(s.cx(), s.cz()));
			if (s.generation() != generation || m == null) {
				b.close();
				continue;
			}
			m.building = false;
			m.close();
			m.quads = b.quads();
			m.groundQuads = b.groundQuads();
			m.lod = s.lod();
			m.version = s.look().version();
			m.hash = s.hash();
			m.minY = b.minY();
			m.maxY = b.maxY();
			if (b.data() != null) {
				m.buffer = RenderSystem.getDevice().createBuffer(() -> "Temple grass", GpuBuffer.USAGE_VERTEX, b.data().vertexBuffer());
			}
			b.close();
		}
	}
	private static void schedule(RealisticGrass.Look look, double camX, double camZ, double now) {
		int ccx = Math.floorDiv((int) Math.floor(camX), TILE), ccz = Math.floorDiv((int) Math.floor(camZ), TILE);
		int lawn = Math.max(1, (int) Math.ceil(look.distance() / TILE));
		int reach = lawn + PLANT_TILES;
		int started = 0;
		for (Iterator<Long> it = changed.iterator(); it.hasNext();) {
			long k = it.next();
			Mesh m = meshes.get(k);
			if (m != null && m.building) {
				continue;
			}
			it.remove();
			if (m != null && m.lod >= 0) {
				Snapshot s = snapshot((int) (k >> 32), (int) k, m.lod, look);
				if (s.hash() != m.hash) {
					start(m, s, now, true);
				}
			}
		}
		Mesh stalest = null;
		int staleX = 0, staleZ = 0, staleLod = 0;
		double staleScore = 0;
		for (int ring = 0; ring <= reach && started < 4; ring++) {
			for (int dz = -ring; dz <= ring && started < 4; dz++) {
				for (int dx = -ring; dx <= ring && started < 4; dx++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					int cx = ccx + dx, cz = ccz + dz;
					if (!level.hasChunk(cx * TILE >> 4, cz * TILE >> 4)) {
						continue;
					}
					int lod = ring > lawn ? TUFTS_ONLY : ring <= RINGS[0] ? 0 : ring <= RINGS[1] ? 1 : ring <= RINGS[2] ? 2 : 3;
					Mesh m = meshes.get(key(cx, cz));
					if (m != null && m.building) {
						continue;
					}
					if (m == null || m.lod != lod || m.version != look.version()) {
						if (inFlight < 8) {
							if (m == null) {
								m = new Mesh();
								meshes.put(key(cx, cz), m);
							}
							start(m, snapshot(cx, cz, lod, look), now, false);
							started++;
						}
						continue;
					}
					double score = (now - m.checkedAt) * (ring <= RINGS[0] ? 8 : ring <= RINGS[1] ? 2 : 1);
					if (score > staleScore) {
						staleScore = score;
						stalest = m;
						staleX = cx;
						staleZ = cz;
						staleLod = lod;
					}
				}
			}
		}
		if (started == 0 && stalest != null && inFlight < 8) {
			Snapshot s = snapshot(staleX, staleZ, staleLod, look);
			stalest.checkedAt = now;
			if (s.hash() != stalest.hash) {
				start(stalest, s, now, false);
			}
		}
		Iterator<Map.Entry<Long, Mesh>> it = meshes.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Mesh> e = it.next();
			int cx = (int) (e.getKey() >> 32), cz = e.getKey().intValue();
			if (Math.abs(cx - ccx) > reach + 1 || Math.abs(cz - ccz) > reach + 1) {
				e.getValue().close();
				it.remove();
			}
		}
	}
	private static void start(Mesh m, Snapshot s, double now, boolean urgent) {
		m.checkedAt = now;
		if (s.count() == 0) {
			m.close();
			m.quads = 0;
			m.lod = s.lod();
			m.version = s.look().version();
			m.hash = s.hash();
			return;
		}
		m.building = true;
		inFlight++;
		if (worker == null) {
			worker = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "Temple grass");
				t.setDaemon(true);
				t.setPriority(Thread.NORM_PRIORITY - 1);
				return t;
			});
		}
		if (urgent && urgentWorker == null) {
			urgentWorker = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "Temple grass (changed)");
				t.setDaemon(true);
				return t;
			});
		}
		(urgent ? urgentWorker : worker).execute(() -> {
			Built built;
			try {
				built = build(s);
			} catch (RuntimeException e) {
				LOGGER.error("Could not mesh grass", e);
				built = new Built(s, null, null, 0, 0, 0, 0);
			}
			finished.add(built);
		});
	}
	private static Snapshot snapshot(int cx, int cz, int lod, RealisticGrass.Look look) {
		int x0 = cx * TILE, z0 = cz * TILE;
		int minSection = level.getMinSectionY();
		int[] cells = new int[(TILE + 2) * (TILE + 2) * 3];
		int count = 0;
		long hash = lod * 31L + look.version();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		LevelChunk chunk = null;
		int chunkX = Integer.MIN_VALUE, chunkZ = 0;
		boolean[] grassy = null;
		for (int tz = -1; tz <= TILE; tz++) {
			for (int tx = -1; tx <= TILE; tx++) {
				boolean border = tx < 0 || tz < 0 || tx >= TILE || tz >= TILE;
				if (border && lod == TUFTS_ONLY) {
					continue;
				}
				int wx = x0 + tx, wz = z0 + tz;
				if (wx >> 4 != chunkX || wz >> 4 != chunkZ || chunk == null) {
					chunkX = wx >> 4;
					chunkZ = wz >> 4;
					chunk = level.hasChunk(chunkX, chunkZ) ? level.getChunk(chunkX, chunkZ) : null;
					grassy = null;
					if (chunk != null) {
						LevelChunkSection[] all = chunk.getSections();
						grassy = new boolean[all.length];
						for (int si = 0; si < all.length; si++) {
							grassy[si] = all[si] != null && !all[si].hasOnlyAir() && all[si].maybeHas(st -> st.is(Blocks.GRASS_BLOCK));
						}
					}
				}
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				int x = wx & 15, z = wz & 15;
				for (int si = 0; si < sections.length; si++) {
					if (!grassy[si]) {
						continue;
					}
					LevelChunkSection section = sections[si];
					LevelChunkSection up = si + 1 < sections.length ? sections[si + 1] : null;
					for (int y = 0; y < 16; y++) {
						BlockState state = section.getBlockState(x, y, z);
						if (!state.is(Blocks.GRASS_BLOCK) || state.getValue(BlockStateProperties.SNOWY)) {
							continue;
						}
						BlockState above = y < 15 ? section.getBlockState(x, y + 1, z)
								: up == null ? Blocks.AIR.defaultBlockState() : up.getBlockState(x, 0, z);
						int plant = border ? 0 : RealisticGrass.plant(above);
						if (plant == 3) {
							plant = 0;
						}
						if (plant == 0 && (lod == TUFTS_ONLY || !open(above))) {
							continue;
						}
						int wy = (minSection + si) * 16 + y;
						pos.set(wx, wy + 1, wz);
						int light = level.getBrightness(LightLayer.BLOCK, pos) | level.getBrightness(LightLayer.SKY, pos) << 4;
						pos.setY(wy);
						int tint = BiomeColors.getAverageGrassColor(level, pos) & 0xFFFFFF;
						if ((count + 1) * 3 > cells.length) {
							cells = java.util.Arrays.copyOf(cells, cells.length * 2);
						}
						int packed = tx + 1 | tz + 1 << 4 | plant << 8 | light << 12 | (border ? 1 << 20 : 0);
						cells[count * 3] = packed;
						cells[count * 3 + 1] = wy;
						cells[count * 3 + 2] = tint;
						count++;
						hash = (hash * 0x9E3779B97F4A7C15L) ^ (packed * 73856093L + wy * 19349663L + tint);
					}
				}
			}
		}
		return new Snapshot(cx, cz, lod, cells, count, hash, look, generation);
	}
	private static boolean open(BlockState above) {
		return above.isAir() || !nz.temple.client.compat.States.blocksMotion(above) && above.getFluidState().isEmpty() && !above.is(Blocks.SNOW);
	}
	private static int mix(int h) {
		h ^= h >>> 16;
		h *= 0x7FEB352D;
		h ^= h >>> 15;
		h *= 0x846CA68B;
		h ^= h >>> 16;
		return h;
	}
	private static float cellNoise(int x, int z) {
		return (mix(x * 0x1F1F1F1F ^ z * 0x5BD1E995) >>> 8) / (float) (1 << 24);
	}
	private static float patch(float x, float z) {
		int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
		float fx = x - ix, fz = z - iz;
		float ux = fx * fx * (3 - 2 * fx), uz = fz * fz * (3 - 2 * fz);
		float a = cellNoise(ix, iz), b = cellNoise(ix + 1, iz), c = cellNoise(ix, iz + 1), d = cellNoise(ix + 1, iz + 1);
		return (a + (b - a) * ux) * (1 - uz) + (c + (d - c) * ux) * uz;
	}

	private static final class Blades {
		final BufferBuilder out;
		int quads;
		float minY = Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		int seed;
		Blades(BufferBuilder out) {
			this.out = out;
		}

		float next() {
			seed = seed * 1664525 + 1013904223;
			return (seed >>> 8) / (float) (1 << 24);
		}
		void blade(float x, float y, float z, float h, float halfWidth, int rgb, float rank, int kind, int light, int segments) {
			int facing = (int) (next() * FACINGS) % FACINGS;
			float face = facing * 6.2831855f / FACINGS;
			float wx = (float) -Math.sin(face), wz = (float) Math.cos(face);
			float lean = (0.06f + 0.26f * next()) * h;
			float leanDir = next() * 6.2831855f;
			float lx = (float) Math.cos(leanDir) * lean, lz = (float) Math.sin(leanDir) * lean;
			float shade = 0.84f + 0.3f * next();
			float warm = 0.9f + 0.2f * next();
			int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * shade * warm));
			int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * shade));
			int b = Math.min(255, Math.round((rgb & 0xFF) * shade * (2 - warm) * 0.9f));
			int colour = Math.round(Math.min(0.999f, rank) * 255) << 24 | r << 16 | g << 8 | b;
			h = Math.min(h, 3.9f);
			int code = facing + (kind == PLANT ? FACINGS : 0);
			for (int s = 0; s < segments; s++) {
				float t0 = s / (float) segments, t1 = (s + 1) / (float) segments;
				boolean tip = s == segments - 1;
				float w0 = halfWidth * (1 - 0.72f * t0), w1 = tip ? 0 : halfWidth * (1 - 0.72f * t1);
				float x0 = x + lx * t0 * t0, y0 = y + h * t0, z0 = z + lz * t0 * t0;
				float x1 = x + lx * t1 * t1, y1 = y + h * t1, z1 = z + lz * t1 * t1;
				vertex(x0 - wx * w0, y0, z0 - wz * w0, colour, t0, h, code, 0, light);
				vertex(x0 + wx * w0, y0, z0 + wz * w0, colour, t0, h, code, 1, light);
				vertex(x1 + wx * w1, y1, z1 + wz * w1, colour, t1, h, code, tip ? 2 : 1, light);
				vertex(x1 - wx * w1, y1, z1 - wz * w1, colour, t1, h, code, tip ? 2 : 0, light);
				quads++;
			}
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y + h);
		}
		void ground(float x, float y, float z, int[] corners, int light) {
			vertex(x, y, z, corners[0], 0, 0, 0, 2, light);
			vertex(x, y, z + 1, corners[2], 0, 0, 0, 2, light);
			vertex(x + 1, y, z + 1, corners[3], 0, 0, 0, 2, light);
			vertex(x + 1, y, z, corners[1], 0, 0, 0, 2, light);
			quads++;
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
		}
		private void vertex(float x, float y, float z, int colour, float t, float h, int code, int side, int light) {
			out.addVertex(x, y, z).setUv(t * 0.999f + 2 * side + 6 * code, h + 4 * light).setColor(colour);
		}
	}
	private static Built build(Snapshot s) {
		RealisticGrass.Look look = s.look();
		int lod = s.lod();
		int lawnBlades = lod == TUFTS_ONLY ? 0 : Math.max(1, Math.round(BLADES * look.density() * SHARE[lod]));
		int lawnQuads = 1;
		int shortBlades = lod >= 3 ? 10 : lod == 0 ? 90 : lod == 1 ? 40 : 20, tallBlades = lod >= 3 ? 12 : lod == 0 ? 120 : lod == 1 ? 50 : 26;
		int plantQuads = lod == 0 ? 3 : 2;
		int quads = 0;
		for (int i = 0; i < s.count(); i++) {
			if ((s.cells()[i * 3] >> 20 & 1) != 0) {
				continue;
			}
			int plant = s.cells()[i * 3] >> 8 & 0xF;
			quads += lod == TUFTS_ONLY ? 0 : 1;
			quads += lawnBlades * lawnQuads + (plant == 1 ? shortBlades * plantQuads : plant == 2 ? tallBlades * plantQuads : 0);
		}
		if (quads == 0) {
			return new Built(s, null, null, 0, 0, 0, 0);
		}
		ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(quads * 4 * FORMAT.getVertexSize());
		try {
			Blades blades = new Blades(Gpu.bufferBuilder(bytes, PrimitiveTopology.QUADS, FORMAT));
			float share = lod == TUFTS_ONLY ? 1 : SHARE[lod];
			Map<Integer, Integer> at = new HashMap<>();
			for (int i = 0; i < s.count(); i++) {
				at.put((s.cells()[i * 3] & 0xFF) | s.cells()[i * 3 + 1] << 8, i);
			}
			float[] blend = new float[5];
			int groundQuads = 0;
			int[] corners = new int[4];
			for (int i = 0; lod != TUFTS_ONLY && i < s.count(); i++) {
				int packed = s.cells()[i * 3];
				if ((packed >> 20 & 1) != 0) {
					continue;
				}
				for (int k = 0; k < 4; k++) {
					blend(s, at, i, k & 1, k >> 1, blend);
					corners[k] = 0xFF000000 | Math.round(blend[0] * 0.8f) << 16 | Math.round(blend[1] * 0.8f) << 8 | Math.round(blend[2] * 0.8f);
				}
				blades.ground((packed & 0xF) - 1, s.cells()[i * 3 + 1] + 1.012f, (packed >> 4 & 0xF) - 1, corners, packed >> 12 & 0xFF);
				groundQuads++;
			}
			for (int i = 0; i < s.count(); i++) {
				int packed = s.cells()[i * 3];
				if ((packed >> 20 & 1) != 0) {
					continue;
				}
				int x = (packed & 0xF) - 1, z = (packed >> 4 & 0xF) - 1, plant = packed >> 8 & 0xF, light = packed >> 12 & 0xFF;
				int wy = s.cells()[i * 3 + 1];
				int wx = s.cx() * TILE + x, wz = s.cz() * TILE + z;
				blades.seed = mix(wx * 73856093 ^ wy * 19349663 ^ wz * 83492791);
				float top = wy + 1;
				for (int b = 0; b < lawnBlades; b++) {
					float fx = blades.next(), fz = blades.next();
					float grown = 0.6f * patch((wx + fx) * 0.31f, (wz + fz) * 0.31f) + 0.4f * blades.next();
					float h = Math.max(0.08f, 0.5f * look.height() * (1 + look.variation() * (grown * 2 - 1) * 0.9f));
					boolean thatch = b % 3 == 0;
					if (thatch) {
						h *= 0.3f + 0.25f * blades.next();
					}
					blend(s, at, i, fx, fz, blend);
					int tint = Math.round(blend[0] * 0.72f) << 16 | Math.round(blend[1] * 0.72f) << 8 | Math.round(blend[2] * 0.72f);
					int blockLight = Math.min(15, (int) (blend[3] + blades.next())), skyLight = Math.min(15, (int) (blend[4] + blades.next()));
					blades.blade(x + fx, top, z + fz, h, WIDTH * (thatch ? 1.7f : 1) * (0.7f + 0.6f * blades.next()), tint, blades.next() * share, LAWN,
							blockLight | skyLight << 4, lawnQuads);
				}
				if (plant != 0) {
					int n = plant == 1 ? shortBlades : tallBlades;
					float scaleH = 0.5f + 0.5f * look.height();
					float tuftWidth = lod >= 3 ? 0.05f : lod == 0 ? 0.011f : lod == 1 ? 0.018f : 0.03f;
					int bright = scale(s.cells()[i * 3 + 2], 0.8f);
					for (int b = 0; b < n; b++) {
						float a = blades.next() * 6.2831855f, d = (float) Math.sqrt(blades.next()) * 0.36f;
						float h = plant == 1 ? 0.6f + 0.55f * blades.next() : 1.15f + 0.85f * blades.next();
						h *= scaleH * (1 - 0.3f * d / 0.36f);
						blades.blade(x + 0.5f + (float) Math.cos(a) * d, top, z + 0.5f + (float) Math.sin(a) * d, h,
								tuftWidth * (0.8f + 0.4f * blades.next()), bright, 0, PLANT, light, plantQuads);
					}
				}
			}
			MeshData data = blades.out.buildOrThrow();
			return new Built(s, bytes, data, blades.quads, groundQuads, blades.minY, blades.maxY);
		} catch (RuntimeException e) {
			bytes.close();
			throw e;
		}
	}
	private static void blend(Snapshot s, Map<Integer, Integer> at, int i, float fx, float fz, float[] out) {
		int[] cells = s.cells();
		int px = cells[i * 3] & 0xF, pz = cells[i * 3] >> 4 & 0xF, wy = cells[i * 3 + 1];
		int dx = fx < 0.5f ? -1 : 1, dz = fz < 0.5f ? -1 : 1;
		float ax = Math.abs(fx - 0.5f), az = Math.abs(fz - 0.5f);
		java.util.Arrays.fill(out, 0);
		for (int k = 0; k < 4; k++) {
			int ox = (k & 1) * dx, oz = (k >> 1) * dz;
			float w = ((k & 1) == 0 ? 1 - ax : ax) * ((k >> 1) == 0 ? 1 - az : az);
			int key = px + ox | pz + oz << 4;
			Integer n = k == 0 ? Integer.valueOf(i) : at.get(key | wy << 8);
			if (n == null) {
				n = at.get(key | wy + 1 << 8);
			}
			if (n == null) {
				n = at.get(key | wy - 1 << 8);
			}
			int c = n == null ? i : n;
			int tint = cells[c * 3 + 2], light = cells[c * 3] >> 12 & 0xFF;
			out[0] += w * (tint >> 16 & 0xFF);
			out[1] += w * (tint >> 8 & 0xFF);
			out[2] += w * (tint & 0xFF);
			out[3] += w * (light & 0xF);
			out[4] += w * (light >> 4 & 0xF);
		}
	}
	private static int scale(int rgb, float k) {
		int r = Math.round(((rgb >> 16) & 0xFF) * k), g = Math.round(((rgb >> 8) & 0xFF) * k), b = Math.round((rgb & 0xFF) * k);
		return r << 16 | g << 8 | b;
	}

	private static List<float[]> pushers(Minecraft mc, double camX, double camY, double camZ, double now) {
		List<float[]> out = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || living.isSpectator() || living.isInvisible()) {
				continue;
			}
			double dx = e.getX() - camX, dz = e.getZ() - camZ;
			if (dx * dx + dz * dz > 40 * 40) {
				continue;
			}
			float radius = (float) Math.min(1.7, e.getBbWidth() * 0.5 + 0.3);
			var going = e.getDeltaMovement();
			double speed = Math.sqrt(going.x * going.x + going.z * going.z);
			float gx = speed > 0.02 ? (float) (going.x / speed) : 0, gz = speed > 0.02 ? (float) (going.z / speed) : 0;
			boolean own = e == mc.player && mc.options.getCameraType().isFirstPerson();
			out.add(new float[] {(float) dx, (float) (e.getY() - camY), (float) dz, radius, own ? 0.6f : 1, gx, gz});
			double[] from = trailFrom.get(e.getId());
			if (from == null) {
				trailFrom.put(e.getId(), new double[] {e.getX(), e.getY(), e.getZ()});
			} else {
				double mx = e.getX() - from[0], my = e.getY() - from[1], mz = e.getZ() - from[2];
				double moved = mx * mx + my * my + mz * mz;
				if (moved > 0.55 * 0.55) {
					if (moved < 16) {
						double flat = Math.sqrt(mx * mx + mz * mz);
						trails.addLast(new Trail(from[0], from[1], from[2], radius * 0.85f, now, flat > 1e-3 ? (float) (mx / flat) : 0,
								flat > 1e-3 ? (float) (mz / flat) : 0));
					}
					from[0] = e.getX();
					from[1] = e.getY();
					from[2] = e.getZ();
				}
			}
		}
		while (!trails.isEmpty() && (now - trails.peekFirst().time() > TRAIL_SECONDS || trails.size() > 48)) {
			trails.pollFirst();
		}
		if (trailFrom.size() > 128) {
			trailFrom.clear();
		}
		for (Trail t : trails) {
			float left = (float) (1 - (now - t.time()) / TRAIL_SECONDS);
			out.add(new float[] {(float) (t.x() - camX), (float) (t.y() - camY), (float) (t.z() - camZ), t.radius(), 0.7f * left * left,
					t.dirX(), t.dirZ()});
		}
		out.sort((a, b) -> Float.compare(a[0] * a[0] + a[2] * a[2], b[0] * b[0] + b[2] * b[2]));
		return out;
	}
	private static void render(LevelTerrainRenderContext context, Minecraft mc, RealisticGrass.Look look, double camX, double camY,
			double camZ, double now) {
		List<float[]> pushers = pushers(mc, camX, camY, camZ, now);
		if (meshes.isEmpty()) {
			return;
		}
		pipeline();
		if (config == null) {
			config = RenderSystem.getDevice().createBuffer(() -> "Temple grass config", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
					CONFIG_BYTES);
		}
		float sunAngle = WetGround.sunAngle();
		float sx = (float) -Math.sin(sunAngle), sy = (float) Math.cos(sunAngle);
		float rain = mc.level.getRainLevel(1);
		float day = Math.max(0, Math.min(1, sy * 3 + 0.3f));
		int sky = WetGround.skyColor();
		float seconds = (float) ((System.nanoTime() % 3_600_000_000_000L) / 1e9);
		boolean wet = WetGround.active();
		float lawnEnd = look.distance();
		float plantEnd = look.distance() + PLANT_TILES * TILE - 8;
		CONFIG.clear();
		Std140Builder b = Std140Builder.intoBuffer(CONFIG)
				.putVec4(seconds, look.flow(), look.displacement(), look.shine())
				.putVec4(sy >= 0 ? sx : -sx, Math.abs(sy), 0.15f, day)
				.putVec4(((sky >> 16) & 0xFF) / 255f, ((sky >> 8) & 0xFF) / 255f, (sky & 0xFF) / 255f, rain)
				.putVec4(wet ? 0.55f + 0.45f * rain : 0.35f * rain, wet ? WetGround.coverage() : 0, wet && WetGround.flooded() ? 1 : 0, 0)
				.putVec4(lawnEnd * 0.72f, lawnEnd, plantEnd - 16, plantEnd)
				.putVec4(THIN[0], THIN[1], THIN[2], THIN[3])
				.putVec4(THIN[4], THIN[5], SHARE[1], SHARE[2])
				.putVec4(SHARE[3], WIDTH, mc.gameRenderer.mainRenderTarget().height, 0);
		for (int i = 0; i < MAX_PUSHERS; i++) {
			if (i < pushers.size()) {
				float[] p = pushers.get(i);
				b.putVec4(p[0], p[1], p[2], p[3]);
			} else {
				b.putVec4(0, 0, 0, 1);
			}
		}
		for (int i = 0; i < MAX_PUSHERS; i++) {
			if (i < pushers.size()) {
				float[] p = pushers.get(i);
				b.putVec4(p[4], p[5], p[6], 0);
			} else {
				b.putVec4(0, 0, 0, 0);
			}
		}
		CONFIG.clear();
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.writeToBuffer(config.slice(), CONFIG);
		Matrix4f view = new Matrix4f(context.levelState().cameraRenderState.viewRotationMatrix);
		Vector4f white = new Vector4f(1, 1, 1, 1);
		RenderTarget main = mc.gameRenderer.mainRenderTarget();
		var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		RenderSystem.AutoStorageIndexBuffer indices = Gpu.sequentialBuffer(PrimitiveTopology.QUADS);
		var frustum = context.levelState().cameraRenderState.cullFrustum;
		double reach = plantEnd + 24;
		try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple grass", main.getColorTextureView(), Optional.empty(),
				main.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("Sampler2", mc.gameRenderer.levelLightmap(), sampler);
			pass.setUniform("GrassConfig", config);
			for (int part = 0; part < 2; part++)
			for (Map.Entry<Long, Mesh> e : meshes.entrySet()) {
				Mesh m = e.getValue();
				int first = part == 0 ? 0 : m.groundQuads, count = part == 0 ? m.groundQuads : m.quads - m.groundQuads;
				if (m.buffer == null || count <= 0) {
					continue;
				}
				int cx = (int) (e.getKey() >> 32), cz = e.getKey().intValue();
				double ox = cx * (double) TILE, oz = cz * (double) TILE;
				double nearX = Math.max(ox - camX, Math.max(0, camX - ox - TILE)), nearZ = Math.max(oz - camZ, Math.max(0, camZ - oz - TILE));
				if (nearX * nearX + nearZ * nearZ > reach * reach) {
					continue;
				}
				if (frustum != null && !frustum.isVisible(new AABB(ox - 1, m.minY - 0.5, oz - 1, ox + TILE + 1, m.maxY + 0.5, oz + TILE + 1))) {
					continue;
				}
				Matrix4f data = new Matrix4f();
				data.m20((float) ox);
				data.m22((float) oz);
				data.m30((float) (ox % 512.0));
				data.m32((float) (oz % 512.0));
				GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(view, white,
						new Vector3f((float) (ox - camX), (float) -camY, (float) (oz - camZ)), data);
				pass.setUniform("DynamicTransforms", transform);
				int indexCount = count * 6;
				Gpu.vertices(pass, m.buffer);
				pass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
				Gpu.drawIndexed(pass, indexCount, 0, first * 4);
			}
		}
	}
}
