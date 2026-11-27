package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import nz.temple.client.modules.cosmetic.Knives;
import nz.temple.client.render.EspStateAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandLayer.class)
public abstract class KnifeThirdPersonMixin {
	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void temple$knife(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector, int light, int overlay,
			int outline, Operation<Void> original, ArmedEntityRenderState state, ItemStackRenderState itemState, ItemStack stack,
			HumanoidArm arm, PoseStack armPose, SubmitNodeCollector armCollector, int armLight) {
		if (arm == HumanoidArm.RIGHT && ((EspStateAccess) state).temple$mine() && Knives.thirdPerson(pose, collector, light, stack)) {
			return;
		}
		original.call(item, pose, collector, light, overlay, outline);
	}
}
