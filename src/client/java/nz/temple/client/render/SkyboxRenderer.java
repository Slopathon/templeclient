package nz.temple.client.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;

import java.util.Optional;
import java.util.OptionalDouble;

public final class SkyboxRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/skybox");
	private static final float R = 10f;
	private static RenderPipeline pipeline;
	private static GpuBuffer cube;
	private static boolean failed;
	private static RenderPass into;
	private SkyboxRenderer() {
	}

	public static boolean draw(RenderTarget target, Identifier texture, int tint, float brightness, float spin, float tilt,
			float twinkle, boolean starField, int fogColor, float fogAmount, int style, float aurora, int palette,
			float constellationAzimuth, int constellation, int skyA, int skyB, boolean themedShader) {
		if (failed || target == null) {
			return false;
		}
		try {
			ensure();
			AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
			GpuSampler sampler = RenderSystem.getSamplerCache()
					.getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, true);
			Matrix4f view = Gpu.modelView().rotateX(tilt).rotateY(spin);
			Vector4f colour = new Vector4f(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, brightness);
			float seconds = (float) ((System.nanoTime() % 3_600_000_000_000L) / 1e9);
			Matrix4f skyData = new Matrix4f();
			skyData.m00(((fogColor >> 16) & 0xFF) / 255f);
			skyData.m01(((fogColor >> 8) & 0xFF) / 255f);
			skyData.m02((fogColor & 0xFF) / 255f);
			skyData.m03(Math.max(0, Math.min(1, fogAmount)));
			skyData.m10(style);
			skyData.m11(Math.max(0, Math.min(1, aurora)));
			skyData.m12(palette);
			skyData.m13(constellationAzimuth);
			skyData.m20(((skyA >> 16) & 0xFF) / 255f);
			skyData.m21(((skyA >> 8) & 0xFF) / 255f);
			skyData.m22((skyA & 0xFF) / 255f);
			skyData.m23(constellation);
			skyData.m30(((skyB >> 16) & 0xFF) / 255f);
			skyData.m31(((skyB >> 8) & 0xFF) / 255f);
			skyData.m32((skyB & 0xFF) / 255f);
			skyData.m33(themedShader ? 1 : 0);
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
					.writeTransform(view, colour, new Vector3f(seconds, twinkle, starField ? 1 : 0), skyData);
			RenderSystem.AutoStorageIndexBuffer indices = Gpu.sequentialBuffer(PrimitiveTopology.QUADS);
			GpuBuffer indexBuffer = indices.getBuffer(36);
			RenderPass own = into != null ? null
					: Gpu.openPass(RenderSystem.getDevice().createCommandEncoder(), () -> "Temple skybox", target.getColorTextureView(),
							Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty());
			try {
				RenderPass pass = own != null ? own : into;
				pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.bindTexture("Sampler0", tex.getTextureView(), sampler);
				Gpu.vertices(pass, cube);
				pass.setIndexBuffer(indexBuffer, indices.type());
				Gpu.drawIndexed(pass, 36, 0, 0);
			} finally {
				if (own != null) {
					own.close();
				}
			}
			return true;
		} catch (RuntimeException e) {
			failed = true;
			LOGGER.error("Skybox disabled: could not draw it", e);
			return false;
		}
	}
	public static void drawInto(RenderPass pass) {
		into = pass;
	}
	public static RenderPipeline pipeline() {
		ensure();
		return pipeline;
	}
	private static void ensure() {
		if (pipeline == null) {
			RenderPipeline source = RenderPipelines.END_SKY;
			RenderPipeline.Builder builder = RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/skybox"))
					.withVertexShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/skybox"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/skybox"))
					.withDepthStencilState(Optional.ofNullable(Gpu.depthStencil(source)))
					.withCull(false)
					.withPrimitiveTopology(Gpu.topology(source));
			for (var layout : Gpu.bindGroupLayouts(source)) {
				builder.withBindGroupLayout(layout);
			}
			for (int i = 0; i < nz.temple.client.compat.Gpu.colorTargets(source).size(); i++) {
				builder.withColorTargetState(i, nz.temple.client.compat.Gpu.colorTargets(source).get(i));
			}
			for (int i = 0; i < nz.temple.client.compat.Gpu.vertexBindings(source).size(); i++) {
				builder.withVertexBinding(i, nz.temple.client.compat.Gpu.vertexBindings(source).get(i));
			}
			pipeline = RenderPipelines.register(builder.build());
		}
		if (cube == null) {
			cube = buildCube();
		}
	}
	private static GpuBuffer buildCube() {
		float[][] faces = {
				{-R, -R, -R, -R, -R, R, R, -R, R, R, -R, -R},
				{-R, R, -R, R, R, -R, R, R, R, -R, R, R},
				{-R, -R, -R, R, -R, -R, R, R, -R, -R, R, -R},
				{-R, -R, R, -R, R, R, R, R, R, R, -R, R},
				{-R, -R, -R, -R, R, -R, -R, R, R, -R, -R, R},
				{R, -R, -R, R, -R, R, R, R, R, R, R, -R},
		};
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
			BufferBuilder builder = Gpu.bufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			for (float[] f : faces) {
				for (int v = 0; v < 4; v++) {
					builder.addVertex(f[v * 3], f[v * 3 + 1], f[v * 3 + 2]).setUv(0, 0).setColor(0xFFFFFFFF);
				}
			}
			try (MeshData mesh = builder.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "Temple skybox cube", 40, mesh.vertexBuffer());
			}
		}
	}
}
