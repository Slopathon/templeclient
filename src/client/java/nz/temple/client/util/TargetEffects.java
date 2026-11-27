package nz.temple.client.util;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import nz.temple.client.mixin.EffectParticlesAccessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TargetEffects {
	public record Shown(Holder<MobEffect> effect, int level, int ticks) {
	}
	private TargetEffects() {
	}
	public static List<Shown> of(LivingEntity entity) {
		Map<Holder<MobEffect>, Shown> out = new LinkedHashMap<>();
		for (MobEffectInstance e : entity.getActiveEffects()) {
			out.put(e.getEffect(), new Shown(e.getEffect(), e.getAmplifier() + 1, e.isInfiniteDuration() ? -1 : e.getDuration()));
		}
		try {
			List<ParticleOptions> particles = entity.getEntityData().get(EffectParticlesAccessor.temple$effectParticles());
			for (ParticleOptions p : particles) {
				if (!(p instanceof ColorParticleOption colour)) {
					continue;
				}
				int rgb = Math.round(colour.getRed() * 255) << 16 | Math.round(colour.getGreen() * 255) << 8 | Math.round(colour.getBlue() * 255);
				Holder<MobEffect> match = byColour(rgb);
				if (match != null && !out.containsKey(match)) {
					out.put(match, new Shown(match, 0, -1));
				}
			}
		} catch (RuntimeException ignored) {
		}
		return new ArrayList<>(out.values());
	}
	private static Holder<MobEffect> byColour(int rgb) {
		Holder<MobEffect> best = null;
		int bestDistance = 12;
		for (var holder : BuiltInRegistries.MOB_EFFECT.asHolderIdMap()) {
			int c = holder.value().getColor() & 0xFFFFFF;
			int d = Math.abs((c >> 16 & 0xFF) - (rgb >> 16 & 0xFF)) + Math.abs((c >> 8 & 0xFF) - (rgb >> 8 & 0xFF)) + Math.abs((c & 0xFF) - (rgb & 0xFF));
			if (d < bestDistance) {
				bestDistance = d;
				best = holder;
			}
		}
		return best;
	}
}
