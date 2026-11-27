package nz.temple.client.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import org.joml.Vector4f;
import nz.temple.TempleNz;
import nz.temple.cosmetic.Finish;
import nz.temple.cosmetic.Finishes;
import nz.temple.cosmetic.SurfacePacking;

import java.util.Optional;
import java.util.OptionalDouble;

public final class Meshes {
	public static final VertexFormat FORMAT = VertexFormat.builder(0)
			.addAttribute("Position", GpuFormat.RGB32_FLOAT)
			.addAttribute("Color", GpuFormat.RGBA8_UNORM)
			.addAttribute("UV0", GpuFormat.RG32_FLOAT)
			.addAttribute("UV1", GpuFormat.RG16_SINT)
			.addAttribute("UV2", GpuFormat.RG16_SINT)
			.addAttribute("Normal", GpuFormat.RGBA8_SNORM)
			.addAttribute("LineWidth", GpuFormat.R32_FLOAT)
			.build();
	public static final int PATTERN_NONE = 0, PATTERN_HORN = 1, PATTERN_BARBS = 2, PATTERN_VEINS = 3, PATTERN_FUR = 4,
			PATTERN_BRUSHED = 5, PATTERN_ENGRAVED = 6, PATTERN_WEAVE = 7, PATTERN_FRACTURE = 8, PATTERN_SCALES = 9,
			PATTERN_HEX = 10, PATTERN_GRAIN = 11;
	public enum Material {
		MATTE(0), METAL(1), GLASS(2), NEON(3);
		final float code;
		Material(float code) {
			this.code = code;
		}
	}
	@FunctionalInterface
	public interface Surface {
		void at(double u, double v, double[] out);
	}
	private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);
	private static final Vector3f NO_OFFSET = new Vector3f();
	private static final Matrix4f IDENTITY_TEX = new Matrix4f();
	private static final int MAX_QUADS = 40_000;

	private BloomSink bloomSink;
	private boolean bloomCopy;
	private float bloomStrength = 1;
	private final Layer solid = new Layer(false);
	private final Layer glass = new Layer(true);
	private Material material = Material.MATTE;
	private float emission;
	private int finish;
	private boolean finishGlass;
	private int pattern;
	private float patternScale = 1;
	private float accent;
	private int accentColour = -1;
	private boolean accentOwn;
	private float occlusion = 1;
	private SurfaceAt accentAt;
	private SurfaceAt occlusionAt;
	private static final int LIGHT_SLOTS = 1024;
	private final long[] lightKeys = new long[LIGHT_SLOTS];
	private final int[] lightValues = new int[LIGHT_SLOTS];
	private final int[] lightStamps = new int[LIGHT_SLOTS];
	private int lightStamp = 1;
	private long lastLightKey = Long.MIN_VALUE;
	private int lastLight;
	private int fixedLight = -1;
	private final double[] uvA = new double[2], uvB = new double[2], uvC = new double[2], uvD = new double[2];
	private static final double[] NO_UV = new double[2];
	private Matrix4fc pose;
	private double camX;
	private double camY;
	private double camZ;
	private int quads;
	private GpuTexture scene;
	private GpuTextureView sceneView;
	private final Vector3f normal = new Vector3f();
	private final double[] p = new double[6];
	private static final class Layer {
		final boolean glass;
		VertexRing buffer;
		Layer(boolean glass) {
			this.glass = glass;
		}
	}
	private static RenderPipeline pipeline;
	public static RenderPipeline pipeline() {
		if (pipeline == null) {
			pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/fx_mesh"))
					.withVertexShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/fx_mesh"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/fx_mesh"))
					.withBindGroupLayout(BindGroupLayout.builder().withSampler("SceneSampler").withSampler("LightSampler")
							.withSampler("FinishTable").build())
					.withVertexBinding(0, FORMAT)
					.withPrimitiveTopology(PrimitiveTopology.QUADS)
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))

					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
					.withCull(false)
					.build());
		}
		return pipeline;
	}
	public void transform(org.joml.Matrix4d transform) {
		local = transform;
	}
	private org.joml.Matrix4d local;
	private final org.joml.Vector3d tmpPos = new org.joml.Vector3d();
	@FunctionalInterface
	interface BloomSink {
		void quad(double ax, double ay, double az, int ca, double bx, double by, double bz, int cb,
				double cx, double cy, double cz, int cc, double dx, double dy, double dz, int cd);
	}

	void bloomSink(BloomSink sink) {
		bloomSink = sink;
	}

	public void bloom(boolean on, float strength) {
		bloomCopy = on;
		bloomStrength = strength;
	}
	public static int lastFrameQuads;
	private double pixelsPerUnit = 900;
	void begin(Matrix4fc pose, double camX, double camY, double camZ) {
		Minecraft mcLod = Minecraft.getInstance();
		double fov = Math.toRadians(Math.max(30, Math.min(110, mcLod.options.fov().get())));
		pixelsPerUnit = mcLod.getWindow().getHeight() / 2.0 / Math.tan(fov / 2);
		lastFrameQuads = quads;
		local = null;
		bloomCopy = false;
		this.pose = pose;
		this.camX = camX;
		this.camY = camY;
		this.camZ = camZ;
		quads = 0;
		emission = 0;
		material(Material.MATTE);
		lightStamp++;
		lastLightKey = Long.MIN_VALUE;
		fixedLight = -1;
	}
	public void material(Material material) {
		this.material = material;
		finish = material.ordinal();
		finishGlass = material == Material.GLASS;
		pattern = PATTERN_NONE;
		patternScale = 1;
		accent = emission;
		accentColour = -1;
		accentOwn = false;
		occlusion = 1;
		accentAt = null;
		occlusionAt = null;
	}
	public void emission(float emission) {
		this.emission = Math.max(0, Math.min(1, emission));
		if (finish < 4) {
			accent = this.emission;
			accentColour = -1;
		}
	}
	public void finish(Finish f) {
		finish = Finishes.index(f);
		finishGlass = f.isLegacy() ? f.legacy() == Material.GLASS.ordinal() : f.transmission() > 0.01f;
		material = f.isLegacy() ? Material.values()[f.legacy()] : Material.MATTE;
		pattern = PATTERN_NONE;
		patternScale = 1;
		accent = 0;
		accentColour = -1;
		accentOwn = false;
		occlusion = 1;
		accentAt = null;
		occlusionAt = null;
	}
	public void pattern(int pattern, double scale) {
		this.pattern = pattern;
		this.patternScale = (float) scale;
	}
	public void accent(double weight, int rgb) {
		accent = (float) Math.max(0, Math.min(1, weight));
		accentColour = rgb & 0xFFFFFF;
		accentOwn = false;
	}
	public void accentOwnColour(double weight) {
		accent = (float) Math.max(0, Math.min(1, weight));
		accentColour = -1;
		accentOwn = true;
	}
	public void accentAt(SurfaceAt f) {
		accentAt = f;
		accentScale = 1;
	}
	public void accentAt(SurfaceAt f, double scale) {
		accentAt = f;
		accentScale = scale;
	}
	private double accentScale = 1;
	public static final class Grid implements SurfaceAt {
		private final SurfaceAt f;
		private final double[][] small = new double[32 * 16][];
		private final java.util.Map<Long, double[]> large = new java.util.HashMap<>();
		public Grid(SurfaceAt f) {
			this.f = f;
		}

		@Override
		public double at(double u, double v) {
			return f.at(u, v);
		}

		double[] values(int su, int sv) {
			boolean fits = su < 32 && sv < 16;
			long key = (long) su << 32 | sv;
			double[] values = fits ? small[su * 16 + sv] : large.get(key);
			if (values == null) {
				values = new double[(su + 1) * (sv + 1)];
				for (int j = 0; j <= sv; j++) {
					for (int i = 0; i <= su; i++) {
						values[j * (su + 1) + i] = f.at((double) i / su, (double) j / sv);
					}
				}
				if (fits) {
					small[su * 16 + sv] = values;
				} else {
					large.put(key, values);
				}
			}
			return values;
		}
	}
	public void occlusion(double o) {
		occlusion = (float) Math.max(0, Math.min(1, o));
	}
	public void occlusionAt(SurfaceAt f) {
		occlusionAt = f;
	}
	public void lightFrom(net.minecraft.core.BlockPos pos) {
		fixedLight = pos == null ? -1 : lightAt(pos.getX(), pos.getY(), pos.getZ());
	}
	@FunctionalInterface
	public interface SurfaceAt {
		double at(double u, double v);
	}
	public Material material() {
		return material;
	}

	private VertexRing ring() {
		Layer l = finishGlass ? glass : solid;
		if (l.buffer == null) {
			RenderPipeline pipe = pipeline();
			l.buffer = new VertexRing(() -> "Temple.nz meshes", Gpu.vertexFormat(pipe), Gpu.topology(pipe),
					RenderType.BIG_BUFFER_SIZE);
		}
		return l.buffer;
	}
	void flush() {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		ensureScene(main);
		flush(solid, main);
		if (glass.buffer != null && glass.buffer.building()) {
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main.getColorTexture(), scene,
					0, 0, 0, 0, 0, main.width, main.height);
		}
		flush(glass, main);
	}

	private void flush(Layer l, RenderTarget main) {
		if (l.buffer == null || !l.buffer.building()) {
			return;
		}
		VertexRing.Upload info = l.buffer.upload();
		if (info != null) {
			GpuTextureView finishTable = FinishTable.view();
			GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
					.writeTransform(new org.joml.Matrix4f(Gpu.modelView()).mul(pose), WHITE, NO_OFFSET, IDENTITY_TEX);
			try (RenderPass pass = Gpu.openPass(RenderSystem.getDevice().createCommandEncoder(), () -> "Temple.nz meshes",
					main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
				pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline()));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transforms);
				pass.bindTexture("SceneSampler", sceneView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
				pass.bindTexture("LightSampler", Minecraft.getInstance().gameRenderer.levelLightmap(),
						RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
				pass.bindTexture("FinishTable", finishTable, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				Gpu.vertices(pass, info.vertices());
				pass.setIndexBuffer(info.indices(), info.indexType());
				Gpu.drawIndexed(pass, info.indexCount(), 0, 0);
			}
		}
	}
	private void ensureScene(RenderTarget main) {
		if (scene != null && scene.getWidth(0) == main.width && scene.getHeight(0) == main.height) {
			return;
		}
		if (sceneView != null) {
			sceneView.close();
		}
		if (scene != null) {
			scene.close();
		}
		GpuDevice device = RenderSystem.getDevice();
		GpuFormat format = Gpu.format(main.getColorTexture());
		scene = Gpu.createTexture(() -> "Temple.nz mesh scene", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
				format, main.width, main.height, 1, 1);
		sceneView = device.createTextureView(scene);
	}
	public String selfTest() {
		try {
			RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			ensureScene(main);
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main.getColorTexture(), scene,
					0, 0, 0, 0, 0, main.width, main.height);
			return null;
		} catch (RuntimeException e) {
			return "scene copy: " + e;
		}
	}
	private static final int OFF_POS = FORMAT.getElement("Position").offset();
	private static final int OFF_COLOR = FORMAT.getElement("Color").offset();
	private static final int OFF_UV0 = FORMAT.getElement("UV0").offset();
	private static final int OFF_UV1 = FORMAT.getElement("UV1").offset();
	private static final int OFF_UV2 = FORMAT.getElement("UV2").offset();
	private static final int OFF_NORMAL = FORMAT.getElement("Normal").offset();
	private static final int OFF_ACCENT = FORMAT.getElement("LineWidth").offset();
	private final Vector3f posTmp = new Vector3f();

	private static long quad(VertexRing ring) {
		int base = ring.vertexCount();
		long to = ring.vertices(4);
		long ix = ring.indices(6);
		MemoryUtil.memPutInt(ix, base);
		MemoryUtil.memPutInt(ix + 4, base + 1);
		MemoryUtil.memPutInt(ix + 8, base + 2);
		MemoryUtil.memPutInt(ix + 12, base + 2);
		MemoryUtil.memPutInt(ix + 16, base + 3);
		MemoryUtil.memPutInt(ix + 20, base);
		return to;
	}
	private void vertexAt(long p, double x, double y, double z, double nx, double ny, double nz, int argb, double u, double v,
			int light, double accentW, double occl) {
		if (local != null) {
			local.transformPosition(tmpPos.set(x, y, z));
			x = tmpPos.x;
			y = tmpPos.y;
			z = tmpPos.z;
			local.transformDirection(tmpPos.set(nx, ny, nz));
			nx = tmpPos.x;
			ny = tmpPos.y;
			nz = tmpPos.z;
		}
		float fnx = (float) nx, fny = (float) ny, fnz = (float) nz;
		float len2 = fnx * fnx + fny * fny + fnz * fnz;
		if (len2 > 1e-20f && Math.abs(len2 - 1f) > 1e-4f) {
			float inv = (float) (1 / Math.sqrt(len2));
			fnx *= inv;
			fny *= inv;
			fnz *= inv;
		}
		normal.set(fnx, fny, fnz);
		MemoryUtil.memPutFloat(p + OFF_POS, (float) (x - camX));
		MemoryUtil.memPutFloat(p + OFF_POS + 4, (float) (y - camY));
		MemoryUtil.memPutFloat(p + OFF_POS + 8, (float) (z - camZ));
		MemoryUtil.memPutByte(p + OFF_COLOR, (byte) (argb >> 16));
		MemoryUtil.memPutByte(p + OFF_COLOR + 1, (byte) (argb >> 8));
		MemoryUtil.memPutByte(p + OFF_COLOR + 2, (byte) argb);
		MemoryUtil.memPutByte(p + OFF_COLOR + 3, (byte) (argb >>> 24));
		MemoryUtil.memPutFloat(p + OFF_UV0, (float) (u * patternScale));
		MemoryUtil.memPutFloat(p + OFF_UV0 + 4, (float) (v * patternScale));
		MemoryUtil.memPutShort(p + OFF_UV1, (short) SurfacePacking.finishAndPattern(finish, pattern));
		MemoryUtil.memPutShort(p + OFF_UV1 + 2, (short) SurfacePacking.accentAndOcclusion(accentW, occl));
		MemoryUtil.memPutShort(p + OFF_UV2, (short) (light & 0xFFFF));
		MemoryUtil.memPutShort(p + OFF_UV2 + 2, (short) (light >>> 16));
		MemoryUtil.memPutByte(p + OFF_NORMAL, normalByte(normal.x));
		MemoryUtil.memPutByte(p + OFF_NORMAL + 1, normalByte(normal.y));
		MemoryUtil.memPutByte(p + OFF_NORMAL + 2, normalByte(normal.z));
		MemoryUtil.memPutByte(p + OFF_NORMAL + 3, (byte) 0);
		MemoryUtil.memPutFloat(p + OFF_ACCENT, SurfacePacking.accentColour(accentColour < 0 ? argb : accentColour));
	}
	private static byte normalByte(float c) {
		return (byte) ((int) (Math.max(-1f, Math.min(1f, c)) * 127f) & 0xFF);
	}
	private int lightAt(int bx, int by, int bz) {
		long key = net.minecraft.core.BlockPos.asLong(bx, by, bz);
		if (key == lastLightKey) {
			return lastLight;
		}
		int slot = (int) (it.unimi.dsi.fastutil.HashCommon.mix(key) & (LIGHT_SLOTS - 1));
		for (int probe = 0; probe < 8; probe++) {
			int i = (slot + probe) & (LIGHT_SLOTS - 1);
			if (lightStamps[i] != lightStamp) {
				break;
			}
			if (lightKeys[i] == key) {
				lastLightKey = key;
				lastLight = lightValues[i];
				return lastLight;
			}
		}
		int packed = 15 * 16 | (15 * 16) << 16;
		net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(key);
			int block = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
			int sky = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
			packed = block * 16 | (sky * 16) << 16;
		}
		for (int probe = 0; probe < 8; probe++) {
			int i = (slot + probe) & (LIGHT_SLOTS - 1);
			if (lightStamps[i] != lightStamp) {
				lightStamps[i] = lightStamp;
				lightKeys[i] = key;
				lightValues[i] = packed;
				break;
			}
		}
		lastLightKey = key;
		lastLight = packed;
		return packed;
	}
	private int lightFor(double[] a, double[] b, double[] c, double[] d) {
		if (fixedLight >= 0) {
			return fixedLight;
		}
		tmpPos.set((a[0] + b[0] + c[0] + d[0]) * 0.25, (a[1] + b[1] + c[1] + d[1]) * 0.25, (a[2] + b[2] + c[2] + d[2]) * 0.25);
		if (local != null) {
			local.transformPosition(tmpPos);
		}
		return lightAt(net.minecraft.util.Mth.floor(tmpPos.x), net.minecraft.util.Mth.floor(tmpPos.y), net.minecraft.util.Mth.floor(tmpPos.z));
	}
	private final double[] bloomCorners = new double[12];
	private void bloom(double[] a, double[] b, double[] c, double[] d, int ca, int cb, int cc, int cd) {
		double[] q = bloomCorners;
		if (local == null) {
			bloomSink.quad(a[0], a[1], a[2], ca, b[0], b[1], b[2], cb, c[0], c[1], c[2], cc, d[0], d[1], d[2], cd);
			return;
		}
		for (int i = 0; i < 4; i++) {
			double[] v = i == 0 ? a : i == 1 ? b : i == 2 ? c : d;
			local.transformPosition(tmpPos.set(v[0], v[1], v[2]));
			q[i * 3] = tmpPos.x;
			q[i * 3 + 1] = tmpPos.y;
			q[i * 3 + 2] = tmpPos.z;
		}
		bloomSink.quad(q[0], q[1], q[2], ca, q[3], q[4], q[5], cb, q[6], q[7], q[8], cc, q[9], q[10], q[11], cd);
	}

	private int glowColour(int argb) {
		return Fx.withAlpha(argb, ((argb >>> 24) & 0xFF) * bloomStrength);
	}
	public void quad(double[] a, double[] b, double[] c, double[] d, int ca, int cb, int cc, int cd) {
		quad(a, b, c, d, ca, cb, cc, cd, NO_UV, NO_UV, NO_UV, NO_UV, accent, accent, accent, accent, occlusion, occlusion, occlusion,
				occlusion);
	}

	public void quad(double[] a, double[] b, double[] c, double[] d, int ca, int cb, int cc, int cd, double[] ua, double[] ub,
			double[] uc, double[] ud, double wa, double wb, double wc, double wd, double oa, double ob, double oc, double od) {
		if (((ca | cb | cc | cd) >>> 24) == 0 || ++quads > MAX_QUADS) {
			return;
		}
		if (bloomSink != null) {
			if (bloomCopy) {
				bloom(a, b, c, d, glowColour(ca), glowColour(cb), glowColour(cc), glowColour(cd));
			} else if (finish >= 4 && (accentColour >= 0 || accentOwn) && wa + wb + wc + wd > 0.4) {
				int glow = 0xFF000000 | accentColour;
				bloom(a, b, c, d, accentGlow(accentOwn ? ca : glow, wa, ca), accentGlow(accentOwn ? cb : glow, wb, cb),
						accentGlow(accentOwn ? cc : glow, wc, cc), accentGlow(accentOwn ? cd : glow, wd, cd));
			}
		}
		int light = lightFor(a, b, c, d);
		long to = quad(ring());
		vertexAt(to, a[0], a[1], a[2], a[3], a[4], a[5], ca, ua[0], ua[1], light, wa, oa);
		vertexAt(to + VERTEX_BYTES, b[0], b[1], b[2], b[3], b[4], b[5], cb, ub[0], ub[1], light, wb, ob);
		vertexAt(to + 2L * VERTEX_BYTES, c[0], c[1], c[2], c[3], c[4], c[5], cc, uc[0], uc[1], light, wc, oc);
		vertexAt(to + 3L * VERTEX_BYTES, d[0], d[1], d[2], d[3], d[4], d[5], cd, ud[0], ud[1], light, wd, od);
	}
	private static int accentGlow(int glow, double weight, int corner) {
		return Fx.withAlpha(glow, (float) (Math.min(1, weight * 1.6) * 0.7 * ((corner >>> 24) & 0xFF)));
	}
	public void surface(Surface s, int su, int sv, int argb, ColourAt colour) {

		int n = su + 1;
		if (rowRoom < n) {
			rowRoom = Math.max(n, 32);
			for (int r = 0; r < 2; r++) {
				rowPoints[r] = new double[rowRoom][6];
				rowColours[r] = new int[rowRoom];
				rowAccent[r] = new double[rowRoom];
				if (rowFinished[r] != 0) {
					MemoryUtil.nmemFree(rowFinished[r]);
				}
				rowFinished[r] = MemoryUtil.nmemAlloc((long) rowRoom * VERTEX_BYTES);
			}
		}
		int light = Math.max(fixedLight, 0);
		rowLast = sv;
		rowAccentGrid = accentAt instanceof Grid g ? g.values(su, sv) : null;
		rowOcclusionGrid = occlusionAt instanceof Grid g ? g.values(su, sv) : null;
		int a = 0;
		surfaceRow(s, su, 0, 0, argb, colour, a, light);
		for (int j = 1; j <= sv; j++) {
			int b = 1 - a;
			surfaceRow(s, su, j, (double) j / sv, argb, colour, b, light);
			double[][] row0 = rowPoints[a], row1 = rowPoints[b];
			int[] c0 = rowColours[a], c1 = rowColours[b];
			double[] w0 = rowAccent[a], w1 = rowAccent[b];
			long f0 = rowFinished[a], f1 = rowFinished[b];
			for (int i = 0; i < su; i++) {
				int ca = c0[i], cb = c0[i + 1], cc = c1[i + 1], cd = c1[i];
				if (((ca | cb | cc | cd) >>> 24) == 0 || ++quads > MAX_QUADS) {
					continue;
				}
				if (bloomSink != null) {
					if (bloomCopy) {
						bloom(row0[i], row0[i + 1], row1[i + 1], row1[i], glowColour(ca), glowColour(cb), glowColour(cc), glowColour(cd));
					} else if (finish >= 4 && (accentColour >= 0 || accentOwn) && w0[i] + w0[i + 1] + w1[i + 1] + w1[i] > 0.4) {
						int glow = 0xFF000000 | accentColour;
						bloom(row0[i], row0[i + 1], row1[i + 1], row1[i], accentGlow(accentOwn ? ca : glow, w0[i], ca),
								accentGlow(accentOwn ? cb : glow, w0[i + 1], cb), accentGlow(accentOwn ? cc : glow, w1[i + 1], cc),
								accentGlow(accentOwn ? cd : glow, w1[i], cd));
					}
				}
				long to = quad(ring());
				MemoryUtil.memCopy(f0 + (long) i * VERTEX_BYTES, to, VERTEX_BYTES);
				MemoryUtil.memCopy(f0 + (long) (i + 1) * VERTEX_BYTES, to + VERTEX_BYTES, VERTEX_BYTES);
				MemoryUtil.memCopy(f1 + (long) (i + 1) * VERTEX_BYTES, to + 2L * VERTEX_BYTES, VERTEX_BYTES);
				MemoryUtil.memCopy(f1 + (long) i * VERTEX_BYTES, to + 3L * VERTEX_BYTES, VERTEX_BYTES);
				if (fixedLight < 0) {
					int here = lightFor(row0[i], row0[i + 1], row1[i + 1], row1[i]);
					for (int k = 0; k < 4; k++) {
						long at = to + (long) k * VERTEX_BYTES + OFF_UV2;
						MemoryUtil.memPutShort(at, (short) (here & 0xFFFF));
						MemoryUtil.memPutShort(at + 2, (short) (here >>> 16));
					}
				}
			}
			a = b;
		}
	}
	private int rowRoom;
	private final double[][][] rowPoints = new double[2][][];
	private final int[][] rowColours = new int[2][];
	private final double[][] rowAccent = new double[2][];
	private final long[] rowFinished = new long[2];
	private double[] rowAccentGrid;
	private double[] rowOcclusionGrid;
	private void surfaceRow(Surface s, int su, int j, double v, int argb, ColourAt colour, int r, int light) {
		double[] accentGrid = rowAccentGrid;
		double[] occlusionGrid = rowOcclusionGrid;
		int row = j * (su + 1);
		double[][] points = rowPoints[r];
		int[] colours = rowColours[r];
		double[] weights = rowAccent[r];
		long finished = rowFinished[r];
		double[] shape = placedShape;
		for (int i = 0; i <= su; i++) {
			double u = (double) i / su;
			double[] out = points[i];
			int c;
			if (shape != null) {
				int k = (row + i) * 6;
				double lx = shape[k], ly = shape[k + 1], lz = shape[k + 2], nx = shape[k + 3], ny = shape[k + 4], nz = shape[k + 5];
				out[0] = placedX + t00 * lx + t10 * ly + t20 * lz;
				out[1] = placedY + t01 * lx + t11 * ly + t21 * lz;
				out[2] = placedZ + t02 * lx + t12 * ly + t22 * lz;
				out[3] = t00 * nx + t10 * ny + t20 * nz;
				out[4] = t01 * nx + t11 * ny + t21 * nz;
				out[5] = t02 * nx + t12 * ny + t22 * nz;
				c = placedColours[i + (placedEdges && (j == 0 || j == rowLast) ? 16 : 0)];
			} else {
				out[0] = out[1] = out[2] = out[3] = out[4] = out[5] = 0;
				s.at(u, v, out);
				double len = Math.sqrt(out[3] * out[3] + out[4] * out[4] + out[5] * out[5]);
				if (len > 1e-9) {
					out[3] /= len;
					out[4] /= len;
					out[5] /= len;
				} else {
					out[4] = 1;
				}
				c = colour == null ? argb : colour.at(u, v);
			}
			double w = accentGrid != null ? accentGrid[row + i] * accentScale : accentAt == null ? accent : accentAt.at(u, v) * accentScale;
			colours[i] = c;
			weights[i] = w;
			vertexAt(finished + (long) i * VERTEX_BYTES, out[0], out[1], out[2], out[3], out[4], out[5], c, u, v, light, w,
					occlusionGrid != null ? occlusionGrid[row + i] : weight(occlusionAt, occlusion, u, v));
		}
	}
	private double[] placedShape;
	private int[] placedColours;
	private boolean placedEdges;
	private double placedX, placedY, placedZ;
	private float t00, t01, t02, t10, t11, t12, t20, t21, t22;
	private int rowLast;
	private final org.joml.Matrix3f placedTurn = new org.joml.Matrix3f();
	private void place(double[] shape, int su, int sv, double x, double y, double z, Quaternionf q, int[] colours, boolean edges) {
		if (q == null) {
			placedTurn.identity();
		} else {
			q.get(placedTurn);
		}
		t00 = placedTurn.m00();
		t01 = placedTurn.m01();
		t02 = placedTurn.m02();
		t10 = placedTurn.m10();
		t11 = placedTurn.m11();
		t12 = placedTurn.m12();
		t20 = placedTurn.m20();
		t21 = placedTurn.m21();
		t22 = placedTurn.m22();
		placedX = x;
		placedY = y;
		placedZ = z;
		placedColours = colours;
		placedEdges = edges;
		placedShape = shape;
		try {
			surface(null, su, sv, 0, null);
		} finally {
			placedShape = null;
		}
	}
	private static double weight(SurfaceAt f, double flat, double u, double v) {
		return f == null ? flat : f.at(u, v);
	}
	@FunctionalInterface
	public interface ColourAt {
		int at(double u, double v);
	}
	public interface ModelPainter {
		boolean part(Meshes mesh, nz.temple.cosmetic.Model.Part part);
		int colour(nz.temple.cosmetic.Model.Part part, double ramp);
		default double glow(nz.temple.cosmetic.Model.Part part, double mask) {
			return 0;
		}
		default double glowAt(nz.temple.cosmetic.Model.Part part, double mask, double ramp, float x, float y, float z) {
			return glow(part, mask);
		}
	}
	private final double[] mA = new double[6], mB = new double[6], mC = new double[6];
	private final double[] muA = new double[2], muB = new double[2], muC = new double[2];
	private final org.joml.Vector3d mTmp = new org.joml.Vector3d();
	public void model(nz.temple.cosmetic.Model m, org.joml.Matrix4d transform, org.joml.Matrix4d[] joints, ModelPainter painter) {
		if (m == null) {
			return;
		}
		int[] idx = m.indices();
		for (nz.temple.cosmetic.Model.Part part : m.parts()) {
			if (!painter.part(this, part)) {
				continue;
			}
			int j = part.joint();
			org.joml.Matrix4d xf = j >= 0 && joints != null && j < joints.length && joints[j] != null ? joints[j] : transform;
			org.joml.Matrix4d next = j >= 0 && joints != null && j + 1 < joints.length ? joints[j + 1] : null;
			if (local == null && fixedLight >= 0 && !bloomCopy) {
				rigidPart(m, part, xf, next, painter);
				continue;
			}
			int end = part.firstIndex() + part.indexCount();
			for (int i = part.firstIndex(); i < end; i += 3) {
				int va = idx[i], vb = idx[i + 1], vc = idx[i + 2];
				modelVertex(m, va, xf, next, mA, muA);
				modelVertex(m, vb, xf, next, mB, muB);
				modelVertex(m, vc, xf, next, mC, muC);
				int ca = painter.colour(part, m.ramp(va)), cb = painter.colour(part, m.ramp(vb)), cc = painter.colour(part, m.ramp(vc));
				double ga = painter.glowAt(part, m.glowMask(va), m.ramp(va), m.x(va), m.y(va), m.z(va));
				double gb = painter.glowAt(part, m.glowMask(vb), m.ramp(vb), m.x(vb), m.y(vb), m.z(vb));
				double gc = painter.glowAt(part, m.glowMask(vc), m.ramp(vc), m.x(vc), m.y(vc), m.z(vc));
				quad(mA, mB, mC, mC, ca, cb, cc, cc, muA, muB, muC, muC, ga, gb, gc, gc, m.occlusion(va), m.occlusion(vb), m.occlusion(vc),
						m.occlusion(vc));
			}
		}
	}
	private final org.joml.Vector3d mTmp2 = new org.joml.Vector3d();
	private float[] rvPos = new float[0];
	private float[] rvNormal = new float[0];
	private int[] rvColour = new int[0];
	private float[] rvAccent = new float[0];
	private int[] rvGlow = new int[0];
	private static final int VERTEX_BYTES = FORMAT.getVertexSize();
	private final org.joml.Matrix4f rvMatrix = new org.joml.Matrix4f();
	private final org.joml.Matrix3f rvNormalMatrix = new org.joml.Matrix3f();
	private final org.joml.Matrix4f rvNext = new org.joml.Matrix4f();
	private final org.joml.Matrix3f rvNextNormal = new org.joml.Matrix3f();
	private final org.joml.Matrix4d rvPlace = new org.joml.Matrix4d();
	private final Vector3f rvTmp2 = new Vector3f();
	private final Vector3f rvTmp = new Vector3f();
	private record PartMesh(int[] vertices, int[] triangles, boolean unitNormals) {
	}
	private static final java.util.Map<nz.temple.cosmetic.Model.Part, PartMesh> PART_MESHES = new java.util.IdentityHashMap<>();
	private static PartMesh partMesh(nz.temple.cosmetic.Model m, nz.temple.cosmetic.Model.Part part) {
		PartMesh mesh = PART_MESHES.get(part);
		if (mesh == null) {
			int[] idx = m.indices();
			int[] place = new int[m.vertexCount()];
			java.util.Arrays.fill(place, -1);
			int[] vertices = new int[Math.min(part.indexCount(), m.vertexCount())];
			int[] triangles = new int[part.indexCount()];
			int n = 0;
			for (int i = 0; i < triangles.length; i++) {
				int v = idx[part.firstIndex() + i];
				if (place[v] < 0) {
					place[v] = n;
					vertices[n++] = v;
				}
				triangles[i] = place[v];
			}
			boolean unit = true;
			for (int k = 0; k < n && unit; k++) {
				int v = vertices[k];
				double length = m.nx(v) * m.nx(v) + m.ny(v) * m.ny(v) + m.nz(v) * m.nz(v);
				unit = Math.abs(length - 1) < 2e-3;
			}
			mesh = new PartMesh(java.util.Arrays.copyOf(vertices, n), triangles, unit);
			PART_MESHES.put(part, mesh);
		}
		return mesh;
	}
	private void rigidPart(nz.temple.cosmetic.Model m, nz.temple.cosmetic.Model.Part part, org.joml.Matrix4d xf, org.joml.Matrix4d next,
			ModelPainter painter) {
		PartMesh mesh = partMesh(m, part);
		int[] vertices = mesh.vertices();
		int[] triangles = mesh.triangles();
		int n = vertices.length;
		quads += triangles.length / 3;
		if (quads > MAX_QUADS || n == 0) {
			return;
		}
		if (rvColour.length < n) {
			rvPos = new float[n * 3];
			rvNormal = new float[n * 3];
			rvColour = new int[n];
			rvAccent = new float[n];
			rvGlow = new int[n];
		}
		rvMatrix.set(rvPlace.translation(-camX, -camY, -camZ).mul(xf));
		rvMatrix.normal(rvNormalMatrix);
		float sx = rvMatrix.m00() * rvMatrix.m00() + rvMatrix.m01() * rvMatrix.m01() + rvMatrix.m02() * rvMatrix.m02();
		float sy = rvMatrix.m10() * rvMatrix.m10() + rvMatrix.m11() * rvMatrix.m11() + rvMatrix.m12() * rvMatrix.m12();
		float sz = rvMatrix.m20() * rvMatrix.m20() + rvMatrix.m21() * rvMatrix.m21() + rvMatrix.m22() * rvMatrix.m22();
		boolean even = next == null && mesh.unitNormals() && sx > 1e-12f && Math.abs(sy - sx) < sx * 1e-4f && Math.abs(sz - sx) < sx * 1e-4f;
		if (even) {
			rvNormalMatrix.scale((float) Math.sqrt(sx));
		}
		if (next != null) {
			rvNext.set(rvPlace.translation(-camX, -camY, -camZ).mul(next));
			rvNext.normal(rvNextNormal);
		}
		VertexRing ring = ring();
		boolean bloomAccent = bloomSink != null && finish >= 4 && (accentColour >= 0 || accentOwn);
		int light = fixedLight;
		int packedFinish = SurfacePacking.finishAndPattern(finish, pattern);
		float scale = patternScale;
		int base = ring.vertexCount();
		long to = ring.vertices(n);
		boolean anyClear = false;
		for (int k = 0; k < n; k++) {
			int v = vertices[k];
			float x = m.x(v), y = m.y(v), z = m.z(v);
			float w = next == null ? 0 : (float) m.bend(v);
			rvMatrix.transformPosition(x, y, z, rvTmp);
			if (w > 0) {
				rvTmp.lerp(rvNext.transformPosition(x, y, z, rvTmp2), w);
			}
			rvPos[k * 3] = rvTmp.x;
			rvPos[k * 3 + 1] = rvTmp.y;
			rvPos[k * 3 + 2] = rvTmp.z;
			rvNormalMatrix.transform(m.nx(v), m.ny(v), m.nz(v), rvTmp);
			if (w > 0) {
				rvTmp.lerp(rvNextNormal.transform(m.nx(v), m.ny(v), m.nz(v), rvTmp2), w);
			}
			if (!even) {
				float len = rvTmp.length();
				if (len > 1e-10f) {
					rvTmp.div(len);
				}
			}
			rvNormal[k * 3] = rvTmp.x;
			rvNormal[k * 3 + 1] = rvTmp.y;
			rvNormal[k * 3 + 2] = rvTmp.z;
			double ramp = m.ramp(v);
			int colour = painter.colour(part, ramp);
			rvColour[k] = colour;
			anyClear |= (colour >>> 24) == 0;
			rvAccent[k] = (float) painter.glowAt(part, m.glowMask(v), ramp, x, y, z);
			if (bloomAccent) {
				rvGlow[k] = accentGlow(accentOwn ? colour : 0xFF000000 | accentColour, rvAccent[k], colour);
			}
			writeVertex(to + (long) k * VERTEX_BYTES, m, v, k, packedFinish, scale, light);
		}
		if (!anyClear && !bloomAccent) {
			long ix = ring.indices(triangles.length);
			for (int i = 0; i < triangles.length; i++) {
				MemoryUtil.memPutInt(ix + 4L * i, base + triangles[i]);
			}
			return;
		}
		for (int i = 0; i < triangles.length; i += 3) {
			int va = triangles[i], vb = triangles[i + 1], vc = triangles[i + 2];
			if (((rvColour[va] | rvColour[vb] | rvColour[vc]) >>> 24) == 0) {
				continue;
			}
			if (bloomAccent && rvAccent[va] + rvAccent[vb] + 2 * rvAccent[vc] > 0.4) {
				int gc = rvGlow[vc];
				double cx = rvPos[vc * 3] + camX, cy = rvPos[vc * 3 + 1] + camY, cz = rvPos[vc * 3 + 2] + camZ;
				bloomSink.quad(rvPos[va * 3] + camX, rvPos[va * 3 + 1] + camY, rvPos[va * 3 + 2] + camZ, rvGlow[va],
						rvPos[vb * 3] + camX, rvPos[vb * 3 + 1] + camY, rvPos[vb * 3 + 2] + camZ, rvGlow[vb],
						cx, cy, cz, gc, cx, cy, cz, gc);
			}
			long ix = ring.indices(3);
			MemoryUtil.memPutInt(ix, base + va);
			MemoryUtil.memPutInt(ix + 4, base + vb);
			MemoryUtil.memPutInt(ix + 8, base + vc);
		}
	}
	private void writeVertex(long p, nz.temple.cosmetic.Model m, int v, int k, int packedFinish, float scale, int light) {
		int argb = rvColour[k];
		MemoryUtil.memPutFloat(p + OFF_POS, rvPos[k * 3]);
		MemoryUtil.memPutFloat(p + OFF_POS + 4, rvPos[k * 3 + 1]);
		MemoryUtil.memPutFloat(p + OFF_POS + 8, rvPos[k * 3 + 2]);
		MemoryUtil.memPutByte(p + OFF_COLOR, (byte) (argb >> 16));
		MemoryUtil.memPutByte(p + OFF_COLOR + 1, (byte) (argb >> 8));
		MemoryUtil.memPutByte(p + OFF_COLOR + 2, (byte) argb);
		MemoryUtil.memPutByte(p + OFF_COLOR + 3, (byte) (argb >>> 24));
		MemoryUtil.memPutFloat(p + OFF_UV0, m.u(v) * scale);
		MemoryUtil.memPutFloat(p + OFF_UV0 + 4, m.v(v) * scale);
		MemoryUtil.memPutShort(p + OFF_UV1, (short) packedFinish);
		MemoryUtil.memPutShort(p + OFF_UV1 + 2, (short) SurfacePacking.accentAndOcclusion(rvAccent[k], m.occlusion(v)));
		MemoryUtil.memPutShort(p + OFF_UV2, (short) (light & 0xFFFF));
		MemoryUtil.memPutShort(p + OFF_UV2 + 2, (short) (light >>> 16));
		MemoryUtil.memPutByte(p + OFF_NORMAL, normalByte(rvNormal[k * 3]));
		MemoryUtil.memPutByte(p + OFF_NORMAL + 1, normalByte(rvNormal[k * 3 + 1]));
		MemoryUtil.memPutByte(p + OFF_NORMAL + 2, normalByte(rvNormal[k * 3 + 2]));
		MemoryUtil.memPutByte(p + OFF_NORMAL + 3, (byte) 0);
		MemoryUtil.memPutFloat(p + OFF_ACCENT, SurfacePacking.accentColour(accentColour < 0 ? argb : accentColour));
	}
	private void modelVertex(nz.temple.cosmetic.Model m, int v, org.joml.Matrix4d xf, org.joml.Matrix4d next, double[] out, double[] uv) {
		double w = next == null ? 0 : m.bend(v);
		xf.transformPosition(mTmp.set(m.x(v), m.y(v), m.z(v)));
		if (w > 0) {
			next.transformPosition(mTmp2.set(m.x(v), m.y(v), m.z(v)));
			mTmp.lerp(mTmp2, w);
		}
		out[0] = mTmp.x;
		out[1] = mTmp.y;
		out[2] = mTmp.z;
		xf.transformDirection(mTmp.set(m.nx(v), m.ny(v), m.nz(v)));
		if (w > 0) {
			next.transformDirection(mTmp2.set(m.nx(v), m.ny(v), m.nz(v)));
			mTmp.lerp(mTmp2, w);
		}
		mTmp.normalize();
		out[3] = mTmp.x;
		out[4] = mTmp.y;
		out[5] = mTmp.z;
		uv[0] = m.u(v);
		uv[1] = m.v(v);
	}
	private int lod(double x, double y, double z, double radius, int detail, int min) {
		if (local != null) {
			local.transformPosition(tmpPos.set(x, y, z));
			x = tmpPos.x;
			y = tmpPos.y;
			z = tmpPos.z;
		}
		double dx = x - camX, dy = y - camY, dz = z - camZ;
		double dist = Math.max(0.05, Math.sqrt(dx * dx + dy * dy + dz * dz) - radius);
		double px = radius / dist * pixelsPerUnit;
		int bands = (int) Math.ceil(2.4 * Math.sqrt(Math.max(0, px)));
		return Math.max(min, Math.min(detail, bands));
	}
	public void ellipsoid(double x, double y, double z, double rx, double ry, double rz, Quaternionf q, int argb, int detail) {
		detail = lod(x, y, z, Math.max(rx, Math.max(ry, rz)), Math.max(detail, 10), 4);
		Vector3f tmp = new Vector3f();
		int around = detail * 2;
		double[] turn = circle(around);
		double[] half = circle(around);
		int bands = detail;
		surface((u, v, o) -> {
			int i = (int) Math.round(u * around) * 2;
			int j = (int) Math.round(v * bands) * 2;
			double sinPhi = half[j + 1], cosPhi = half[j];
			double sx = sinPhi * turn[i];
			double sy = -cosPhi;
			double sz = sinPhi * turn[i + 1];
			put(o, x, y, z, q, tmp, sx * rx, sy * ry, sz * rz, sx / rx, sy / ry, sz / rz);
		}, around, bands, argb, null);
	}
	private static final double[][] CIRCLES = new double[257][];
	private static double[] circle(int n) {
		double[] table = n < CIRCLES.length ? CIRCLES[n] : null;
		if (table == null) {
			table = new double[(n + 1) * 2];
			for (int i = 0; i <= n; i++) {
				double a = Math.PI * 2 * i / n;
				table[i * 2] = Math.cos(a);
				table[i * 2 + 1] = Math.sin(a);
			}
			if (n < CIRCLES.length) {
				CIRCLES[n] = table;
			}
		}
		return table;
	}
	public void sphere(double x, double y, double z, double r, int argb, int detail) {
		ellipsoid(x, y, z, r, r, r, null, argb, detail);
	}
	public void torus(double x, double y, double z, Quaternionf q, double big, double small, int argb, int segments, ColourAt colour) {
		Vector3f tmp = new Vector3f();
		int around = lod(x, y, z, big + small, segments, 8);
		int across = lod(x, y, z, small, 10, 4);
		double[] ring = circle(around);
		double[] tube = circle(across);
		surface((u, v, o) -> {
			int i = (int) Math.round(u * around) * 2;
			int j = (int) Math.round(v * across) * 2;
			double cx = ring[i];
			double cz = ring[i + 1];
			double cb = tube[j], sb = tube[j + 1];
			put(o, x, y, z, q, tmp, (big + small * cb) * cx, small * sb, (big + small * cb) * cz, cb * cx, sb, cb * cz);
		}, around, across, argb, colour);
	}
	public void disc(double x, double y, double z, Quaternionf q, double radius, double thickness, int face, int rim, int segments) {
		Vector3f tmp = new Vector3f();
		double h = thickness / 2;
		surface((u, v, o) -> {
			double a = u * Math.PI * 2;
			put(o, x, y, z, q, tmp, Math.cos(a) * radius, -h + v * thickness, Math.sin(a) * radius, Math.cos(a), 0, Math.sin(a));
		}, segments, 1, rim, null);
		for (int side = -1; side <= 1; side += 2) {
			int s = side;
			surface((u, v, o) -> {
				double a = u * Math.PI * 2;
				double r = v * radius;
				put(o, x, y, z, q, tmp, Math.cos(a) * r, s * h, Math.sin(a) * r, 0, s, 0);
			}, segments, 2, face, null);
		}
	}
	public void tube(double[] path, int count, double[] radius, int[] colours, int sides) {
		if (count < 2) {
			return;
		}
		double widest = 0;
		for (int i = 0; i < count; i++) {
			widest = Math.max(widest, radius[i]);
		}
		sides = lod(path[0], path[1], path[2], widest, Math.max(sides, 12), 5);
		if (count < 24) {
			int sub = count < 8 ? 4 : 2;
			int n = (count - 1) * sub + 1;
			double[] p = new double[n * 3];
			double[] r = new double[n];
			int[] c = new int[n];
			for (int i = 0; i < count - 1; i++) {
				int i0 = Math.max(0, i - 1), i2 = i + 1, i3 = Math.min(count - 1, i + 2);
				for (int s = 0; s < sub; s++) {
					double t = (double) s / sub;
					int o = i * sub + s;
					for (int k = 0; k < 3; k++) {
						double p0 = path[i0 * 3 + k], p1 = path[i * 3 + k], p2 = path[i2 * 3 + k], p3 = path[i3 * 3 + k];
						double t2 = t * t, t3 = t2 * t;
						p[o * 3 + k] = 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
					}
					r[o] = radius[i] + (radius[i2] - radius[i]) * t;
					c[o] = Fx.lerp(colours[i], colours[i2], t);
				}
			}
			System.arraycopy(path, (count - 1) * 3, p, (n - 1) * 3, 3);
			r[n - 1] = radius[count - 1];
			c[n - 1] = colours[count - 1];
			path = p;
			radius = r;
			colours = c;
			count = n;
		}
		if (tubeRoom < sides + 1) {
			tubeRoom = Math.max(sides + 1, 16);
			tubeRings[0] = new double[tubeRoom][6];
			tubeRings[1] = new double[tubeRoom][6];
		}
		double[][] ring0 = null;
		double nx = 0;
		double ny = 1;
		double nz = 0;
		for (int i = 0; i < count; i++) {
			int a = Math.max(0, i - 1);
			int b = Math.min(count - 1, i + 1);
			double tx = path[b * 3] - path[a * 3];
			double ty = path[b * 3 + 1] - path[a * 3 + 1];
			double tz = path[b * 3 + 2] - path[a * 3 + 2];
			double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
			if (tl < 1e-9) {
				tx = 0;
				ty = 1;
				tz = 0;
				tl = 1;
			}
			tx /= tl;
			ty /= tl;
			tz /= tl;
			double dot = nx * tx + ny * ty + nz * tz;
			nx -= dot * tx;
			ny -= dot * ty;
			nz -= dot * tz;
			double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (nl < 1e-6) {
				double anyX = Math.abs(ty) < 0.9 ? 0 : 1, anyY = Math.abs(ty) < 0.9 ? 1 : 0;
				nx = anyY * tz;
				ny = -anyX * tz;
				nz = anyX * ty - anyY * tx;
				nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
			}
			nx /= nl;
			ny /= nl;
			nz /= nl;
			double bx = ty * nz - tz * ny;
			double by = tz * nx - tx * nz;
			double bz = tx * ny - ty * nx;
			double[][] ring = tubeRings[i & 1];
			for (int s = 0; s <= sides; s++) {
				double ang = Math.PI * 2 * s / sides;
				double ca = Math.cos(ang);
				double sa = Math.sin(ang);
				double dx = nx * ca + bx * sa;
				double dy = ny * ca + by * sa;
				double dz = nz * ca + bz * sa;
				double r = radius[i];
				double[] at = ring[s];
				at[0] = path[i * 3] + dx * r;
				at[1] = path[i * 3 + 1] + dy * r;
				at[2] = path[i * 3 + 2] + dz * r;
				at[3] = dx;
				at[4] = dy;
				at[5] = dz;
			}
			if (ring0 != null) {
				for (int s = 0; s < sides; s++) {
					quad(ring0[s], ring0[s + 1], ring[s + 1], ring[s], colours[i - 1], colours[i - 1], colours[i], colours[i]);
				}
			}
			ring0 = ring;
		}
	}
	private int tubeRoom;
	private final double[][][] tubeRings = new double[2][][];
	public void crystal(double x, double y, double z, Quaternionf q, double length, double width, int argb, int facets) {
		Vector3f tmp = new Vector3f();
		double[] top = new double[3];
		double[] bottom = new double[3];
		rot(q, tmp, 0, length * 0.5, 0, top);
		rot(q, tmp, 0, -length * 0.5, 0, bottom);
		for (int i = 0; i < facets; i++) {
			double a0 = Math.PI * 2 * i / facets;
			double a1 = Math.PI * 2 * (i + 1) / facets;
			double[] w0 = new double[3];
			double[] w1 = new double[3];
			rot(q, tmp, Math.cos(a0) * width, length * 0.08, Math.sin(a0) * width, w0);
			rot(q, tmp, Math.cos(a1) * width, length * 0.08, Math.sin(a1) * width, w1);
			facet(x, y, z, top, w0, w1, argb);
			facet(x, y, z, bottom, w1, w0, argb);
		}
	}
	private void facet(double x, double y, double z, double[] a, double[] b, double[] c, int argb) {
		double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
		double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		double[] pa = {x + a[0], y + a[1], z + a[2], nx, ny, nz};
		double[] pb = {x + b[0], y + b[1], z + b[2], nx, ny, nz};
		double[] pc = {x + c[0], y + c[1], z + c[2], nx, ny, nz};
		quad(pa, pb, pc, pc, argb, argb, argb, argb);
	}
	public void box(double x, double y, double z, Quaternionf q, double hx, double hy, double hz, int argb) {
		Vector3f tmp = new Vector3f();
		double[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (double[] f : n) {
			double[] s = f[0] != 0 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
			double[] t = {f[1] * s[2] - f[2] * s[1], f[2] * s[0] - f[0] * s[2], f[0] * s[1] - f[1] * s[0]};
			double[][] corners = new double[4][];
			double[][] signs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
			double[] wn = new double[3];
			rot(q, tmp, f[0], f[1], f[2], wn);
			for (int k = 0; k < 4; k++) {
				double lx = (f[0] + s[0] * signs[k][0] + t[0] * signs[k][1]) * hx;
				double ly = (f[1] + s[1] * signs[k][0] + t[1] * signs[k][1]) * hy;
				double lz = (f[2] + s[2] * signs[k][0] + t[2] * signs[k][1]) * hz;
				double[] w = new double[3];
				rot(q, tmp, lx, ly, lz, w);
				corners[k] = new double[] {x + w[0], y + w[1], z + w[2], wn[0], wn[1], wn[2]};
			}
			quad(corners[0], corners[1], corners[2], corners[3], argb, argb, argb, argb);
		}
	}
	public void blade(double x, double y, double z, Quaternionf q, double length, double width, double thickness, double bend,
			int root, int tip) {
		Vector3f tmp = new Vector3f();
		for (int side = -1; side <= 1; side += 2) {
			int s = side;
			surface((u, v, o) -> {
				double along = u * length;
				double half = width * Math.sin(Math.PI * Math.min(1, u * 1.15 + 0.05)) * 0.5;
				double across = (v - 0.5) * 2 * half;
				double droop = -bend * u * u;
				put(o, x, y, z, q, tmp, along, droop + s * thickness * 0.5 * (1 - u), across, 0, s, 0);
			}, 8, 2, 0, (u, v) -> Fx.lerp(root, tip, u));
		}
	}
	public void feather(double x, double y, double z, Quaternionf q, double length, double width, double cup, double bend,
			int root, int tip, int shaft) {
		int along = (int) Math.max(4, Math.min(9, Math.round(length * 11)));
		int across = length > 0.3 ? 4 : 2;
		double[] vane = featherShape(length, width, cup, bend, along, across);
		int[] columns = featherColumns;
		for (int i = 0; i <= along; i++) {
			int base = Fx.lerp(root, tip, Math.pow((double) i / along, 1.3));
			columns[i] = base;

			columns[i + 16] = Fx.lerp(base, base & 0xFF000000, 0.12);
		}
		place(vane, along, across, x, y, z, q, columns, true);
		double sw = Math.abs(width) * 0.035;
		int steps = Math.max(3, along - 3);
		double[] strip = featherShaft;
		for (int j = 0; j <= 1; j++) {
			for (int i = 0; i <= steps; i++) {
				double u = (double) i / steps;
				int k = (j * (steps + 1) + i) * 6;
				strip[k] = u * length * 0.92;
				strip[k + 1] = -bend * u * u + Math.abs(width) * 0.012;
				strip[k + 2] = (j - 0.5) * 2 * sw * (1 - u * 0.8);
				strip[k + 3] = 0;
				strip[k + 4] = 1;
				strip[k + 5] = 0;
				if (j == 0) {
					columns[i] = Fx.lerp(shaft, tip, u * 0.8);
				}
			}
		}
		place(strip, steps, 1, x, y, z, q, columns, false);
	}
	private final double[] featherShaft = new double[16 * 2 * 6];
	private final int[] featherColumns = new int[32];
	private static final int FEATHER_SLOTS = 2048;
	private static final double[] FEATHER_KEYS = new double[FEATHER_SLOTS * 4];
	private static final double[][] FEATHERS = new double[FEATHER_SLOTS][];

	private static double[] featherShape(double length, double width, double cup, double bend, int along, int across) {
		long mixed = Double.doubleToRawLongBits(length) * 0x9E3779B97F4A7C15L + Double.doubleToRawLongBits(width) * 0xC2B2AE3D27D4EB4FL
				+ Double.doubleToRawLongBits(bend) * 0x165667B19E3779F9L + Double.doubleToRawLongBits(cup);
		int first = (int) (mixed >>> 40) & (FEATHER_SLOTS - 1);
		int slot = first;
		for (int probe = 0; probe < 8; probe++) {
			slot = (first + probe) & (FEATHER_SLOTS - 1);
			double[] kept = FEATHERS[slot];
			if (kept == null) {
				break;
			}
			if (FEATHER_KEYS[slot * 4] == length && FEATHER_KEYS[slot * 4 + 1] == width && FEATHER_KEYS[slot * 4 + 2] == cup
					&& FEATHER_KEYS[slot * 4 + 3] == bend && kept.length == (along + 1) * (across + 1) * 6) {
				return kept;
			}
		}

		FEATHER_KEYS[slot * 4] = length;
		FEATHER_KEYS[slot * 4 + 1] = width;
		FEATHER_KEYS[slot * 4 + 2] = cup;
		FEATHER_KEYS[slot * 4 + 3] = bend;
		double[] shape = new double[(along + 1) * (across + 1) * 6];
		FEATHERS[slot] = shape;
		double[] a = new double[3];
		double[] b = new double[3];
		double[] c = new double[3];
		for (int j = 0; j <= across; j++) {
			for (int i = 0; i <= along; i++) {
				double u = (double) i / along, v = (double) j / across;
				featherPoint(u, v, length, width, cup, bend, a);
				double e = 1e-3;
				featherPoint(Math.min(1, u + e), v, length, width, cup, bend, b);
				featherPoint(u, Math.min(1, v + e), length, width, cup, bend, c);
				double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
				double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
				double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
				if (ny < 0) {
					nx = -nx;
					ny = -ny;
					nz = -nz;
				}
				double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (nl > 1e-9) {
					nx /= nl;
					ny /= nl;
					nz /= nl;
				} else {
					nx = 0;
					ny = 1;
					nz = 0;
				}
				int k = (j * (along + 1) + i) * 6;
				shape[k] = a[0];
				shape[k + 1] = a[1];
				shape[k + 2] = a[2];
				shape[k + 3] = nx;
				shape[k + 4] = ny;
				shape[k + 5] = nz;
			}
		}
		return shape;
	}

	private static void featherPoint(double u, double v, double length, double width, double cup, double bend, double[] out) {

		double quill = Math.min(1, u / 0.16);
		double tipU = Math.max(0, (u - 0.62) / 0.38);
		double envelope = Math.sqrt(quill) * Math.sqrt(Math.max(0, 1 - tipU * tipU));
		double across = v * 2 - 1;
		double side = across < 0 ? 0.36 : 0.64;
		double z = across * width * side * envelope;
		double y = -bend * u * u - cup * Math.abs(width) * across * across * envelope;
		out[0] = u * length;
		out[1] = y;
		out[2] = z;
	}
	private static void put(double[] o, double x, double y, double z, Quaternionf q, Vector3f tmp,
			double lx, double ly, double lz, double nx, double ny, double nz) {
		if (q == null) {
			o[0] = x + lx;
			o[1] = y + ly;
			o[2] = z + lz;
			o[3] = nx;
			o[4] = ny;
			o[5] = nz;
			return;
		}
		q.transform(tmp.set((float) lx, (float) ly, (float) lz));
		o[0] = x + tmp.x;
		o[1] = y + tmp.y;
		o[2] = z + tmp.z;
		q.transform(tmp.set((float) nx, (float) ny, (float) nz));
		o[3] = tmp.x;
		o[4] = tmp.y;
		o[5] = tmp.z;
	}
	private static void rot(Quaternionf q, Vector3f tmp, double lx, double ly, double lz, double[] out) {
		if (q == null) {
			out[0] = lx;
			out[1] = ly;
			out[2] = lz;
			return;
		}
		q.transform(tmp.set((float) lx, (float) ly, (float) lz));
		out[0] = tmp.x;
		out[1] = tmp.y;
		out[2] = tmp.z;
	}
	public static Quaternionf pointing(double dx, double dy, double dz) {
		Vector3f dir = new Vector3f((float) dx, (float) dy, (float) dz);
		if (dir.lengthSquared() < 1e-12) {
			return new Quaternionf();
		}
		return new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir.normalize());
	}
}
