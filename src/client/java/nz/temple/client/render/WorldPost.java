package nz.temple.client.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

public final class WorldPost {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/worldpost");
	public static final int MAX_SHOCKS = 4;
	private static final int CONFIG_BYTES = 64 * 2 + 16 * 5 + 16 * MAX_SHOCKS * 2 + 16 + 16 * 4 + 16 + 16 + 16 * 6;
	private static final ByteBuffer CONFIG = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private static RenderPipeline pipeline;
	private static GpuBuffer config;
	private static GpuTexture copy;
	private static GpuTextureView copyView;
	private static boolean failed;
	private static final Matrix4f viewProj = new Matrix4f();
	private static final Matrix4f invViewProj = new Matrix4f();
	private static Vec3 camera = Vec3.ZERO;
	private static boolean captured;
	private static float wetStrength;
	private static float wetF0;
	private static float wetDrops;
	private static float impact;
	private static float impactX;
	private static float impactY;
	private static final double[] impactWorld = new double[3];
	private static float impactSeed;
	private static float impactFrame;
	private static float impactStyle;
	private static int impactColor;
	private static final double[] shockCentre = new double[MAX_SHOCKS * 3];
	private static final float[] shockRadius = new float[MAX_SHOCKS];
	private static final int[] shockColor = new int[MAX_SHOCKS];
	private static final float[] shockStrength = new float[MAX_SHOCKS];
	private static int shocks;
	private static float shockWidth = 2.5f;
	private static boolean shockGrid;
	private static boolean ground;
	private static double groundBase;
	private static float groundDepth;
	private static float groundDensity;
	private static float groundReach;
	private static float groundWisps;
	private static float groundDrift;
	private static int groundColor;
	private static boolean groundTerrain;
	private static final GroundHeights heights = new GroundHeights();
	private static long lastFrame;
	private static float underwater;
	private static boolean underwaterCaustics;

	private WorldPost() {
	}
	private static float[] sunDirection(float angle) {
		return new float[] {(float) -Math.sin(angle), (float) Math.cos(angle), 0};
	}
	public static void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ctx -> capture(ctx.levelState().cameraRenderState));
		LevelRenderEvents.END_MAIN.register(context -> nz.temple.client.compat.Gpu.outsidePass(() -> apply()));
	}

	private static void capture(CameraRenderState state) {
		if (state.projectionMatrix == null || state.viewRotationMatrix == null || state.pos == null) {
			captured = false;
			return;
		}
		viewProj.set(nz.temple.client.render.LevelProjection.projection(state)).mul(state.viewRotationMatrix);
		viewProj.invert(invViewProj);
		camera = state.pos;
		captured = true;
	}

	public static void reflections(float strength, float f0, float drops) {
		wetStrength = strength;
		wetF0 = f0;
		wetDrops = drops;
	}

	public static void underwater(float strength, boolean caustics) {
		underwater = strength;
		underwaterCaustics = caustics;
	}
	public static void impact(double x, double y, double z, float strength, int seed, int frame, int style, int argb) {
		if (strength > impact) {
			impact = Math.min(1, strength);
			impactWorld[0] = x;
			impactWorld[1] = y;
			impactWorld[2] = z;
			impactSeed = (seed & 0xFFFF) / 97f;
			impactFrame = frame;
			impactStyle = style;
			impactColor = argb;
		}
	}
	private static boolean placeImpact() {
		org.joml.Vector4f p = new org.joml.Vector4f((float) (impactWorld[0] - camera.x), (float) (impactWorld[1] - camera.y),
				(float) (impactWorld[2] - camera.z), 1);
		viewProj.transform(p);
		if (p.w <= 0.05f) {
			return false;
		}
		impactX = p.x / p.w * 0.5f + 0.5f;
		impactY = p.y / p.w * 0.5f + 0.5f;
		return true;
	}
	public static void shock(double x, double y, double z, float radius, int argb, float strength, float width, boolean grid) {
		if (shocks >= MAX_SHOCKS || strength <= 0) {
			return;
		}
		shockCentre[shocks * 3] = x;
		shockCentre[shocks * 3 + 1] = y;
		shockCentre[shocks * 3 + 2] = z;
		shockRadius[shocks] = radius;
		shockColor[shocks] = argb;
		shockStrength[shocks] = strength;
		shockWidth = width;
		shockGrid = grid;
		shocks++;
	}
	private static void apply() {
		Minecraft mc = Minecraft.getInstance();
		long nowNanos = System.nanoTime();
		float dt = lastFrame == 0 ? 0.016f : Math.min(0.2f, (nowNanos - lastFrame) / 1e9f);
		lastFrame = nowNanos;
		nz.temple.client.modules.render.Ambience ambience = nz.temple.client.modules.render.Ambience.active();
		ground = ambience != null && ambience.groundFog() && mc.level != null && mc.level.dimensionType().hasSkyLight();
		if (ground) {
			groundBase = ambience.groundBase(mc, dt);
			groundDepth = ambience.groundDepth();
			groundDensity = ambience.groundDensity();
			groundReach = Math.min(ambience.groundReach(), mc.options.renderDistance().get() * 16f);
			groundWisps = ambience.groundWisps();
			groundDrift = ambience.groundDrift();
			groundColor = ambience.groundColor();
			groundTerrain = ambience.groundOnTerrain();
			if (groundTerrain) {
				heights.update(mc.level, mc.gameRenderer.mainCamera().position().x, mc.gameRenderer.mainCamera().position().z);
			}
		}
		nz.temple.client.modules.render.Atmosphere atmo = mc.level != null && mc.level.dimensionType().hasSkyLight()
				? nz.temple.client.modules.render.Atmosphere.active() : null;
		boolean wanted = wetStrength > 0 || impact > 0 || shocks > 0 || ground || underwater > 0 || atmo != null;
		try {
			if (failed || !wanted || !captured || mc.level == null) {
				return;
			}
			if (impact > 0 && !placeImpact()) {
				impact = 0;
				if (wetStrength <= 0 && shocks == 0 && !ground && underwater <= 0 && atmo == null) {
					return;
				}
			}
			RenderTarget main = mc.gameRenderer.mainRenderTarget();
			ensure(main);
			boolean zeroToOne = Gpu.depthZeroToOne();
			CONFIG.clear();
			Std140Builder b = Std140Builder.intoBuffer(CONFIG)
					.putMat4f(viewProj)
					.putMat4f(invViewProj)
					.putVec4((float) camera.x, (float) camera.y, (float) camera.z, (float) ((System.nanoTime() % 3_600_000_000_000L) / 1e9))
					.putVec4(wetStrength, wetF0, wetDrops, zeroToOne ? 1 : 0)
					.putVec4(impact, impactSeed, impactX, impactY)
					.putVec4(impactStyle, impactFrame, (float) main.width / Math.max(1, main.height), 0)
					.putVec4(((impactColor >> 16) & 0xFF) / 255f, ((impactColor >> 8) & 0xFF) / 255f, (impactColor & 0xFF) / 255f, 1);
			for (int i = 0; i < MAX_SHOCKS; i++) {
				if (i < shocks) {
					b.putVec4((float) (shockCentre[i * 3] - camera.x), (float) (shockCentre[i * 3 + 1] - camera.y),
							(float) (shockCentre[i * 3 + 2] - camera.z), shockRadius[i]);
				} else {
					b.putVec4(0, 0, 0, -1);
				}
			}
			for (int i = 0; i < MAX_SHOCKS; i++) {
				int c = i < shocks ? shockColor[i] : 0;
				b.putVec4(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, i < shocks ? shockStrength[i] : 0);
			}
			b.putVec4(shockWidth, shockGrid ? 1 : 0, shocks, 0);
			float sunAngle = nz.temple.client.modules.render.WetGround.sunAngle();
			float[] sun = sunDirection(sunAngle);
			float rain = mc.level.getRainLevel(1);
			float daylight = Math.max(0, Math.min(1, sun[1] * 3 + 0.3f)) * (1 - 0.7f * rain);
			int sky = nz.temple.client.modules.render.WetGround.skyColor();
			float r;
			float g;
			float bl;
			if (groundColor != 0) {
				r = ((groundColor >> 16) & 0xFF) / 255f;
				g = ((groundColor >> 8) & 0xFF) / 255f;
				bl = (groundColor & 0xFF) / 255f;
			} else {
				float light = 0.08f + 0.92f * Math.max(0, Math.min(1, sun[1] * 2.5f + 0.35f));
				r = (0.55f * ((sky >> 16) & 0xFF) / 255f + 0.45f * 0.86f) * light;
				g = (0.55f * ((sky >> 8) & 0xFF) / 255f + 0.45f * 0.88f) * light;
				bl = (0.55f * (sky & 0xFF) / 255f + 0.45f * 0.9f) * light;
			}
			double base = groundTerrain ? heights.maxHeight() : groundBase;
			b.putVec4((float) (base - camera.y), groundDepth, ground ? groundDensity : 0, groundReach);
			b.putVec4(groundWisps, groundDrift, daylight, 0);
			b.putVec4(r, g, bl, 0);
			b.putVec4(sun[0], sun[1], sun[2], 0);
			b.putVec4(underwater, underwaterCaustics ? 1 : 0, daylight, 0);
			b.putVec4(heights.originX(), heights.originZ(), mc.level.getMinY(), ground && groundTerrain ? 1 : 0);
			if (atmo != null) {
				b.putVec4(atmo.start(), atmo.density(), atmo.opacity(), atmo.mist());
				b.putVec4(mc.options.renderDistance().get() * 16f, atmo.sky() ? 1 : 0, 0, 0);
				for (int c : atmo.gradient()) {
					b.putVec4(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 1);
				}
			} else {
				for (int i = 0; i < 6; i++) {
					b.putVec4(0, 0, 0, 0);
				}
			}
			CONFIG.clear();
			// The height map's texture is made (and uploaded) on first use: that has to happen before the render pass opens.
			var heightView = heights.view();
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.writeToBuffer(config.slice(), CONFIG);
			encoder.copyTextureToTexture(main.getColorTexture(), copy, 0, 0, 0, 0, 0, main.width, main.height);
			try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple world post", main.getColorTextureView(), Optional.empty())) {
				pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("PostConfig", config);
				pass.bindTexture("InSampler", copyView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
				pass.bindTexture("DepthSampler", main.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.bindTexture("HeightSampler", heightView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				Gpu.drawFullscreen(pass);
			}
		} catch (RuntimeException e) {
			failed = true;
			LOGGER.error("World post effects disabled", e);
		} finally {
			wetStrength = 0;
			impact = 0;
			shocks = 0;
			ground = false;
			underwater = 0;
		}
	}
	public static boolean failed() {
		return failed;
	}
	private static void ensure(RenderTarget main) {
		if (config == null) {
			pipeline();
			config = RenderSystem.getDevice().createBuffer(() -> "Temple world post config",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_BYTES);
		}
		if (copy != null && copy.getWidth(0) == main.width && copy.getHeight(0) == main.height) {
			return;
		}
		if (copyView != null) {
			copyView.close();
		}
		if (copy != null) {
			copy.close();
		}
		GpuFormat format = Gpu.format(main.getColorTexture());
		copy = Gpu.createTexture(() -> "Temple world post copy",
				GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, format, main.width, main.height, 1, 1);
		copyView = RenderSystem.getDevice().createTextureView(copy);
	}

	public static RenderPipeline pipeline() {
		if (pipeline == null) {
			pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/world_post"))
					.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/world_post"))
					.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
					.withBindGroupLayout(BindGroupLayout.builder().withSampler("DepthSampler").build())
					.withBindGroupLayout(BindGroupLayout.builder().withSampler("HeightSampler").build())
					.withBindGroupLayout(BindGroupLayout.builder().withUniform("PostConfig", UniformType.UNIFORM_BUFFER).build())
					.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.build());
		}
		return pipeline;
	}
}
