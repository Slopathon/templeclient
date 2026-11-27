package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.ItemPhysics;
import nz.temple.client.render.ItemPhysicsState;

/**
 * Item Physics: the dropped-item renderer's hover and spin are swapped for a pose that tumbles through the air and
 * settles lying on the ground. Only the two transforms are replaced; stack piles, lighting and the shadow stay vanilla.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
			at = @At("TAIL"))
	private void temple$physicsState(ItemEntity entity, ItemEntityRenderState state, float partialTicks, CallbackInfo ci) {
		var v = entity.getDeltaMovement();
		((ItemPhysicsState) state).temple$setPhysics(entity.getId(), entity.onGround(), entity.isInWater(), (float) v.length());
	}

	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void temple$rest(PoseStack pose, float x, float y, float z, Operation<Void> original, ItemEntityRenderState state,
			PoseStack statePose, SubmitNodeCollector collector, CameraRenderState camera) {
		ItemPhysics physics = ItemPhysics.active();
		if (physics == null) {
			original.call(pose, x, y, z);
			return;
		}
		original.call(pose, x, physics.restHeight(state), z);
	}

	// 26.3 spins the item about an axis rather than by a quaternion.
	//? if >=26.3 {
	/*	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lcom/mojang/math/Axis;F)V"))
	private void temple$tumble(PoseStack pose, com.mojang.math.Axis axis, float angle, Operation<Void> original,
			ItemEntityRenderState state, PoseStack statePose, SubmitNodeCollector collector, CameraRenderState camera) {
		ItemPhysics physics = ItemPhysics.active();
		if (physics == null) {
			original.call(pose, axis, angle);
		} else {
			pose.rotate(physics.rotation(state));
		}
	}
	*///?} else {
	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
	private void temple$tumble(PoseStack pose, Quaternionfc spin, Operation<Void> original, ItemEntityRenderState state,
			PoseStack statePose, SubmitNodeCollector collector, CameraRenderState camera) {
		ItemPhysics physics = ItemPhysics.active();
		original.call(pose, physics == null ? spin : physics.rotation(state));
	}
	//?}
}
