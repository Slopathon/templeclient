//? if >=26.3 {
/*// From 26.3 the hands' equip dip is ticked here, apart from the renderer (see ItemInHandAnimationMixin for earlier
// versions, where one class did both).
package nz.temple.client.mixin;

import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.FirstPersonAnimations;

// The Animations module's equip dip: slowed, held during the attack cooldown, or hidden.
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsEquipMixin {
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
}
*///?}
