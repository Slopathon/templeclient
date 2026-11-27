package nz.temple.client.render.streamproof;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Everything one render pass on the game's frame did, played back afterwards onto the clean frame with Temple's own
 * draws left out. Draws are dropped by pipeline: every Temple pipeline adds something on top of the world (ESP, chams,
 * effects, glow), except the few that stand in for vanilla drawing (the custom sky, waving foliage, wet ground), which
 * are kept so the recording has no holes where they were.
 */
public final class PassRecorder {
	private static final String[] KEPT = {"pipeline/foliage_", "pipeline/wet_ground_", "pipeline/skybox"};
	/** Keyed by what the pass was given: the pipeline before 26.3, its compiled form from it. */
	private static final Map<Object, Boolean> HIDDEN = new IdentityHashMap<>();

	private final RenderPassDescriptor descriptor;
	private final List<Object> ops = new ArrayList<>();
	private boolean skipping;
	private int shownDraws;

	PassRecorder(RenderPassDescriptor descriptor) {
		this.descriptor = descriptor;
	}

	/** Whether draws with {@code pipeline} are Temple's and stay off the recording. */
	static boolean hidden(Object pipeline) {
		return HIDDEN.computeIfAbsent(pipeline, p -> {
			// A compiled pipeline (26.3) is named after the location of the pipeline it was compiled from.
			//? if >=26.3 {
			/*var location = net.minecraft.resources.Identifier.parse(((com.mojang.renderpearl.frontend.FrontendRenderPipeline) p).name());
			*///?} else {
			var location = ((RenderPipeline) p).getLocation();
			//?}
			if (!"templenz".equals(location.getNamespace())) {
				return false;
			}
			for (String kept : KEPT) {
				if (location.getPath().startsWith(kept)) {
					return false;
				}
			}
			return true;
		});
	}

	private record Pipeline(Object pipeline) {
	}

	/** A draw call: skipped on playback while a hidden pipeline is set. */
	private record Draw(Consumer<RenderPass> call) {
	}

	public void pipeline(Object pipeline) {
		skipping = hidden(pipeline);
		ops.add(new Pipeline(pipeline));
	}

	public void state(Consumer<RenderPass> call) {
		ops.add(call);
	}

	public void draw(Consumer<RenderPass> call) {
		if (!skipping) {
			shownDraws++;
		}
		ops.add(new Draw(call));
	}

	public void bindTexture(String name, GpuTextureView view, GpuSampler sampler) {
		state(p -> p.bindTexture(name, view, sampler));
	}

	public void setUniform(String name, GpuBuffer value) {
		state(p -> p.setUniform(name, value));
	}

	public void setUniform(String name, GpuBufferSlice value) {
		state(p -> p.setUniform(name, value));
	}

	public void enableScissor(int x, int y, int width, int height) {
		state(p -> p.enableScissor(x, y, width, height));
	}

	public void disableScissor() {
		state(RenderPass::disableScissor);
	}

	public void setIndexBuffer(GpuBuffer buffer, IndexType type) {
		state(p -> p.setIndexBuffer(buffer, type));
	}

	public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, GpuBuffer indexBuffer, IndexType indexType,
			Collection<String> uniforms, T argument) {
		List<RenderPass.Draw<T>> copy = new ArrayList<>(draws);
		List<String> names = new ArrayList<>(uniforms);
		draw(p -> p.drawMultipleIndexed(copy, indexBuffer, indexType, names, argument));
	}

	//? if <26 {
	/*// 1.21.11's pass binds whole buffers, and draws one instance with no instancing arguments.
	public void setVertexBuffer(int slot, GpuBuffer buffer) {
		state(p -> p.setVertexBuffer(slot, buffer));
	}

	public void drawIndexed(int baseVertex, int firstIndex, int indexCount, int instanceCount) {
		draw(p -> p.drawIndexed(baseVertex, firstIndex, indexCount, instanceCount));
	}

	public void draw(int firstVertex, int vertexCount) {
		draw(p -> p.draw(firstVertex, vertexCount));
	}
	*///?} else {
	public void setVertexBuffer(int slot, GpuBufferSlice buffer) {
		state(p -> p.setVertexBuffer(slot, buffer));
	}

	public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
		draw(p -> p.drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance));
	}

	public void multiDrawIndexed(IntBuffer parameters, int instanceCount, int firstInstance, int drawCount) {
		IntBuffer copy = copy(parameters);
		draw(p -> p.multiDrawIndexed(copy, instanceCount, firstInstance, drawCount));
	}

	public void multiDrawIndexed(PointerBuffer firstIndexOffsets, IntBuffer indexCounts, IntBuffer vertexOffsets, int drawCount) {
		PointerBuffer offsets = PointerBuffer.allocateDirect(firstIndexOffsets.remaining());
		offsets.put(firstIndexOffsets.duplicate()).flip();
		IntBuffer counts = copy(indexCounts);
		IntBuffer vertices = copy(vertexOffsets);
		draw(p -> p.multiDrawIndexed(offsets, counts, vertices, drawCount));
	}

	public void drawIndexedIndirect(GpuBufferSlice commands, int drawCount) {
		draw(p -> p.drawIndexedIndirect(commands, drawCount));
	}

	public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
		draw(p -> p.draw(vertexCount, instanceCount, firstVertex, firstInstance));
	}

	public void multiDraw(IntBuffer parameters, int instanceCount, int firstInstance, int drawCount) {
		IntBuffer copy = copy(parameters);
		draw(p -> p.multiDraw(copy, instanceCount, firstInstance, drawCount));
	}

	public void multiDraw(IntBuffer firstVertices, IntBuffer vertexCounts, int drawCount) {
		IntBuffer first = copy(firstVertices);
		IntBuffer counts = copy(vertexCounts);
		draw(p -> p.multiDraw(first, counts, drawCount));
	}

	public void drawIndirect(GpuBufferSlice commands, int drawCount) {
		draw(p -> p.drawIndirect(commands, drawCount));
	}

	private static IntBuffer copy(IntBuffer source) {
		IntBuffer copy = BufferUtils.createIntBuffer(source.remaining());
		copy.put(source.duplicate()).flip();
		return copy;
	}
	//?}

	/** Plays the pass back onto the clean frame. Called once the original pass has closed. */
	@SuppressWarnings("unchecked")
	void replay(StreamproofRenderer.Targets targets) {
		boolean clears = descriptor.depthAttachment() != null && descriptor.depthAttachment().clearValue().isPresent();
		for (var attachment : descriptor.colorAttachments()) {
			clears |= attachment != null && attachment.clearValue().isPresent();
		}
		if (shownDraws == 0 && !clears) {
			return;
		}
		// 26.3 made the descriptor an immutable record with a builder; before it the descriptor was its own builder.
		//? if >=26.3 {
		/*var copy = RenderPassDescriptor.builder(descriptor.label());
		var area = descriptor.renderArea();
		*///?} else {
		RenderPassDescriptor copy = RenderPassDescriptor.create(descriptor.label());
		var area = descriptor.renderArea;
		//?}
		for (var attachment : descriptor.colorAttachments()) {
			if (attachment == null) {
				copy.withUnusedColorAttachment();
			} else {
				copy.withColorAttachment(targets.map(attachment.textureView()), attachment.clearValue());
			}
		}
		var depth = descriptor.depthAttachment();
		if (depth != null) {
			copy.withDepthAttachment(targets.map(depth.textureView()), depth.clearValue());
		}
		if (area != null) {
			copy.withRenderArea(area);
		}
		boolean skip = false;
		//? if >=26.3 {
		/*RenderPassDescriptor built = copy.build();
		*///?} else {
		RenderPassDescriptor built = copy;
		//?}
		//? if >=26 {
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(built)) {
		//?} else {
		/*try (RenderPass pass = built.open(RenderSystem.getDevice().createCommandEncoder())) {
		*///?}
			for (Object op : ops) {
				if (op instanceof Pipeline p) {
					skip = hidden(p.pipeline());
					if (!skip) {
						//? if >=26.3 {
						/*pass.setPipeline((com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline) p.pipeline());
						*///?} else {
						pass.setPipeline((RenderPipeline) p.pipeline());
						//?}
					}
				} else if (op instanceof Draw d) {
					if (!skip) {
						d.call().accept(pass);
					}
				} else {
					((Consumer<RenderPass>) op).accept(pass);
				}
			}
		}
	}
}
