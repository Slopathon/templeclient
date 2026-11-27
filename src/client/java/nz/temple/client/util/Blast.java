package nz.temple.client.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Blast {
	public static final float CRYSTAL = 6f;
	public static final float ANCHOR = 5f;
	private Blast() {
	}

	public static float damage(LivingEntity target, Vec3 center, float power) {
		double reach = power * 2.0;
		double dist = Math.sqrt(target.distanceToSqr(center)) / reach;
		if (dist > 1) {
			return 0;
		}
		double exposure = ServerExplosion.getSeenPercent(center, target);
		return reduced(target, baseDamage(dist, exposure, reach));
	}
	public static float damageAt(LivingEntity target, Vec3 feet, Vec3 center, float power) {
		double reach = power * 2.0;
		double dist = feet.distanceTo(center) / reach;
		if (dist > 1) {
			return 0;
		}
		AABB body = target.getBoundingBox().move(feet.subtract(target.position()));
		return reduced(target, baseDamage(dist, exposure(target, body, center), reach));
	}
	private static float baseDamage(double dist, double exposure, double reach) {
		double impact = (1 - dist) * exposure;
		return (float) ((impact * impact + impact) / 2 * 7 * reach + 1);
	}
	private static double exposure(LivingEntity target, AABB body, Vec3 center) {
		double stepX = 1.0 / ((body.maxX - body.minX) * 2.0 + 1.0);
		double stepY = 1.0 / ((body.maxY - body.minY) * 2.0 + 1.0);
		double stepZ = 1.0 / ((body.maxZ - body.minZ) * 2.0 + 1.0);
		if (stepX < 0 || stepY < 0 || stepZ < 0) {
			return 0;
		}
		double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
		double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
		int seen = 0;
		int total = 0;
		for (double x = 0; x <= 1; x += stepX) {
			for (double y = 0; y <= 1; y += stepY) {
				for (double z = 0; z <= 1; z += stepZ) {
					Vec3 sample = new Vec3(body.minX + (body.maxX - body.minX) * x + offsetX,
							body.minY + (body.maxY - body.minY) * y,
							body.minZ + (body.maxZ - body.minZ) * z + offsetZ);
					HitResult hit = target.level().clip(new ClipContext(sample, center, ClipContext.Block.COLLIDER,
							ClipContext.Fluid.NONE, target));
					if (hit.getType() == HitResult.Type.MISS) {
						seen++;
					}
					total++;
				}
			}
		}
		return total == 0 ? 0 : (double) seen / total;
	}
	private static float reduced(LivingEntity target, float damage) {
		if (target instanceof Player) {
			Difficulty difficulty = target.level().getDifficulty();
			if (difficulty == Difficulty.PEACEFUL) {
				return 0;
			}
			if (difficulty == Difficulty.EASY) {
				damage = Math.min(damage / 2 + 1, damage);
			} else if (difficulty == Difficulty.HARD) {
				damage = damage * 3 / 2;
			}
		}
		damage = CombatRules.getDamageAfterAbsorb(target, damage, target.level().damageSources().explosion(null, null),
				(float) target.getArmorValue(), (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		int protection = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = target.getItemBySlot(slot);
			for (var entry : stack.getEnchantments().entrySet()) {
				String id = name(entry.getKey());
				if (id.equals("protection")) {
					protection += entry.getIntValue();
				} else if (id.equals("blast_protection")) {
					protection += entry.getIntValue() * 2;
				}
			}
		}
		damage = CombatRules.getDamageAfterMagicAbsorb(damage, Math.min(20, protection));
		var resistance = target.getEffect(MobEffects.RESISTANCE);
		if (resistance != null) {
			damage *= Math.max(0, 1 - (resistance.getAmplifier() + 1) * 0.2f);
		}
		return Math.max(0, damage);
	}
	public static float crystalOn(LivingEntity target, BlockPos base) {
		return damage(target, new Vec3(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5), CRYSTAL);
	}
	private static String name(Holder<Enchantment> holder) {
		return holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
	}
}
