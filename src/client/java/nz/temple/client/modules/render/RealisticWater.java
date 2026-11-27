package nz.temple.client.modules.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;
import nz.temple.client.render.WorldPost;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;

public final class RealisticWater extends Module {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/realistic-water");
	private static final int CONFIG_BYTES = 16 * 5 + 64 * 2;
	public static final String VOLUMETRIC_WARNING = "Water you pour or let in is shown only as droplets. The real water is "
			+ "hidden while this is on, so it may not be where the droplets are. Swimming, flow, drowning and the server are not "
			+ "changed. The droplets stay until you turn this off.";
	public static final int TAG_STILL = 224;
	public static final int TAG_FLOW = 192;
	public static final int TAG_FALLING = 160;
	public static final int TAG_HIDDEN = 150;
	private static final java.util.Set<Long> HIDDEN = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static volatile RealisticWater instance;
	private static volatile boolean tagging;
	private static final ThreadLocal<int[]> CURRENT = ThreadLocal.withInitial(() -> new int[] {255});
	private static boolean failed;
	private static RenderPipeline pipeline;
	private static GpuBuffer configBuffer;
	private static final ByteBuffer CONFIG = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private static GpuTexture sceneColor;
	private static GpuTextureView sceneColorView;
	private static GpuTexture sceneDepth;
	private static GpuTextureView sceneDepthView;
	private static final Matrix4f viewProj = new Matrix4f();
	private static final Matrix4f invViewProj = new Matrix4f();
	private static Vec3 camera = Vec3.ZERO;
	private static boolean captured;
	private static volatile boolean underwater;
	private final NumberSetting intensity;
	private final NumberSetting waves;
	private final BoolSetting reflections;
	private final BoolSetting foam;
	private final BoolSetting caustics;
	private final BoolSetting underwaterView;
	private final BoolSetting volumetric;
	public RealisticWater() {
		super("Realistic Water", Category.COSMETICS, "Draws water like a shader pack: waves, reflections, refraction, depth "
				+ "colour, foam and caustics.");
		intensity = add(new NumberSetting("Intensity", 100, 0, 100, 5, "%").describe("How strongly the new look replaces "
				+ "vanilla water."));
		waves = add(new NumberSetting("Waves", 60, 0, 100, 5, "%").describe("How high the waves roll."));
		reflections = add(new BoolSetting("World reflections", true).describe("Water reflects its surroundings, not only the "
				+ "sky colour. Costs some FPS."));
		foam = add(new BoolSetting("Foam", true).describe("Foam along the shore and around things in the water."));
		caustics = add(new BoolSetting("Caustics", true).describe("Patterns of light on the bed of shallow water."));
		underwaterView = add(new BoolSetting("Underwater", true).describe("Light shafts, caustics and clearer colour under "
				+ "water, in place of the vanilla overlay."));
		volumetric = add(new BoolSetting("Volumetric", false).enableWarning(VOLUMETRIC_WARNING)
				.describe("Water you pour from a bucket, or let in by breaking a block, becomes a block's worth of "
						+ "droplets that flow, spread, split and join again. The real water it makes is hidden."));
		instance = this;
	}
	public static void register() {
		LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(ctx -> capture(ctx.levelState().cameraRenderState));
		nz.temple.client.render.water.DropletWater.register();
		nz.temple.client.render.water.DropletRenderer.register();
	}
	@Override
	protected void onEnable() {
		failed = false;
		tagging = true;
		rebuild();
	}
	@Override
	protected void onDisable() {
		tagging = false;
		underwater = false;
		rebuild();
	}
	private static void rebuild() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.level != null) {
				mc.levelExtractor.allChanged();
			}
		});
	}
	public boolean volumetric() {
		return volumetric.get();
	}
	public static void beginFluid(BlockGetter level, BlockPos pos, FluidState fluid) {
		int[] current = CURRENT.get();
		if (!tagging || !fluid.is(FluidTags.WATER)) {
			current[0] = 255;
			return;
		}
		if (!HIDDEN.isEmpty() && HIDDEN.contains(pos.asLong())) {
			current[0] = TAG_HIDDEN;
			return;
		}
		if (!fluid.isSource() && fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING)) {
			current[0] = TAG_FALLING;
			return;
		}
		Vec3 flow = fluid.getFlow(level, pos);
		if (flow.x == 0 && flow.z == 0) {
			current[0] = TAG_STILL;
			return;
		}
		double angle = Math.atan2(flow.z, flow.x);
		int step = (int) Math.round(angle / (Math.PI * 2) * 32) & 31;
		current[0] = TAG_FLOW + step;
	}
	public static int tagVertex(int argb) {
		int tag = CURRENT.get()[0];
		return tag == 255 ? argb : (tag << 24) | (argb & 0xFFFFFF);
	}
	public static void hide(BlockPos pos, boolean hidden) {
		boolean changed = hidden ? HIDDEN.add(pos.asLong()) : HIDDEN.remove(pos.asLong());
		Minecraft mc = Minecraft.getInstance();
		if (changed && mc.level != null) {
			int sx = net.minecraft.core.SectionPos.blockToSectionCoord(pos.getX());
			int sy = net.minecraft.core.SectionPos.blockToSectionCoord(pos.getY());
			int sz = net.minecraft.core.SectionPos.blockToSectionCoord(pos.getZ());
			mc.level.setSectionRangeDirty(sx, sy, sz, sx, sy, sz);
		}
	}
	public static boolean hidden(BlockPos pos) {
		return HIDDEN.contains(pos.asLong());
	}
	public static boolean active() {
		RealisticWater m = instance;
		return m != null && m.isEnabled() && tagging && !failed;
	}

	public static boolean underwaterView() {
		return active() && underwater;
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

	public static void prepare(ChunkSectionLayerGroup group) {
		RealisticWater m = instance;
		Minecraft mc = Minecraft.getInstance();
		if (group == ChunkSectionLayerGroup.OPAQUE) {
			nz.temple.client.render.water.DropletWater.frame(active() && m.volumetric.get());
			underwater = active() && m.underwaterView.get() && mc.player != null
					&& mc.gameRenderer.mainCamera().getFluidInCamera() == net.minecraft.world.level.material.FogType.WATER;
			if (underwater) {
				WorldPost.underwater(m.intensity.getFloat() / 100f, m.caustics.get());
			}
			return;
		}
		if (!active() || !captured || group != ChunkSectionLayerGroup.TRANSLUCENT || mc.level == null) {
			return;
		}
		try {
			ensure(mc.gameRenderer.mainRenderTarget());
			RenderTarget main = mc.gameRenderer.mainRenderTarget();
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.copyTextureToTexture(main.getColorTexture(), sceneColor, 0, 0, 0, 0, 0, main.width, main.height);
			encoder.copyTextureToTexture(main.getDepthTexture(), sceneDepth, 0, 0, 0, 0, 0, main.width, main.height);
			float seconds = (float) ((System.nanoTime() % 3_600_000_000_000L) / 1e9);
			float rain = mc.level.getRainLevel(1);
			float sunAngle = WetGround.sunAngle();
			float sx = (float) -Math.sin(sunAngle);
			float sy = (float) Math.cos(sunAngle);
			float daylight = Math.max(0, Math.min(1, sy * 3 + 0.3f)) * (1 - 0.8f * rain);
			int sky = WetGround.skyColor();
			boolean zeroToOne = Gpu.depthZeroToOne();
			CONFIG.clear();
			Std140Builder.intoBuffer(CONFIG)
					.putVec4(seconds, m.intensity.getFloat() / 100f, m.waves.getFloat() / 100f, m.reflections.get() ? 1 : 0)
					.putVec4(m.foam.get() ? 1 : 0, m.caustics.get() ? 1 : 0, underwater ? 1 : 0, zeroToOne ? 1 : 0)
					.putVec4(sx, sy, 0, daylight)
					.putVec4(((sky >> 16) & 0xFF) / 255f, ((sky >> 8) & 0xFF) / 255f, (sky & 0xFF) / 255f, rain)
					.putVec4((float) (camera.x % 8192), (float) camera.y, (float) (camera.z % 8192), 0)
					.putMat4f(viewProj)
					.putMat4f(invViewProj);
			CONFIG.clear();
			encoder.writeToBuffer(configBuffer.slice(), CONFIG);
		} catch (RuntimeException e) {
			fail("could not prepare", e);
		}
	}
	public static RenderPipeline pipeline(ChunkSectionLayerGroup group, RenderPipeline original) {
		if (!active() || !captured || sceneColorView == null || group != ChunkSectionLayerGroup.TRANSLUCENT
				|| original != RenderPipelines.TRANSLUCENT_TERRAIN) {
			return original;
		}
		return pipeline();
	}
	public static boolean bind(RenderPass pass, RenderPipeline selected) {
		if (selected == null || selected != pipeline) {
			return false;
		}
		pass.setUniform("WaterConfig", configBuffer);
		pass.bindTexture("SceneSampler", sceneColorView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		pass.bindTexture("SceneDepthSampler", sceneDepthView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
		return true;
	}
	private static void ensure(RenderTarget main) {
		pipeline();
		if (configBuffer == null) {
			configBuffer = RenderSystem.getDevice().createBuffer(() -> "Temple.nz water",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_BYTES);
		}
		if (sceneColor != null && sceneColor.getWidth(0) == main.width && sceneColor.getHeight(0) == main.height) {
			return;
		}
		close();
		var device = RenderSystem.getDevice();
		sceneColor = Gpu.createTexture(() -> "Temple.nz water scene", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
				Gpu.format(main.getColorTexture()), main.width, main.height, 1, 1);
		sceneColorView = device.createTextureView(sceneColor);
		sceneDepth = Gpu.createTexture(() -> "Temple.nz water depth", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
				GpuFormat.D32_FLOAT, main.width, main.height, 1, 1);
		sceneDepthView = device.createTextureView(sceneDepth);
	}
	private static void close() {
		if (sceneColorView != null) {
			sceneColorView.close();
			sceneColor.close();
			sceneDepthView.close();
			sceneDepth.close();
			sceneColorView = null;
		}
	}

	public static RenderPipeline pipeline() {
		if (pipeline == null) {
			RenderPipeline source = RenderPipelines.TRANSLUCENT_TERRAIN;
			RenderPipeline.Builder builder = RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/water"))
					.withVertexShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/water"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/water"))
					.withPrimitiveTopology(Gpu.topology(source))
					.withPolygonMode(source.getPolygonMode())
					.withCull(source.isCull())
					.withDepthStencilState(Optional.ofNullable(Gpu.depthStencil(source)));
			for (var layout : Gpu.bindGroupLayouts(source)) {
				builder.withBindGroupLayout(layout);
			}
			builder.withBindGroupLayout(BindGroupLayout.builder().withUniform("WaterConfig", UniformType.UNIFORM_BUFFER).build());
			builder.withBindGroupLayout(BindGroupLayout.builder().withSampler("SceneSampler").build());
			builder.withBindGroupLayout(BindGroupLayout.builder().withSampler("SceneDepthSampler").build());
			for (int i = 0; i < nz.temple.client.compat.Gpu.colorTargets(source).size(); i++) {
				builder.withColorTargetState(i, nz.temple.client.compat.Gpu.colorTargets(source).get(i));
			}
			for (int i = 0; i < nz.temple.client.compat.Gpu.vertexBindings(source).size(); i++) {
				builder.withVertexBinding(i, nz.temple.client.compat.Gpu.vertexBindings(source).get(i));
			}
			source.getShaderDefines().flags().forEach(builder::withShaderDefine);
			source.getShaderDefines().values().forEach((key, value) -> {
				try {
					if (value.contains(".")) {
						builder.withShaderDefine(key, Float.parseFloat(value));
					} else {
						builder.withShaderDefine(key, Integer.parseInt(value));
					}
				} catch (NumberFormatException e) {
					builder.withShaderDefine(key);
				}
			});
			pipeline = RenderPipelines.register(builder.build());
		}
		return pipeline;
	}
	public static List<RenderPipeline> pipelines() {
		List<RenderPipeline> all = new java.util.ArrayList<>(nz.temple.client.render.water.DropletRenderer.pipelines());
		all.add(pipeline());
		return all;
	}
	private static void fail(String message, RuntimeException error) {
		if (!failed) {
			failed = true;
			LOGGER.error("Realistic water disabled: {}", message, error);
		}
	}
}
