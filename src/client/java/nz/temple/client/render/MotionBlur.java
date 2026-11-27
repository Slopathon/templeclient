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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.function.DoubleSupplier;

public final class MotionBlur {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/motionblur");
	private static final int CONFIG_BYTES = 80;
	private static final int SAMPLES = 12;
	private static final org.joml.Matrix4f current = new org.joml.Matrix4f();
	private static final org.joml.Matrix4f previous = new org.joml.Matrix4f();
	private static final org.joml.Matrix4f reproject = new org.joml.Matrix4f();
	private static final ByteBuffer CONFIG = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private static RenderPipeline pipeline;
	private static GpuBuffer config;
	private static GpuTexture history;
	private static GpuTextureView historyView;
	private static boolean fresh = true;
	private static boolean failed;
	private static long lastFrame;
	private static Object lastLevel;
	private static DoubleSupplier strength = () -> -1;
	private MotionBlur() {
	}
	public static void register(DoubleSupplier strength) {
		MotionBlur.strength = strength;
		LevelRenderEvents.END_MAIN.register(context -> nz.temple.client.compat.Gpu.outsidePass(() -> apply()));
	}

	private static void apply() {
		double s = strength.getAsDouble();
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		Minecraft mc = Minecraft.getInstance();
		if (failed || s <= 0 || mc.level == null) {
			fresh = true;
			return;
		}
		try {
			RenderTarget main = mc.gameRenderer.mainRenderTarget();
			ensure(main);
			if (mc.level != lastLevel) {
				lastLevel = mc.level;
				fresh = true;
			}
			if (!LevelProjection.cameraViewProjection(current)) {
				mc.gameRenderer.mainCamera().getViewRotationProjectionMatrix(current);
			}
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main.getColorTexture(), history,
					0, 0, 0, 0, 0, main.width, main.height);
			if (!fresh && dt > 0) {
				reproject.set(previous).mul(new org.joml.Matrix4f(current).invert());
				float shutter = (float) (s * (1.0 / 60.0) / dt);
				CONFIG.clear();
				Std140Builder.intoBuffer(CONFIG).putMat4f(reproject).putVec4(shutter, SAMPLES, 0.06f, 0);
				CONFIG.clear();
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(config.slice(), CONFIG);
				try (RenderPass pass = Gpu.openPass(RenderSystem.getDevice().createCommandEncoder(), () -> "Temple motion blur",
						main.getColorTextureView(), Optional.empty())) {
					pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
					RenderSystem.bindDefaultUniforms(pass);
					pass.setUniform("BlurConfig", config);
					pass.bindTexture("InSampler", historyView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
					Gpu.drawFullscreen(pass);
				}
			}
			previous.set(current);
			fresh = false;
		} catch (RuntimeException e) {
			failed = true;
			LOGGER.error("Motion blur disabled", e);
		}
	}
	private static void ensure(RenderTarget main) {
		if (pipeline == null) {
			pipeline = pipeline();
			config = RenderSystem.getDevice().createBuffer(() -> "Temple motion blur config",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_BYTES);
		}
		if (history != null && history.getWidth(0) == main.width && history.getHeight(0) == main.height) {
			return;
		}
		if (historyView != null) {
			historyView.close();
		}
		if (history != null) {
			history.close();
		}
		GpuFormat format = Gpu.format(main.getColorTexture());
		history = Gpu.createTexture(() -> "Temple motion blur history",
				GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, format, main.width, main.height, 1, 1);
		historyView = RenderSystem.getDevice().createTextureView(history);
		fresh = true;
	}
	public static RenderPipeline pipeline() {
		if (pipeline == null) {
			pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/motion_blur"))
					.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/motion_blur"))
					.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
					.withBindGroupLayout(BindGroupLayout.builder().withUniform("BlurConfig", UniformType.UNIFORM_BUFFER).build())
					.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM,
							ColorTargetState.WRITE_ALL))
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.build());
		}
		return pipeline;
	}
}
