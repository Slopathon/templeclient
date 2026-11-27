package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaves out Minecraft's crit sparks and damage hearts on a hit that Hit Particles has just drawn. */
@Mixin(ClientPacketListener.class)
public abstract class VanillaHitParticlesMixin {
	@WrapOperation(method = "handleAnimate", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V"))
	private void temple$noCritSparks(ParticleEngine engine, Entity entity, ParticleOptions particle, Operation<Void> original) {
		if (!nz.temple.client.util.VanillaFx.replacesHitParticles(entity)) {
			original.call(engine, entity, particle);
		}
	}

	@Inject(method = "handleParticleEvent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER), cancellable = true)
	private void temple$noDamageHearts(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		// 26.3 made the packet a record: same values, accessor names without "get".
		//? if >=26.3 {
		/*var type = packet.particle().getType();
		double x = packet.x(), y = packet.y(), z = packet.z();
		*///?} else {
		var type = packet.getParticle().getType();
		double x = packet.getX(), y = packet.getY(), z = packet.getZ();
		//?}
		if ((type == ParticleTypes.DAMAGE_INDICATOR || type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT)
				&& nz.temple.client.util.VanillaFx.replacesHitAt(x, y, z)) {
			ci.cancel();
		}
	}
}
