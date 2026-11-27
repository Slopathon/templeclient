package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.render.Chams;
import nz.temple.client.render.EspStateAccess;
import nz.temple.client.render.HandChamsCollector;

@Mixin(ItemInHandLayer.class)
public abstract class HeldItemChamsMixin {
	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void temple$heldChams(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector, int light, int overlay,
			int outline, Operation<Void> original, ArmedEntityRenderState state, ItemStackRenderState itemState, ItemStack stack,
			net.minecraft.world.entity.HumanoidArm arm, PoseStack armPose, SubmitNodeCollector armCollector, int armLight) {
		Chams chams = TempleNzClient.modules() == null ? null : TempleNzClient.modules().get(Chams.class);
		HandChamsCollector.Source source = chams == null ? null : chams.heldSource();
		boolean held = source != null && ((EspStateAccess) state).temple$chams();
		if (!held && !(collector instanceof HandChamsCollector)) {
			original.call(item, pose, collector, light, overlay, outline);
			return;
		}
		HandChamsCollector.item(stack);
		try {
			original.call(item, pose, held ? HandChamsCollector.wrap(collector, source) : collector, light, overlay, outline);
		} finally {
			HandChamsCollector.item(ItemStack.EMPTY);
		}
	}
}
