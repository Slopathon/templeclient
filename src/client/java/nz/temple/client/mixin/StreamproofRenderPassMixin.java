package nz.temple.client.mixin;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.lwjgl.PointerBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.streamproof.PassRecorder;
import nz.temple.client.render.streamproof.StreamproofPass;
import nz.temple.client.render.streamproof.StreamproofRenderer;

import java.nio.IntBuffer;
import java.util.Collection;

/** Streamproof: a pass onto the game's frame writes down everything it does, and is played back when it closes. */
// From 26.3 RenderPass is an interface; the hooks go on its one implementation.
//? if >=26.3 {
/*@Mixin(com.mojang.renderpearl.frontend.FrontendRenderPass.class)
*///?} else if >=26 {
@Mixin(RenderPass.class)
//?} else {
/*@Mixin(com.mojang.blaze3d.opengl.GlRenderPass.class)
*///?}
public abstract class StreamproofRenderPassMixin implements StreamproofPass {
	@Unique
	private PassRecorder temple$recorder;

	@Override
	public void templenz$setRecorder(PassRecorder recorder) {
		temple$recorder = recorder;
	}

	@Inject(method = "setPipeline", at = @At("HEAD"))
	//? if >=26.3 {
	/*private void temple$pipeline(com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline pipeline, CallbackInfo ci) {
	*///?} else {
	private void temple$pipeline(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, CallbackInfo ci) {
	//?}
		if (temple$recorder != null) {
			temple$recorder.pipeline(pipeline);
		}
	}

	// 26.3 binds textures through a setUniform overload.
	//? if >=26.3 {
	/*@Inject(method = "setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;)V", at = @At("HEAD"))
	*///?} else {
	@Inject(method = "bindTexture", at = @At("HEAD"))
	//?}
	private void temple$texture(String name, GpuTextureView view, GpuSampler sampler, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.bindTexture(name, view, sampler);
		}
	}

	@Inject(method = "setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBuffer;)V", at = @At("HEAD"))
	private void temple$uniform(String name, GpuBuffer value, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.setUniform(name, value);
		}
	}

	@Inject(method = "setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"))
	private void temple$uniformSlice(String name, GpuBufferSlice value, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.setUniform(name, value);
		}
	}

	@Inject(method = "enableScissor", at = @At("HEAD"))
	private void temple$scissor(int x, int y, int width, int height, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.enableScissor(x, y, width, height);
		}
	}

	@Inject(method = "disableScissor", at = @At("HEAD"))
	private void temple$noScissor(CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.disableScissor();
		}
	}

	@Inject(method = "setIndexBuffer", at = @At("HEAD"))
	private void temple$indices(GpuBuffer buffer, IndexType type, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.setIndexBuffer(buffer, type);
		}
	}

	@Inject(method = "drawMultipleIndexed", at = @At("HEAD"))
	private <T> void temple$drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, GpuBuffer indexBuffer, IndexType indexType,
			Collection<String> uniforms, T argument, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.drawMultipleIndexed(draws, indexBuffer, indexType, uniforms, argument);
		}
	}

	//? if <26 {
	/*// 1.21.11: whole vertex buffers, single-instance draws, and none of the multi-draws.
	@Inject(method = "setVertexBuffer", at = @At("HEAD"))
	private void temple$vertices(int slot, GpuBuffer buffer, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.setVertexBuffer(slot, buffer);
		}
	}

	@Inject(method = "drawIndexed", at = @At("HEAD"))
	private void temple$drawIndexed(int baseVertex, int firstIndex, int indexCount, int instanceCount, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.drawIndexed(baseVertex, firstIndex, indexCount, instanceCount);
		}
	}

	@Inject(method = "draw", at = @At("HEAD"))
	private void temple$draw(int firstVertex, int vertexCount, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.draw(firstVertex, vertexCount);
		}
	}
	*///?} else {
	@Inject(method = "setVertexBuffer", at = @At("HEAD"))
	private void temple$vertices(int slot, GpuBufferSlice buffer, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.setVertexBuffer(slot, buffer);
		}
	}

	@Inject(method = "drawIndexed", at = @At("HEAD"))
	private void temple$drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
		}
	}

	@Inject(method = "multiDrawIndexed(Ljava/nio/IntBuffer;III)V", at = @At("HEAD"))
	private void temple$multiDrawIndexed(IntBuffer parameters, int instanceCount, int firstInstance, int drawCount, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.multiDrawIndexed(parameters, instanceCount, firstInstance, drawCount);
		}
	}

	@Inject(method = "multiDrawIndexed(Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V", at = @At("HEAD"))
	private void temple$multiDrawIndexedSeparate(PointerBuffer offsets, IntBuffer counts, IntBuffer vertices, int drawCount, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.multiDrawIndexed(offsets, counts, vertices, drawCount);
		}
	}

	@Inject(method = "drawIndexedIndirect", at = @At("HEAD"))
	private void temple$drawIndexedIndirect(GpuBufferSlice commands, int drawCount, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.drawIndexedIndirect(commands, drawCount);
		}
	}

	@Inject(method = "draw", at = @At("HEAD"))
	private void temple$draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.draw(vertexCount, instanceCount, firstVertex, firstInstance);
		}
	}

	@Inject(method = "multiDraw(Ljava/nio/IntBuffer;III)V", at = @At("HEAD"))
	private void temple$multiDraw(IntBuffer parameters, int instanceCount, int firstInstance, int drawCount, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.multiDraw(parameters, instanceCount, firstInstance, drawCount);
		}
	}

	@Inject(method = "multiDraw(Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V", at = @At("HEAD"))
	private void temple$multiDrawSeparate(IntBuffer first, IntBuffer counts, int drawCount, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.multiDraw(first, counts, drawCount);
		}
	}

	@Inject(method = "drawIndirect", at = @At("HEAD"))
	private void temple$drawIndirect(GpuBufferSlice commands, int drawCount, CallbackInfo ci) {
		if (temple$recorder != null) {
			temple$recorder.drawIndirect(commands, drawCount);
		}
	}
	//?}

	@Inject(method = "close", at = @At("TAIL"))
	private void temple$replay(CallbackInfo ci) {
		PassRecorder recorder = temple$recorder;
		if (recorder != null) {
			temple$recorder = null;
			StreamproofRenderer.onPassClosed(recorder);
		}
	}
}
