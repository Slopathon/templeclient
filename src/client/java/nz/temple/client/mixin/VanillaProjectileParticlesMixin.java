package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
public abstract class VanillaProjectileParticlesMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
	private void temple$noArrowSparks(Level level, ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz,
			Operation<Void> original) {
		if (level.isClientSide() && particle == ParticleTypes.CRIT && nz.temple.client.util.VanillaFx.replacesArrowSparks()) {
			return;
		}
		original.call(level, particle, x, y, z, dx, dy, dz);
	}
}
