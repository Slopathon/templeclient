package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;

public final class GrimLenience {
	public static final double PUSH_PER_ENTITY = 0.07;
	public static final double HARD_STEP = 0.09;
	public static net.minecraft.world.phys.Vec3 stepTowards(net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to) {
		return new net.minecraft.world.phys.Vec3(step(from.x, to.x), step(from.y, to.y), step(from.z, to.z));
	}
	private static double step(double from, double to) {
		return from + Math.max(-HARD_STEP, Math.min(HARD_STEP, to - from));
	}
	private static final int[] pushers = new int[3];
	private static final boolean[] hard = new boolean[3];
	private static int lastTick = Integer.MIN_VALUE;
	private GrimLenience() {
	}
	public static int pushers(LocalPlayer player) {
		update(player);
		return Math.min(pushers[0], Math.min(pushers[1], pushers[2]));
	}

	public static boolean hardCollision(LocalPlayer player) {
		update(player);
		return hard[0] && hard[1] && hard[2];
	}
	private static void update(LocalPlayer player) {
		if (player.tickCount == lastTick) {
			return;
		}
		lastTick = player.tickCount;
		int slot = Math.floorMod(player.tickCount, 3);
		pushers[slot] = countPushers(player);
		hard[slot] = hardNearby(player);
	}
	private static int countPushers(LocalPlayer player) {
		if (player.isPassenger() || player.isSpectator() || Minecraft.getInstance().level == null) {
			return 0;
		}
		AABB box = player.getBoundingBox().inflate(0.2);
		int count = 0;
		for (Entity entity : Minecraft.getInstance().level.getEntities(player, box.inflate(2))) {
			if (entity instanceof LivingEntity living && living.isPushable() && !entity.isSpectator()
					&& entity.getBoundingBox().intersects(box) && entity != player.getVehicle()) {
				count++;
			}
		}
		return count;
	}
	private static boolean hardNearby(LocalPlayer player) {
		if (player.isPassenger() || Minecraft.getInstance().level == null) {
			return false;
		}
		AABB box = player.getBoundingBox().inflate(1);
		for (Entity entity : Minecraft.getInstance().level.getEntities(player, box.inflate(2))) {
			if ((entity instanceof AbstractBoat || entity instanceof Shulker || entity instanceof net.minecraft.world.entity.animal.happyghast.HappyGhast)
					&& entity.getBoundingBox().intersects(box)) {
				return true;
			}
		}
		return false;
	}
}
