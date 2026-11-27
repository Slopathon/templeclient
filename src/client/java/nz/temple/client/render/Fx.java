package nz.temple.client.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class Fx {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/fx");
	private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);
	private static final Vector3f NO_OFFSET = new Vector3f();
	private static final Matrix4f IDENTITY_TEX = new Matrix4f();
	private static final int MAX_QUADS_PER_FRAME = 60_000;
	@FunctionalInterface
	public interface Source {
		void emit(Fx fx, float partialTick);
	}
	private final List<Source> sources = new ArrayList<>();
	private final List<nz.temple.util.Perf.Section> timers = new ArrayList<>();
	private final Meshes meshes = new Meshes();
	private final Layer depthLayer = new Layer(false, false);
	private final Layer throughLayer = new Layer(true, false);
	private final Layer depthGlowLayer = new Layer(false, true);
	private final Layer throughGlowLayer = new Layer(true, true);
	private final Layer bloomLayer = new Layer(false, true, true);
	private boolean bloom;
	private Layer layer = depthLayer;
	private boolean through;
	private boolean additive;
	private int quadsThisFrame;
	private Matrix4fc pose;
	private double camX;
	private double camY;
	private double camZ;
	private final Vector3f right = new Vector3f();
	private final Vector3f up = new Vector3f();
	private final Vector3f forward = new Vector3f();
	private boolean failed;
	private static final VertexFormat FORMAT = VertexFormat.builder(0)
			.addAttribute("Position", com.mojang.blaze3d.GpuFormat.RGB32_FLOAT)
			.addAttribute("UV0", com.mojang.blaze3d.GpuFormat.RG32_FLOAT)
			.addAttribute("Color", com.mojang.blaze3d.GpuFormat.RGBA8_UNORM)
			.build();
	private static final int OFF_POS = FORMAT.getElement("Position").offset();
	private static final int OFF_UV = FORMAT.getElement("UV0").offset();
	private static final int OFF_COLOR = FORMAT.getElement("Color").offset();
	private static final class Layer {
		final boolean through;
		final boolean additive;
		final boolean bloom;
		RenderPipeline pipeline;
		VertexRing buffer;
		Layer(boolean through, boolean additive) {
			this(through, additive, false);
		}

		Layer(boolean through, boolean additive, boolean bloom) {
			this.bloom = bloom;
			this.through = through;
			this.additive = additive;
		}
	}
	private void meshBloom(double ax, double ay, double az, int ca, double bx, double by, double bz, int cb,
			double cx, double cy, double cz, int cc, double dx, double dy, double dz, int cd) {
		Layer previous = layer;
		layer = bloomLayer;
		quad(ax, ay, az, ca, bx, by, bz, cb, cx, cy, cz, cc, dx, dy, dz, cd);
		layer = previous;
	}
	public void register() {
		meshes.bloomSink(this::meshBloom);

		LevelRenderEvents.AFTER_SOLID_FEATURES.register(ctx -> nz.temple.client.compat.Gpu.outsidePass(() -> draw(ctx)));
	}
	public void addSource(String name, Source source) {
		sources.add(source);
		timers.add(nz.temple.util.Perf.section("fx:" + name));
	}
	public void throughWalls(boolean through) {
		this.through = through;
		select();
	}
	public void additive(boolean additive) {
		this.additive = additive;
		select();
	}
	public void bloom(boolean bloom) {
		this.bloom = bloom;
		select();
	}
	private void select() {
		layer = bloom ? bloomLayer : through ? (additive ? throughGlowLayer : throughLayer) : (additive ? depthGlowLayer : depthLayer);
	}
	public Meshes mesh() {
		return meshes;
	}
	public Vec3 cameraPos() {
		return new Vec3(camX, camY, camZ);
	}
	public Vec3 cameraRight() {
		return new Vec3(right.x, right.y, right.z);
	}
	public Vec3 cameraUp() {
		return new Vec3(up.x, up.y, up.z);
	}
	private static final nz.temple.util.Perf.Section SUBMIT = nz.temple.util.Perf.section("fx submit");
	private static int frame;
	public static int frame() {
		return frame;
	}
	public static double buildNanos;
	public static double submitNanos;
	private void draw(LevelRenderContext context) {
		if (failed || sources.isEmpty()) {
			return;
		}
		try {
			var camera = context.levelState().cameraRenderState;
			camX = camera.pos.x;
			camY = camera.pos.y;
			camZ = camera.pos.z;
			Quaternionf rotation = camera.orientation;
			rotation.transform(right.set(1, 0, 0));
			rotation.transform(up.set(0, 1, 0));
			rotation.transform(forward.set(0, 0, -1));
			pose = context.poseStack().last().pose();
			float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			quadsThisFrame = 0;
			meshes.begin(pose, camX, camY, camZ);
			nz.temple.util.Perf.frame();
			frame++;
			long t0 = System.nanoTime();
			for (int i = 0; i < sources.size(); i++) {
				Source source = sources.get(i);
				through = false;
				additive = false;
				bloom = false;
				select();
				long began = timers.get(i).begin();
				try {
					source.emit(this, partial);
				} catch (RuntimeException e) {
					LOGGER.error("FX source failed", e);
				}
				timers.get(i).end(began);
			}
			nz.temple.client.cosmetic.Anchor.nextFrame();
			long t1 = System.nanoTime();
			meshes.flush();
			flush(depthLayer);
			flush(depthGlowLayer);
			flush(throughLayer);
			flush(throughGlowLayer);
			flush(bloomLayer);
			long t2 = System.nanoTime();
			buildNanos += ((t1 - t0) - buildNanos) * 0.05;
			submitNanos += ((t2 - t1) - submitNanos) * 0.05;
			SUBMIT.end(SUBMIT.begin() - (t2 - t1));
		} catch (RuntimeException e) {
			failed = true;
			LOGGER.error("FX rendering failed; disabling glow effects", e);
		}
	}
	private VertexRing ring() {
		Layer l = layer;
		if (l.buffer == null) {
			ensure(l);
		}
		return l.buffer;
	}
	public List<RenderPipeline> pipelines() {
		List<RenderPipeline> out = new ArrayList<>();
		for (Layer l : new Layer[] {depthLayer, throughLayer, depthGlowLayer, throughGlowLayer, bloomLayer}) {
			ensure(l);
			out.add(l.pipeline);
		}
		return out;
	}
	private static void ensure(Layer l) {
		{
			if (l.pipeline == null) {
				String name = "pipeline/fx" + (l.through ? "_through" : "") + (l.additive ? "_glow" : "") + (l.bloom ? "_bloom" : "");
				l.pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
						.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, name))
						.withVertexShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/fx_soft"))
						.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/fx_soft"))
						.withVertexBinding(0, FORMAT)
						.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
						.withColorTargetState(new ColorTargetState(l.additive ? BlendFunction.LIGHTNING : BlendFunction.TRANSLUCENT))

						.withDepthStencilState(l.through ? Optional.empty()
								: Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)))
						.withCull(false)
						.build());
				l.buffer = new VertexRing(() -> "Temple.nz FX", Gpu.vertexFormat(l.pipeline), Gpu.topology(l.pipeline),
						RenderType.SMALL_BUFFER_SIZE);
			}
		}
	}
	private final Matrix4f viewMatrix = new Matrix4f();
	private void flush(Layer l) {
		if (l.buffer == null || !l.buffer.building()) {
			return;
		}
		VertexRing.Upload info = l.buffer.upload();
		if (info != null) {
			GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
					.writeTransform(viewMatrix.set(Gpu.modelView()).mul(pose), WHITE, NO_OFFSET, IDENTITY_TEX);
			RenderTarget main = l.bloom ? GlowBuffer.active() : Minecraft.getInstance().gameRenderer.mainRenderTarget();
			if (main != null) {
			try (RenderPass pass = Gpu.openPass(RenderSystem.getDevice().createCommandEncoder(), () -> "Temple.nz FX",
					main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
				pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(l.pipeline));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transforms);
				Gpu.vertices(pass, info.vertices());
				pass.setIndexBuffer(info.indices(), info.indexType());
				Gpu.drawIndexed(pass, info.indexCount(), 0, 0);
			}
			}
		}
	}
	private void vertex(VertexRing vc, double x, double y, double z, int argb) {
		vertex(vc, x, y, z, argb, 0, 0);
	}
	private void vertex(VertexRing vc, double x, double y, double z, int argb, float u, float v) {
		long p = vc.vertex();
		MemoryUtil.memPutFloat(p + OFF_POS, (float) (x - camX));
		MemoryUtil.memPutFloat(p + OFF_POS + 4, (float) (y - camY));
		MemoryUtil.memPutFloat(p + OFF_POS + 8, (float) (z - camZ));
		MemoryUtil.memPutFloat(p + OFF_UV, u);
		MemoryUtil.memPutFloat(p + OFF_UV + 4, v);
		MemoryUtil.memPutByte(p + OFF_COLOR, (byte) (argb >> 16));
		MemoryUtil.memPutByte(p + OFF_COLOR + 1, (byte) (argb >> 8));
		MemoryUtil.memPutByte(p + OFF_COLOR + 2, (byte) argb);
		MemoryUtil.memPutByte(p + OFF_COLOR + 3, (byte) (argb >>> 24));
	}

	public void screenFlash(int argb) {
		if ((argb >>> 24) == 0) {
			return;
		}
		boolean previous = additive;
		additive(true);
		double d = 0.2, h = 1.5;
		double cx = camX + forward.x * d, cy = camY + forward.y * d, cz = camZ + forward.z * d;
		double rx = right.x * h, ry = right.y * h, rz = right.z * h;
		double ux = up.x * h, uy = up.y * h, uz = up.z * h;
		quad(cx - rx - ux, cy - ry - uy, cz - rz - uz, argb, cx + rx - ux, cy + ry - uy, cz + rz - uz, argb,
				cx + rx + ux, cy + ry + uy, cz + rz + uz, argb, cx - rx + ux, cy - ry + uy, cz - rz + uz, argb);
		additive(previous);
	}
	public void quad(double ax, double ay, double az, int ca, double bx, double by, double bz, int cb,
			double cx, double cy, double cz, int cc, double dx, double dy, double dz, int cd) {
		if (((ca | cb | cc | cd) >>> 24) == 0) {
			return;
		}
		if (++quadsThisFrame > MAX_QUADS_PER_FRAME) {
			return;
		}
		VertexRing vc = ring();
		vertex(vc, ax, ay, az, ca);
		vertex(vc, bx, by, bz, cb);
		vertex(vc, cx, cy, cz, cc);
		vertex(vc, dx, dy, dz, cd);
	}
	private void triangle(double ax, double ay, double az, int ca, double bx, double by, double bz, int cb,
			double cx, double cy, double cz, int cc) {
		quad(ax, ay, az, ca, bx, by, bz, cb, cx, cy, cz, cc, ax, ay, az, ca);
	}
	public void glow(double x, double y, double z, double radius, int argb) {
		sprite(x, y, z, radius, radius, 0, argb, DOT, 0);
	}
	private static final int DOT = 1;
	private static final int RIBBON = 2;
	private static final int SPARKLE = 3;
	private static final int SMOKE = 4;
	private static final int RING = 5;
	private static final int DISC = 6;
	private static final int STREAK = 7;

	private void shaped(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz,
			double dx, double dy, double dz, int ca, int cb, int cc, int cd, int kind, int cell) {
		if (((ca | cb | cc | cd) >>> 24) == 0 || ++quadsThisFrame > MAX_QUADS_PER_FRAME) {
			return;
		}
		float k = kind * 4f;
		float c = Math.max(0, Math.min(255, cell)) * 4f;
		VertexRing vc = ring();
		vertex(vc, ax, ay, az, ca, k - 1, c - 1);
		vertex(vc, bx, by, bz, cb, k + 1, c - 1);
		vertex(vc, cx, cy, cz, cc, k + 1, c + 1);
		vertex(vc, dx, dy, dz, cd, k - 1, c + 1);
	}
	private void sprite(double x, double y, double z, double rx, double ry, double spin, int argb, int kind, int cell) {
		if ((argb >>> 24) == 0 || rx <= 0 || ry <= 0) {
			return;
		}
		double cs = Math.cos(spin), sn = Math.sin(spin);
		double ux = (right.x * cs + up.x * sn) * rx, uy = (right.y * cs + up.y * sn) * rx, uz = (right.z * cs + up.z * sn) * rx;
		double vx = (-right.x * sn + up.x * cs) * ry, vy = (-right.y * sn + up.y * cs) * ry, vz = (-right.z * sn + up.z * cs) * ry;
		shaped(x - ux - vx, y - uy - vy, z - uz - vz, x + ux - vx, y + uy - vy, z + uz - vz,
				x + ux + vx, y + uy + vy, z + uz + vz, x - ux + vx, y - uy + vy, z - uz + vz, argb, argb, argb, argb, kind, cell);
	}
	public void light(double x, double y, double z, double radius, int argb, double core) {
		sprite(x, y, z, radius, radius, 0, argb, DOT, (int) Math.round(Math.max(0, Math.min(1, core)) * 8));
	}
	public void sparkle(double x, double y, double z, double radius, int argb, double spin) {
		sprite(x, y, z, radius, radius, spin, argb, SPARKLE, 7);
	}
	public void smoke(double x, double y, double z, double radius, int argb, double seed, double spin) {
		int cell = (int) Math.floorMod(Math.round(seed * 97.0), 250L) + 1;
		sprite(x, y, z, radius, radius, spin, argb, SMOKE, cell);
	}
	public void softRing(double x, double y, double z, double radius, int argb, double core) {
		sprite(x, y, z, radius, radius, 0, argb, RING, (int) Math.round(Math.max(0, Math.min(1, core)) * 8));
	}

	public void softRing(double x, double y, double z, Vec3 a, Vec3 b, double radius, int argb, double core) {
		plane(x, y, z, a, b, radius, argb, RING, (int) Math.round(Math.max(0, Math.min(1, core)) * 8));
	}
	public void softPool(double x, double y, double z, Vec3 a, Vec3 b, double radius, int argb) {
		plane(x, y, z, a, b, radius, argb, DOT, 0);
	}
	public void disc(double x, double y, double z, Vec3 a, Vec3 b, double rx, double ry, int argb) {
		double ax = a.x * rx, ay = a.y * rx, az = a.z * rx, bx = b.x * ry, by = b.y * ry, bz = b.z * ry;
		shaped(x - ax - bx, y - ay - by, z - az - bz, x + ax - bx, y + ay - by, z + az - bz, x + ax + bx, y + ay + by, z + az + bz,
				x - ax + bx, y - ay + by, z - az + bz, argb, argb, argb, argb, DISC, 0);
	}
	private void plane(double x, double y, double z, Vec3 a, Vec3 b, double radius, int argb, int kind, int cell) {
		double ax = a.x * radius, ay = a.y * radius, az = a.z * radius, bx = b.x * radius, by = b.y * radius, bz = b.z * radius;
		shaped(x - ax - bx, y - ay - by, z - az - bz, x + ax - bx, y + ay - by, z + az - bz, x + ax + bx, y + ay + by, z + az + bz,
				x - ax + bx, y - ay + by, z - az + bz, argb, argb, argb, argb, kind, cell);
	}

	public void streak(double x, double y, double z, double dx, double dy, double dz, double length, double width, int argb) {
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-7 || (argb >>> 24) == 0) {
			return;
		}
		double hx = dx / len * length / 2, hy = dy / len * length / 2, hz = dz / len * length / 2;
		double tx = x - camX, ty = y - camY, tz = z - camZ;
		double sx = hy * tz - hz * ty, sy = hz * tx - hx * tz, sz = hx * ty - hy * tx;
		double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (sl < 1e-9) {
			return;
		}
		double k = width / 2 / sl;
		sx *= k;
		sy *= k;
		sz *= k;
		shaped(x - hx - sx, y - hy - sy, z - hz - sz, x + hx - sx, y + hy - sy, z + hz - sz, x + hx + sx, y + hy + sy, z + hz + sz,
				x - hx + sx, y - hy + sy, z - hz + sz, argb, argb, argb, argb, STREAK, 6);
	}
	public void ribbon(double[] path, int count, double[] width, int[] colours, double core) {
		if (count < 2) {
			return;
		}
		int sub = count < 12 ? 4 : count < 40 ? 2 : 1;
		int n = (count - 1) * sub + 1;
		double[] p = new double[n * 3];
		double[] w = new double[n];
		int[] c = new int[n];
		for (int i = 0; i < count - 1; i++) {
			int i0 = Math.max(0, i - 1), i2 = i + 1, i3 = Math.min(count - 1, i + 2);
			for (int s = 0; s < sub; s++) {
				double t = (double) s / sub;
				int o = i * sub + s;
				for (int k = 0; k < 3; k++) {
					p[o * 3 + k] = catmull(path[i0 * 3 + k], path[i * 3 + k], path[i2 * 3 + k], path[i3 * 3 + k], t);
				}
				w[o] = width[i] + (width[i2] - width[i]) * t;
				c[o] = lerp(colours[i], colours[i2], t);
			}
		}
		System.arraycopy(path, (count - 1) * 3, p, (n - 1) * 3, 3);
		w[n - 1] = width[count - 1];
		c[n - 1] = colours[count - 1];
		int cell = (int) Math.round(Math.max(0, Math.min(1, core)) * 8);
		double pax = 0, pay = 0, paz = 0, pbx = 0, pby = 0, pbz = 0;
		for (int i = 0; i < n; i++) {
			int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
			double tx = p[b * 3] - p[a * 3], ty = p[b * 3 + 1] - p[a * 3 + 1], tz = p[b * 3 + 2] - p[a * 3 + 2];
			double vx = p[i * 3] - camX, vy = p[i * 3 + 1] - camY, vz = p[i * 3 + 2] - camZ;
			double sx = ty * vz - tz * vy, sy = tz * vx - tx * vz, sz = tx * vy - ty * vx;
			double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (sl < 1e-9) {
				sx = right.x;
				sy = right.y;
				sz = right.z;
				sl = 1;
			}
			double k = w[i] / 2 / sl;
			double ax = p[i * 3] + sx * k, ay = p[i * 3 + 1] + sy * k, az = p[i * 3 + 2] + sz * k;
			double bx = p[i * 3] - sx * k, by = p[i * 3 + 1] - sy * k, bz = p[i * 3 + 2] - sz * k;
			if (i > 0) {
				shaped(pbx, pby, pbz, bx, by, bz, ax, ay, az, pax, pay, paz, c[i - 1], c[i], c[i], c[i - 1], RIBBON, cell);
			}
			pax = ax;
			pay = ay;
			paz = az;
			pbx = bx;
			pby = by;
			pbz = bz;
		}
	}
	public void ribbon(double[] path, int count, double headWidth, double tailWidth, int head, int tail, double core) {
		double[] w = new double[count];
		int[] c = new int[count];
		for (int i = 0; i < count; i++) {
			double f = count == 1 ? 1 : (double) i / (count - 1);
			w[i] = tailWidth + (headWidth - tailWidth) * f;
			c[i] = lerp(tail, head, f);
		}
		ribbon(path, count, w, c, core);
	}
	private static double catmull(double p0, double p1, double p2, double p3, double t) {
		double t2 = t * t, t3 = t2 * t;
		return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
	}
	public void orb(double x, double y, double z, double radius, int argb) {
		boolean previous = additive;
		additive(true);
		glow(x, y, z, radius, withAlpha(argb, alpha(argb) * 0.45f));
		additive(previous);
		glow(x, y, z, radius * 0.38, argb);
	}
	private static double noise(double seed, int index) {
		double v = Math.sin(seed * 12.9898 + index * 78.233) * 43758.5453;
		return (v - Math.floor(v)) * 2 - 1;
	}
	public void shard(double x, double y, double z, double dx, double dy, double dz, double length, double width, int base, int tip) {
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-6 || length <= 0) {
			return;
		}
		dx /= len;
		dy /= len;
		dz /= len;
		double tx = x - camX, ty = y - camY, tz = z - camZ;
		double sx = dy * tz - dz * ty, sy = dz * tx - dx * tz, sz = dx * ty - dy * tx;
		double slen = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (slen < 1e-6) {
			return;
		}
		double k = width / 2 / slen;
		sx *= k;
		sy *= k;
		sz *= k;
		double ex = x + dx * length, ey = y + dy * length, ez = z + dz * length;
		quad(x - sx, y - sy, z - sz, base, x + sx, y + sy, z + sz, base, ex, ey, ez, tip, ex, ey, ez, tip);
	}
	public void arc(double ax, double ay, double az, double bx, double by, double bz, int segments, double jitter,
			double width, int ca, int cb, double seed) {
		if (segments < 1) {
			return;
		}
		double dx = bx - ax, dy = by - ay, dz = bz - az;
		double tx = (ax + bx) / 2 - camX, ty = (ay + by) / 2 - camY, tz = (az + bz) / 2 - camZ;
		double px = dy * tz - dz * ty, py = dz * tx - dx * tz, pz = dx * ty - dy * tx;
		double plen = Math.sqrt(px * px + py * py + pz * pz);
		if (plen < 1e-6) {
			px = 0;
			py = 1;
			pz = 0;
			plen = 1;
		}
		px /= plen;
		py /= plen;
		pz /= plen;
		double lx = ax, ly = ay, lz = az;
		for (int i = 1; i <= segments; i++) {
			double t = (double) i / segments;
			double off = i == segments ? 0 : noise(seed, i) * jitter;
			double nx = ax + dx * t + px * off;
			double ny = ay + dy * t + py * off;
			double nz = az + dz * t + pz * off;
			softBeam(lx, ly, lz, nx, ny, nz, width, lerp(ca, cb, t - 1.0 / segments), lerp(ca, cb, t));
			lx = nx;
			ly = ny;
			lz = nz;
		}
	}
	public void helix(double x, double y, double z, double radius, double height, double turns, int segments,
			double bandWidth, int start, int end, double phase) {
		double previousX = 0, previousY = 0, previousZ = 0;
		for (int i = 0; i <= segments; i++) {
			double t = (double) i / segments;
			double angle = phase + t * turns * Math.PI * 2;
			double r = radius * (1 - t * 0.35);
			double cx = x + Math.cos(angle) * r;
			double cy = y + height * t;
			double cz = z + Math.sin(angle) * r;
			if (i > 0) {
				int c0 = lerp(start, end, t - 1.0 / segments);
				int c1 = lerp(start, end, t);
				quad(previousX, previousY, previousZ, c0, cx, cy, cz, c1,
						cx, cy + bandWidth, cz, c1 & 0xFFFFFF, previousX, previousY + bandWidth, previousZ, c0 & 0xFFFFFF);
			}
			previousX = cx;
			previousY = cy;
			previousZ = cz;
		}
	}
	public void cracks(double x, double y, double z, int count, double length, double width, int inner, int outer, double seed) {
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2 * i / count + noise(seed, i) * 0.4;
			double len = length * (0.55 + 0.45 * Math.abs(noise(seed, i + 31)));
			double px = x, pz = z;
			int steps = 3;
			for (int s = 1; s <= steps; s++) {
				double t = (double) s / steps;
				double wobble = noise(seed, i * 7 + s) * 0.25 * len;
				double nx = x + Math.cos(angle) * len * t + Math.cos(angle + 1.5708) * wobble;
				double nz = z + Math.sin(angle) * len * t + Math.sin(angle + 1.5708) * wobble;
				softBeam(px, y, pz, nx, y, nz, width * (1 - t * 0.6), lerp(inner, outer, t - 1.0 / steps), lerp(inner, outer, t));
				px = nx;
				pz = nz;
			}
		}
	}
	public void dome(double x, double y, double z, double radius, double height, int rings, int segments, int bottom, int top) {
		for (int r = 0; r < rings; r++) {
			double v0 = (double) r / rings;
			double v1 = (double) (r + 1) / rings;
			double r0 = radius * Math.cos(v0 * Math.PI / 2);
			double r1 = radius * Math.cos(v1 * Math.PI / 2);
			double y0 = y + height * Math.sin(v0 * Math.PI / 2);
			double y1 = y + height * Math.sin(v1 * Math.PI / 2);
			int c0 = lerp(bottom, top, v0);
			int c1 = lerp(bottom, top, v1);
			for (int i = 0; i < segments; i++) {
				double a0 = Math.PI * 2 * i / segments;
				double a1 = Math.PI * 2 * (i + 1) / segments;
				quad(x + Math.cos(a0) * r0, y0, z + Math.sin(a0) * r0, c0,
						x + Math.cos(a1) * r0, y0, z + Math.sin(a1) * r0, c0,
						x + Math.cos(a1) * r1, y1, z + Math.sin(a1) * r1, c1,
						x + Math.cos(a0) * r1, y1, z + Math.sin(a0) * r1, c1);
			}
		}
	}
	public void wisp(double x, double y, double z, double radius, int argb, double seed) {
		for (int i = 0; i < 3; i++) {
			double ox = noise(seed, i) * radius * 0.45;
			double oy = noise(seed, i + 11) * radius * 0.3;
			double oz = noise(seed, i + 23) * radius * 0.45;
			double r = radius * (0.6 + 0.4 * Math.abs(noise(seed, i + 41)));
			smoke(x + ox, y + oy, z + oz, r, withAlpha(argb, alpha(argb) * 0.6f), seed * 3 + i, noise(seed, i + 57) * Math.PI);
		}
	}
	public void ellipse(double x, double y, double z, double rx, double ry, int center, int edge, int segments) {
		double px = x + right.x * rx, py = y + right.y * rx, pz = z + right.z * rx;
		for (int i = 1; i <= segments; i++) {
			double a = Math.PI * 2 * i / segments;
			double c = Math.cos(a) * rx, s = Math.sin(a) * ry;
			double qx = x + right.x * c + up.x * s, qy = y + right.y * c + up.y * s, qz = z + right.z * c + up.z * s;
			triangle(x, y, z, center, px, py, pz, edge, qx, qy, qz, edge);
			px = qx;
			py = qy;
			pz = qz;
		}
	}
	public void coin(double x, double y, double z, double radius, double spin, int argb) {
		float a = alpha(argb);
		if (a <= 0) {
			return;
		}
		double cos = Math.cos(spin);
		double squash = Math.max(0.1, Math.abs(cos));
		int rim = lerp(argb | 0xFF000000, 0xFF3A2400, 0.35);
		int face = lerp(argb | 0xFF000000, 0xFFFFFFFF, 0.35);
		boolean previousBlend = additive;
		additive(true);
		glow(x, y, z, radius * 2.8, withAlpha(argb, a * 0.3f));
		additive(previousBlend);
		ellipse(x, y, z, radius * squash, radius, withAlpha(rim, a * 0.95f), withAlpha(rim, a * 0.8f), 20);
		ellipse(x, y, z, radius * 0.78 * squash, radius * 0.78, withAlpha(face, a), withAlpha(argb, a * 0.7f), 20);
		ellipse(x, y, z, radius * 0.34 * squash, radius * 0.42, withAlpha(0xFFFFFFFF, a * 0.55f * (float) squash), 0xFFFFFF, 12);
		if (cos > 0.25) {
			double sweep = Math.sin(spin * 1.5) * 0.45;
			double ox = right.x * radius * squash * sweep, oy = right.y * radius * squash * sweep, oz = right.z * radius * squash * sweep;
			double hx = up.x * radius * 0.7, hy = up.y * radius * 0.7, hz = up.z * radius * 0.7;
			int shine = withAlpha(0xFFFFFFFF, a * 0.6f * (float) cos);
			softBeam(x + ox - hx, y + oy - hy, z + oz - hz, x + ox + hx, y + oy + hy, z + oz + hz, radius * 0.22 * squash, shine, shine);
		}
	}
	public void star(double x, double y, double z, double radius, int argb, double spin) {
		sparkle(x, y, z, radius, argb, spin);
		boolean previous = additive;
		additive(true);
		glow(x, y, z, radius * 0.55, withAlpha(argb, alpha(argb) * 0.5f));
		additive(previous);
	}

	public void ring(double x, double y, double z, double r0, double r1, int inner, int outer) {
		ring(x, y, z, r0, r1, inner, outer, 48);
	}
	public void ring(double x, double y, double z, double r0, double r1, int inner, int outer, int segments) {
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
			quad(x + c0 * r0, y, z + s0 * r0, inner, x + c1 * r0, y, z + s1 * r0, inner,
					x + c1 * r1, y, z + s1 * r1, outer, x + c0 * r1, y, z + s0 * r1, outer);
		}
	}
	public void wall(double x, double y0, double y1, double z, double r, double from, double sweep, int segments,
			java.util.function.IntUnaryOperator bottom, java.util.function.IntUnaryOperator top) {
		for (int i = 0; i < segments; i++) {
			double a0 = from + sweep * i / segments;
			double a1 = from + sweep * (i + 1) / segments;
			double c0 = Math.cos(a0) * r, s0 = Math.sin(a0) * r, c1 = Math.cos(a1) * r, s1 = Math.sin(a1) * r;
			quad(x + c0, y0, z + s0, bottom.applyAsInt(i), x + c1, y0, z + s1, bottom.applyAsInt(i + 1),
					x + c1, y1, z + s1, top.applyAsInt(i + 1), x + c0, y1, z + s0, top.applyAsInt(i));
		}
	}
	public void beam(double ax, double ay, double az, double bx, double by, double bz, double width, int ca, int cb) {
		double dx = bx - ax, dy = by - ay, dz = bz - az;
		double tx = (ax + bx) / 2 - camX, ty = (ay + by) / 2 - camY, tz = (az + bz) / 2 - camZ;
		double sx = dy * tz - dz * ty, sy = dz * tx - dx * tz, sz = dx * ty - dy * tx;
		double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1e-7) {
			return;
		}
		double k = width / 2 / len;
		sx *= k;
		sy *= k;
		sz *= k;
		quad(ax - sx, ay - sy, az - sz, ca, bx - sx, by - sy, bz - sz, cb, bx + sx, by + sy, bz + sz, cb, ax + sx, ay + sy, az + sz, ca);
	}

	public void softBeam(double ax, double ay, double az, double bx, double by, double bz, double width, int ca, int cb) {
		double dx = bx - ax, dy = by - ay, dz = bz - az;
		double tx = (ax + bx) / 2 - camX, ty = (ay + by) / 2 - camY, tz = (az + bz) / 2 - camZ;
		double sx = dy * tz - dz * ty, sy = dz * tx - dx * tz, sz = dx * ty - dy * tx;
		double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1e-7) {
			return;
		}
		double k = width / 2 / len;
		sx *= k;
		sy *= k;
		sz *= k;
		shaped(ax - sx, ay - sy, az - sz, bx - sx, by - sy, bz - sz, bx + sx, by + sy, bz + sz, ax + sx, ay + sy, az + sz,
				ca, cb, cb, ca, RIBBON, 0);
	}
	public static int alpha(int argb) {
		return (argb >>> 24) & 0xFF;
	}
	public static int withAlpha(int argb, float alpha) {
		return (Math.max(0, Math.min(255, Math.round(alpha * Theme.intensity()))) << 24) | (argb & 0xFFFFFF);
	}
	public static int fade(int argb, double factor) {
		return withAlpha(argb, (float) (alpha(argb) * Math.max(0, Math.min(1, factor))));
	}
	public static int lerp(int from, int to, double t) {
		float u = (float) Math.max(0, Math.min(1, t));
		int a = Math.round(alpha(from) + (alpha(to) - alpha(from)) * u);
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * u);
		int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * u);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * u);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
	public static int hue(double h) {
		return 0xFF000000 | (java.awt.Color.HSBtoRGB((float) (h - Math.floor(h)), 0.72f, 1f) & 0xFFFFFF);
	}
}
