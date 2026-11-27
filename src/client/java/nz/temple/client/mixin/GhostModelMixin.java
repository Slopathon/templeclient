package nz.temple.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import nz.temple.client.modules.SchemaModules;
import nz.temple.client.modules.combat.TimeFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time Freeze: ghost entities are drawn see-through, at the module's ghost opacity. */
@Mixin(LivingEntityRenderer.class)
public abstract class GhostModelMixin {
	@Shadow
	public abstract Identifier getTextureLocation(LivingEntityRenderState state);

	@Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
	private void temple$ghostRenderType(LivingEntityRenderState state, boolean bodyVisible, boolean translucent, boolean glowing,
			CallbackInfoReturnable<RenderType> cir) {
		if (temple$timeFreeze() instanceof TimeFreeze tf && tf.isGhostState(state)) {
			//? if >=26.3 {
			/*cir.setReturnValue(RenderTypes.entityTranslucentCull(getTextureLocation(state)));
			*///?} else if >=26 {
			cir.setReturnValue(RenderTypes.entityTranslucentCullItemTarget(getTextureLocation(state)));
			//?} else {
			/*cir.setReturnValue(RenderTypes.itemEntityTranslucentCull(getTextureLocation(state)));
			*///?}
		}
	}

	@Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
	private void temple$ghostTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
		if (temple$timeFreeze() instanceof TimeFreeze tf && tf.isGhostState(state)) {
			cir.setReturnValue(tf.ghostOpacityAlpha() << 24 | 0xFFFFFF);
		}
	}

	@Unique
	private static Object temple$timeFreeze() {
		return SchemaModules.get("Time Freeze");
	}
}
