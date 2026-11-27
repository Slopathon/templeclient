package nz.temple.client.modules.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import nz.temple.client.util.Friends;
import nz.temple.client.modules.SchemaModule;
import nz.temple.client.modules.combat.AntiBot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

public class Targets extends SchemaModule {
   private static Targets instance;
   private static final Map<LivingEntity, Long> lastHitTime = new WeakHashMap<>();
   public static final int ALLOW_PLAYERS = 1;
   public static final int ALLOW_HOSTILE = 2;
   public static final int ALLOW_PASSIVE = 4;

   public static void onHit(LivingEntity entity) {
      lastHitTime.put(entity, System.currentTimeMillis());
   }

   public Targets() {
      super("Targets", "Utility", true);
      instance = this;
   }

   public static boolean isTargetAllowed(LivingEntity entity, Player self) {
      if (entity == self) {
         return false;
      }

      if (entity.isRemoved()) {
         return false;
      }

      if (AntiBot.isBot(entity)) {
         return false;
      }

      if (Friends.isProtected(entity)) {
         return false;
      }

      boolean skipDead = getBool("Don't Target Dead", true);
      if (skipDead && entity.getHealth() <= 0.0F) {
         return false;
      }

      if (getBool("Ignore Invisible", true) && entity.isInvisible()) {
         return false;
      }

      if (entity instanceof Player player) {
         if (player.isSpectator()) {
            return false;
         }

         if (getBool("Survival Only", true) && !isSurvivalLike(player)) {
            return false;
         }
      }

      return isTypeAllowed(entity);
   }

   private static boolean isSurvivalLike(Player player) {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() == null) {
            return true;
         }

         PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
         if (info == null) {
            return true;
         }

         GameType gm = info.getGameMode();
         return gm == null ? true : gm == GameType.SURVIVAL || gm == GameType.ADVENTURE;
      } catch (Throwable t) {
         return true;
      }
   }

   public static boolean isTypeAllowed(LivingEntity entity) {
      return entity instanceof Player;
   }

   public static boolean isTypeAllowed(LivingEntity entity, int typeMask) {
      if (entity instanceof Player && (typeMask & 1) != 0) {
         return true;
      } else {
         return entity instanceof Monster && (typeMask & 2) != 0
            ? true
            : !(entity instanceof Player)
               && !(entity instanceof Monster)
               && (entity instanceof Animal || entity instanceof AmbientCreature || entity instanceof Mob)
               && (typeMask & 4) != 0;
      }
   }

   public static LivingEntity getTarget(List<? extends LivingEntity> candidates, Player self) {
      if (candidates != null && !candidates.isEmpty()) {
         boolean skipDead = getBool("Don't Target Dead", true);
         boolean ignoreInvis = getBool("Ignore Invisible", true);
         boolean survivalOnly = getBool("Survival Only", true);
         List<LivingEntity> filtered = new ArrayList<>();

         for (LivingEntity e : candidates) {
            if ((!skipDead || !(e.getHealth() <= 0.0F))
               && !Friends.isProtected(e)
               && (!ignoreInvis || !e.isInvisible())
               && !(e instanceof Player p && (p.isSpectator() || survivalOnly && !isSurvivalLike(p)))) {
               filtered.add(e);
            }
         }

         if (filtered.isEmpty()) {
            return null;
         }

         String mode = getStr("Mode", "Nearest");

         return switch (mode) {
            case "Lowest HP" -> (LivingEntity)filtered.stream().min(Comparator.comparingDouble(LivingEntity::getHealth)).orElse(null);
            case "Last Hit" -> (LivingEntity)filtered.stream().max(Comparator.comparingLong(ex -> lastHitTime.getOrDefault(ex, 0L))).orElse(null);
            default -> (LivingEntity)filtered.stream().min(Comparator.comparingDouble(ex -> ex.distanceToSqr(self))).orElse(null);
         };
      } else {
         return null;
      }
   }

   private static String getStr(String key, String def) {
      return instance == null ? def : instance.getSetting(key, def);
   }

   private static boolean getBool(String key, boolean def) {
      return instance == null ? def : instance.getSetting(key, def);
   }
}
