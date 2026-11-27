package nz.temple.client.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class SnowVolume {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/snow");
	private static final int MAP = 128;
	private static final int MAX_SHELLS = 48;
	private static final double PERIOD = 1024;
	public record Settings(boolean enabled, float density, float flakeSize, float wind, float fall, float haze,
			float brightness, int shells, int rgb) {
	}
	private final Supplier<Settings> settings;
	private RenderPipeline pipeline;
	private GpuBuffer shellBuffer;
	private DynamicTexture heights;
	private int mapX = Integer.MIN_VALUE;
	private int mapZ;
	private int mapRow;
	private ClientLevel mapLevel;
	private boolean failed;
	public SnowVolume(Supplier<Settings> settings) {
		this.settings = settings;
	}
	public void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(ctx -> nz.temple.client.compat.Gpu.outsidePass(() -> draw(ctx)));
	}
	private void draw(LevelRenderContext context) {
		Settings s = settings.get();
		Minecraft mc = Minecraft.getInstance();
		if (failed || s == null || !s.enabled() || mc.level == null || mc.player == null) {
			return;
		}
		try {
			ensure();
			var camera = context.levelState().cameraRenderState;
			double cx = camera.pos.x;
			double cy = camera.pos.y;
			double cz = camera.pos.z;
			updateHeights(mc.level, cx, cz);
			double seconds = (System.nanoTime() % 3_600_000_000_000L) / 1e9;
			double windX = s.wind() * 0.8;
			double windZ = s.wind() * 0.45;
			double ox = wrap(cx + seconds * windX);
			double oy = wrap(cy + seconds * s.fall());
			double oz = wrap(cz + seconds * windZ);

			int renderBlocks = mc.options.getEffectiveRenderDistance() * 16;
			float maxDistance = (float) Math.max(12, Math.min(72, renderBlocks * 0.75));
			int shells = Math.max(8, Math.min(MAX_SHELLS, s.shells()));
			float near = 1.4f;
			float ratio = (float) Math.pow(maxDistance / near, 1.0 / shells);
			var window = mc.getWindow();
			double fov = Math.toRadians(mc.options.fov().get());
			float pixelAngle = (float) (2 * Math.tan(fov / 2) / Math.max(1, window.getHeight()));
			int sky = mc.level.getSkyDarken();
			float light = Math.max(0.18f, 1 - sky / 12.5f);
			Matrix4f view = new Matrix4f(Gpu.modelView()).mul(context.poseStack().last().pose());
			Vector4f colour = new Vector4f(((s.rgb() >> 16) & 0xFF) / 255f * light, ((s.rgb() >> 8) & 0xFF) / 255f * light,
					(s.rgb() & 0xFF) / 255f * light, s.brightness());
			Matrix4f data = new Matrix4f();
			data.m00(near);
			data.m01(ratio);
			data.m02(shells);
			data.m03(maxDistance);
			data.m10(s.density());
			data.m11(s.flakeSize());
			data.m12((float) (seconds % 600.0));
			data.m13(pixelAngle);
			data.m20((float) (mapX - cx));
			data.m21((float) (mapZ - cz));
			data.m22((float) cy);
			data.m23(s.haze());
			data.m30(mc.level.getMinY());
			data.m31(MAP);
			data.m32(0);
			data.m33(1);
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
					.writeTransform(view, colour, new Vector3f((float) ox, (float) oy, (float) oz), data);
			RenderSystem.AutoStorageIndexBuffer indices = Gpu.sequentialBuffer(PrimitiveTopology.QUADS);
			int indexCount = shells * 36;
			GpuBuffer indexBuffer = indices.getBuffer(MAX_SHELLS * 36);
			GpuSampler sampler = RenderSystem.getSamplerCache()
					.getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false);
			RenderTarget main = mc.gameRenderer.mainRenderTarget();
			try (RenderPass pass = Gpu.openPass(RenderSystem.getDevice().createCommandEncoder(), () -> "Temple snow",
					main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
				pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.bindTexture("Sampler0", heights.getTextureView(), sampler);
				Gpu.vertices(pass, shellBuffer);
				pass.setIndexBuffer(indexBuffer, indices.type());
				Gpu.drawIndexed(pass, indexCount, (MAX_SHELLS - shells) * 36, 0);
			}
		} catch (RuntimeException e) {
			failed = true;
			LOGGER.error("Volumetric snow disabled", e);
		}
	}
	private static double wrap(double v) {
		double r = v % PERIOD;
		return r < 0 ? r + PERIOD : r;
	}
	private void updateHeights(ClientLevel level, double cx, double cz) {
		int wantX = (int) Math.floor(cx) - MAP / 2;
		int wantZ = (int) Math.floor(cz) - MAP / 2;
		boolean full = level != mapLevel || Math.abs(wantX - mapX) > 8 || Math.abs(wantZ - mapZ) > 8;
		var pixels = heights.getPixels();
		if (full) {
			mapLevel = level;
			mapX = wantX;
			mapZ = wantZ;
			for (int z = 0; z < MAP; z++) {
				writeRow(level, pixels, z);
			}
		} else {
			for (int i = 0; i < 4; i++) {
				writeRow(level, pixels, mapRow);
				mapRow = (mapRow + 1) % MAP;
			}
		}
		heights.upload();
	}
	private void writeRow(ClientLevel level, com.mojang.blaze3d.platform.NativeImage pixels, int z) {
		int minY = level.getMinY();
		for (int x = 0; x < MAP; x++) {
			int wx = mapX + x;
			int wz = mapZ + z;
			int h = level.hasChunk(wx >> 4, wz >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - minY : 0;
			h = Math.max(0, Math.min(0xFFFF, h));
			pixels.setPixel(x, z, 0xFF000000 | ((h >> 8) << 16) | ((h & 0xFF) << 8));
		}
	}

	public static RenderPipeline buildPipeline() {
		RenderPipeline source = RenderPipelines.END_SKY;
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/snow_volume"))
				.withVertexShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/snow_volume"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/snow_volume"))
				.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withCull(false)
				.withPrimitiveTopology(Gpu.topology(source));
		for (var layout : Gpu.bindGroupLayouts(source)) {
			builder.withBindGroupLayout(layout);
		}
		for (int i = 0; i < nz.temple.client.compat.Gpu.vertexBindings(source).size(); i++) {
			builder.withVertexBinding(i, nz.temple.client.compat.Gpu.vertexBindings(source).get(i));
		}
		return RenderPipelines.register(builder.build());
	}
	private static RenderPipeline shared;
	public static RenderPipeline pipeline() {
		if (shared == null) {
			shared = buildPipeline();
		}
		return shared;
	}
	private void ensure() {
		if (pipeline == null) {
			pipeline = pipeline();
		}
		if (heights == null) {
			heights = new DynamicTexture(() -> "Temple.nz snow heights", MAP, MAP, false);
		}
		if (shellBuffer == null) {
			shellBuffer = buildShells();
		}
	}

	private static GpuBuffer buildShells() {
		float[][] faces = {
				{-1, -1, -1, -1, -1, 1, 1, -1, 1, 1, -1, -1},
				{-1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1},
				{-1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1},
				{-1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1, 1},
				{-1, -1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1},
				{1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1},
		};
		int vertices = MAX_SHELLS * 24;
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(vertices * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
			BufferBuilder builder = Gpu.bufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			for (int shell = MAX_SHELLS - 1; shell >= 0; shell--) {
				for (float[] f : faces) {
					for (int v = 0; v < 4; v++) {
						builder.addVertex(f[v * 3], f[v * 3 + 1], f[v * 3 + 2]).setUv(shell, 0).setColor(0xFFFFFFFF);
					}
				}
			}
			try (MeshData mesh = builder.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "Temple snow shells", 40, mesh.vertexBuffer());
			}
		}
	}
}
