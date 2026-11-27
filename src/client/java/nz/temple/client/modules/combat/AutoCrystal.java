package nz.temple.client.modules.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.Map.Entry;
import nz.temple.client.event.FrameEvents;
import nz.temple.client.modules.SchemaModule;
import nz.temple.client.modules.client.CustomRotations;
import nz.temple.client.util.AccessorCompat;
import nz.temple.client.util.CrystalDamage;
import nz.temple.client.util.InventoryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class AutoCrystal extends SchemaModule {
   private AutoCrystal.Stage stage = AutoCrystal.Stage.IDLE;
   private long stageReadyAt = 0L;
   private long placeReadyAt = 0L;
   private long breakReadyAt = 0L;
   private long swapBackReadyAt = 0L;
   private int savedSlot = -1;
   private int lastAttackedCrystalId = -1;
   private final SplittableRandom rng = new SplittableRandom();
   private static AutoCrystal INSTANCE;
   private static final double KILL_RANGE = 24.0;
   private boolean killStopped = false;
   private final Map<Integer, Float> killWatch = new HashMap<>();
   private long killWatchUntil = 0L;
   private final Set<Integer> aliveSeen = new HashSet<>();
   private AutoCrystal.Bob bob = AutoCrystal.Bob.NONE;
   private BlockPos lastBase = null;
   private int bobCrystalId = -1;
   private long bobStartMs = 0L;
   private boolean aiming = false;
   private float aimYaw;
   private float aimPitch;
   private static final long BOB_TIMEOUT_MS = 750L;
   private static final float BACK_DONE_DEG = 0.5F;

   public AutoCrystal() {
      super("Auto Crystal", "Combat");
      INSTANCE = this;
      FrameEvents.register(this::onFrame);
   }

   @Override
   public void onDisable() {
      AimAssist.clearSilentRotation();
      this.swapBackReadyAt = 0L;
      this.restoreSlotNow(Minecraft.getInstance());
      this.resetState();
      this.clearKillState();
   }

   @Override
   public void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (this.isEnabled()) {
         if (mc.player != null && mc.level != null && mc.gameMode != null) {
            if (mc.gui.screen() == null) {
               if (!mc.options.keyUse.isDown()) {
                  this.clearKillState();
               } else {
                  this.pollKills(mc);
               }

               if (!mc.player.isUsingItem()) {
                  long now = System.currentTimeMillis();
                  if (this.swapBackReadyAt > 0L && now >= this.swapBackReadyAt) {
                     this.restoreSlotNow(mc);
                     this.swapBackReadyAt = 0L;
                  }

                  boolean rmbDown = mc.options.keyUse.isDown();
                  if (!rmbDown && this.stage != AutoCrystal.Stage.IDLE) {
                     AimAssist.clearSilentRotation();
                     this.beginRestoreSlot(mc, now);
                     this.resetState();
                  } else if (rmbDown) {
                     switch (this.stage) {
                        case IDLE:
                           this.tickIdle(mc, now);
                           break;
                        case SWITCH_CRYSTAL:
                           this.tickSwitchCrystal(mc, now);
                           break;
                        case NORMAL:
                           this.tickNormal(mc, now);
                     }
                  }
               }
            }
         }
      }
   }

   private void tickIdle(Minecraft mc, long now) {
      if (this.canActivateHitCrystal(mc)) {
         if (this.workWithAllows(mc)) {
            this.activateHitCrystal(mc, now);
         }
      }
   }

   private boolean canActivateHitCrystal(Minecraft mc) {
      if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof EndCrystal) {
         return this.hasCrystalInHotbar(mc);
      } else if (mc.hitResult instanceof BlockHitResult blockHit) {
         BlockPos pos = blockHit.getBlockPos();
         BlockState state = mc.level.getBlockState(pos);
         if (state.isAir()) {
            return false;
         } else {
            return !state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK) ? false : this.hasCrystalInHotbar(mc);
         }
      } else {
         return false;
      }
   }

   private void activateHitCrystal(Minecraft mc, long now) {
      if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof EndCrystal) {
         this.stageReadyAt = now + this.switchDelayMs();
         this.stage = AutoCrystal.Stage.SWITCH_CRYSTAL;
      } else if (mc.hitResult instanceof BlockHitResult blockHit) {
         BlockPos var9 = blockHit.getBlockPos();
         BlockState hitState = mc.level.getBlockState(var9);
         if (hitState.is(Blocks.OBSIDIAN) || hitState.is(Blocks.BEDROCK)) {
            this.stageReadyAt = now + this.switchDelayMs();
            this.stage = AutoCrystal.Stage.SWITCH_CRYSTAL;
         }
      }
   }

   private void tickSwitchCrystal(Minecraft mc, long now) {
      if (now >= this.stageReadyAt) {
         int slot = this.findSlot(mc, Items.END_CRYSTAL);
         if (slot == -1) {
            this.resetState();
         } else {
            this.swapTo(mc, slot);
            this.placeReadyAt = 0L;
            this.breakReadyAt = now + this.breakDelayMs();
            this.stage = AutoCrystal.Stage.NORMAL;
         }
      }
   }

   private void tickNormal(Minecraft mc, long now) {
      if (!mc.player.getMainHandItem().is(Items.END_CRYSTAL)) {
         int slot = this.findSlot(mc, Items.END_CRYSTAL);
         if (slot == -1) {
            this.resetState();
         } else {
            this.swapTo(mc, slot);
         }
      } else if (this.actionsBlocked(mc, now)) {
         if (this.aiming) {
            AimAssist.clearSilentRotation();
            this.aiming = false;
            this.bob = AutoCrystal.Bob.NONE;
            this.lastBase = null;
         }
      } else {
         if (this.getSetting("Head Bob", false)) {
            this.updateHeadBob(mc, now);
         }

         HitResult hit = mc.hitResult;
         if (hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            boolean validBase = state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
            if (validBase && now >= this.placeReadyAt && this.bob == AutoCrystal.Bob.NONE) {
               if (this.lastAttackedCrystalId != -1) {
                  if (this.crystalIsAlive(mc, this.lastAttackedCrystalId)) {
                     return;
                  }

                  this.lastAttackedCrystalId = -1;
               }

               if (!this.hasCrystalOnBlock(mc, pos)) {
                  this.simulateUse(mc);
                  this.placeReadyAt = now + this.placeDelayMs();
                  this.lastBase = pos.immutable();
               }
            }
         }

         if (hit instanceof EntityHitResult ehr
            && ehr.getEntity() instanceof EndCrystal crystal
            && !crystal.isRemoved()
            && crystal.isAlive()
            && now >= this.breakReadyAt) {
            this.watchIfLethal(mc, crystal, now);
            this.simulateAttack(mc);
            this.lastAttackedCrystalId = crystal.getId();
            this.breakReadyAt = now + this.breakDelayMs();
         }
      }
   }

   private void updateHeadBob(Minecraft mc, long now) {
      switch (this.bob) {
         case NONE:
            if (this.lastBase == null) {
               return;
            }

            EndCrystal c = this.findCrystalAbove(mc, this.lastBase);
            if (c == null || !c.isAlive() || crosshairOn(mc, c) || !inReach(mc, c)) {
               return;
            }

            this.bob = AutoCrystal.Bob.TO_CRYSTAL;
            this.bobCrystalId = c.getId();
            this.bobStartMs = now;
            this.aiming = false;
            this.tickToCrystal(mc, now, c);
            break;
         case TO_CRYSTAL:
            EndCrystal bobCrystal = crystalById(mc, this.bobCrystalId);
            if (bobCrystal == null || bobCrystal.isRemoved() || !bobCrystal.isAlive() || now - this.bobStartMs > 750L) {
               this.bob = AutoCrystal.Bob.BACK;
               this.tickBack(mc);
               return;
            }

            this.tickToCrystal(mc, now, bobCrystal);
            break;
         case BACK:
            this.tickBack(mc);
      }
   }

   private void tickToCrystal(Minecraft mc, long now, EndCrystal c) {
      float[] sent = this.aiming ? new float[]{this.aimYaw, this.aimPitch} : null;
      float[] target = crystalAim(mc, c);
      this.stepAim(mc, target[0], target[1]);
      if (sent != null && now >= this.breakReadyAt) {
         if (rayHitsEntity(mc, sent[0], sent[1], c)) {
            this.watchIfLethal(mc, c, now);
            mc.gameMode.attack(mc.player, c);
            nz.temple.client.compat.Players.swing(mc.player, InteractionHand.MAIN_HAND);
            this.lastAttackedCrystalId = c.getId();
            this.breakReadyAt = now + this.breakDelayMs();
         }
      }
   }

   private void tickBack(Minecraft mc) {
      if (!this.aiming) {
         this.finishBob();
      } else {
         float camYaw = mc.player.getYRot();
         float camPitch = mc.player.getXRot();
         this.stepAim(mc, camYaw, camPitch);
         float dy = Mth.wrapDegrees(camYaw - this.aimYaw);
         if (Math.abs(dy) < 0.5F && Math.abs(camPitch - this.aimPitch) < 0.5F) {
            this.finishBob();
         }
      }
   }

   private void finishBob() {
      if (this.aiming) {
         AimAssist.clearSilentRotation();
      }

      this.aiming = false;
      this.bob = AutoCrystal.Bob.NONE;
      this.lastBase = null;
   }

   private void stepAim(Minecraft mc, float tYaw, float tPitch) {
      float fromYaw = this.aiming ? this.aimYaw : mc.player.getYRot();
      float fromPitch = this.aiming ? this.aimPitch : mc.player.getXRot();
      float speed = Math.max(1.0F, this.getSetting("Rotation Speed", 180.0F));
      float nYaw = stepAngle(fromYaw, tYaw, speed);
      float nPitch = Mth.clamp(stepAngle(fromPitch, tPitch, speed), -90.0F, 90.0F);
      float[] cr = CustomRotations.shape(this, fromYaw, fromPitch, tYaw, tPitch, nYaw, nPitch);
      this.aimYaw = cr[0];
      this.aimPitch = Mth.clamp(cr[1], -90.0F, 90.0F);
      this.aiming = true;
      AimAssist.setSilentRotation(this.aimYaw, this.aimPitch);
   }

   private static float stepAngle(float from, float to, float maxStep) {
      float d = Mth.wrapDegrees(to - from);
      return Math.abs(d) <= maxStep ? from + d : from + Math.signum(d) * maxStep;
   }

   private void onFrame() {
      if (this.isEnabled()) {
         if (this.aiming && !this.killStopped) {
            AimAssist.setSilentRotation(this.aimYaw, this.aimPitch);
         }
      }
   }

   private static boolean rayHitsEntity(Minecraft mc, float yaw, float pitch, EndCrystal c) {
      Vec3 eyes = mc.player.getEyePosition();
      double range = mc.player.entityInteractionRange();
      Vec3 end = eyes.add(AimAssist.getRotationVector(pitch, yaw).scale(range));
      Optional<Vec3> hit = c.getBoundingBox().inflate(c.getPickRadius()).clip(eyes, end);
      if (hit.isEmpty()) {
         return false;
      }

      double entDist = eyes.distanceToSqr(hit.get());
      BlockHitResult block = mc.level.clip(new ClipContext(eyes, hit.get(), Block.COLLIDER, Fluid.NONE, mc.player));
      return block.getType() == Type.MISS || eyes.distanceToSqr(block.getLocation()) >= entDist;
   }

   private static float[] crystalAim(Minecraft mc, EndCrystal c) {
      Vec3 eyes = mc.player.getEyePosition();
      AABB box = c.getBoundingBox().deflate(0.25);
      Vec3 centre = box.getCenter();
      double d = eyes.distanceTo(centre);
      Vec3 along = eyes.add(mc.player.getViewVector(1.0F).scale(d));
      Vec3 p = new Vec3(Mth.clamp(along.x, box.minX, box.maxX), Mth.clamp(along.y, box.minY, box.maxY), Mth.clamp(along.z, box.minZ, box.maxZ));
      Vec3 v = p.subtract(eyes);
      double h = Math.sqrt(v.x * v.x + v.z * v.z);
      return new float[]{(float)(Math.atan2(-v.x, v.z) * 180.0 / Math.PI), (float)(Math.atan2(v.y, h) * -180.0 / Math.PI)};
   }

   private static boolean crosshairOn(Minecraft mc, EndCrystal c) {
      return mc.hitResult instanceof EntityHitResult e && e.getEntity() == c;
   }

   private static boolean inReach(Minecraft mc, EndCrystal c) {
      double r = mc.player.entityInteractionRange();
      Vec3 eyes = mc.player.getEyePosition();
      AABB b = c.getBoundingBox();
      double x = Mth.clamp(eyes.x, b.minX, b.maxX);
      double y = Mth.clamp(eyes.y, b.minY, b.maxY);
      double z = Mth.clamp(eyes.z, b.minZ, b.maxZ);
      return eyes.distanceToSqr(x, y, z) <= r * r;
   }

   private static EndCrystal crystalById(Minecraft mc, int id) {
      return mc.level.getEntity(id) instanceof EndCrystal ec ? ec : null;
   }

   private boolean stopOnKill() {
      return this.getSetting("Stop On Kill", true);
   }

   private void clearKillState() {
      this.killStopped = false;
      this.killWatch.clear();
      this.killWatchUntil = 0L;
      this.aliveSeen.clear();
   }

   public static void onEntityDeath(Entity e) {
      AutoCrystal self = INSTANCE;
      Minecraft mc = Minecraft.getInstance();
      if (self != null && self.isEnabled() && self.stopOnKill()) {
         if (e instanceof Player p && mc.player != null) {
            if (!AimAssist.isSelfLike(p)) {
               if (mc.options.keyUse.isDown()) {
                  if (!(p.distanceToSqr(mc.player) > 576.0)) {
                     self.killStopped = true;
                     self.killWatch.clear();
                  }
               }
            }
         }
      }
   }

   private void pollKills(Minecraft mc) {
      if (this.stopOnKill() && !this.killStopped && mc.level != null && mc.player != null) {
         for (Player p : mc.level.players()) {
            if (p != mc.player && !(p.distanceToSqr(mc.player) > 576.0)) {
               if (!p.isDeadOrDying() && !(p.getHealth() <= 0.0F)) {
                  this.aliveSeen.add(p.getId());
               } else if (this.aliveSeen.contains(p.getId())) {
                  this.killStopped = true;
                  this.killWatch.clear();
                  return;
               }
            }
         }
      }
   }

   private void watchIfLethal(Minecraft mc, EndCrystal crystal, long now) {
      if (this.stopOnKill() && !this.killStopped && mc.level != null && mc.player != null) {
         Vec3 center = crystal.position();
         boolean any = false;

         for (Player p : mc.level.players()) {
            if (p != mc.player && !p.isDeadOrDying() && !p.isSpectator() && !(p.distanceToSqr(center) > 144.0)) {
               float hp = p.getHealth() + p.getAbsorptionAmount();
               float dmg = CrystalDamage.worstCase(mc, center, p);
               if (dmg > 0.0F && dmg * 1.15F + 0.5F >= hp) {
                  this.killWatch.put(p.getId(), hp);
                  any = true;
               }
            }
         }

         if (any) {
            int ping = 0;

            try {
               PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
               if (info != null) {
                  ping = info.getLatency();
               }
            } catch (Throwable var11) {
            }

            this.killWatchUntil = now + Math.max(600L, ping * 2L + 350L);
         }
      }
   }

   private boolean actionsBlocked(Minecraft mc, long now) {
      if (!this.stopOnKill()) {
         return false;
      }

      if (this.killStopped) {
         return true;
      }

      if (this.killWatch.isEmpty()) {
         return false;
      }

      if (now >= this.killWatchUntil) {
         this.killWatch.clear();
         return false;
      }

      Iterator<Entry<Integer, Float>> it = this.killWatch.entrySet().iterator();

      while (it.hasNext()) {
         Entry<Integer, Float> en = it.next();
         if ((mc.level == null ? null : mc.level.getEntity(en.getKey())) instanceof Player p && !p.isRemoved()) {
            if (p.isDeadOrDying() || p.getHealth() <= 0.0F) {
               this.killStopped = true;
               this.killWatch.clear();
               return true;
            }

            float curr = p.getHealth() + p.getAbsorptionAmount();
            if (curr < en.getValue() - 0.01F) {
               it.remove();
            }
         } else {
            it.remove();
         }
      }

      return !this.killWatch.isEmpty();
   }

   public void beginHandoff(int inheritedSavedSlot) {
      if (this.isEnabled()) {
         if (this.stage == AutoCrystal.Stage.IDLE) {
            long now = System.currentTimeMillis();
            this.savedSlot = inheritedSavedSlot;
            this.lastAttackedCrystalId = -1;
            this.placeReadyAt = 0L;
            this.breakReadyAt = now + this.breakDelayMs();
            this.stage = AutoCrystal.Stage.NORMAL;
         }
      }
   }

   private void resetState() {
      if (this.aiming) {
         AimAssist.clearSilentRotation();
      }

      this.aiming = false;
      this.bob = AutoCrystal.Bob.NONE;
      this.lastBase = null;
      this.bobCrystalId = -1;
      this.stage = AutoCrystal.Stage.IDLE;
      this.stageReadyAt = 0L;
      this.lastAttackedCrystalId = -1;
      this.placeReadyAt = 0L;
      this.breakReadyAt = 0L;
   }

   private void beginRestoreSlot(Minecraft mc, long now) {
      if (this.savedSlot >= 0 && mc.player != null) {
         if (!this.getSetting("Swap Back", true)) {
            this.savedSlot = -1;
         } else {
            long delay = this.swapBackDelayMs();
            if (delay <= 0L) {
               this.restoreSlotNow(mc);
            } else {
               this.swapBackReadyAt = now + delay;
            }
         }
      }
   }

   private void restoreSlotNow(Minecraft mc) {
      if (this.savedSlot >= 0 && mc.player != null) {
         setSelected(mc.player.getInventory(), this.savedSlot);
      }

      this.savedSlot = -1;
      this.swapBackReadyAt = 0L;
   }

   private void simulateUse(Minecraft mc) {
      AccessorCompat.addClick(mc.options.keyUse);
   }

   private void simulateAttack(Minecraft mc) {
      if (mc.hitResult instanceof EntityHitResult ehr) {
         mc.gameMode.attack(mc.player, ehr.getEntity());
         nz.temple.client.compat.Players.swing(mc.player, InteractionHand.MAIN_HAND);
      }
   }

   private EndCrystal findCrystalAbove(Minecraft mc, BlockPos pos) {
      AABB region = new AABB(pos.above()).inflate(0.2, 1.0, 0.2);

      for (EndCrystal ec : mc.level.getEntitiesOfClass(EndCrystal.class, region)) {
         if (!ec.isRemoved() && BlockPos.containing(ec.position()).below().equals(pos)) {
            return ec;
         }
      }

      return null;
   }

   private boolean hasCrystalOnBlock(Minecraft mc, BlockPos pos) {
      return this.findCrystalAbove(mc, pos) != null;
   }

   private boolean crystalIsAlive(Minecraft mc, int id) {
      return mc.level.getEntity(id) instanceof EndCrystal ec && !ec.isRemoved() && ec.isAlive();
   }

   private boolean workWithAllows(Minecraft mc) {
      boolean wCrystal = this.getSetting("Work with Crystal", true);
      boolean wSword = this.getSetting("Work with Sword", true);
      boolean wTotem = this.getSetting("Work with Totem", true);
      boolean wFist = this.getSetting("Work with Fist", true);
      if (!wCrystal && !wSword && !wTotem && !wFist) {
         return true;
      } else {
         ItemStack held = mc.player.getMainHandItem();
         if (wCrystal && held.is(Items.END_CRYSTAL)) {
            return true;
         } else if (wSword && held.is(ItemTags.SWORDS)) {
            return true;
         } else {
            return wTotem && held.is(Items.TOTEM_OF_UNDYING) ? true : wFist && held.isEmpty();
         }
      }
   }

   private boolean hasCrystalInHotbar(Minecraft mc) {
      return this.findSlot(mc, Items.END_CRYSTAL) != -1;
   }

   private int findSlot(Minecraft mc, Item item) {
      for (int i = 0; i < 9; i++) {
         if (mc.player.getInventory().getItem(i).is(item)) {
            return i;
         }
      }

      return -1;
   }

   private void swapTo(Minecraft mc, int slot) {
      if (this.savedSlot == -1) {
         this.savedSlot = getSelected(mc.player.getInventory());
      }

      setSelected(mc.player.getInventory(), slot);
   }

   private static int getSelected(Inventory inv) {
      return InventoryHelper.getSelected(inv);
   }

   private static void setSelected(Inventory inv, int slot) {
      InventoryHelper.setSelected(inv, slot);
   }

   private long switchDelayMs() {
      return this.randomMs("Switch Delay", 1L, 50L);
   }

   private long placeDelayMs() {
      return this.randomMs("Place Delay", 50L, 100L);
   }

   private long breakDelayMs() {
      return this.randomMs("Break Delay", 50L, 100L);
   }

   private long swapBackDelayMs() {
      return this.randomMs("Swap Back Delay", 50L, 100L);
   }

   private long randomMs(String key, long loDefault, long hiDefault) {
      long lo = toLong(this.getSetting(key + "_lo", loDefault));
      long hi = toLong(this.getSetting(key + "_hi", hiDefault));
      if (hi < lo) {
         hi = lo;
      }

      return lo == hi ? lo : lo + this.rng.nextLong(hi - lo + 1L);
   }

   private static long toLong(Object o) {
      if (o == null) {
         return 0L;
      } else if (o instanceof Number n) {
         return n.longValue();
      } else {
         try {
            return Long.parseLong(o.toString());
         } catch (NumberFormatException e) {
            return 0L;
         }
      }
   }

   private enum Bob {
      NONE,
      TO_CRYSTAL,
      BACK;
   }

   private enum Stage {
      IDLE,
      SWITCH_CRYSTAL,
      NORMAL;
   }
}
