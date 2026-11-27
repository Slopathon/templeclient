package nz.temple.client.modules.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import nz.temple.client.event.FrameEvents;
import nz.temple.client.util.Friends;
import nz.temple.client.modules.SchemaModule;
import nz.temple.client.util.AccessorCompat;
import nz.temple.client.util.InventoryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class AutoSpearDtap extends SchemaModule {
   private static final long CLICK_TIMEOUT_MS = 700L;
   private static final long BACK_TIMEOUT_MS = 500L;
   private AutoSpearDtap.Phase phase = AutoSpearDtap.Phase.IDLE;
   private long phaseStart = 0L;
   private long phaseDelay = 0L;
   private int originalSlot = -1;
   private int maceSlot = -1;
   private int targetId = -1;
   private long lastDtap = 0L;
   private volatile int pendingTarget = -1;
   private volatile long pendingAt = 0L;
   private final Map<Integer, Integer> lastHurt = new HashMap<>();
   private final Random random = new Random();

   public AutoSpearDtap() {
      super("Auto Spear Dtap", "combat");
      FrameEvents.register(this::onFrame);
   }

   @Override
   public void onEnable() {
      this.reset(false);
      this.lastHurt.clear();
   }

   @Override
   public void onDisable() {
      this.reset(true);
      this.lastHurt.clear();
   }

   private void reset(boolean restoreSlot) {
      Minecraft mc = Minecraft.getInstance();
      if (restoreSlot && mc.player != null && this.originalSlot != -1 && this.phase != AutoSpearDtap.Phase.IDLE) {
         InventoryHelper.setSelected(mc.player.getInventory(), this.originalSlot);
      }

      this.phase = AutoSpearDtap.Phase.IDLE;
      this.originalSlot = -1;
      this.maceSlot = -1;
      this.targetId = -1;
      this.pendingTarget = -1;
   }

   @Override
   public void onTick() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (this.isEnabled() && player != null && mc.level != null) {
         boolean usingSpear = this.phase == AutoSpearDtap.Phase.IDLE && player.isUsingItem() && isSpear(player.getUseItem());

         for (Entity e : mc.level.getEntities(player, player.getBoundingBox().inflate(8.0), en -> en instanceof LivingEntity && en != player)) {
            LivingEntity le = (LivingEntity)e;
            int id = le.getId();
            int prev = this.lastHurt.getOrDefault(id, 0);
            int now = le.hurtTime;
            this.lastHurt.put(id, now);
            if (usingSpear && now > prev && now >= 8 && this.pendingTarget == -1 && isOnCrosshairLine(player, le)) {
               this.pendingTarget = id;
               this.pendingAt = System.currentTimeMillis();
            }
         }

         if (this.lastHurt.size() > 256) {
            this.lastHurt.keySet().removeIf(idx -> mc.level.getEntity(idx) == null);
         }

         if (this.pendingTarget != -1 && System.currentTimeMillis() - this.pendingAt > 400L) {
            this.pendingTarget = -1;
         }
      } else {
         this.lastHurt.clear();
      }
   }

   private static boolean isOnCrosshairLine(LocalPlayer player, LivingEntity target) {
      Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
      double d = to.length();
      if (d > 7.0) {
         return false;
      } else {
         return d < 0.001 ? true : player.getViewVector(1.0F).dot(to.scale(1.0 / d)) > 0.5;
      }
   }

   private void onFrame() {
      Minecraft mc = Minecraft.getInstance();
      if (this.isEnabled()) {
         LocalPlayer player = mc.player;
         if (player != null && mc.level != null) {
            if (mc.gui.screen() == null && player.isAlive()) {
               long now = System.currentTimeMillis();
               switch (this.phase) {
                  case IDLE:
                     int id = this.pendingTarget;
                     if (id == -1) {
                        return;
                     }

                     this.pendingTarget = -1;
                     this.tryStart(mc, player, id, now);
                     break;
                  case SWAP_WAIT:
                     if (now - this.phaseStart < this.phaseDelay) {
                        return;
                     }

                     Inventory inv = player.getInventory();
                     if (InventoryHelper.getSelected(inv) != this.originalSlot || !isMace(inv.getItem(this.maceSlot))) {
                        this.reset(false);
                        return;
                     }

                     InventoryHelper.setSelected(inv, this.maceSlot);
                     this.phase = AutoSpearDtap.Phase.CLICK_WAIT;
                     this.phaseStart = now;
                     this.phaseDelay = this.humanize() ? this.randomDelay("Click Delay", 20, 60) : 0L;
                     break;
                  case CLICK_WAIT:
                     long waited = now - this.phaseStart;
                     boolean timedOut = waited >= this.phaseDelay + 700L;
                     boolean useEnded = !player.isUsingItem();
                     boolean elapsed = waited >= this.phaseDelay;
                     boolean charged = player.getAttackStrengthScale(0.0F) >= Math.clamp(this.getSetting("Min Mace Charge %", 0).intValue(), 0, 100) / 100.0F;
                     if (!timedOut && (!useEnded || !elapsed || !charged)) {
                        return;
                     }

                     if (!timedOut && !this.crosshairOk(mc)) {
                        return;
                     }

                     if (!timedOut) {
                        AccessorCompat.addClick(mc.options.keyAttack);
                     }

                     this.phase = AutoSpearDtap.Phase.BACK_WAIT;
                     this.phaseStart = now;
                     this.phaseDelay = this.humanize() ? this.randomDelay("Swap Back Delay", 50, 120) : 0L;
                     break;
                  case BACK_WAIT:
                     boolean clickConsumed = AccessorCompat.getClickCount(mc.options.keyAttack) == 0;
                     boolean backElapsed = now - this.phaseStart >= this.phaseDelay;
                     boolean backTimedOut = now - this.phaseStart >= this.phaseDelay + 500L;
                     if ((!clickConsumed || !backElapsed) && !backTimedOut) {
                        return;
                     }

                     if (this.getSetting("Swap Back", true) && this.originalSlot != -1) {
                        InventoryHelper.setSelected(player.getInventory(), this.originalSlot);
                     }

                     this.lastDtap = now;
                     this.phase = AutoSpearDtap.Phase.IDLE;
                     this.originalSlot = -1;
                     this.maceSlot = -1;
                     this.targetId = -1;
               }
            } else {
               if (this.phase != AutoSpearDtap.Phase.IDLE) {
                  this.reset(true);
               }
            }
         }
      }
   }

   private void tryStart(Minecraft mc, LocalPlayer player, int id, long now) {
      if (mc.level.getEntity(id) instanceof LivingEntity target && target.isAlive()) {
         if (!Friends.isProtected(target)) {
            if (this.getSetting("Legit", false)) {
               if (this.getSetting("Only Players", true) && !(target instanceof Player)) {
                  return;
               }

               if (player.distanceTo(target) > this.getSetting("Max Distance", 3.5)) {
                  return;
               }

               if (this.getSetting("Skip Shielding", true) && isShielding(target)) {
                  return;
               }

               if (now - this.lastDtap < this.getSetting("Cooldown", 250).intValue()) {
                  return;
               }

               int chance = Math.clamp(this.getSetting("Chance %", 100).intValue(), 1, 100);
               if (chance < 100 && this.random.nextInt(100) >= chance) {
                  return;
               }
            }

            Inventory inv = player.getInventory();
            int mace = findMaceSlot(inv);
            if (mace >= 0) {
               this.originalSlot = InventoryHelper.getSelected(inv);
               this.maceSlot = mace;
               this.targetId = id;
               this.phase = AutoSpearDtap.Phase.SWAP_WAIT;
               this.phaseStart = now;
               this.phaseDelay = this.humanize() ? this.randomDelay("Mace Swap Delay", 20, 60) : 0L;
            }
         }
      }
   }

   private boolean humanize() {
      return this.getSetting("Humanize", true);
   }

   private boolean crosshairOk(Minecraft mc) {
      if (this.getSetting("Legit", false) && this.getSetting("Require Crosshair", true)) {
         HitResult hit = mc.hitResult;
         return hit != null && hit.getType() == Type.ENTITY && ((EntityHitResult)hit).getEntity() instanceof LivingEntity;
      } else {
         return true;
      }
   }

   private static boolean isShielding(LivingEntity e) {
      return e.isBlocking() ? true : e.isUsingItem() && e.getUseItem().is(Items.SHIELD) && e.getTicksUsingItem() >= 5;
   }

   private static boolean isMace(ItemStack s) {
      return !s.isEmpty() && s.is(Items.MACE);
   }

   private static boolean isSpear(ItemStack s) {
      if (s.isEmpty()) {
         return false;
      }

      try {
         return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().endsWith("spear");
      } catch (Throwable t) {
         return false;
      }
   }

   private static int findMaceSlot(Inventory inv) {
      for (int slot = 0; slot < 9; slot++) {
         if (isMace(inv.getItem(slot))) {
            return slot;
         }
      }

      return -1;
   }

   private int randomDelay(String key, int defLo, int defHi) {
      int lo = this.getSetting(key + "_lo", defLo);
      int hi = this.getSetting(key + "_hi", defHi);
      return hi <= lo ? Math.max(0, lo) : lo + this.random.nextInt(hi - lo + 1);
   }

   private enum Phase {
      IDLE,
      SWAP_WAIT,
      CLICK_WAIT,
      BACK_WAIT;
   }
}
