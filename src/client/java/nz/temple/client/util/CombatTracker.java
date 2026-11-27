package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CombatTracker {
	private static Entity last;
	private static long at;
	private static float healthAt;
	private static int hurtAt;
	private static net.minecraft.world.phys.Vec3 eyeAt;
	private static net.minecraft.world.phys.Vec3 lookAt;

	private CombatTracker() {
	}

	public static void attacked(Entity target) {
		last = target;
		at = System.currentTimeMillis();
		healthAt = target instanceof LivingEntity living ? living.getHealth() : 0;
		hurtAt = target instanceof LivingEntity living ? living.hurtTime : 0;
		var player = Minecraft.getInstance().player;
		eyeAt = player == null ? null : player.getEyePosition();
		lookAt = player == null ? null : player.getViewVector(1f);
	}

	public static float healthAtAttack() {
		return healthAt;
	}

	public static int hurtTimeAtAttack() {
		return hurtAt;
	}

	public static net.minecraft.world.phys.Vec3 eyeAtAttack() {
		return eyeAt;
	}

	public static net.minecraft.world.phys.Vec3 lookAtAttack() {
		return lookAt;
	}

	public static LivingEntity recent(long maxAgeMs) {
		if (last instanceof LivingEntity living && living.isAlive() && System.currentTimeMillis() - at <= maxAgeMs
				&& living.level() == Minecraft.getInstance().level) {
			return living;
		}
		return null;
	}

	public static boolean hitRecently(Entity entity, long maxAgeMs) {
		return entity != null && entity == last && System.currentTimeMillis() - at <= maxAgeMs;
	}

	public static long lastAttackMillis() {
		return at;
	}
}
