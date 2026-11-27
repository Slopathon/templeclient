package nz.temple.client.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

public final class HitTiming {
	private static final int GAP = 11;
	private static final Map<Integer, Integer> lastAttack = new HashMap<>();
	private static LocalPlayer owner;
	private HitTiming() {
	}
	public static boolean canDamage(LocalPlayer player, LivingEntity target) {
		sync(player);
		Integer last = lastAttack.get(target.getId());
		int now = player.tickCount;
		if (last != null && now >= last && now - last < GAP) {
			return false;
		}
		if (target.hurtTime <= 0) {
			return true;
		}
		int hurtStart = now - (Math.max(1, target.hurtDuration) - target.hurtTime);
		return last != null && hurtStart >= last && now - last < GAP + 20;
	}
	public static void attacked(LocalPlayer player, LivingEntity target) {
		sync(player);
		lastAttack.put(target.getId(), player.tickCount);
		if (lastAttack.size() > 64) {
			int now = player.tickCount;
			lastAttack.values().removeIf(t -> now - t > 200 || t > now);
		}
	}
	private static void sync(LocalPlayer player) {
		if (player != owner) {
			owner = player;
			lastAttack.clear();
		}
	}
}
