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
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;
import nz.temple.client.modules.render.Ambience;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

public final class ScreenFilter {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/filter");
	private static final int CONFIG_BYTES = 64;
	private static final ByteBuffer CONFIG = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private static RenderPipeline pipeline;
	private static GpuBuffer config;
	private static GpuTexture copy;
	private static GpuTextureView copyView;
	private static boolean failed;
	private static boolean done;
	/** Set when the world was drawn this frame. The filter only runs then: on a frame where the world is not redrawn the
	 *  screen still holds the last, already filtered picture, and grading it again every frame darkens it to black. */
	private static boolean worldDrawn;
	private ScreenFilter() {
	}

	public static void register() {
		LevelRenderEvents.END_MAIN.register(context -> {
			Ambience a = Ambience.active();
			worldDrawn = true;
			done = false;
			if (a != null && !a.filterHand()) {
				done = apply(a);
			}
		});
	}
	public static void beforeGui() {
		Minecraft mc = Minecraft.getInstance();
		Ambience a = Ambience.active();
		if (!done && worldDrawn && a != null && a.filterHand() && mc.level != null) {
			apply(a);
		}
		done = false;
		worldDrawn = false;
	}
	private static boolean apply(Ambience a) {
		float[] g = a.grade();
		if (failed || g == null) {
			return false;
		}
		for (float v : g) {
			if (!Float.isFinite(v)) {
				return false;
			}
		}
		try {
			RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			if (main.width <= 0 || main.height <= 0) {
				return false;
			}
			ensure(main);
			int tint = a.tintColor();
			CONFIG.clear();
			Std140Builder.intoBuffer(CONFIG)
					.putVec4(g[0], g[1], g[2], g[3])
					.putVec4(g[4], g[5], g[6], (float) ((System.nanoTime() % 3_600_000_000_000L) / 1e9))
					.putVec4(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, (float) main.width / Math.max(1, main.height))
					.putVec4(WorldEffects.current() != null ? 1 : 0, 0, 0, 0);
			CONFIG.clear();
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.writeToBuffer(config.slice(), CONFIG);
			encoder.copyTextureToTexture(main.getColorTexture(), copy, 0, 0, 0, 0, 0, main.width, main.height);
			try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple filter", main.getColorTextureView(), Optional.empty())) {
				pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("FilterConfig", config);
				pass.bindTexture("InSampler", copyView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.bindTexture("DepthSampler", main.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				Gpu.drawFullscreen(pass);
			}
			return true;
		} catch (RuntimeException e) {
			failed = true;
			LOGGER.error("Screen filter disabled", e);
			return false;
		}
	}
	private static void ensure(RenderTarget main) {
		if (config == null) {
			pipeline();
			config = RenderSystem.getDevice().createBuffer(() -> "Temple filter config",
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
		copy = Gpu.createTexture(() -> "Temple filter copy",
				GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, format, main.width, main.height, 1, 1);
		copyView = RenderSystem.getDevice().createTextureView(copy);
	}
	public static RenderPipeline pipeline() {
		if (pipeline == null) {
			pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/screen_filter"))
					.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/screen_filter"))
					.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
					.withBindGroupLayout(BindGroupLayout.builder().withSampler("DepthSampler").build())
					.withBindGroupLayout(BindGroupLayout.builder().withUniform("FilterConfig", UniformType.UNIFORM_BUFFER).build())
					.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.build());
		}
		return pipeline;
	}
}
