//? if >=26.3 {
/*// From 26.3 the first-person hands are drawn here from a render state (see ItemInHandChamsMixin for earlier versions).
package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.HandChamsCollector;

// Hand Chams: the first-person arm and item are submitted through HandChamsCollector.
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsChamsMixin {
	@ModifyVariable(method = "submitHandsWithItems", at = @At("HEAD"), argsOnly = true)
	private SubmitNodeCollector temple$chamsCollector(SubmitNodeCollector collector) {
		nz.temple.client.render.Afterimages.begin(collector);
		return HandChamsCollector.wrap(collector);
	}

	@Inject(method = "submitHandsWithItems", at = @At("RETURN"))
	private void temple$afterimages(CallbackInfo ci) {
		nz.temple.client.render.Afterimages.end();
	}

	// The held item, for the duration of its submit: the chams draw it again with their own render types.
	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void temple$chamsItem(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector, int light, int overlay,
			int outline, Operation<Void> original, net.minecraft.client.renderer.state.level.PlayerRenderState playerState,
			net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState hands, float a, float b, InteractionHand hand,
			float c, ItemStack stack, float d, PoseStack armPose, SubmitNodeCollector armCollector, int armLight) {
		HandChamsCollector.item(stack);
		try {
			original.call(item, pose, collector, light, overlay, outline);
		} finally {
			HandChamsCollector.item(ItemStack.EMPTY);
		}
	}
}
*///?}
