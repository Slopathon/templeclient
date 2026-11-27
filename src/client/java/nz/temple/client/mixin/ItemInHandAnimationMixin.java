//? if <26.3 {
// Up to 26.2 one class ticks and draws the first-person hands. 26.3 split it in two: see FirstPersonHandsAnimationMixin
// and FirstPersonHandsEquipMixin, which replace this one there (mixins_excluded).
package nz.temple.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.FirstPersonAnimations;

/** The first-person half of the Animations module, hooked into vanilla's hand renderer (see FirstPersonAnimations). */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandAnimationMixin {
	@Shadow
	private float mainHandHeight;
	@Shadow
	private float offHandHeight;
	@Shadow
	private float oOffHandHeight;
	@Shadow
	private ItemStack mainHandItem;
	@Shadow
	private ItemStack offHandItem;

	@Unique
	private float temple$mainBefore;
	@Unique
	private float temple$offBefore;
	@Unique
	private boolean temple$itemPushed;

	@Inject(method = "tick", at = @At("HEAD"))
	private void temple$equipBefore(CallbackInfo ci) {
		temple$mainBefore = mainHandHeight;
		temple$offBefore = offHandHeight;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void temple$equip(CallbackInfo ci) {
		FirstPersonAnimations.Equip e = new FirstPersonAnimations.Equip();
		e.mainHeight = mainHandHeight;
		e.offHeight = offHandHeight;
		e.oOffHeight = oOffHandHeight;
		e.mainItem = mainHandItem;
		e.offItem = offHandItem;
		FirstPersonAnimations.equip(e, temple$mainBefore, temple$offBefore);
		mainHandHeight = e.mainHeight;
		offHandHeight = e.offHeight;
		oOffHandHeight = e.oOffHeight;
		mainHandItem = e.mainItem;
		offHandItem = e.offItem;
	}

	@Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
	private void temple$swingStyle(PoseStack pose, HumanoidArm arm, float swing, CallbackInfo ci) {
		if (FirstPersonAnimations.swingStyle(pose, arm, swing)) {
			ci.cancel();
		}
	}

	@Inject(method = "applyEatTransform", at = @At("HEAD"), cancellable = true)
	private void temple$noEat(PoseStack pose, float partialTick, HumanoidArm arm, ItemStack stack, Player player, CallbackInfo ci) {
		if (FirstPersonAnimations.beforeEat(pose)) {
			ci.cancel();
		}
	}

	@Inject(method = "applyEatTransform", at = @At("RETURN"))
	private void temple$partialEat(PoseStack pose, float partialTick, HumanoidArm arm, ItemStack stack, Player player, CallbackInfo ci) {
		FirstPersonAnimations.afterEat(pose);
	}

	@Inject(method = "renderItem", at = @At("HEAD"))
	private void temple$itemTransform(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack pose,
			SubmitNodeCollector collector, int light, CallbackInfo ci) {
		boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
		temple$itemPushed = context.firstPerson()
				&& FirstPersonAnimations.beforeItem(pose, right, right == (entity.getMainArm() == HumanoidArm.RIGHT));
	}

	@Inject(method = "renderItem", at = @At("RETURN"))
	private void temple$itemRestore(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack pose,
			SubmitNodeCollector collector, int light, CallbackInfo ci) {
		FirstPersonAnimations.afterItem(pose, temple$itemPushed);
		temple$itemPushed = false;
	}

	@Inject(method = nz.temple.client.compat.MixinNames.ARM_WITH_ITEM, at = @At("HEAD"), cancellable = true)
	private void temple$placeHand(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing,
			ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (nz.temple.client.modules.cosmetic.Knives.firstPerson(pose, collector, light, hand)) {
			ci.cancel();
			return;
		}
		if (FirstPersonAnimations.placeHand(pose, hand)) {
			ci.cancel();
		}
	}

	@Inject(method = nz.temple.client.compat.MixinNames.ARM_WITH_ITEM, at = @At("RETURN"))
	private void temple$restoreHand(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing,
			ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		FirstPersonAnimations.restoreHand(pose, hand);
	}
}
//?}
