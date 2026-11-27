package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shrinks or hides the totem that flies at the screen when your own totem pops. Up to 26.2 the renderer keeps the
 * popped item itself; from 26.3 it arrives with the player's render state.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class TotemScreenMixin {
	//? if >=26.3 {
	/*@Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
	private void temple$hideTotem(net.minecraft.client.renderer.state.level.PlayerRenderState player, PoseStack pose, float partialTicks,
			net.minecraft.client.renderer.SubmitNodeCollector collector, CallbackInfo ci) {
		if (temple$scale(temple$item(player)) <= 0) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "renderItemActivationAnimation", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void temple$sizeTotem(PoseStack pose, float x, float y, float z, Operation<Void> original,
			net.minecraft.client.renderer.state.level.PlayerRenderState player, PoseStack activationPose, float partialTicks,
			net.minecraft.client.renderer.SubmitNodeCollector collector) {
		float k = temple$scale(temple$item(player));
		original.call(pose, x * k, y * k, z * k);
	}

	private static @Nullable ItemStack temple$item(net.minecraft.client.renderer.state.level.PlayerRenderState player) {
		return player.itemActivation == null ? null : player.itemActivation.item;
	}
	*///?} else {
	@org.spongepowered.asm.mixin.Shadow
	private @Nullable ItemStack itemActivationItem;

	@Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
	private void temple$hideTotem(PoseStack poseStack, float partialTicks, net.minecraft.client.renderer.SubmitNodeCollector collector,
			CallbackInfo ci) {
		if (temple$scale(itemActivationItem) <= 0) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "renderItemActivationAnimation", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void temple$sizeTotem(PoseStack poseStack, float x, float y, float z, Operation<Void> original) {
		float k = temple$scale(itemActivationItem);
		original.call(poseStack, x * k, y * k, z * k);
	}
	//?}

	/** How big the popped item is drawn: Totem Scale for a totem, otherwise as vanilla has it. */
	private static float temple$scale(@Nullable ItemStack item) {
		return item != null && item.is(Items.TOTEM_OF_UNDYING) ? nz.temple.client.util.VanillaFx.screenTotemScale() : 1;
	}
}
