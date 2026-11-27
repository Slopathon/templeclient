package nz.temple.client.util;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class VanillaFx {
	public interface At {
		boolean test(double x, double y, double z);
	}
	public interface Scale {
		float get();
	}
	public static volatile Consumer<Entity> attacked;
	public static volatile Predicate<Entity> hitParticles;
	public static volatile At hitAt;
	public static volatile Consumer<Vec3> explosion;
	public static volatile Predicate<Vec3> explosionReplaced;
	public static volatile BooleanSupplier cracksHidden;
	public static volatile Predicate<BlockPos> breakAnimationParticles;
	public static volatile Predicate<BlockPos> blockEffectsParticles;
	public static volatile BooleanSupplier boltHidden;
	public static volatile Predicate<Entity> totemParticles;
	public static volatile Scale totemScale;
	public static volatile Predicate<Entity> corpseHidden;
	public static volatile Predicate<Entity> poofReplaced;
	public static volatile BooleanSupplier pearlParticles;
	public static volatile BooleanSupplier arrowSparks;
	private VanillaFx() {
	}

	public static void attacked(Entity target) {
		Consumer<Entity> hook = attacked;
		if (hook != null) {
			hook.accept(target);
		}
	}
	public static boolean replacesHitParticles(Entity entity) {
		return test(hitParticles, entity);
	}

	public static boolean replacesHitAt(double x, double y, double z) {
		At hook = hitAt;
		return hook != null && hook.test(x, y, z);
	}
	public static void onExplosion(Vec3 center) {
		Consumer<Vec3> hook = explosion;
		if (hook != null) {
			hook.accept(center);
		}
	}
	public static boolean replacesExplosion(Vec3 center) {
		return test(explosionReplaced, center);
	}

	public static boolean hidesVanillaCracks() {
		return test(cracksHidden);
	}

	public static boolean replacesBreakParticles(BlockPos pos) {
		return test(blockEffectsParticles, pos) || test(breakAnimationParticles, pos);
	}

	public static boolean hidesVanillaBolt() {
		return test(boltHidden);
	}

	public static boolean hidesTotemParticles(Entity entity) {
		return test(totemParticles, entity);
	}

	public static float screenTotemScale() {
		Scale hook = totemScale;
		return hook == null ? 1 : hook.get();
	}
	public static boolean hidesCorpse(Entity entity) {
		return test(corpseHidden, entity);
	}

	public static boolean replacesPoof(Entity entity) {
		return test(poofReplaced, entity);
	}

	public static boolean replacesPearlParticles() {
		return test(pearlParticles);
	}

	public static boolean replacesArrowSparks() {
		return test(arrowSparks);
	}

	private static boolean test(BooleanSupplier hook) {
		return hook != null && hook.getAsBoolean();
	}

	private static <T> boolean test(Predicate<T> hook, T value) {
		return hook != null && hook.test(value);
	}
}
