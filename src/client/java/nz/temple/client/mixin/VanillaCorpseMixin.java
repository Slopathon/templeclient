package nz.temple.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Leaves the vanilla falling corpse undrawn where Death Effects draws the body breaking apart or rising itself. */
@Mixin(EntityRenderer.class)
public abstract class VanillaCorpseMixin {
	// The target's own arguments are taken in full rather than with MixinExtras @Local: the parameter-annotation
	// attribute @Local leaves crashes ZKM's saver (see FoliageVertexMixin).
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	//? if >=26.3 {
	/*private void temple$noCorpse(Entity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z,
			float partialTick, CallbackInfoReturnable<Boolean> cir) {
	*///?} else {
	private void temple$noCorpse(Entity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z,
			CallbackInfoReturnable<Boolean> cir) {
	//?}
		if (nz.temple.client.util.VanillaFx.hidesCorpse(entity)) {
			cir.setReturnValue(false);
		}
	}
}
