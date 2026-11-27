package nz.temple.client.compat;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * The parts of the GPU API that changed shape between the Minecraft versions Temple is built for, behind one call each,
 * so render code reads the same everywhere. Import names are the 26.2 ones; the build rewrites the packages that moved
 * in 26.3 (renderpearlMoves in build.gradle), and on 1.21.11 the 26.x types are Temple's stand-ins (src/shim-1.21.11).
 *
 * <p>Depth is written as 26.x has it, reversed: 1 is near, 0 is far. 1.21.11's runs the usual way, so depth values
 * cross over as {@code 1 - d} and comparisons are mirrored (see the 1.21.11 CompareOp).
 */
public final class Gpu {
	// 26.3 allows no other GPU command while a render pass is open, and vanilla draws the level inside one long pass that
	// Fabric's level events fire in. Work that needs passes or uploads of its own waits until the open pass closes.
	private static boolean passOpen;
	private static boolean draining;
	private static final java.util.ArrayDeque<Runnable> DEFERRED = new java.util.ArrayDeque<>();
	private static final boolean IRIS = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris");

	private Gpu() {
	}

	/**
	 * Whether Iris is installed. From 26.3 Iris ends the level's main render pass after the solid part and opens its own
	 * for the translucent part, as Temple's MainPassSplitMixin does; with Iris there, Temple leaves the opening to it.
	 */
	public static boolean irisSplitsMainPass() {
		return IRIS;
	}

	/**
	 * Runs {@code work} now, or (from 26.3, while a render pass is open) as soon as that pass closes, in the order it
	 * was asked for. Before 26.3 no pass is ever open between Temple's own draws, so it always runs at once.
	 */
	public static void outsidePass(Runnable work) {
		if (passOpen) {
			DEFERRED.add(work);
		} else {
			work.run();
		}
	}

	/** Called by the render pass tracking mixins (26.3). */
	public static void passOpened() {
		passOpen = true;
	}

	/** Called by the render pass tracking mixins (26.3): runs the work that waited for the pass. */
	public static void passClosed() {
		passOpen = false;
		if (draining) {
			return;
		}
		draining = true;
		try {
			Runnable next;
			while (!passOpen && (next = DEFERRED.poll()) != null) {
				// This runs inside vanilla's close(): one failing piece of work must not take the frame down with it.
				try {
					next.run();
				} catch (RuntimeException e) {
					org.slf4j.LoggerFactory.getLogger("templenz/gpu").error("Deferred render work failed", e);
				}
			}
		} finally {
			draining = false;
		}
	}

	// ---- pipelines ------------------------------------------------------------------------------------------------

	/**
	 * What {@code RenderPass.setPipeline} takes: the pipeline itself before 26.3; from 26.3 its compiled form, which the
	 * game's pipeline cache compiles the first time it is asked for.
	 */
	//? if >=26.3 {
	/*public static com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline pipeline(RenderPipeline pipeline) {
		return RenderSystem.getCompiledPipeline(pipeline);
	}
	*///?} else {
	public static RenderPipeline pipeline(RenderPipeline pipeline) {
		return pipeline;
	}
	//?}

	/** Compiles {@code pipeline} now. False when its shaders don't compile on this machine, so the effect can stay off. */
	public static boolean precompile(RenderPipeline pipeline) {
		//? if >=26.3 {
		/*try {
			return RenderSystem.getCompiledPipelineNullable(pipeline) != null;
		} catch (RuntimeException e) {
			return false;
		}
		*///?} else {
		return RenderSystem.getDevice().precompilePipeline(pipeline).isValid();
		//?}
	}

	// Before 26.3 these are arrays with a null in each unused slot, which List.of would refuse. 1.21.11 has one of each.
	/** A pipeline's colour targets, in slot order. */
	public static List<ColorTargetState> colorTargets(RenderPipeline pipeline) {
		//? if >=26.3 {
		/*return pipeline.getColorTargetStates();
		*///?} else if >=26 {
		return java.util.Arrays.asList(pipeline.getColorTargetStates());
		//?} else {
		/*int mask = (pipeline.isWriteColor() ? ColorTargetState.WRITE_COLOR : 0) | (pipeline.isWriteAlpha() ? ColorTargetState.WRITE_ALPHA : 0);
		return List.of(new ColorTargetState(pipeline.getBlendFunction(), GpuFormat.RGBA8_UNORM, mask));
		*///?}
	}

	/** A pipeline's vertex buffer formats, in binding order. */
	public static List<VertexFormat> vertexBindings(RenderPipeline pipeline) {
		//? if >=26.3 {
		/*return pipeline.getVertexFormatBindings();
		*///?} else if >=26 {
		return java.util.Arrays.asList(pipeline.getVertexFormatBindings());
		//?} else {
		/*return List.of(pipeline.getVertexFormat());
		*///?}
	}

	/** The vertex format of a pipeline's first vertex buffer. */
	public static VertexFormat vertexFormat(RenderPipeline pipeline) {
		//? if >=26 {
		return pipeline.getVertexFormatBinding(0);
		//?} else {
		/*return pipeline.getVertexFormat();
		*///?}
	}

	public static PrimitiveTopology topology(RenderPipeline pipeline) {
		//? if >=26 {
		return pipeline.getPrimitiveTopology();
		//?} else {
		/*return PrimitiveTopology.of(pipeline.getVertexFormatMode());
		*///?}
	}

	/** A pipeline's depth test and write, or null for neither. */
	public static @Nullable DepthStencilState depthStencil(RenderPipeline pipeline) {
		//? if >=26 {
		return pipeline.getDepthStencilState();
		//?} else {
		/*com.mojang.blaze3d.platform.CompareOp test = switch (pipeline.getDepthTestFunction()) {
			case LEQUAL_DEPTH_TEST -> com.mojang.blaze3d.platform.CompareOp.GREATER_THAN_OR_EQUAL;
			case LESS_DEPTH_TEST -> com.mojang.blaze3d.platform.CompareOp.GREATER_THAN;
			case EQUAL_DEPTH_TEST -> com.mojang.blaze3d.platform.CompareOp.EQUAL;
			case GREATER_DEPTH_TEST -> com.mojang.blaze3d.platform.CompareOp.LESS_THAN;
			default -> com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS;
		};
		if (test == com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS && !pipeline.isWriteDepth()) {
			return null;
		}
		return new DepthStencilState(test, pipeline.isWriteDepth(), pipeline.getDepthBiasScaleFactor(), pipeline.getDepthBiasConstant());
		*///?}
	}

	/** A pipeline's samplers and uniforms, as the layouts 26.x groups them in (on 1.21.11, one layout with all of them). */
	public static List<BindGroupLayout> bindGroupLayouts(RenderPipeline pipeline) {
		//? if >=26 {
		return pipeline.getBindGroupLayouts();
		//?} else {
		/*BindGroupLayout.Builder layout = BindGroupLayout.builder();
		pipeline.getSamplers().forEach(layout::withSampler);
		for (var uniform : pipeline.getUniforms()) {
			if (uniform.textureFormat() != null) {
				layout.withUniform(uniform.name(), uniform.type(), format(uniform.textureFormat()));
			} else {
				layout.withUniform(uniform.name(), uniform.type());
			}
		}
		return List.of(layout.build());
		*///?}
	}

	public static Identifier vertexShader(RenderPipeline pipeline) {
		//? if >=26.3 {
		/*return pipeline.getShaders().get(com.mojang.blaze3d.shaders.ShaderType.VERTEX);
		*///?} else {
		return pipeline.getVertexShader();
		//?}
	}

	public static Identifier fragmentShader(RenderPipeline pipeline) {
		//? if >=26.3 {
		/*return pipeline.getShaders().get(com.mojang.blaze3d.shaders.ShaderType.FRAGMENT);
		*///?} else {
		return pipeline.getFragmentShader();
		//?}
	}

	// ---- textures and targets -------------------------------------------------------------------------------------

	/** An offscreen colour target, with a depth buffer when {@code depth} (32-bit float, as vanilla uses). */
	public static TextureTarget textureTarget(String label, int width, int height, boolean depth, GpuFormat color) {
		//? if >=26.3 {
		/*return new TextureTarget(label, width, height, color, depth ? GpuFormat.D32_FLOAT : null);
		*///?} else if >=26 {
		return new TextureTarget(label, width, height, depth, color);
		//?} else {
		/*return nz.temple.client.compat.legacy.LegacyTextures.withFormat(color, () -> new TextureTarget(label, width, height, depth));
		*///?}
	}

	public static GpuTexture createTexture(Supplier<String> label, int usage, GpuFormat format, int width, int height, int layers,
			int mips) {
		//? if >=26 {
		return RenderSystem.getDevice().createTexture(label, usage, format, width, height, layers, mips);
		//?} else {
		/*return nz.temple.client.compat.legacy.LegacyTextures.withFormat(format,
				() -> RenderSystem.getDevice().createTexture(label, usage, format.textureFormat(), width, height, layers, mips));
		*///?}
	}

	/** The format a texture was made in. */
	public static GpuFormat format(GpuTexture texture) {
		//? if >=26 {
		return texture.getFormat();
		//?} else {
		/*return format(texture.getFormat());
		*///?}
	}

	//? if <26 {
	/*private static GpuFormat format(com.mojang.blaze3d.textures.TextureFormat format) {
		return switch (format) {
			case DEPTH32 -> GpuFormat.D32_FLOAT;
			case RED8, RED8I -> GpuFormat.R8_UNORM;
			default -> GpuFormat.RGBA8_UNORM;
		};
	}
	*///?}

	/** Whether clip-space depth runs 0..1 (as Vulkan's and 26.x's OpenGL setup do) rather than -1..1. */
	public static boolean depthZeroToOne() {
		//? if >=26 {
		return RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		//?} else {
		/*return false;
		*///?}
	}

	// ---- passes and draws -----------------------------------------------------------------------------------------

	/** A render pass onto {@code color}: cleared to {@code clear} when given, otherwise drawn over. */
	public static RenderPass openPass(CommandEncoder encoder, Supplier<String> label, GpuTextureView color, Optional<Vector4fc> clear) {
		//? if >=26 {
		return encoder.createRenderPass(label, color, clear);
		//?} else {
		/*return encoder.createRenderPass(label, color, argb(clear));
		*///?}
	}

	/** As {@link #openPass}, with a depth buffer cleared to {@code clearDepth} (26.x's reversed depth) when given. */
	public static RenderPass openPass(CommandEncoder encoder, Supplier<String> label, GpuTextureView color, Optional<Vector4fc> clear,
			@Nullable GpuTextureView depth, OptionalDouble clearDepth) {
		//? if >=26 {
		return encoder.createRenderPass(label, color, clear, depth, clearDepth);
		//?} else {
		/*OptionalDouble legacyDepth = clearDepth.isPresent() ? OptionalDouble.of(1 - clearDepth.getAsDouble()) : clearDepth;
		return encoder.createRenderPass(label, color, argb(clear), depth, legacyDepth);
		*///?}
	}

	//? if <26 {
	/*private static java.util.OptionalInt argb(Optional<Vector4fc> colour) {
		return colour.map(c -> java.util.OptionalInt.of(argb(c))).orElse(java.util.OptionalInt.empty());
	}

	private static int argb(Vector4fc c) {
		return net.minecraft.util.ARGB.colorFromFloat(c.w(), c.x(), c.y(), c.z());
	}
	*///?}

	public static void clear(CommandEncoder encoder, GpuTexture texture, Vector4fc colour) {
		//? if >=26 {
		encoder.clearColorTexture(texture, colour);
		//?} else {
		/*encoder.clearColorTexture(texture, argb(colour));
		*///?}
	}

	/** Clears a depth texture to the far plane. */
	public static void clearDepthFar(CommandEncoder encoder, GpuTexture depth) {
		//? if >=26 {
		encoder.clearDepthTexture(depth, 0.0);
		//?} else {
		/*encoder.clearDepthTexture(depth, 1.0);
		*///?}
	}

	/** Binds {@code buffer} whole as the vertex buffer. */
	public static void vertices(RenderPass pass, GpuBuffer buffer) {
		//? if >=26 {
		pass.setVertexBuffer(0, buffer.slice());
		//?} else {
		/*pass.setVertexBuffer(0, buffer);
		*///?}
	}

	/** The fullscreen triangle every Temple post-processing shader draws (three vertices, no buffer). */
	public static void drawFullscreen(RenderPass pass) {
		//? if >=26 {
		pass.draw(3, 1, 0, 0);
		//?} else {
		/*pass.draw(0, 3);
		*///?}
	}

	/** {@code indexCount} indices from {@code firstIndex}, each vertex index offset by {@code baseVertex}; one instance. */
	public static void drawIndexed(RenderPass pass, int indexCount, int firstIndex, int baseVertex) {
		//? if >=26 {
		pass.drawIndexed(indexCount, 1, firstIndex, baseVertex, 0);
		//?} else {
		/*pass.drawIndexed(baseVertex, firstIndex, indexCount, 1);
		*///?}
	}

	/** Indices the shared sequential index buffers are grown to before a frame's level is drawn. */
	private static final int RESERVED_INDICES = 1 << 19;

	/**
	 * Grows the game's shared sequential index buffers to their full size, before the level is drawn. Growing one frees
	 * the old buffer, and vanilla binds it for terrain early in the frame and draws with it later; Temple's effects
	 * asking for more indices in between left vanilla drawing from a freed buffer, which crashed 1.21.11's OpenGL
	 * driver. Called as each frame's level rendering starts.
	 */
	public static void reserveSequentialIndices() {
		for (PrimitiveTopology topology : new PrimitiveTopology[] {PrimitiveTopology.QUADS, PrimitiveTopology.TRIANGLES,
				PrimitiveTopology.LINES}) {
			sequentialBuffer(topology).getBuffer(RESERVED_INDICES);
		}
	}

	/** The game's shared index buffer for drawing {@code topology} without an index buffer of one's own. */
	public static RenderSystem.AutoStorageIndexBuffer sequentialBuffer(PrimitiveTopology topology) {
		//? if >=26 {
		return RenderSystem.getSequentialBuffer(topology);
		//?} else {
		/*return RenderSystem.getSequentialBuffer(topology.mode);
		*///?}
	}

	/** Uploads {@code width} x {@code height} RGBA8 pixels from {@code data} to the top-left of {@code texture}'s first level. */
	public static void writeRgba(GpuTexture texture, java.nio.ByteBuffer data, int width, int height) {
		//? if >=26 {
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, data, 0, 0, 0, 0, width, height);
		//?} else {
		/*RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, data, com.mojang.blaze3d.platform.NativeImage.Format.RGBA,
				0, 0, 0, 0, width, height);
		*///?}
	}

	/** A vertex builder writing {@code format} vertices for {@code topology} into {@code bytes}. */
	public static com.mojang.blaze3d.vertex.BufferBuilder bufferBuilder(com.mojang.blaze3d.vertex.ByteBufferBuilder bytes,
			PrimitiveTopology topology, VertexFormat format) {
		//? if >=26 {
		return new com.mojang.blaze3d.vertex.BufferBuilder(bytes, topology, format);
		//?} else {
		/*return new com.mojang.blaze3d.vertex.BufferBuilder(bytes, topology.mode, format);
		*///?}
	}

	/** Draws {@code view} into the GUI over x0..x1, y0..y1 with the given texture coordinates. */
	public static void blit(net.minecraft.client.gui.GuiGraphicsExtractor g, GpuTextureView view, com.mojang.blaze3d.textures.GpuSampler sampler,
			int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1) {
		//? if >=26 {
		g.blit(view, sampler, x0, y0, x1, y1, u0, u1, v0, v1);
		//?} else {
		/*((nz.temple.client.mixin.LegacyGuiBlitInvoker) g).templenz$submitBlit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
				view, sampler, x0, y0, x1, y1, u0, u1, v0, v1, -1);
		*///?}
	}

	/** A copy of the current model-view matrix. */
	public static Matrix4f modelView() {
		//? if >=26 {
		return RenderSystem.getModelViewMatrixCopy();
		//?} else {
		/*return new Matrix4f(RenderSystem.getModelViewMatrix());
		*///?}
	}
}
