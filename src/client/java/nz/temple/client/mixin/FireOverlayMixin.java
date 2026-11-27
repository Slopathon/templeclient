package nz.temple.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.CleanView;

/** Clean View: the first-person fire overlay sits lower, or not at all. */
@Mixin(ScreenEffectRenderer.class)
public abstract class FireOverlayMixin {
	// 1.21.11 drew the fire straight into a buffer source.
	//? if >=26 {
	private static final String FIRE = "submitFire";
	//?} else {
	/*private static final String FIRE = "renderFire";
	*///?}

	@Unique
	private static boolean temple$lowered;

	// The target's own arguments are taken in full rather than with MixinExtras @Local: the parameter-annotation
	// attribute @Local leaves crashes ZKM's saver (see FoliageVertexMixin).
	//? if >=26 {
	@Inject(method = FIRE, at = @At("HEAD"), cancellable = true)
	private static void temple$lowerFire(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector sink,
			net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, CallbackInfo ci) {
		temple$lower(pose, ci);
	}

	@Inject(method = FIRE, at = @At("RETURN"))
	private static void temple$lowerFireDone(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector sink,
			net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, CallbackInfo ci) {
		temple$restore(pose);
	}
	//?} else {
	/*@Inject(method = FIRE, at = @At("HEAD"), cancellable = true)
	private static void temple$lowerFire(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource sink,
			net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, CallbackInfo ci) {
		temple$lower(pose, ci);
	}

	@Inject(method = FIRE, at = @At("RETURN"))
	private static void temple$lowerFireDone(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource sink,
			net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, CallbackInfo ci) {
		temple$restore(pose);
	}
	*///?}

	private static void temple$lower(PoseStack pose, CallbackInfo ci) {
		float drop = CleanView.fireDrop();
		if (drop < 0) {
			ci.cancel();
			return;
		}
		temple$lowered = drop > 0;
		if (temple$lowered) {
			pose.pushPose();
			pose.translate(0, -drop, 0);
		}
	}

	private static void temple$restore(PoseStack pose) {
		if (temple$lowered) {
			temple$lowered = false;
			pose.popPose();
		}
	}
}
