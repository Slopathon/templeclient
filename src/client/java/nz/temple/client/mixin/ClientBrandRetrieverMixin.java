package nz.temple.client.mixin;

import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.client.Privacy;

@Mixin(ClientBrandRetriever.class)
public class ClientBrandRetrieverMixin {
	@Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
	private static void temple$spoofBrand(CallbackInfoReturnable<String> cir) {
		if (Privacy.spoofsVanillaBrand()) {
			cir.setReturnValue(ClientBrandRetriever.VANILLA_NAME);
		}
	}
}
