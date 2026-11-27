package nz.temple.client.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

public final class Entities {
	public enum Group { PLAYER, HOSTILE, PASSIVE, ITEM }

	private Entities() {
	}

	private static final java.util.Map<net.minecraft.world.entity.EntityType<?>, String> IDS = new java.util.IdentityHashMap<>();
	private static final java.util.Map<net.minecraft.world.item.Item, String> ITEM_IDS = new java.util.IdentityHashMap<>();

	public static String id(Entity entity) {
		net.minecraft.world.entity.EntityType<?> type = entity.getType();
		String id = IDS.get(type);
		if (id == null) {
			id = net.minecraft.world.entity.EntityType.getKey(type).toString();
			synchronized (IDS) {
				IDS.put(type, id);
			}
		}
		return id;
	}
	public static String id(net.minecraft.world.item.Item item) {
		String id = ITEM_IDS.get(item);
		if (id == null) {
			id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
			synchronized (ITEM_IDS) {
				ITEM_IDS.put(item, id);
			}
		}
		return id;
	}
	public static Group group(Entity entity) {
		if (entity instanceof Player) {
			return Group.PLAYER;
		}
		if (entity instanceof Enemy) {
			return Group.HOSTILE;
		}
		if (entity instanceof LivingEntity) {
			return Group.PASSIVE;
		}
		return Group.ITEM;
	}
	public static float healthFraction(Entity entity) {
		if (entity instanceof LivingEntity living && living.getMaxHealth() > 0) {
			return Math.max(0, Math.min(1, living.getHealth() / living.getMaxHealth()));
		}
		return 1f;
	}
}
