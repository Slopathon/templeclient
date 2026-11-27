package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.social.FriendManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Targets {
	private Targets() {
	}

	public static LivingEntity nearest(LocalPlayer self, double range, String kind, boolean ignoreFriends, FriendManager friends) {
		if (Minecraft.getInstance().level == null) {
			return null;
		}
		Vec3 eye = self.getEyePosition();
		double rangeSq = range * range;
		LivingEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || living == self || !living.isAlive() || living.isDeadOrDying()
					|| !matches(living, kind)) {
				continue;
			}
			if (living instanceof Player p && ignoreFriends && friends != null && friends.isFriend(p.getName().getString())) {
				continue;
			}
			double d = living.distanceToSqr(eye.x, eye.y, eye.z);
			if (d <= rangeSq && d < bestSq) {
				bestSq = d;
				best = living;
			}
		}
		return best;
	}

	public static boolean matches(LivingEntity entity, String kind) {
		if (kind.equals("All")) {
			return true;
		}
		if (entity instanceof Player) {
			return kind.equals("Players");
		}
		if (entity instanceof Animal) {
			return kind.equals("Animals");
		}
		return kind.equals("Mobs") && entity instanceof Enemy;
	}

	public static float[] rotations(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double flat = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
		return new float[] {yaw, pitch};
	}

	public static float yawDiff(float from, float to) {
		float d = (to - from) % 360f;
		if (d > 180f) {
			d -= 360f;
		}
		if (d < -180f) {
			d += 360f;
		}
		return d;
	}

	public static boolean isFriend(Entity entity, boolean ignoreFriends, FriendManager friends) {
		return entity instanceof Player p && ignoreFriends && friends != null && friends.isFriend(p.getName().getString());
	}

	public static List<Player> enemies(LocalPlayer self, double range, FriendManager friends, boolean ignoreNaked) {
		Minecraft mc = Minecraft.getInstance();
		List<Player> out = new ArrayList<>();
		if (mc.level == null) {
			return out;
		}
		for (Player p : mc.level.players()) {
			if (p == self || !p.isAlive() || p.isDeadOrDying() || p.isSpectator() || p.isCreative()
					|| p.distanceToSqr(self) > range * range || friends.isFriend(p.getName().getString())
					|| ignoreNaked && naked(p)) {
				continue;
			}
			out.add(p);
		}
		out.sort(Comparator.comparingDouble(p -> p.distanceToSqr(self)));
		return out;
	}

	public static Player nearestEnemy(LocalPlayer self, double range, FriendManager friends, boolean ignoreNaked) {
		List<Player> list = enemies(self, range, friends, ignoreNaked);
		return list.isEmpty() ? null : list.get(0);
	}

	public static boolean naked(Player p) {
		return p.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && p.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
				&& p.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && p.getItemBySlot(EquipmentSlot.FEET).isEmpty();
	}

	public static BlockPos feet(Player p) {
		return BlockPos.containing(p.getX(), p.getY() + 0.2, p.getZ());
	}

	public static boolean inHole(Player p) {
		Minecraft mc = Minecraft.getInstance();
		BlockPos f = feet(p);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (!Place.solid(mc.level, f.relative(d))) {
				return false;
			}
		}
		return true;
	}
}
