package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.util.random.WeightedList;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class VanillaExplosionMixin {
	@WrapOperation(method = "handleExplosion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
	private void temple$noExplosionParticle(ClientLevel level, ParticleOptions particle, double x, double y, double z, double dx, double dy,
			double dz, Operation<Void> original, ClientboundExplodePacket packet) {
		if (!nz.temple.client.util.VanillaFx.replacesExplosion(packet.center())) {
			original.call(level, particle, x, y, z, dx, dy, dz);
		}
	}

	@WrapOperation(method = "handleExplosion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;trackExplosionEffects(Lnet/minecraft/world/phys/Vec3;FILnet/minecraft/util/random/WeightedList;)V"))
	private void temple$noExplosionDebris(ClientLevel level, Vec3 center, float radius, int blocks, WeightedList<ExplosionParticleInfo> particles,
			Operation<Void> original, ClientboundExplodePacket packet) {
		if (!nz.temple.client.util.VanillaFx.replacesExplosion(center)) {
			original.call(level, center, radius, blocks, particles);
		}
	}
}
