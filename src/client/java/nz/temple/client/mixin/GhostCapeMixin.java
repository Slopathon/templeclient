package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import nz.temple.client.modules.SchemaModules;
import nz.temple.client.modules.combat.TimeFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Time Freeze: ghost players keep their cape. */
@Mixin(CapeLayer.class)
public class GhostCapeMixin {
	@WrapOperation(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;isInvisible:Z"))
	private boolean temple$ghostCape(AvatarRenderState state, Operation<Boolean> original) {
		boolean invisible = original.call(state);
		return invisible && !(SchemaModules.get("Time Freeze") instanceof TimeFreeze tf && tf.isGhostState(state));
	}
}
