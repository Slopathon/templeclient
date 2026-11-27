package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class CrystalDamage {
   private static final double POWER_DIAMETER = 12.0;

   private CrystalDamage() {
   }

   public static float worstCase(Minecraft mc, Vec3 center, LivingEntity e) {
      float now = damage(mc, center, e, Vec3.ZERO);
      if (now <= 0.0F) {
         return 0.0F;
      }

      double sx = (e.getX() - e.xo) * 2.0;
      double sz = (e.getZ() - e.zo) * 2.0;
      double len = Math.sqrt(sx * sx + sz * sz);
      if (len > 0.7) {
         sx *= 0.7 / len;
         sz *= 0.7 / len;
      }

      return sx * sx + sz * sz < 0.0025 ? now : Math.max(now, damage(mc, center, e, new Vec3(sx, 0.0, sz)));
   }

   public static float damage(Minecraft mc, Vec3 center, LivingEntity e, Vec3 shift) {
      if (mc.level == null) {
         return 0.0F;
      }

      double d = e.position().add(shift).distanceTo(center) / 12.0;
      if (d > 1.0) {
         return 0.0F;
      }

      double exposure = seenPercent(mc, center, e.getBoundingBox().move(shift), e);
      double impact = (1.0 - d) * exposure;
      float dmg = (int)((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
      Difficulty diff = mc.level.getDifficulty();
      if (diff == Difficulty.PEACEFUL) {
         dmg = 0.0F;
      } else if (diff == Difficulty.EASY) {
         dmg = Math.min(dmg / 2.0F + 1.0F, dmg);
      } else if (diff == Difficulty.HARD) {
         dmg *= 1.5F;
      }

      float armor = (float)e.getAttributeValue(Attributes.ARMOR);
      float tough = (float)e.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
      float f = 2.0F + tough / 4.0F;
      float g = Mth.clamp(armor - dmg / f, armor * 0.2F, 20.0F);
      dmg *= 1.0F - g / 25.0F;
      dmg = protection(mc, e, dmg);
      return Math.max(0.0F, dmg);
   }

   private static float protection(Minecraft mc, LivingEntity e, float dmg) {
      try {
         for (MobEffectInstance inst : e.getActiveEffects()) {
            if (((MobEffect)inst.getEffect().value()).getDescriptionId().endsWith("resistance")) {
               int k = (inst.getAmplifier() + 1) * 5;
               dmg *= Math.max(0, 25 - k) / 25.0F;
            }
         }
      } catch (Throwable var14) {
      }

      try {
         Registry<Enchantment> reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
         Holder<Enchantment> prot = (Holder<Enchantment>)reg.get(Enchantments.PROTECTION).orElse(null);
         Holder<Enchantment> blast = (Holder<Enchantment>)reg.get(Enchantments.BLAST_PROTECTION).orElse(null);
         int epf = 0;
         EquipmentSlot[] slots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

         for (EquipmentSlot slot : slots) {
            ItemStack st = e.getItemBySlot(slot);
            if (!st.isEmpty()) {
               if (prot != null) {
                  epf += EnchantmentHelper.getItemEnchantmentLevel(prot, st);
               }

               if (blast != null) {
                  epf += EnchantmentHelper.getItemEnchantmentLevel(blast, st) * 2;
               }
            }
         }

         dmg *= 1.0F - Mth.clamp(epf, 0, 20) / 25.0F;
      } catch (Throwable var13) {
      }

      return dmg;
   }

   private static double seenPercent(Minecraft mc, Vec3 center, AABB box, LivingEntity e) {
      double dx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
      double dy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
      double dz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
      if (!(dx < 0.0) && !(dy < 0.0) && !(dz < 0.0)) {
         double xo = (1.0 - Math.floor(1.0 / dx) * dx) / 2.0;
         double zo = (1.0 - Math.floor(1.0 / dz) * dz) / 2.0;
         int hit = 0;
         int total = 0;

         for (double x = 0.0; x <= 1.0; x += dx) {
            for (double y = 0.0; y <= 1.0; y += dy) {
               for (double z = 0.0; z <= 1.0; z += dz) {
                  Vec3 pt = new Vec3(Mth.lerp(x, box.minX, box.maxX) + xo, Mth.lerp(y, box.minY, box.maxY), Mth.lerp(z, box.minZ, box.maxZ) + zo);
                  HitResult r = mc.level.clip(new ClipContext(pt, center, Block.COLLIDER, Fluid.NONE, e));
                  if (r.getType() == Type.MISS) {
                     hit++;
                  }

                  total++;
               }
            }
         }

         return total == 0 ? 0.0 : (double)hit / total;
      } else {
         return 0.0;
      }
   }
}
