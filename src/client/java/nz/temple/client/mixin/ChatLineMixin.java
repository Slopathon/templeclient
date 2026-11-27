package nz.temple.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.ChatAnimations;

@Mixin(ChatComponent.class)
public abstract class ChatLineMixin {
	@Inject(method = "addMessageToDisplayQueue", at = @At("TAIL"))
	private void temple$lineAdded(CallbackInfo ci) {
		ChatAnimations.line();
	}
}
