package nz.temple.client.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTexture;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.render.streamproof.PassRecorder;
import nz.temple.client.render.streamproof.StreamproofPass;
import nz.temple.client.render.streamproof.StreamproofRenderer;

/**
 * Streamproof: render passes onto the game's frame start recording, and clears and copies into it are repeated on
 * the clean frame, so the clean frame goes through everything vanilla does to the real one.
 */
// 26.3 split the encoder into an interface and its implementation; the hooks go on the implementation.
//? if >=26.3 {
/*@Mixin(com.mojang.renderpearl.frontend.FrontendCommandEncoder.class)
*///?} else if >=26 {
@Mixin(CommandEncoder.class)
//?} else {
/*@Mixin(com.mojang.blaze3d.opengl.GlCommandEncoder.class)
*///?}
public abstract class StreamproofEncoderMixin {
	//? if <26 {
	/*// 1.21.11 opens a pass from a label and its targets (the three-argument form calls this one), and clears to packed
	// ARGB colours. The pass is noted down as the 26.x descriptor Temple's 1.21.11 stand-in describes.
	@Inject(method = "createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;",
			at = @At("RETURN"))
	private void temple$record(java.util.function.Supplier<String> label, com.mojang.blaze3d.textures.GpuTextureView color,
			java.util.OptionalInt clear, com.mojang.blaze3d.textures.GpuTextureView depth, java.util.OptionalDouble clearDepth,
			CallbackInfoReturnable<RenderPass> cir) {
		RenderPassDescriptor descriptor = RenderPassDescriptor.create(label).withColorAttachment(color, clear);
		if (depth != null) {
			descriptor.withDepthAttachment(depth, clearDepth);
		}
		PassRecorder recorder = StreamproofRenderer.onPassCreated(descriptor);
		if (recorder != null) {
			((StreamproofPass) cir.getReturnValue()).templenz$setRecorder(recorder);
		}
	}

	@Inject(method = "clearColorTexture", at = @At("TAIL"))
	private void temple$clearColor(GpuTexture color, int value, CallbackInfo ci) {
		CommandEncoder self = (CommandEncoder) (Object) this;
		StreamproofRenderer.mirror(color, t -> self.clearColorTexture(t.map(color), value));
	}

	@Inject(method = "clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;D)V",
			at = @At("TAIL"))
	private void temple$clearBoth(GpuTexture color, int value, GpuTexture depth, double depthValue, CallbackInfo ci) {
		CommandEncoder self = (CommandEncoder) (Object) this;
		StreamproofRenderer.mirror(color, t -> self.clearColorAndDepthTextures(t.map(color), value, t.map(depth), depthValue));
	}

	@Inject(method = "clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;DIIII)V",
			at = @At("TAIL"))
	private void temple$clearRegion(GpuTexture color, int value, GpuTexture depth, double depthValue, int x, int y, int width,
			int height, CallbackInfo ci) {
		CommandEncoder self = (CommandEncoder) (Object) this;
		StreamproofRenderer.mirror(color, t -> self.clearColorAndDepthTextures(t.map(color), value, t.map(depth), depthValue, x, y, width, height));
	}
	*///?} else {
	@Inject(method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPass;",
			at = @At("RETURN"))
	private void temple$record(RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPass> cir) {
		PassRecorder recorder = StreamproofRenderer.onPassCreated(descriptor);
		if (recorder != null) {
			((StreamproofPass) cir.getReturnValue()).templenz$setRecorder(recorder);
		}
	}

	@Inject(method = "clearColorTexture", at = @At("TAIL"))
	private void temple$clearColor(GpuTexture color, Vector4fc value, CallbackInfo ci) {
		CommandEncoder self = (CommandEncoder) (Object) this;
		StreamproofRenderer.mirror(color, t -> self.clearColorTexture(t.map(color), value));
	}

	@Inject(method = "clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/blaze3d/textures/GpuTexture;D)V",
			at = @At("TAIL"))
	private void temple$clearBoth(GpuTexture color, Vector4fc value, GpuTexture depth, double depthValue, CallbackInfo ci) {
		CommandEncoder self = (CommandEncoder) (Object) this;
		StreamproofRenderer.mirror(color, t -> self.clearColorAndDepthTextures(t.map(color), value, t.map(depth), depthValue));
	}

	//?}

	// 26.3 added a mip level ahead of the region.
	//? if >=26.3 {
	/*@Inject(method = "clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/blaze3d/textures/GpuTexture;DIIIII)V",
			at = @At("TAIL"))
	private void temple$clearRegion(GpuTexture color, Vector4fc value, GpuTexture depth, double depthValue, int mip, int x, int y,
			int width, int height, CallbackInfo ci) {
		CommandEncoder self = (CommandEncoder) (Object) this;
		StreamproofRenderer.mirror(color,
				t -> self.clearColorAndDepthTextures(t.map(color), value, t.map(depth), depthValue, mip, x, y, width, height));
	}
	*///?} else if >=26 {
	@Inject(method = "clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/blaze3d/textures/GpuTexture;DIIII)V",
			at = @At("TAIL"))
	private void temple$clearRegion(GpuTexture color, Vector4fc value, GpuTexture depth, double depthValue, int x, int y, int width,
			int height, CallbackInfo ci) {
		CommandEncoder self = (CommandEncoder) (Object) this;
		StreamproofRenderer.mirror(color, t -> self.clearColorAndDepthTextures(t.map(color), value, t.map(depth), depthValue, x, y, width, height));
	}
	//?}

	@Inject(method = "clearDepthTexture", at = @At("TAIL"))
	private void temple$clearDepth(GpuTexture depth, double value, CallbackInfo ci) {
		CommandEncoder self = (CommandEncoder) (Object) this;
		StreamproofRenderer.mirror(depth, t -> self.clearDepthTexture(t.map(depth), value));
	}

	@Inject(method = "copyTextureToTexture", at = @At("TAIL"))
	private void temple$copy(GpuTexture source, GpuTexture destination, int mip, int destX, int destY, int sourceX, int sourceY, int width,
			int height, CallbackInfo ci) {
		CommandEncoder self = (CommandEncoder) (Object) this;
		StreamproofRenderer.mirror(destination,
				t -> self.copyTextureToTexture(t.map(source), t.map(destination), mip, destX, destY, sourceX, sourceY, width, height));
	}
}
