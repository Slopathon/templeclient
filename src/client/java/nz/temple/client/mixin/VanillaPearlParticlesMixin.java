package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThrownEnderpearl.class)
public abstract class VanillaPearlParticlesMixin {
	@WrapOperation(method = "onHit", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
	private void temple$noPearlBurst(Level level, ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz,
			Operation<Void> original) {
		if (level.isClientSide() && nz.temple.client.util.VanillaFx.replacesPearlParticles()) {
			return;
		}
		original.call(level, particle, x, y, z, dx, dy, dz);
	}
}
