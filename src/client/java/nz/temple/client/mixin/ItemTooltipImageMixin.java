package nz.temple.client.mixin;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.render.BetterTooltips;

import java.util.Optional;

@Mixin(Item.class)
public abstract class ItemTooltipImageMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void temple$preview(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		Optional<TooltipComponent> preview = BetterTooltips.image(stack);
		if (preview.isPresent()) {
			cir.setReturnValue(preview);
		}
	}
}
