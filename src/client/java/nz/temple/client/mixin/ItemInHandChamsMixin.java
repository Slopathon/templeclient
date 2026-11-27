//? if <26.3 {
// 26.3 split this renderer; see FirstPersonHandsChamsMixin, which replaces this one there (mixins_excluded).
package nz.temple.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.HandChamsCollector;

/** Hand Chams: the first-person arm and item are submitted through {@link HandChamsCollector}. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandChamsMixin {
	@ModifyVariable(method = "submitHandsWithItems", at = @At("HEAD"), argsOnly = true)
	private SubmitNodeCollector temple$chamsCollector(SubmitNodeCollector collector) {
		nz.temple.client.render.Afterimages.begin(collector);
		return HandChamsCollector.wrap(collector);
	}

	@Inject(method = "submitHandsWithItems", at = @At("RETURN"))
	private void temple$afterimages(CallbackInfo ci) {
		nz.temple.client.render.Afterimages.end();
	}

	@Inject(method = "renderItem", at = @At("HEAD"))
	private void temple$chamsItem(LivingEntity owner, ItemStack stack, ItemDisplayContext context, PoseStack pose,
			SubmitNodeCollector collector, int light, CallbackInfo ci) {
		HandChamsCollector.item(stack);
	}

	@Inject(method = "renderItem", at = @At("TAIL"))
	private void temple$chamsItemDone(LivingEntity owner, ItemStack stack, ItemDisplayContext context, PoseStack pose,
			SubmitNodeCollector collector, int light, CallbackInfo ci) {
		HandChamsCollector.item(ItemStack.EMPTY);
	}
}
//?}
