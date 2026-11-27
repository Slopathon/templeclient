package nz.temple.client.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
//? if <26.3 {
import net.minecraft.client.renderer.rendertype.OutputTarget;
//?}
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Real bloom for glowing things. Anything drawn with a render type whose output is {@link #TARGET} lands in an
 * off-screen buffer instead of the scene: chams auras, pets, cosmetics. That buffer holds a copy of the scene's depth
 * taken after the opaque terrain, so glow sources stay hidden behind walls. After the translucent features it is
 * blurred at half resolution (two rounds of a separable Gaussian, which gives a wide, soft falloff) and added back
 * onto the screen, so light seems to spill off its source into the air around it.
 */
public final class GlowBuffer {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/glow");
	private static final int CONFIG_BYTES = 16;

	private static TextureTarget glow;
	private static TextureTarget half;
	private static TextureTarget halfB;
	private static RenderPipeline blurH;
	private static RenderPipeline blurV;
	private static RenderPipeline composite;
	private static GpuBuffer config;
	private static final ByteBuffer CONFIG = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private static boolean failed;
	private static boolean prepared;

	/** The glow buffer while it is collecting light this frame, or null. */
	public static RenderTarget active() {
		return prepared ? glow : null;
	}

	private static final java.util.List<BooleanSupplier> USERS = new java.util.ArrayList<>();
	private static float spread = 1;
	private static float intensity = 1;

	// 26.3 has no per-render-type output target, so there glow sources draw straight into the scene (no bloom halo).
	//? if <26.3 {
	/** The output target glow sources draw into. Before the buffer exists it falls back to the scene. */
	public static final OutputTarget TARGET = new OutputTarget("templenz_glow",
			() -> glow != null && prepared ? glow : Minecraft.getInstance().gameRenderer.mainRenderTarget());
	//?}

	private GlowBuffer() {
	}

	/** Something that may draw glow this frame; the buffer is only prepared and composited when one says yes. */
	public static void addUser(BooleanSupplier active) {
		USERS.add(active);
	}

	/** How wide (1 = default) and bright the bloom is this frame. */
	public static void style(float spread, float intensity) {
		GlowBuffer.spread = spread;
		GlowBuffer.intensity = intensity;
	}

	public static void register() {
		LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(context -> nz.temple.client.compat.Gpu.outsidePass(GlowBuffer::begin));
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> nz.temple.client.compat.Gpu.outsidePass(GlowBuffer::finish));
	}

	private static boolean wanted() {
		if (failed) {
			return false;
		}
		for (BooleanSupplier user : USERS) {
			if (user.getAsBoolean()) {
				return true;
			}
		}
		return false;
	}

	private static void begin() {
		prepared = false;
		if (!wanted()) {
			return;
		}
		try {
			RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			ensure(main.width, main.height);
			Gpu.clear(RenderSystem.getDevice().createCommandEncoder(), glow.getColorTexture(), new Vector4f(0, 0, 0, 0));
			glow.copyDepthFrom(main);
			prepared = true;
		} catch (RuntimeException e) {
			fail("could not prepare the glow buffer", e);
		}
	}

	private static void finish() {
		if (!prepared) {
			return;
		}
		prepared = false;
		try {
			RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			CONFIG.clear();
			Std140Builder.intoBuffer(CONFIG).putVec4(spread, intensity, 0, 0);
			CONFIG.clear();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(config.slice(), CONFIG);
			pass(blurH, glow, half);
			pass(blurV, half, halfB);
			pass(blurH, halfB, half);
			pass(blurV, half, halfB);
			pass(composite, halfB, main);
		} catch (RuntimeException e) {
			fail("could not blur the glow", e);
		}
	}

	private static void pass(RenderPipeline pipeline, RenderTarget from, RenderTarget to) {
		try (RenderPass pass = Gpu.openPass(RenderSystem.getDevice().createCommandEncoder(), () -> "Temple glow",
				to.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("GlowConfig", config);
			pass.bindTexture("InSampler", from.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			Gpu.drawFullscreen(pass);
		}
	}

	private static void ensure(int width, int height) {
		if (blurH == null) {
			blurH = build("glow_blur_h", "core/glow_blur", true, false);
			blurV = build("glow_blur_v", "core/glow_blur", false, false);
			composite = build("glow_composite", "core/glow_composite", false, true);
			config = RenderSystem.getDevice().createBuffer(() -> "Temple glow config",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_BYTES);
		}
		int hw = Math.max(1, width / 2);
		int hh = Math.max(1, height / 2);
		if (glow == null) {
			glow = nz.temple.client.compat.Gpu.textureTarget("Temple glow", width, height, true, GpuFormat.RGBA8_UNORM);
			half = nz.temple.client.compat.Gpu.textureTarget("Temple glow half", hw, hh, false, GpuFormat.RGBA8_UNORM);
			halfB = nz.temple.client.compat.Gpu.textureTarget("Temple glow half B", hw, hh, false, GpuFormat.RGBA8_UNORM);
		} else if (glow.width != width || glow.height != height) {
			glow.resize(width, height);
			half.resize(hw, hh);
			halfB.resize(hw, hh);
		}
	}

	private static RenderPipeline build(String name, String shader, boolean horizontal, boolean add) {
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/" + name))
				.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, shader))
				.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
				.withBindGroupLayout(BindGroupLayout.builder().withUniform("GlowConfig", UniformType.UNIFORM_BUFFER).build())
				.withColorTargetState(new ColorTargetState(add ? Optional.of(BlendFunction.ADDITIVE) : Optional.empty(),
						GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
		if (horizontal) {
			builder.withShaderDefine("HORIZONTAL");
		}
		return RenderPipelines.register(builder.build());
	}

	/** Every pipeline, for the startup shader check. */
	public static java.util.List<RenderPipeline> pipelines() {
		if (blurH == null) {
			blurH = build("glow_blur_h", "core/glow_blur", true, false);
			blurV = build("glow_blur_v", "core/glow_blur", false, false);
			composite = build("glow_composite", "core/glow_composite", false, true);
		}
		return java.util.List.of(blurH, blurV, composite);
	}

	private static void fail(String message, RuntimeException e) {
		if (!failed) {
			failed = true;
			LOGGER.error("Glow disabled: {}", message, e);
		}
	}
}
