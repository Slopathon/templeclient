package nz.temple.client.modules.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;
import nz.temple.module.Category;
import nz.temple.client.render.WorldPost;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public final class WetGround extends Module {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/wet-ground");
	private static final int CONFIG_BYTES = 64;
	private static volatile WetGround instance;
	private static boolean failed;
	private static RenderPipeline solid;
	private static RenderPipeline cutout;
	private static GpuBuffer configBuffer;
	private static final ByteBuffer CONFIG = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private static volatile int skyColor = 0xFF7BA4FF;
	private static volatile float sunAngle;
	private final ModeSetting style;
	private final NumberSetting puddles;
	private final NumberSetting shine;
	private final BoolSetting ripples;
	private final BoolSetting rainBoost;
	private final BoolSetting reflections;
	public WetGround() {
		super("Wet Ground", Category.COSMETICS, "Makes the ground look wet: darker soil, puddles that reflect the sky, and raindrop "
				+ "ripples. Wetter while it rains.");
		style = add(new ModeSetting("Style", List.of("Puddles", "Flooded"), "Puddles").describe("Puddles adds patches of water on damp "
				+ "ground. Flooded covers all open ground in a reflective layer of water."));
		puddles = add(new NumberSetting("Puddles", 45, 0, 100, 5, "%").describe("How much of the ground is covered by puddles.")
				.visibleWhen(() -> style.is("Puddles")));
		shine = add(new NumberSetting("Shine", 70, 0, 100, 5, "%").describe("How strongly wet ground and puddles reflect the world and the "
				+ "sun."));
		reflections = add(new BoolSetting("World reflections", true).describe("Water reflects its surroundings (trees, players, buildings "
				+ "and the sky), not only the sky colour. Costs some FPS."));
		ripples = add(new BoolSetting("Raindrops", true).describe("Raindrop ripples on the water."));
		rainBoost = add(new BoolSetting("Wetter in rain", true).describe("More puddles and a stronger shine while it rains."));
		instance = this;
	}
	public static void sky(int color) {
		skyColor = color;
	}
	public static void sun(float angle) {
		sunAngle = angle;
	}

	public static int skyColor() {
		return skyColor;
	}

	public static float sunAngle() {
		return sunAngle;
	}
	public static boolean active() {
		WetGround m = instance;
		return m != null && m.isEnabled() && !failed;
	}

	public static boolean flooded() {
		WetGround m = instance;
		return m != null && m.style.is("Flooded");
	}

	public static float coverage() {
		WetGround m = instance;
		if (m == null) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		float rain = mc.level == null || !m.rainBoost.get() ? 0 : mc.level.getRainLevel(1);
		float coverage = flooded() ? 1 : m.puddles.getFloat() / 100f;
		return coverage + (1 - coverage) * 0.55f * rain;
	}
	public static void prepare(ChunkSectionLayerGroup group) {
		WetGround m = instance;
		if (!active() || group != ChunkSectionLayerGroup.OPAQUE) {
			return;
		}
		try {
			ensurePipelines();
			if (configBuffer == null) {
				configBuffer = RenderSystem.getDevice().createBuffer(() -> "Temple.nz wet ground",
						GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_BYTES);
			}
			Minecraft mc = Minecraft.getInstance();
			float rain = mc.level == null || !m.rainBoost.get() ? 0 : mc.level.getRainLevel(1);
			boolean flooded = m.style.is("Flooded");
			boolean mirror = m.reflections.get() && !WorldPost.failed();
			float coverage = flooded ? 1 : m.puddles.getFloat() / 100f;
			coverage = coverage + (1 - coverage) * 0.55f * rain;
			float shineAmount = Math.min(1, m.shine.getFloat() / 100f * (1 + 0.35f * rain));
			float seconds = (float) ((System.nanoTime() % 3_600_000_000_000L) / 1e9);
			float sx = (float) -Math.sin(sunAngle);
			float sy = (float) Math.cos(sunAngle);
			float daylight = Math.max(0, Math.min(1, sy * 3 + 0.3f)) * (1 - 0.8f * rain);
			int sky = skyColor;
			CONFIG.clear();
			Std140Builder.intoBuffer(CONFIG)
					.putVec4(seconds, coverage, shineAmount, m.ripples.get() ? 1 + rain : 0)
					.putVec4(sx, sy, 0, daylight)
					.putVec4(((sky >> 16) & 0xFF) / 255f, ((sky >> 8) & 0xFF) / 255f, (sky & 0xFF) / 255f, rain)
					.putVec4(flooded ? 1 : 0, mirror ? 1 : 0, 0, 0);
			CONFIG.clear();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(configBuffer.slice(), CONFIG);
			if (mirror) {
				WorldPost.reflections(shineAmount, flooded ? 0.3f : 0.12f, m.ripples.get() ? (1 + rain) * (1 + rain) : 0);
			}
		} catch (RuntimeException e) {
			fail("could not prepare", e);
		}
	}
	public static RenderPipeline pipeline(ChunkSectionLayerGroup group, RenderPipeline original) {
		if (!active() || configBuffer == null || group != ChunkSectionLayerGroup.OPAQUE) {
			return original;
		}
		if (original != RenderPipelines.CUTOUT_TERRAIN && original != RenderPipelines.SOLID_TERRAIN) {
			return original;
		}
		ensurePipelines();
		return original == RenderPipelines.CUTOUT_TERRAIN ? cutout : solid;
	}
	public static boolean bind(RenderPass pass, RenderPipeline selected) {
		if (selected != null && (selected == solid || selected == cutout)) {
			pass.setUniform("WetConfig", configBuffer);
			pass.setUniform("WaveConfig", WavingFoliage.windBuffer());
			return true;
		}
		return false;
	}
	public static List<RenderPipeline> pipelines() {
		ensurePipelines();
		return List.of(solid, cutout);
	}
	private static void ensurePipelines() {
		if (solid == null) {
			solid = build(RenderPipelines.SOLID_TERRAIN, "solid");
			cutout = build(RenderPipelines.CUTOUT_TERRAIN, "cutout");
		}
	}
	private static RenderPipeline build(RenderPipeline source, String name) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/wet_ground_" + name))
				.withVertexShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/foliage_wave"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/wet_ground"))
				.withPrimitiveTopology(Gpu.topology(source))
				.withPolygonMode(source.getPolygonMode())
				.withCull(source.isCull())
				.withDepthStencilState(java.util.Optional.ofNullable(Gpu.depthStencil(source)));
		for (var layout : Gpu.bindGroupLayouts(source)) {
			builder.withBindGroupLayout(layout);
		}
		builder.withBindGroupLayout(BindGroupLayout.builder().withUniform("WaveConfig", UniformType.UNIFORM_BUFFER).build());
		builder.withBindGroupLayout(BindGroupLayout.builder().withUniform("WetConfig", UniformType.UNIFORM_BUFFER).build());
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
		return RenderPipelines.register(builder.build());
	}
	private static void fail(String message, RuntimeException error) {
		if (!failed) {
			failed = true;
			LOGGER.error("Wet ground disabled: {}", message, error);
		}
	}
}
