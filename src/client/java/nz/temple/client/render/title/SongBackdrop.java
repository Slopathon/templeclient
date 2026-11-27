package nz.temple.client.render.title;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;
import nz.temple.client.modules.render.Music;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

public final class SongBackdrop {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/song");
	private static final int CONFIG_BYTES = 64;
	private static final ByteBuffer CONFIG = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private static RenderPipeline pipeline;
	private static GpuBuffer config;
	private static boolean failed;

	private static float wanted;
	private static float shown;
	private static long lastNanos = System.nanoTime();
	private static long coverVersion = -1;
	private static double coverAt = -100;
	private static double time;
	private SongBackdrop() {
	}

	public static boolean active() {
		return playing() && Minecraft.getInstance().level == null;
	}
	private static boolean playing() {
		Music m = Music.instance();
		return !failed && m != null && m.songMenuActive();
	}
	public static void request(float show) {
		wanted = Math.max(wanted, show);
	}
	public static boolean covers() {
		return shown > 0.995f && playing();
	}
	public static void render() {
		long now = System.nanoTime();
		float dt = (float) Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		time += dt;
		float target = playing() ? wanted : 0;
		wanted = 0;
		shown = target >= shown ? target : Math.max(target, shown - dt * 3);
		if (shown <= 0.002f || failed) {
			return;
		}
		Music m = Music.instance();
		try {
			draw(m, shown);
		} catch (RuntimeException e) {
			failed = true;
			LOGGER.error("Song backdrop disabled", e);
		}
	}
	private static void draw(Music m, float show) {
		Minecraft mc = Minecraft.getInstance();
		RenderTarget main = mc.gameRenderer.mainRenderTarget();
		ensure();
		if (m.coverVersion() != coverVersion) {
			coverVersion = m.coverVersion();
			coverAt = time;
		}
		Identifier blur = m.blurCover();
		AbstractTexture tex = blur != null ? mc.getTextureManager().getTexture(blur) : null;
		float coverIn = tex == null ? 0 : smooth((float) ((time - coverAt) / 1.1));
		int[] size = m.blurCoverSize();
		float sw = main.width;
		float sh = main.height;
		float zoom = 1.18f + 0.04f * (float) Math.sin(time * 0.11);
		float w = sw * zoom;
		float h = w * size[1] / size[0];
		if (h < sh * zoom) {
			h = sh * zoom;
			w = h * size[0] / size[1];
		}
		float x = (sw - w) / 2 + (float) Math.sin(time * 0.07) * sw * 0.05f;
		float y = (sh - h) / 2 + (float) Math.cos(time * 0.09) * sh * 0.05f;
		int shade = m.coverShade();
		CONFIG.clear();
		Std140Builder.intoBuffer(CONFIG)
				.putVec4(((shade >> 16) & 0xFF) / 255f, ((shade >> 8) & 0xFF) / 255f, (shade & 0xFF) / 255f, show)
				.putVec4(x / sw, y / sh, w / sw, h / sh)
				.putVec4(coverIn * 0.9f, (float) (time % 3600), sw / Math.max(1, sh), 0);
		CONFIG.clear();
		var view = tex != null ? tex.getTextureView() : mc.getTextureManager().getTexture(MISSING).getTextureView();
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.writeToBuffer(config.slice(), CONFIG);
		try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple.nz song backdrop", main.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("SongConfig", config);
			pass.bindTexture("CoverSampler", view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			Gpu.drawFullscreen(pass);
		}
	}
	private static final Identifier MISSING = Identifier.withDefaultNamespace("textures/misc/white.png");
	private static float smooth(float t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}
	private static void ensure() {
		if (config == null) {
			pipeline();
			config = RenderSystem.getDevice().createBuffer(() -> "Temple.nz song backdrop config",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_BYTES);
		}
	}
	public static RenderPipeline pipeline() {
		if (pipeline == null) {
			pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/song_backdrop"))
					.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/song_backdrop"))
					.withBindGroupLayout(BindGroupLayout.builder().withSampler("CoverSampler").build())
					.withBindGroupLayout(BindGroupLayout.builder().withUniform("SongConfig", UniformType.UNIFORM_BUFFER).build())
					.withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM,
							ColorTargetState.WRITE_ALL))
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.build());
		}
		return pipeline;
	}
}
