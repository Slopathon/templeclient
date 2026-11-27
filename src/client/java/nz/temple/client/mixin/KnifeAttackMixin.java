package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class KnifeAttackMixin {
	@Inject(method = "startAttack", at = @At("RETURN"))
	private void temple$knifeSwing(CallbackInfoReturnable<Boolean> cir) {
		nz.temple.client.modules.cosmetic.Knives.clicked();
	}
}
