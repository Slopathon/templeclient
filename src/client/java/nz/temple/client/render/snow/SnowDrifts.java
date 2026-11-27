package nz.temple.client.render.snow;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;
import nz.temple.snow.SnowSim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class SnowDrifts {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/snow-drifts");
	private static final float LIFT = 0.004f;
	private static final long SIM_BUDGET = 600_000L;
	private static final long MESH_BUDGET = 900_000L;
	private static final int P = SnowSim.SIDE + 4;
	private static final VertexFormat FORMAT = DefaultVertexFormat.ENTITY;
	private static final int SURFACE = 0, LIP = 1, PRINT = 2;
	private static final float ALPHA_DEPTH = 0.06f;
	public record Settings(boolean enabled, float rate, float windX, float windZ, int rgb) {
	}
	private static final class ChunkMesh {
		GpuBuffer buffer;
		int quads;
		int version = -1;
		int step = -1;
		double builtAt = -10;
		float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		void close() {
			if (buffer != null) {
				buffer.close();
				buffer = null;
			}
		}
	}
	private static final class Walker {
		double x, z;
		int side = 1;
		double seen;
		double plowX = Double.NaN, plowZ;
	}
	private final Supplier<Settings> settings;
	private final LevelGround ground = new LevelGround();
	private final SnowSim sim = new SnowSim(ground);
	private final Map<Long, ChunkMesh> meshes = new HashMap<>();
	private final Map<Integer, Walker> walkers = new HashMap<>();
	private ClientLevel level;
	private long lastNanos = System.nanoTime();
	private boolean running;
	private GpuBuffer prints;
	private int printQuads;
	private double printsBuiltAt = -10;
	private double printOriginX, printOriginZ;
	private int printCount = -1;
	private boolean failed;

	private static RenderPipeline surfacePipeline;
	private static RenderPipeline printPipeline;
	public SnowDrifts(Supplier<Settings> settings) {
		this.settings = settings;
	}
	public void register() {
		LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(ctx -> nz.temple.client.compat.Gpu.outsidePass(() -> draw(ctx)));
	}
	public static List<RenderPipeline> pipelines() {
		if (surfacePipeline == null) {
			surfacePipeline = build(false);
			printPipeline = build(true);
		}
		return List.of(surfacePipeline, printPipeline);
	}
	private static RenderPipeline build(boolean prints) {
		return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, prints ? "pipeline/snow_prints" : "pipeline/snow_drift"))
				.withVertexShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/snow_drift"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/snow_drift"))
				.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler2").build())
				.withVertexBinding(0, FORMAT)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, !prints))
				.withCull(false)
				.build());
	}

	private void draw(LevelTerrainRenderContext context) {
		Settings s = settings.get();
		Minecraft mc = Minecraft.getInstance();
		boolean on = !failed && s != null && s.enabled() && mc.level != null && mc.player != null;
		Overcast.want(on);
		if (!on || mc.level != level) {
			if (running || mc.level != level) {
				reset();
			}
			level = mc.level;
			ground.level(level);
			if (!on) {
				return;
			}
		}
		running = true;
		try {
			long now = System.nanoTime();
			double dt = Math.min(1.0, (now - lastNanos) / 1e9);
			lastNanos = now;
			ground.invalidate();
			double radius = (mc.options.getEffectiveRenderDistance() + 0.5) * SnowSim.CHUNK;
			long t0 = System.nanoTime();
			sim.step(dt, mc.player.getX(), mc.player.getZ(), radius, s.rate(), s.windX(), s.windZ(), SIM_BUDGET);
			feet(mc);
			long t1 = System.nanoTime();
			var camera = context.levelState().cameraRenderState;
			double cx = camera.pos.x, cy = camera.pos.y, cz = camera.pos.z;
			rebuild(mc, cx, cz, s.rgb());
			long t2 = System.nanoTime();
			render(context, mc, cx, cy, cz, s.rgb());
			profile(t1 - t0, t2 - t1, System.nanoTime() - t2);
		} catch (RuntimeException e) {
			failed = true;
			reset();
			LOGGER.error("Snow drifts disabled", e);
		}
	}
	private final long[] profile = new long[4];
	private long profileFrom = System.nanoTime();
	private void profile(long simNanos, long meshNanos, long drawNanos) {
		if (!Boolean.getBoolean("temple.showcase")) {
			return;
		}
		profile[0] += simNanos;
		profile[1] += meshNanos;
		profile[2] += drawNanos;
		profile[3]++;
		if (System.nanoTime() - profileFrom > 5_000_000_000L) {
			long n = Math.max(1, profile[3]);
			LOGGER.info("TEMPLE_SNOW_PROFILE frames {} sim {} us mesh {} us draw {} us chunks {} meshes {}", n, profile[0] / n / 1000,
					profile[1] / n / 1000, profile[2] / n / 1000, sim.chunks().size(), meshes.size());
			java.util.Arrays.fill(profile, 0);
			profileFrom = System.nanoTime();
		}
	}
	private void reset() {
		sim.clear();
		for (ChunkMesh m : meshes.values()) {
			m.close();
		}
		meshes.clear();
		walkers.clear();
		if (prints != null) {
			prints.close();
			prints = null;
		}
		printQuads = 0;
		printCount = -1;
		running = false;
	}
	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}
	private void feet(Minecraft mc) {
		double now = sim.clock();
		double px = mc.player.getX(), pz = mc.player.getZ();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || !living.onGround() || living.isInWater() || living.isPassenger() || living.isSpectator()) {
				continue;
			}
			double x = e.getX(), z = e.getZ();
			if ((x - px) * (x - px) + (z - pz) * (z - pz) > 48 * 48) {
				continue;
			}
			Walker w = walkers.get(e.getId());
			if (w == null) {
				w = new Walker();
				w.x = x;
				w.z = z;
				walkers.put(e.getId(), w);
			}
			w.seen = now;
			plow(e, w, x, z);
			float scale = (float) Math.max(0.55, Math.min(2.2, e.getBbWidth() / 0.6));
			double dx = x - w.x, dz = z - w.z;
			double moved = Math.sqrt(dx * dx + dz * dz);
			if (moved > 4) {
				w.x = x;
				w.z = z;
				continue;
			}
			if (moved < 0.62 * scale) {
				continue;
			}
			float base = sim.baseAt(x, z);
			if (Float.isNaN(base) || Math.abs(e.getY() - base) > 0.6) {
				w.x = x;
				w.z = z;
				continue;
			}
			double yaw = Math.atan2(-dx, dz);
			double off = SnowSim.FOOT * scale * w.side;
			double fx = x + Math.cos(yaw) * off, fz = z + Math.sin(yaw) * off;
			sim.stamp(fx, fz, yaw, w.side, scale);
			w.side = -w.side;
			w.x = x;
			w.z = z;
		}
		if (walkers.size() > 64) {
			walkers.values().removeIf(w -> now - w.seen > 5);
		}
	}
	private void plow(Entity e, Walker w, double x, double z) {
		if (Double.isNaN(w.plowX)) {
			w.plowX = x;
			w.plowZ = z;
			return;
		}
		double dx = x - w.plowX, dz = z - w.plowZ;
		double moved = Math.sqrt(dx * dx + dz * dz);
		if (moved < 0.06) {
			return;
		}
		w.plowX = x;
		w.plowZ = z;
		float base = sim.baseAt(x, z);
		if (moved > 4 || Float.isNaN(base) || Math.abs(e.getY() - base) > 0.6) {
			return;
		}
		sim.plow(x, z, dx, dz, Math.max(0.2, e.getBbWidth() * 0.5), moved);
	}
	private static int lodStep(int chunks) {
		return chunks <= 2 ? 1 : chunks <= 5 ? 2 : 4;
	}
	private static double refresh(int chunks) {
		return chunks <= 2 ? 6 : chunks <= 5 ? 16 : 40;
	}
	private void rebuild(Minecraft mc, double camX, double camZ, int rgb) {
		long start = System.nanoTime();
		double now = sim.clock();
		int view = mc.options.getEffectiveRenderDistance() + 1;
		int ccx = Math.floorDiv((int) Math.floor(camX), SnowSim.CHUNK), ccz = Math.floorDiv((int) Math.floor(camZ), SnowSim.CHUNK);
		List<SnowSim.Chunk> want = new ArrayList<>();
		for (SnowSim.Chunk c : sim.chunks()) {
			int dist = Math.max(Math.abs(c.cx() - ccx), Math.abs(c.cz() - ccz));
			if (dist > view || Double.isNaN(c.steppedTo())) {
				continue;
			}
			ChunkMesh m = meshes.get(key(c.cx(), c.cz()));
			boolean stale = m == null || m.step != lodStep(dist)
					|| (c.urgent() && m.version != c.version() && now - m.builtAt > 0.1)
					|| now - m.builtAt > refresh(dist);
			if (stale) {
				want.add(c);
			}
		}
		want.sort((a, b) -> {
			if (a.urgent() != b.urgent()) {
				return a.urgent() ? -1 : 1;
			}
			int da = Math.max(Math.abs(a.cx() - ccx), Math.abs(a.cz() - ccz)), db = Math.max(Math.abs(b.cx() - ccx), Math.abs(b.cz() - ccz));
			return Integer.compare(da, db);
		});
		int pressing = 0;
		for (SnowSim.Chunk c : want) {
			int dist = Math.max(Math.abs(c.cx() - ccx), Math.abs(c.cz() - ccz));
			if (System.nanoTime() - start > MESH_BUDGET && !(c.urgent() && dist <= 1 && pressing++ < 2)) {
				break;
			}
			ChunkMesh m = meshes.computeIfAbsent(key(c.cx(), c.cz()), k -> new ChunkMesh());
			m.close();
			m.quads = 0;
			m.step = lodStep(dist);
			mesh(c, m, rgb, m.step);
			m.version = c.version();
			m.builtAt = now;
			c.clearUrgent();
		}
		Iterator<Map.Entry<Long, ChunkMesh>> it = meshes.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, ChunkMesh> e = it.next();
			int cx = (int) (e.getKey() >> 32), cz = e.getKey().intValue();
			if (sim.chunk(cx, cz) == null || Math.abs(cx - ccx) > view + 2 || Math.abs(cz - ccz) > view + 2) {
				e.getValue().close();
				it.remove();
			}
		}
	}
	private static final class Quads {
		float[] pos = new float[1024 * 12];
		int[] colour = new int[1024 * 4];
		float[] uv = new float[1024 * 8];
		int[] kind = new int[1024 * 4];
		int[] light = new int[1024 * 4];
		float[] normal = new float[1024 * 12];
		int count;
		void vertex(int v, float x, float y, float z, int argb, float u0, float u1, int kind, int packedLight, float nx, float ny, float nz) {
			if (v == 0 && (count + 1) * 4 > colour.length) {
				pos = java.util.Arrays.copyOf(pos, pos.length * 2);
				colour = java.util.Arrays.copyOf(colour, colour.length * 2);
				uv = java.util.Arrays.copyOf(uv, uv.length * 2);
				this.kind = java.util.Arrays.copyOf(this.kind, this.kind.length * 2);
				light = java.util.Arrays.copyOf(light, light.length * 2);
				normal = java.util.Arrays.copyOf(normal, normal.length * 2);
			}
			int i = count * 4 + v;
			pos[i * 3] = x;
			pos[i * 3 + 1] = y;
			pos[i * 3 + 2] = z;
			colour[i] = argb;
			uv[i * 2] = u0;
			uv[i * 2 + 1] = u1;
			this.kind[i] = kind;
			light[i] = packedLight;
			normal[i * 3] = nx;
			normal[i * 3 + 1] = ny;
			normal[i * 3 + 2] = nz;
			if (v == 3) {
				count++;
			}
		}
		GpuBuffer upload(String label) {
			if (count == 0) {
				return null;
			}
			int vertices = count * 4;
			try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(vertices * FORMAT.getVertexSize())) {
				BufferBuilder b = Gpu.bufferBuilder(bytes, PrimitiveTopology.QUADS, FORMAT);
				for (int i = 0; i < vertices; i++) {
					int l = light[i];
					b.addVertex(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]).setColor(colour[i]).setUv(uv[i * 2], uv[i * 2 + 1])
							.setUv1(kind[i], 0).setUv2((l & 0xF) << 4, (l >> 4 & 0xF) << 4)
							.setNormal(normal[i * 3], normal[i * 3 + 1], normal[i * 3 + 2]);
				}
				try (MeshData mesh = b.buildOrThrow()) {
					return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
				}
			}
		}
	}
	private void mesh(SnowSim.Chunk c, ChunkMesh m, int rgb, int step) {
		int side = SnowSim.SIDE;
		float cell = SnowSim.CELL;
		float[] pd = new float[P * P];
		float[] pb = new float[P * P];
		float[] pg = new float[P * P];
		float[] pr = new float[P * P];
		int gx0 = c.cx() * side - 2, gz0 = c.cz() * side - 2;
		sim.gather(c.cx(), c.cz(), 2, P, pb, pd, pg, pr);
		int bx0 = c.cx() * SnowSim.CHUNK - 1, bz0 = c.cz() * SnowSim.CHUNK - 1;
		int[] light = new int[18 * 18];
		for (int z = 0; z < 18; z++) {
			for (int x = 0; x < 18; x++) {
				float b = sim.baseAt(bx0 + x + 0.5, bz0 + z + 0.5);
				light[z * 18 + x] = Float.isNaN(b) ? 0xF0 : ground.light(bx0 + x, bz0 + z, b);
			}
		}
		Quads q = new Quads();
		float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, bl = (rgb & 0xFF) / 255f;
		int[][] corners = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
		float[] cd = new float[4];
		float[] cg = new float[4];
		float[] cr = new float[4];
		int[] cc = new int[4];
		float[][] cn = new float[4][3];
		int[] cl = new int[4];
		float[] sum = new float[3];
		for (int z = 0; z < side; z += step) {
			for (int x = 0; x < side; x += step) {
				int px = x + 2, pz = z + 2;
				float b = pb[pz * P + px];
				if (Float.isNaN(b)) {
					continue;
				}
				boolean any = false;
				for (int k = 0; k < 4; k++) {
					int kx = px + corners[k][0] * step, kz = pz + corners[k][1] * step;
					corner(pd, pg, pr, pb, kx, kz, b, sum);
					cd[k] = sum[0];
					cg[k] = sum[1];
					cr[k] = sum[2];
					any |= cd[k] > 0.003f || cg[k] > 0;
				}
				if (!any) {
					continue;
				}
				for (int k = 0; k < 4; k++) {
					int kx = px + corners[k][0] * step, kz = pz + corners[k][1] * step;
					float h = cd[k];
					float e = depthOr(pd, pb, kx + step, kz, b, h), w = depthOr(pd, pb, kx - step, kz, b, h);
					float s = depthOr(pd, pb, kx, kz + step, b, h), n = depthOr(pd, pb, kx, kz - step, b, h);
					float nx = -(e - w) / (2 * cell * step), nz = -(s - n) / (2 * cell * step);
					float len = (float) Math.sqrt(nx * nx + 1 + nz * nz);
					cn[k][0] = nx / len;
					cn[k][1] = 1 / len;
					cn[k][2] = nz / len;
					int walls = 0;
					int blockSum = 0, skySum = 0, lit = 0;
					for (int dz = -1; dz <= 0; dz++) {
						for (int dx = -1; dx <= 0; dx++) {
							int ix = kx + dx, iz = kz + dz;
							if (ix < 0 || iz < 0 || ix >= P || iz >= P) {
								continue;
							}
							float nb = pb[iz * P + ix];
							if (!Float.isNaN(nb) && nb > b + h + 0.05f) {
								walls++;
							}
							if (!Float.isNaN(nb) && Math.abs(nb - b) < 1e-3f) {
								int col = light[(Math.floorDiv(gz0 + iz, SnowSim.CELLS) - bz0) * 18 + Math.floorDiv(gx0 + ix, SnowSim.CELLS) - bx0];
								blockSum += col & 0xF;
								skySum += col >> 4 & 0xF;
								lit++;
							}
						}
					}
					float hollow = Math.max(0, Math.min(0.2f, ((e + w + s + n) / 4 - h) * 3));
					float ao = (1 - 0.13f * walls) * (1 - hollow);
					cc[k] = argb(h / ALPHA_DEPTH, r * ao, g * ao, bl * ao);
					cl[k] = lit == 0 ? 0xF0 : Math.round((float) blockSum / lit) | Math.round((float) skySum / lit) << 4;
				}
				float x0 = x * cell, z0 = z * cell, size = step * cell;
				for (int k = 0; k < 4; k++) {
					q.vertex(k, x0 + corners[k][0] * size, b + cd[k] + LIFT, z0 + corners[k][1] * size, cc[k], cg[k], cr[k], SURFACE, cl[k],
							cn[k][0], cn[k][1], cn[k][2]);
				}
				lip(q, pb, px + step, pz, b, 3, 2, x0 + size, z0, x0 + size, z0 + size, 1, 0, cd, cg, cr, cc, cl);
				lip(q, pb, px - 1, pz, b, 0, 1, x0, z0, x0, z0 + size, -1, 0, cd, cg, cr, cc, cl);
				lip(q, pb, px, pz + step, b, 1, 2, x0, z0 + size, x0 + size, z0 + size, 0, 1, cd, cg, cr, cc, cl);
				lip(q, pb, px, pz - 1, b, 0, 3, x0, z0, x0 + size, z0, 0, -1, cd, cg, cr, cc, cl);
			}
		}
		m.quads = q.count;
		m.minY = Float.MAX_VALUE;
		m.maxY = -Float.MAX_VALUE;
		for (int i = 0; i < q.count * 4; i++) {
			m.minY = Math.min(m.minY, q.pos[i * 3 + 1]);
			m.maxY = Math.max(m.maxY, q.pos[i * 3 + 1]);
		}
		m.maxY += SnowSim.MAX_DEPTH;
		m.buffer = q.upload("Temple snow drift");
	}
	private static void lip(Quads q, float[] pb, int nx, int nz, float b, int ka, int kb, float ax, float az, float bx, float bz,
			float ux, float uz, float[] cd, float[] cg, float[] cr, int[] cc, int[] cl) {
		float nb = nx < 0 || nz < 0 || nx >= P || nz >= P ? b : pb[nz * P + nx];
		if (!Float.isNaN(nb) && nb >= b - 1e-3f || Math.max(cd[ka], cd[kb]) <= 0.003f && cg[ka] <= 0 && cg[kb] <= 0) {
			return;
		}
		int sa = shade(cc[ka], 0.86f), sb = shade(cc[kb], 0.86f);
		q.vertex(0, ax, b + cd[ka] + LIFT, az, sa, cg[ka], cr[ka], LIP, cl[ka], ux, 0, uz);
		q.vertex(1, bx, b + cd[kb] + LIFT, bz, sb, cg[kb], cr[kb], LIP, cl[kb], ux, 0, uz);
		q.vertex(2, bx, b, bz, sb, 0, 0, LIP, cl[kb], ux, 0, uz);
		q.vertex(3, ax, b, az, sa, 0, 0, LIP, cl[ka], ux, 0, uz);
	}
	private static void corner(float[] pd, float[] pg, float[] pr, float[] pb, int kx, int kz, float base, float[] out) {
		float d = 0, g = 0, room = 0;
		int n = 0;
		for (int dz = -1; dz <= 0; dz++) {
			for (int dx = -1; dx <= 0; dx++) {
				int x = kx + dx, z = kz + dz;
				if (x < 0 || z < 0 || x >= P || z >= P) {
					continue;
				}
				int i = z * P + x;
				float b = pb[i];
				if (!Float.isNaN(b) && Math.abs(b - base) < 1e-3f) {
					d += pd[i];
					g += pg[i];
					room += pr[i];
					n++;
				}
			}
		}
		out[0] = n == 0 ? 0 : d / n;
		out[1] = n == 0 ? 0 : g / n;
		out[2] = n == 0 ? 0 : room / n;
	}
	private static float depthOr(float[] pd, float[] pb, int kx, int kz, float base, float fallback) {
		if (kx < 1 || kz < 1 || kx >= P || kz >= P) {
			return fallback;
		}
		float sum = 0;
		int n = 0;
		for (int dz = -1; dz <= 0; dz++) {
			for (int dx = -1; dx <= 0; dx++) {
				int i = (kz + dz) * P + kx + dx;
				float b = pb[i];
				if (!Float.isNaN(b) && Math.abs(b - base) < 1e-3f) {
					sum += pd[i];
					n++;
				}
			}
		}
		return n == 0 ? fallback : sum / n;
	}
	private static int argb(float a, float r, float g, float b) {
		return Math.round(clamp(a) * 255) << 24 | Math.round(clamp(r) * 255) << 16 | Math.round(clamp(g) * 255) << 8 | Math.round(clamp(b) * 255);
	}
	private static int shade(int argb, float k) {
		int r = Math.round(((argb >> 16) & 0xFF) * k), g = Math.round(((argb >> 8) & 0xFF) * k), b = Math.round((argb & 0xFF) * k);
		return argb & 0xFF000000 | r << 16 | g << 8 | b;
	}
	private static float clamp(float v) {
		return Math.max(0, Math.min(1, v));
	}
	private void buildPrints(double camX, double camZ, int rgb) {
		double now = sim.clock();
		int count = sim.prints().size();
		if (count == printCount && now - printsBuiltAt < 0.3) {
			return;
		}
		printCount = count;
		printsBuiltAt = now;
		if (prints != null) {
			prints.close();
			prints = null;
		}
		printOriginX = Math.floor(camX);
		printOriginZ = Math.floor(camZ);
		Quads q = new Quads();
		int colour = rgb & 0xFFFFFF;
		for (SnowSim.Print p : sim.prints()) {
			float fade = sim.printFade(p);
			if (fade < 0.02f) {
				continue;
			}
			double dx = p.x() - camX, dz = p.z() - camZ;
			if (dx * dx + dz * dz > 64 * 64) {
				continue;
			}
			double fx = -Math.sin(p.yaw()), fz = Math.cos(p.yaw());
			double rx = Math.cos(p.yaw()), rz = Math.sin(p.yaw());
			double len = 0.17 * p.size(), wid = len;
			float base = sim.baseAt(p.x(), p.z());
			if (Float.isNaN(base)) {
				continue;
			}
			int light = ground.light((int) Math.floor(p.x()), (int) Math.floor(p.z()), base);
			int a = Math.round(fade * 255) << 24 | colour;
			int n = 4;
			double[] gx = new double[(n + 1) * (n + 1)], gz = new double[(n + 1) * (n + 1)];
			float[] gy = new float[(n + 1) * (n + 1)];
			boolean ok = true;
			for (int j = 0; j <= n && ok; j++) {
				for (int i = 0; i <= n; i++) {
					double u = -1 + 2.0 * i / n, v = -1 + 2.0 * j / n;
					double x = p.x() + rx * wid * u + fx * len * v, z = p.z() + rz * wid * u + fz * len * v;
					float h = surfaceHeight(x, z, base);
					if (Float.isNaN(h)) {
						ok = false;
						break;
					}
					gx[j * (n + 1) + i] = x;
					gz[j * (n + 1) + i] = z;
					gy[j * (n + 1) + i] = h + LIFT * 3;
				}
			}
			if (!ok) {
				continue;
			}
			int[][] k = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
			for (int j = 0; j < n; j++) {
				for (int i = 0; i < n; i++) {
					for (int c = 0; c < 4; c++) {
						int gi = (j + k[c][1]) * (n + 1) + i + k[c][0];
						float u = -1 + 2f * (i + k[c][0]) / n, v = -1 + 2f * (j + k[c][1]) / n;
						q.vertex(c, (float) (gx[gi] - printOriginX), gy[gi], (float) (gz[gi] - printOriginZ), a, u * p.side(), v, PRINT, light,
								0, 1, 0);
					}
				}
			}
		}
		printQuads = q.count;
		prints = q.upload("Temple snow prints");
	}
	private float surfaceHeight(double x, double z, float base) {
		int cells = SnowSim.CELLS;
		int gx = (int) Math.floor(x * cells), gz = (int) Math.floor(z * cells);
		float b = sim.cellBase(gx, gz);
		if (Float.isNaN(b) || Math.abs(b - base) > 1e-3f) {
			return Float.NaN;
		}
		float fx = (float) (x * cells - gx), fz = (float) (z * cells - gz);
		float c00 = cornerAt(gx, gz, b), c10 = cornerAt(gx + 1, gz, b), c01 = cornerAt(gx, gz + 1, b), c11 = cornerAt(gx + 1, gz + 1, b);
		return b + LIFT + (c00 * (1 - fx) + c10 * fx) * (1 - fz) + (c01 * (1 - fx) + c11 * fx) * fz;
	}
	private float cornerAt(int kx, int kz, float base) {
		float sum = 0;
		int n = 0;
		for (int dz = -1; dz <= 0; dz++) {
			for (int dx = -1; dx <= 0; dx++) {
				float b = sim.cellBase(kx + dx, kz + dz);
				if (!Float.isNaN(b) && Math.abs(b - base) < 1e-3f) {
					sum += sim.depthNow(kx + dx, kz + dz);
					n++;
				}
			}
		}
		return n == 0 ? 0 : sum / n;
	}

	private void render(LevelTerrainRenderContext context, Minecraft mc, double camX, double camY, double camZ, int rgb) {
		pipelines();
		buildPrints(camX, camZ, rgb);
		Matrix4f view = new Matrix4f(context.levelState().cameraRenderState.viewRotationMatrix);
		float sunAngle = nz.temple.client.modules.render.WetGround.sunAngle();
		float sx = (float) -Math.sin(sunAngle), sy = (float) Math.cos(sunAngle);
		float overcast = mc.level.getRainLevel(1);
		float day = Math.max(0, Math.min(1, sy * 3 + 0.3f)) * (1 - 0.6f * overcast);
		Vector4f white = new Vector4f(1, 1, 1, 1);
		double now = sim.clock();
		RenderTarget main = mc.gameRenderer.mainRenderTarget();
		var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		RenderSystem.AutoStorageIndexBuffer indices = Gpu.sequentialBuffer(PrimitiveTopology.QUADS);
		try (RenderPass pass = Gpu.openPass(RenderSystem.getDevice().createCommandEncoder(), () -> "Temple snow drifts",
				main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(surfacePipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("Sampler2", mc.gameRenderer.levelLightmap(), sampler);
			var frustum = context.levelState().cameraRenderState.cullFrustum;
			for (Map.Entry<Long, ChunkMesh> e : meshes.entrySet()) {
				ChunkMesh m = e.getValue();
				if (m.buffer == null || m.quads == 0) {
					continue;
				}
				int cx = (int) (e.getKey() >> 32), cz = e.getKey().intValue();
				double ox = cx * (double) SnowSim.CHUNK, oz = cz * (double) SnowSim.CHUNK;
				if (frustum != null && !frustum.isVisible(new net.minecraft.world.phys.AABB(ox, m.minY - 0.1, oz,
						ox + SnowSim.CHUNK, m.maxY + 0.1, oz + SnowSim.CHUNK))) {
					continue;
				}
				drawBuffer(pass, indices, view, white, m.buffer, m.quads, ox - camX, -camY, oz - camZ, ox, oz, sx, sy, day, overcast,
						(float) (now - m.builtAt));
			}
			if (prints != null && printQuads > 0) {
				pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(printPipeline));
				RenderSystem.bindDefaultUniforms(pass);
				pass.bindTexture("Sampler2", mc.gameRenderer.levelLightmap(), sampler);
				drawBuffer(pass, indices, view, white, prints, printQuads, printOriginX - camX, -camY, printOriginZ - camZ,
						printOriginX, printOriginZ, sx, sy, day, overcast, 0);
			}
		}
	}
	private static void drawBuffer(RenderPass pass, RenderSystem.AutoStorageIndexBuffer indices, Matrix4f view, Vector4f colour,
			GpuBuffer buffer, int quads, double offX, double offY, double offZ, double originX, double originZ,
			float sunX, float sunY, float day, float overcast, float age) {
		Matrix4f data = new Matrix4f();
		data.m00(sunX);
		data.m01(sunY);
		data.m02(0);
		data.m03(day);
		data.m10(overcast);
		data.m11(age);
		data.m30((float) (originX % 512.0));
		data.m31(0);
		data.m32((float) (originZ % 512.0));
		data.m33(1);
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
				.writeTransform(view, colour, new Vector3f((float) offX, (float) offY, (float) offZ), data);
		pass.setUniform("DynamicTransforms", transform);
		int indexCount = quads * 6;
		Gpu.vertices(pass, buffer);
		pass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
		Gpu.drawIndexed(pass, indexCount, 0, 0);
	}
}
