package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class HitConfirm {
	public interface Listener {
		void hit(LivingEntity target, Vec3 offset);
		void kill(Entity target, int entityId, Vec3 offset);
	}

	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
	private static long lastAttackSeen;
	private static int pendingTarget = -1;
	private static float pendingHealth;
	private static int pendingHurt;
	private static long pendingSince;
	private static Vec3 pendingOffset;
	private static int trackedTarget = -1;
	private static long trackedAt;
	private static Vec3 trackedOffset;
	private static boolean countedKill;
	private HitConfirm() {
	}
	public static void listen(Listener listener) {
		LISTENERS.add(listener);
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			pendingTarget = -1;
			trackedTarget = -1;
			return;
		}
		long last = CombatTracker.lastAttackMillis();
		if (last != lastAttackSeen) {
			lastAttackSeen = last;
			trackedTarget = -1;
			LivingEntity target = CombatTracker.recent(500);
			pendingTarget = target == null ? -1 : target.getId();
			pendingHealth = target == null ? 0 : CombatTracker.healthAtAttack();
			pendingHurt = target == null ? 0 : CombatTracker.hurtTimeAtAttack();
			pendingSince = System.currentTimeMillis();
			pendingOffset = target == null ? null : hitPoint(mc, target).subtract(target.position());
		}
		if (pendingTarget >= 0) {
			Entity pending = mc.level.getEntity(pendingTarget);
			if (pending instanceof LivingEntity living
					&& (living.getHealth() < pendingHealth || living.hurtTime > pendingHurt || !living.isAlive())) {
				trackedTarget = pendingTarget;
				trackedOffset = pendingOffset;
				trackedAt = System.currentTimeMillis();
				countedKill = false;
				pendingTarget = -1;
				for (Listener l : LISTENERS) {
					l.hit(living, trackedOffset);
				}
			} else if (System.currentTimeMillis() - pendingSince > 600) {
				pendingTarget = -1;
			}
		}
		if (countedKill || trackedTarget < 0) {
			return;
		}
		Entity entity = mc.level.getEntity(trackedTarget);
		boolean dead = entity == null || (entity instanceof LivingEntity living && (!living.isAlive() || living.getHealth() <= 0));
		if (dead && System.currentTimeMillis() - trackedAt < 2500) {
			countedKill = true;
			int id = trackedTarget;
			trackedTarget = -1;
			for (Listener l : LISTENERS) {
				l.kill(entity, id, trackedOffset);
			}
		}
	}
	public static Vec3 hitPoint(Minecraft mc, Entity target) {
		Vec3 eye = CombatTracker.eyeAtAttack() != null ? CombatTracker.eyeAtAttack() : mc.player.getEyePosition();
		Vec3 look = CombatTracker.lookAtAttack() != null ? CombatTracker.lookAtAttack() : mc.player.getViewVector(1f);
		AABB box = target.getBoundingBox();
		double reach = eye.distanceTo(box.getCenter()) + 3;
		var clip = box.clip(eye, eye.add(look.scale(reach)));
		if (clip.isPresent()) {
			return clip.get();
		}
		Vec3 center = box.getCenter();
		double along = Math.max(0, center.subtract(eye).dot(look));
		Vec3 near = eye.add(look.scale(along));
		Vec3 inside = new Vec3(clamp(near.x, box.minX, box.maxX), clamp(near.y, box.minY, box.maxY), clamp(near.z, box.minZ, box.maxZ));
		Vec3 toward = inside.subtract(eye);
		if (toward.lengthSqr() < 1e-6) {
			return inside;
		}
		return box.clip(eye, inside.add(toward.normalize().scale(0.05))).orElse(inside);
	}
	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
