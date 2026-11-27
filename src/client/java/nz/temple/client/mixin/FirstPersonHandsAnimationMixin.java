//? if >=26.3 {
/*// From 26.3 the first-person hands are drawn here from a render state (see ItemInHandAnimationMixin for earlier
// versions). The held item is drawn by its render state's submit, wrapped below in place of the old renderItem hook.
package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.FirstPersonAnimations;

// The first-person half of the Animations module, hooked into vanilla's hand renderer (see FirstPersonAnimations).
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAnimationMixin {
	private static final String ARM_WITH_ITEM = "submitArmWithItem";

	@Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
	private void temple$swingStyle(PoseStack pose, HumanoidArm arm, float swing, CallbackInfo ci) {
		if (FirstPersonAnimations.swingStyle(pose, arm, swing)) {
			ci.cancel();
		}
	}

	@Inject(method = "applyEatTransform", at = @At("HEAD"), cancellable = true)
	private void temple$noEat(PoseStack pose, float partialTick, HumanoidArm arm, float useTicks, int useDuration, CallbackInfo ci) {
		if (FirstPersonAnimations.beforeEat(pose)) {
			ci.cancel();
		}
	}

	@Inject(method = "applyEatTransform", at = @At("RETURN"))
	private void temple$partialEat(PoseStack pose, float partialTick, HumanoidArm arm, float useTicks, int useDuration, CallbackInfo ci) {
		FirstPersonAnimations.afterEat(pose);
	}

	@WrapOperation(method = ARM_WITH_ITEM, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void temple$itemTransform(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector, int light, int overlay,
			int outline, Operation<Void> original, net.minecraft.client.renderer.state.level.PlayerRenderState playerState,
			net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState hands, float a, float b, InteractionHand hand,
			float c, ItemStack stack, float d, PoseStack armPose, SubmitNodeCollector armCollector, int armLight) {
		var player = Minecraft.getInstance().player;
		HumanoidArm main = player == null ? HumanoidArm.RIGHT : player.getMainArm();
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? main : main.getOpposite();
		boolean pushed = FirstPersonAnimations.beforeItem(pose, arm == HumanoidArm.RIGHT, hand == InteractionHand.MAIN_HAND);
		try {
			original.call(item, pose, collector, light, overlay, outline);
		} finally {
			FirstPersonAnimations.afterItem(pose, pushed);
		}
	}

	@Inject(method = ARM_WITH_ITEM, at = @At("HEAD"), cancellable = true)
	private void temple$placeHand(net.minecraft.client.renderer.state.level.PlayerRenderState playerState,
			net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState hands, float a, float b, InteractionHand hand,
			float c, ItemStack stack, float d, PoseStack pose, SubmitNodeCollector armCollector, int armLight, CallbackInfo ci) {
		if (nz.temple.client.modules.cosmetic.Knives.firstPerson(pose, armCollector, armLight, hand)) {
			ci.cancel();
			return;
		}
		if (FirstPersonAnimations.placeHand(pose, hand)) {
			ci.cancel();
		}
	}

	@Inject(method = ARM_WITH_ITEM, at = @At("RETURN"))
	private void temple$restoreHand(net.minecraft.client.renderer.state.level.PlayerRenderState playerState,
			net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState hands, float a, float b, InteractionHand hand,
			float c, ItemStack stack, float d, PoseStack pose, SubmitNodeCollector armCollector, int armLight, CallbackInfo ci) {
		FirstPersonAnimations.restoreHand(pose, hand);
	}
}
*///?}
