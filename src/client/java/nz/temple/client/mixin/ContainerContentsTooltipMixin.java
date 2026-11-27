package nz.temple.client.mixin;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.BetterTooltips;

import java.util.function.Consumer;

@Mixin(ItemContainerContents.class)
public abstract class ContainerContentsTooltipMixin {
	@Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
	private void temple$hideList(Item.TooltipContext context, Consumer<Component> out, TooltipFlag flag, DataComponentGetter components,
			CallbackInfo ci) {
		if (BetterTooltips.hideContainerText()) {
			ci.cancel();
		}
	}
}
