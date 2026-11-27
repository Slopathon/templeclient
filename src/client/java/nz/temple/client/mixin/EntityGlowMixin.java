package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.render.Esp;

@Mixin(Minecraft.class)
public abstract class EntityGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void temple$espGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		Esp esp = Esp.instance();
		if (esp != null && esp.glows(entity) || nz.temple.client.modules.render.ItemESP.glows(entity)) {
			cir.setReturnValue(true);
		}
	}
}
