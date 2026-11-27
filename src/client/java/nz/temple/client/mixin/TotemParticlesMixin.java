package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class TotemParticlesMixin {
	@WrapOperation(method = "handleEntityEvent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"))
	private void temple$totemParticles(ParticleEngine engine, Entity entity, ParticleOptions particle, int life, Operation<Void> original) {
		if (particle == ParticleTypes.TOTEM_OF_UNDYING && nz.temple.client.util.VanillaFx.hidesTotemParticles(entity)) {
			return;
		}
		original.call(engine, entity, particle, life);
	}
}
