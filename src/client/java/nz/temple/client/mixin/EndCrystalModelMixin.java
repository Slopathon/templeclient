package nz.temple.client.mixin;

import net.minecraft.client.model.object.crystal.EndCrystalModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import nz.temple.client.modules.render.CrystalChams;

@Mixin(EndCrystalModel.class)
public class EndCrystalModelMixin {
	private static final String SETUP = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V";

	@ModifyArg(method = SETUP, at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"))
	private float temple$spinOuter(float degrees) {
		CrystalChams chams = CrystalChams.active();
		return chams == null ? degrees : degrees * chams.spin();
	}

	@ModifyArg(method = SETUP, at = @At(value = "INVOKE", target = "Lorg/joml/Quaternionf;rotateY(F)Lorg/joml/Quaternionf;"))
	private float temple$spinInner(float radians) {
		CrystalChams chams = CrystalChams.active();
		return chams == null ? radians : radians * chams.spin();
	}
}
