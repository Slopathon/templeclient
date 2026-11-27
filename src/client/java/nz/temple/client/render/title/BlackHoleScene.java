package nz.temple.client.render.title;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import nz.temple.TempleNz;
import nz.temple.client.render.Theme;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

public final class BlackHoleScene {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/black hole");
	private static final BlackHoleScene INSTANCE = new BlackHoleScene();
	private static final int CONFIG_BYTES = 7 * 16;
	private static final float DISTANCE = 18;
	private static final float ZOOM = 0.55f;
	private static final float ROLL = 0.34f;
	public static BlackHoleScene get() {
		return INSTANCE;
	}
	private boolean failed;
	private boolean wanted;
	private float blur;
	private float dim;
	private float blurShown;
	private boolean quality = true;
	private double time;
	private long lastNanos = System.nanoTime();
	private float pointerX = 0.5f;
	private float pointerY = 0.5f;
	private boolean hasPointer;
	private float leanX;
	private float leanY;
	private RenderPipeline pipeline;
	private RenderPipeline resolvePipeline;
	private GpuBuffer config;
	private GpuBuffer resolveConfig;
	private final ByteBuffer configBytes = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private final ByteBuffer resolveBytes = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
	private GpuTexture color;
	private GpuTextureView colorView;
	private int targetW;
	private int targetH;
	private BlackHoleScene() {
	}
	public void request(float blur, float dim, boolean highQuality) {
		wanted = true;
		this.blur = blur;
		this.dim = dim;
		this.quality = highQuality;
	}

	public void pointer(double x, double y, int guiW, int guiH) {
		if (!Minecraft.getInstance().isWindowActive() || x < 0 || y < 0 || x > guiW || y > guiH) {
			hasPointer = false;
			return;
		}
		pointerX = (float) (x / Math.max(1, guiW));
		pointerY = (float) (y / Math.max(1, guiH));
		hasPointer = true;
	}
	public boolean available() {
		return !failed;
	}
	public void render() {
		if (!wanted || failed) {
			wanted = false;
			return;
		}
		wanted = false;
		try {
			draw();
		} catch (RuntimeException e) {
			failed = true;
			LOGGER.error("Black hole failed; menus fall back to the flat backdrop", e);
		}
	}
	private void advance() {
		long now = System.nanoTime();
		float dt = (float) Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		time += dt;
		float k = 1 - (float) Math.exp(-dt * 7);
		blurShown += (blur - blurShown) * k;
		float lean = 1 - (float) Math.exp(-dt * 1.6);
		float tx = hasPointer ? pointerX - 0.5f : 0;
		float ty = hasPointer ? pointerY - 0.5f : 0;
		leanX += (tx - leanX) * lean;
		leanY += (ty - leanY) * lean;
	}
	private void draw() {
		Minecraft mc = Minecraft.getInstance();
		RenderTarget main = mc.gameRenderer.mainRenderTarget();
		GpuDevice device = RenderSystem.getDevice();
		if (pipeline == null) {
			buildPipelines();
		}
		advance();
		float res = quality ? 1f : 0.5f;
		if (blurShown > 0.6f) {
			res *= 0.5f;
		}
		int w = Math.max(1, Math.round(main.width * res));
		int h = Math.max(1, Math.round(main.height * res));
		ensureTarget(device, w, h);
		writeConfig(main.width / (float) Math.max(1, main.height));
		CommandEncoder encoder = device.createCommandEncoder();
		encoder.writeToBuffer(config.slice(), configBytes);
		try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple.nz black hole", colorView, Optional.empty())) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
			pass.setUniform("BlackHoleConfig", config);
			Gpu.drawFullscreen(pass);
		}
		Std140Builder.intoBuffer(resolveBytes.clear()).putVec4(blurShown * 4.5f * res, dim, 0.45f, 0);
		encoder.writeToBuffer(resolveConfig.slice(), resolveBytes.flip());
		try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple.nz black hole resolve", main.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(resolvePipeline));
			pass.setUniform("ResolveConfig", resolveConfig);
			pass.bindTexture("SceneSampler", colorView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			Gpu.drawFullscreen(pass);
		}
	}
	private void writeConfig(float aspect) {
		float elevation = 0.14f + 0.012f * (float) Math.sin(time * 0.05) - leanY * 0.05f;
		float azimuth = (float) (time * 0.01) + leanX * 0.12f;
		float distance = DISTANCE + 0.35f * (float) Math.sin(time * 0.07);
		Vector3f pos = new Vector3f((float) (Math.cos(elevation) * Math.sin(azimuth)), (float) Math.sin(elevation),
				(float) (Math.cos(elevation) * Math.cos(azimuth))).mul(distance);
		Vector3f forward = new Vector3f(pos).negate().normalize();
		Vector3f right = new Vector3f(forward).cross(0, 1, 0).normalize();
		Vector3f up = new Vector3f(right).cross(forward).normalize();
		int accent = Theme.primary();
		if (accent == 0) {
			accent = 0xFFE9B949;
		}
		configBytes.clear();
		Std140Builder b = Std140Builder.intoBuffer(configBytes);
		b.putVec4((float) (time % 3600.0), aspect, ROLL, ZOOM);
		b.putVec4(pos.x, pos.y, pos.z, quality ? 0.075f : 0.11f);
		b.putVec4(right.x, right.y, right.z, 0);
		b.putVec4(up.x, up.y, up.z, 0);
		b.putVec4(forward.x, forward.y, forward.z, 0);
		b.putVec4(linear((accent >> 16) & 0xFF), linear((accent >> 8) & 0xFF), linear(accent & 0xFF), 0.22f);
		b.putVec4(0, 0, blurShown, 0);
		configBytes.flip();
	}
	private static float linear(int c) {
		return (float) Math.pow(c / 255.0, 2.2);
	}
	private void buildPipelines() {
		pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/black_hole"))
				.withVertexShader("core/screenquad")
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/black_hole"))
				.withBindGroupLayout(BindGroupLayout.builder().withUniform("BlackHoleConfig", UniformType.UNIFORM_BUFFER).build())
				.withColorTargetState(ColorTargetState.DEFAULT)
				.withDepthStencilState(Optional.empty())
				.build();
		resolvePipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/black_hole_resolve"))
				.withVertexShader("core/screenquad")
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/title_resolve"))
				.withBindGroupLayout(BindGroupLayout.builder().withSampler("SceneSampler")
						.withUniform("ResolveConfig", UniformType.UNIFORM_BUFFER).build())
				.withColorTargetState(ColorTargetState.DEFAULT)
				.withDepthStencilState(Optional.empty())
				.build();
		GpuDevice device = RenderSystem.getDevice();
		int usage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
		config = device.createBuffer(() -> "Temple.nz black hole config", usage, CONFIG_BYTES);
		resolveConfig = device.createBuffer(() -> "Temple.nz black hole resolve config", usage, 16);
	}
	public String selfTest() {
		try {
			if (pipeline == null) {
				buildPipelines();
			}
			for (RenderPipeline p : new RenderPipeline[] {pipeline, resolvePipeline}) {
				if (!nz.temple.client.compat.Gpu.precompile(p)) {
					return p.getLocation().toString();
				}
			}
			return null;
		} catch (RuntimeException e) {
			return "black hole: " + e;
		}
	}
	private void ensureTarget(GpuDevice device, int width, int height) {
		if (color != null && width == targetW && height == targetH) {
			return;
		}
		if (colorView != null) {
			colorView.close();
		}
		if (color != null) {
			color.close();
		}
		color = Gpu.createTexture(() -> "Temple.nz black hole colour", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
				GpuFormat.RGBA8_UNORM, width, height, 1, 1);
		colorView = device.createTextureView(color);
		targetW = width;
		targetH = height;
	}
}
