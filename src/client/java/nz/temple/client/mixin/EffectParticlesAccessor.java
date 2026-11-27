package nz.temple.client.mixin;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(LivingEntity.class)
public interface EffectParticlesAccessor {
	@Accessor("DATA_EFFECT_PARTICLES")
	static EntityDataAccessor<List<ParticleOptions>> temple$effectParticles() {
		throw new AssertionError();
	}
}
