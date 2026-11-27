package nz.temple.client.render.light;

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
import nz.temple.client.modules.render.RealisticLighting;
import nz.temple.client.modules.render.WetGround;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;

public final class LightingPass {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/lighting");
	private static final int CONFIG_BYTES = 64 * 2 + 16 * 11;
	private static final ByteBuffer CONFIG = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private static final long GRID_BUDGET = 1_200_000L;
	private static final VoxelGrid grid = new VoxelGrid();
	private static RenderPipeline tracePipeline;
	private static RenderPipeline applyPipeline;
	private static GpuBuffer config;
	private static GpuTexture copy;
	private static GpuTextureView copyView;
	private static GpuTexture light;
	private static GpuTextureView lightView;
	private static boolean failed;
	private static final Matrix4f viewProj = new Matrix4f();
	private static final Matrix4f invViewProj = new Matrix4f();
	private static Vec3 camera = Vec3.ZERO;
	private static boolean captured;
	private LightingPass() {
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
	public static void blockChanged(net.minecraft.core.BlockPos pos) {
		grid.changed(pos.getX(), pos.getZ());
	}
	private static float smooth(float from, float to, float x) {
		float t = Math.max(0, Math.min(1, (x - from) / (to - from)));
		return t * t * (3 - 2 * t);
	}
	private static void apply() {
		RealisticLighting.Look look = RealisticLighting.look();
		Minecraft mc = Minecraft.getInstance();
		if (failed || look == null || !captured || mc.level == null || !mc.level.dimensionType().hasSkyLight()) {
			return;
		}
		try {
			float angle = WetGround.sunAngle();
			float sx = (float) -Math.sin(angle), sy = (float) Math.cos(angle);
			boolean night = sy < 0;
			float rain = mc.level.getRainLevel(1);
			float strength = look.shadows() * smooth(0.04f, 0.22f, Math.abs(sy)) * (night ? 0.45f : 1) * (1 - 0.85f * rain);

			float glow = smooth(0.0f, 0.18f, Math.abs(sy)) * (night ? 0.32f : 1) * (1 - 0.9f * rain);
			if (strength < 0.01f && look.occlusion() < 0.01f && glow < 0.01f) {
				return;
			}
			grid.update(mc.level, camera.x, camera.y, camera.z, GRID_BUDGET);
			RenderTarget main = mc.gameRenderer.mainRenderTarget();
			ensure(main, look.quality() == 2 ? 0.75f : 0.5f);
			boolean zeroToOne = Gpu.depthZeroToOne();
			float lx = night ? -sx : sx, ly = Math.abs(sy), lz = 0.03f;
			float len = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
			float warm = night ? 0 : look.warmth() * (1 - rain);
			float low = night ? 0 : 1 - smooth(0.1f, 0.45f, sy);
			float seconds = (float) ((System.nanoTime() % 3_600_000_000_000L) / 1e9);
			org.joml.Vector4f sunScreen = viewProj.transform(new org.joml.Vector4f(lx / len * 100, ly / len * 100, lz / len * 100, 1));
			CONFIG.clear();
			Std140Builder.intoBuffer(CONFIG)
					.putMat4f(viewProj)
					.putMat4f(invViewProj)
					.putVec4((float) (camera.x - grid.originX()), (float) (camera.y - grid.originY()), (float) (camera.z - grid.originZ()),
							zeroToOne ? 1 : 0)
					.putVec4(grid.originX() & VoxelGrid.SIDE - 1, grid.originY() & VoxelGrid.HEIGHT - 1, grid.originZ() & VoxelGrid.SIDE - 1,
							seconds)
					.putVec4(lx / len, ly / len, lz / len, look.softness())
					.putVec4(strength, look.occlusion(), look.quality() == 0 ? 28 : look.quality() == 1 ? 48 : 72,
							look.occlusion() < 0.01f ? 0 : look.quality() == 0 ? 2 : look.quality() == 1 ? 4 : 6)
					.putVec4(look.quality() == 0 ? 0 : 1, 1f / light.getWidth(0), 1f / light.getHeight(0),
							Boolean.getBoolean("temple.light.debug") ? 1 : 0)
					.putVec4(night ? 0.58f : 0.4f - 0.05f * warm, night ? 0.62f : 0.44f, night ? 0.8f : 0.5f + 0.16f * warm, look.sunSize())
					.putVec4(1 + (night ? 0 : 0.1f) + warm * (0.14f + 0.16f * low), 1 + (night ? 0 : 0.08f) + warm * (0.06f + 0.01f * low),
							1 + (night ? 0 : 0.03f) - warm * (0.07f + 0.18f * low), 0)
					.putVec4(look.shafts() * glow, look.dust() * glow, look.bloom() * glow, (float) main.width / Math.max(1, main.height))
					.putVec4(sunScreen.x / Math.max(sunScreen.w, 1e-4f) * 0.5f + 0.5f, sunScreen.y / Math.max(sunScreen.w, 1e-4f) * 0.5f + 0.5f,
							sunScreen.w > 0.01f ? 1 : 0, look.quality() == 0 ? 6 : look.quality() == 1 ? 10 : 16)
					.putVec4(night ? 0.55f : 1, night ? 0.66f : 0.93f - 0.3f * low, night ? 0.9f : 0.78f - 0.45f * low, look.flare() * glow)
					.putVec4((float) (camera.x % 1024.0), (float) (camera.y % 1024.0), (float) (camera.z % 1024.0), seconds);
			CONFIG.clear();
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.writeToBuffer(config.slice(), CONFIG);
			var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
			var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple light trace", lightView, Optional.empty())) {
				pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(tracePipeline));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("LightConfig", config);
				pass.bindTexture("DepthSampler", main.getDepthTextureView(), nearest);
				pass.bindTexture("VoxelSampler", grid.view(), nearest);
				Gpu.drawFullscreen(pass);
			}
			encoder.copyTextureToTexture(main.getColorTexture(), copy, 0, 0, 0, 0, 0, main.width, main.height);
			try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple light apply", main.getColorTextureView(), Optional.empty())) {
				pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(applyPipeline));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("LightConfig", config);
				pass.bindTexture("InSampler", copyView, linear);
				pass.bindTexture("DepthSampler", main.getDepthTextureView(), nearest);
				pass.bindTexture("LightSampler", lightView, linear);
				Gpu.drawFullscreen(pass);
			}
		} catch (RuntimeException e) {
			failed = true;
			LOGGER.error("Realistic lighting disabled", e);
		}
	}
	private static void ensure(RenderTarget main, float scale) {
		pipelines();
		var device = RenderSystem.getDevice();
		if (config == null) {
			config = device.createBuffer(() -> "Temple light config", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_BYTES);
		}
		if (copy == null || copy.getWidth(0) != main.width || copy.getHeight(0) != main.height) {
			if (copy != null) {
				copyView.close();
				copy.close();
			}
			copy = Gpu.createTexture(() -> "Temple light copy", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
					Gpu.format(main.getColorTexture()), main.width, main.height, 1, 1);
			copyView = device.createTextureView(copy);
		}
		int w = Math.max(1, Math.round(main.width * scale)), h = Math.max(1, Math.round(main.height * scale));
		if (light == null || light.getWidth(0) != w || light.getHeight(0) != h) {
			if (light != null) {
				lightView.close();
				light.close();
			}
			light = Gpu.createTexture(() -> "Temple light", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
					GpuFormat.RGBA8_UNORM, w, h, 1, 1);
			lightView = device.createTextureView(light);
		}
	}
	public static List<RenderPipeline> pipelines() {
		if (tracePipeline == null) {
			tracePipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/light_trace"))
					.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/light_trace"))
					.withBindGroupLayout(BindGroupLayout.builder().withSampler("DepthSampler").build())
					.withBindGroupLayout(BindGroupLayout.builder().withSampler("VoxelSampler").build())
					.withBindGroupLayout(BindGroupLayout.builder().withUniform("LightConfig", UniformType.UNIFORM_BUFFER).build())
					.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.build());
			applyPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/light_apply"))
					.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/light_apply"))
					.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
					.withBindGroupLayout(BindGroupLayout.builder().withSampler("DepthSampler").build())
					.withBindGroupLayout(BindGroupLayout.builder().withSampler("LightSampler").build())
					.withBindGroupLayout(BindGroupLayout.builder().withUniform("LightConfig", UniformType.UNIFORM_BUFFER).build())
					.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.build());
		}
		return List.of(tracePipeline, applyPipeline);
	}
}
