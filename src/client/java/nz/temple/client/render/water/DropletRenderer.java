package nz.temple.client.render.water;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;
import nz.temple.client.modules.render.WetGround;
import nz.temple.water.Droplets;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class DropletRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/droplet-render");
	private static final float RADIUS = Droplets.SPACING * 0.85f;
	private static final int CONFIG_BYTES = 64 * 2 + 16 * 6;
	private static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_TEX_COLOR;
	private static RenderPipeline depthPipeline;
	private static RenderPipeline thickPipeline;
	private static RenderPipeline blurH;
	private static RenderPipeline blurV;
	private static RenderPipeline composite;
	private static TextureTarget depthTarget;
	private static TextureTarget thickTarget;
	private static TextureTarget blurA;
	private static TextureTarget blurB;
	private static GpuTexture scene;
	private static GpuTextureView sceneView;
	private static GpuBuffer config;
	private static GpuBuffer vertices;
	private static ByteBufferBuilder bytes;
	private static final ByteBuffer CONFIG = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private static boolean failed;
	private DropletRenderer() {
	}
	public static void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ctx -> {
			var camera = ctx.levelState().cameraRenderState;
			nz.temple.client.compat.Gpu.outsidePass(() -> draw(camera));
		});
	}

	public static List<RenderPipeline> pipelines() {
		ensurePipelines();
		return List.of(depthPipeline, thickPipeline, blurH, blurV, composite);
	}
	private static void ensurePipelines() {
		if (depthPipeline != null) {
			return;
		}
		depthPipeline = sphere("droplet_depth", false);
		thickPipeline = sphere("droplet_thick", true);
		blurH = screen("droplet_blur_h", "core/droplet_blur", true, false);
		blurV = screen("droplet_blur_v", "core/droplet_blur", false, false);
		composite = screen("droplet_composite", "core/droplet_composite", false, true);
	}
	private static RenderPipeline sphere(String name, boolean thickness) {
		return RenderPipelines.register(RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/" + name))
				.withVertexShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/droplet_sphere"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/" + name))
				.withBindGroupLayout(BindGroupLayout.builder().withUniform("DropletConfig", UniformType.UNIFORM_BUFFER).build())
				.withVertexBinding(0, FORMAT)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				.withColorTargetState(new ColorTargetState(thickness ? Optional.of(BlendFunction.ADDITIVE) : Optional.empty(),
						GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
				.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, !thickness))
				.withCull(false)
				.build());
	}
	private static RenderPipeline screen(String name, String shader, boolean horizontal, boolean last) {
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/" + name))
				.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, shader))
				.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
				.withBindGroupLayout(BindGroupLayout.builder().withUniform("DropletConfig", UniformType.UNIFORM_BUFFER).build())
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
		if (last) {
			builder.withBindGroupLayout(BindGroupLayout.builder().withSampler("ThickSampler").build());
			builder.withBindGroupLayout(BindGroupLayout.builder().withSampler("SceneSampler").build());
			builder.withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM,
					ColorTargetState.WRITE_COLOR));
		} else {
			builder.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL));
		}
		if (horizontal) {
			builder.withShaderDefine("HORIZONTAL");
		}
		return RenderPipelines.register(builder.build());
	}
	private static void draw(CameraRenderState camera) {
		if (failed || !DropletWater.active() || camera.projectionMatrix == null || camera.viewRotationMatrix == null) {
			return;
		}
		try {
			ensurePipelines();
			Minecraft mc = Minecraft.getInstance();
			RenderTarget main = mc.gameRenderer.mainRenderTarget();
			ensureTargets(main);
			Droplets.Frame frame = DropletWater.frame();
			int quads = buildSpheres(frame, camera.pos);
			if (quads == 0) {
				return;
			}
			writeConfig(mc, camera, main, frame);
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.copyTextureToTexture(main.getColorTexture(), scene, 0, 0, 0, 0, 0, main.width, main.height);
			depthTarget.copyDepthFrom(main);
			Gpu.clear(encoder, depthTarget.getColorTexture(), new Vector4f(0, 0, 0, 0));
			Gpu.clear(encoder, thickTarget.getColorTexture(), new Vector4f(0, 0, 0, 0));
			RenderSystem.AutoStorageIndexBuffer indices = Gpu.sequentialBuffer(PrimitiveTopology.QUADS);
			int indexCount = quads * 6;
			spheres(thickPipeline, thickTarget, indices, indexCount);
			spheres(depthPipeline, depthTarget, indices, indexCount);
			screenPass(blurH, depthTarget.getColorTextureView(), blurA, null);
			screenPass(blurV, blurA.getColorTextureView(), blurB, null);
			screenPass(composite, blurB.getColorTextureView(), main, thickTarget.getColorTextureView());
		} catch (RuntimeException e) {
			failed = true;
			LOGGER.error("Droplet drawing disabled", e);
		}
	}
	private static void spheres(RenderPipeline pipeline, TextureTarget target, RenderSystem.AutoStorageIndexBuffer indices, int indexCount) {
		try (RenderPass pass = Gpu.openPass(RenderSystem.getDevice().createCommandEncoder(), () -> "Temple droplets",
				target.getColorTextureView(), Optional.empty(), depthTarget.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
			pass.setUniform("DropletConfig", config);
			Gpu.vertices(pass, vertices);
			pass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
			Gpu.drawIndexed(pass, indexCount, 0, 0);
		}
	}
	private static void screenPass(RenderPipeline pipeline, GpuTextureView in, RenderTarget to, GpuTextureView thick) {
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		try (RenderPass pass = Gpu.openPass(RenderSystem.getDevice().createCommandEncoder(), () -> "Temple droplets",
				to.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DropletConfig", config);
			pass.bindTexture("InSampler", in, nearest);
			if (thick != null) {
				pass.bindTexture("ThickSampler", thick, linear);
				pass.bindTexture("SceneSampler", sceneView, linear);
			}
			Gpu.drawFullscreen(pass);
		}
	}
	private static int buildSpheres(Droplets.Frame d, Vec3 camera) {
		int n = d.count;
		if (n == 0) {
			return 0;
		}
		if (bytes == null) {
			bytes = new ByteBufferBuilder(n * 4 * FORMAT.getVertexSize());
		}
		BufferBuilder b = Gpu.bufferBuilder(bytes, PrimitiveTopology.QUADS, FORMAT);
		glide(d);
		for (int i = 0; i < n; i++) {
			float x = (float) (shownX[i] - camera.x);
			float y = (float) (shownY[i] - camera.y);
			float z = (float) (shownZ[i] - camera.z);
			int color = 0xFFFFFFFF;
			b.addVertex(x, y, z).setUv(-1, -1).setColor(color);
			b.addVertex(x, y, z).setUv(1, -1).setColor(color);
			b.addVertex(x, y, z).setUv(1, 1).setColor(color);
			b.addVertex(x, y, z).setUv(-1, 1).setColor(color);
		}
		try (MeshData mesh = b.build()) {
			if (mesh == null) {
				return 0;
			}
			ByteBuffer data = mesh.vertexBuffer();
			if (vertices == null || vertices.size() < data.remaining()) {
				if (vertices != null) {
					vertices.close();
				}
				vertices = RenderSystem.getDevice().createBuffer(() -> "Temple droplets", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
						Math.max(data.remaining(), 1 << 16));
			}
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(vertices.slice(0, data.remaining()), data);
			return n;
		}
	}
	private static float[] shownX = new float[0], shownY = new float[0], shownZ = new float[0];
	private static float[] fromX = new float[0], fromY = new float[0], fromZ = new float[0];
	private static float[] lastX = new float[0], lastY = new float[0], lastZ = new float[0];
	private static int[] lastFrame = new int[0];
	private static int frameNo;
	private static Droplets.Frame gliding;
	private static void glide(Droplets.Frame d) {
		int n = d.count;
		if (shownX.length < n) {
			int size = Math.max(n, shownX.length * 3 / 2);
			shownX = java.util.Arrays.copyOf(shownX, size);
			shownY = java.util.Arrays.copyOf(shownY, size);
			shownZ = java.util.Arrays.copyOf(shownZ, size);
			fromX = java.util.Arrays.copyOf(fromX, size);
			fromY = java.util.Arrays.copyOf(fromY, size);
			fromZ = java.util.Arrays.copyOf(fromZ, size);
		}
		int maxId = 0;
		for (int i = 0; i < n; i++) {
			maxId = Math.max(maxId, d.id[i]);
		}
		if (lastX.length <= maxId) {
			int size = Math.max(maxId + 1, lastX.length * 3 / 2);
			lastX = java.util.Arrays.copyOf(lastX, size);
			lastY = java.util.Arrays.copyOf(lastY, size);
			lastZ = java.util.Arrays.copyOf(lastZ, size);
			lastFrame = java.util.Arrays.copyOf(lastFrame, size);
		}
		if (d != gliding) {
			for (int i = 0; i < n; i++) {
				int id = d.id[i];
				boolean drawn = lastFrame[id] == frameNo && Math.abs(lastX[id] - d.lx[i]) < 0.5f && Math.abs(lastY[id] - d.ly[i]) < 0.5f
						&& Math.abs(lastZ[id] - d.lz[i]) < 0.5f;
				fromX[i] = drawn ? lastX[id] : d.lx[i];
				fromY[i] = drawn ? lastY[id] : d.ly[i];
				fromZ[i] = drawn ? lastZ[id] : d.lz[i];
			}
			gliding = d;
		}
		frameNo++;
		float t = DropletWater.between();
		for (int i = 0; i < n; i++) {
			float x = fromX[i] + (d.x[i] - fromX[i]) * t;
			float y = fromY[i] + (d.y[i] - fromY[i]) * t;
			float z = fromZ[i] + (d.z[i] - fromZ[i]) * t;
			shownX[i] = x;
			shownY[i] = y;
			shownZ[i] = z;
			int id = d.id[i];
			lastX[id] = x;
			lastY[id] = y;
			lastZ[id] = z;
			lastFrame[id] = frameNo;
		}
	}
	private static void writeConfig(Minecraft mc, CameraRenderState camera, RenderTarget main, Droplets.Frame d) {
		if (config == null) {
			config = RenderSystem.getDevice().createBuffer(() -> "Temple droplet config", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
					CONFIG_BYTES);
		}
		Matrix4f viewProj = new Matrix4f(nz.temple.client.render.LevelProjection.projection(camera)).mul(camera.viewRotationMatrix);
		Matrix4f inv = viewProj.invert(new Matrix4f());
		Matrix4f toWorld = new Matrix4f(camera.viewRotationMatrix).invert();
		Vector3f right = toWorld.transformDirection(new Vector3f(1, 0, 0)).normalize();
		Vector3f up = toWorld.transformDirection(new Vector3f(0, 1, 0)).normalize();
		boolean zeroToOne = Gpu.depthZeroToOne();
		float sunAngle = WetGround.sunAngle();
		float sx = (float) -Math.sin(sunAngle);
		float sy = (float) Math.cos(sunAngle);
		float rain = mc.level.getRainLevel(1);
		float daylight = Math.max(0, Math.min(1, sy * 3 + 0.3f)) * (1 - 0.8f * rain);
		BlockPos at = BlockPos.containing(d.x[0], d.y[0] + 0.3, d.z[0]);
		float sky = mc.level.getBrightness(LightLayer.SKY, at) / 15f;
		float block = mc.level.getBrightness(LightLayer.BLOCK, at) / 15f;
		float light = Math.max(0.06f, Math.max(sky * (0.15f + 0.85f * daylight), block * 0.85f));
		int skyColor = WetGround.skyColor();
		float pixels = RADIUS * main.height * camera.projectionMatrix.m11() * 0.5f;
		CONFIG.clear();
		Std140Builder.intoBuffer(CONFIG)
				.putMat4f(viewProj)
				.putMat4f(inv)
				.putVec4(right.x, right.y, right.z, zeroToOne ? 1 : 0)
				.putVec4(up.x, up.y, up.z, RADIUS)
				.putVec4(sx, sy, 0, daylight)
				.putVec4(((skyColor >> 16) & 0xFF) / 255f, ((skyColor >> 8) & 0xFF) / 255f, (skyColor & 0xFF) / 255f, light)
				.putVec4(1f / main.width, 1f / main.height, pixels, rain)
				.putVec4((float) ((System.nanoTime() % 3_600_000_000_000L) / 1e9), 0, 0, 0);
		CONFIG.clear();
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(config.slice(), CONFIG);
	}
	private static void ensureTargets(RenderTarget main) {
		int w = main.width;
		int h = main.height;
		if (depthTarget == null) {
			depthTarget = nz.temple.client.compat.Gpu.textureTarget("Temple droplet distance", w, h, true, GpuFormat.RGBA16_FLOAT);
			thickTarget = nz.temple.client.compat.Gpu.textureTarget("Temple droplet thickness", w, h, false, GpuFormat.RGBA16_FLOAT);
			blurA = nz.temple.client.compat.Gpu.textureTarget("Temple droplet blur A", w, h, false, GpuFormat.RGBA16_FLOAT);
			blurB = nz.temple.client.compat.Gpu.textureTarget("Temple droplet blur B", w, h, false, GpuFormat.RGBA16_FLOAT);
		} else if (depthTarget.width != w || depthTarget.height != h) {
			depthTarget.resize(w, h);
			thickTarget.resize(w, h);
			blurA.resize(w, h);
			blurB.resize(w, h);
		}
		if (scene == null || scene.getWidth(0) != w || scene.getHeight(0) != h) {
			if (sceneView != null) {
				sceneView.close();
				scene.close();
			}
			scene = Gpu.createTexture(() -> "Temple droplet scene", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
					Gpu.format(main.getColorTexture()), w, h, 1, 1);
			sceneView = RenderSystem.getDevice().createTextureView(scene);
		}
	}
}
