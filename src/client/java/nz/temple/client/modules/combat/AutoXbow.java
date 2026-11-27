package nz.temple.client.modules.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import nz.temple.client.event.FrameEvents;
import nz.temple.client.event.PlayerTickEvents;
import nz.temple.client.event.WorldRenderEvent;
import nz.temple.client.input.BindStrings;
import nz.temple.client.modules.SchemaModule;
import nz.temple.client.modules.client.CustomRotations;
import nz.temple.client.render.WorldDraw;
import nz.temple.client.util.InventoryHelper;
import nz.temple.client.util.RotationGcd;
import nz.temple.client.util.ColorStrings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class AutoXbow extends SchemaModule {
   private static final int STAGE_TIMEOUT = 40;
   private static final int JOB_TIMEOUT = 240;
   private static final int SHOOT_TIMEOUT = 100;
   private static final int CART_WAIT = 30;
   private static final double FACE_MARGIN = 0.05;
   private static final double ARROW_SPEED = 3.15;
   private static final Item[] RAILS = new Item[]{Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL};
   private static final Direction[] HORIZONTAL = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
   private static final Field RIGHT_CLICK_DELAY_FIELD;
   private final Random rng = new Random();
   private String shotWhy = "";
   private String bestWhy = "";
   private String fireWhy = "";
   private String fireDiag = "";
   private AutoXbow.Stage stage = AutoXbow.Stage.IDLE;
   private BlockPos railPos = null;
   private BlockPos firePos = null;
   private BlockPos slabPos = null;
   private int ticks = 0;
   private int jobStart = 0;
   private int stageStart = 0;
   private long actAfterMs = 0L;
   private long cooldownUntil = 0L;
   private double jx;
   private double jy;
   private double jz;
   private double jA;
   private double jB;
   private boolean centerOnly = false;
   private int stuckTicks = 0;
   private final ArrayDeque<AutoXbow.RailCand> cands = new ArrayDeque<>();
   private int lastUseTick = -100;
   private boolean bindWasDown = false;
   private int savedSlot = -1;
   private int lastSetSlot = -1;
   private long swapBackAt = 0L;
   private boolean aiming = false;
   private boolean aimSilent = true;
   private float aimYaw;
   private float aimPitch;
   private boolean haveSent = false;
   private float sentYaw;
   private float sentPitch;
   private int aimStable = 0;

   private boolean no(String why) {
      this.shotWhy = why;
      return false;
   }

   public AutoXbow() {
      super("Auto Xbow", "Combat");
      FrameEvents.register(this::onFrame);
      PlayerTickEvents.register(this::onPostTick);
      WorldRenderEvent.register(this::onWorldRender);
   }

   private boolean bindMode() {
      return "Bind".equals(this.getSetting("Mode", "Rail"));
   }

   private boolean silent() {
      return this.getSetting("Silent Rotations", true);
   }

   private boolean humanize() {
      return this.getSetting("Humanize", true);
   }

   private boolean safe() {
      return this.getSetting("Safe", false);
   }

   private boolean autoAim() {
      return this.bindMode() && this.getSetting("Auto Aim", false);
   }

   private int shotHold() {
      return Mth.clamp(this.getSetting("Shot Hold", 2), 0, 6);
   }

   @Override
   public void onEnable() {
      this.hardReset();
      Minecraft mc = Minecraft.getInstance();
      this.bindWasDown = mc.getWindow() != null && isBindDown(mc, this.getSetting("Bind", "NONE"));
   }

   @Override
   public void onDisable() {
      Minecraft mc = Minecraft.getInstance();
      this.restoreSlot(mc);
      this.stopAim();
      this.hardReset();
   }

   private void hardReset() {
      this.stage = AutoXbow.Stage.IDLE;
      this.railPos = null;
      this.firePos = null;
      this.slabPos = null;
      this.cands.clear();
      this.actAfterMs = 0L;
      this.swapBackAt = 0L;
   }

   private void onFrame() {
      if (this.isEnabled() && this.aiming && this.aimSilent) {
         AimAssist.setSilentRotation(this.aimYaw, this.aimPitch);
      }
   }

   @Override
   public void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && mc.gameMode != null) {
         this.ticks++;
         long now = System.currentTimeMillis();
         Inventory inv = mc.player.getInventory();
         if (this.lastSetSlot != -1 && InventoryHelper.getSelected(inv) != this.lastSetSlot) {
            this.lastSetSlot = -1;
            this.savedSlot = -1;
            this.swapBackAt = 0L;
            if (this.stage != AutoXbow.Stage.IDLE) {
               this.finish(now);
               return;
            }
         }

         boolean bindDown = isBindDown(mc, this.getSetting("Bind", "NONE"));
         boolean bindFresh = bindDown && !this.bindWasDown;
         this.bindWasDown = bindDown;
         if (this.stage == AutoXbow.Stage.IDLE) {
            if (this.swapBackAt > 0L && now >= this.swapBackAt) {
               this.restoreSlot(mc);
            }

            if (mc.gui.screen() == null && now >= this.cooldownUntil) {
               if (this.bindMode()) {
                  this.cands.clear();
                  if (bindFresh) {
                     this.startBind(mc, now);
                  }
               } else {
                  this.trackRails(mc, now);
               }
            } else {
               this.cands.clear();
            }
         } else {
            LocalPlayer p = mc.player;
            if (mc.gui.screen() == null && p.isAlive() && !p.isSpectator()) {
               if (this.ticks - this.jobStart <= 240 && this.ticks - this.stageStart <= (this.stage == AutoXbow.Stage.SHOOT ? 100 : 40)) {
                  setRightClickDelay(mc, 4);
                  if (!p.isUsingItem()) {
                     if (now >= this.actAfterMs) {
                        switch (this.stage) {
                           case RAIL:
                              this.planRail(mc, now);
                              break;
                           case CART:
                              this.planCart(mc, now);
                              break;
                           case FIRE:
                              this.planFire(mc, now);
                              break;
                           case SLAB:
                              this.planSlab(mc, now);
                              break;
                           case SHOOT:
                              this.planShoot(mc, now);
                        }
                     }
                  }
               } else {
                  this.timedOut();
                  this.finish(now);
               }
            } else {
               this.finish(now);
            }
         }
      } else {
         this.hardReset();
      }
   }

   private void trackRails(Minecraft mc, long now) {
      if (mc.options.keyUse.isDown()) {
         this.lastUseTick = this.ticks;
      }

      Iterator<AutoXbow.RailCand> it = this.cands.iterator();

      while (it.hasNext()) {
         AutoXbow.RailCand c = it.next();
         if (this.ticks - c.tick() > 4) {
            it.remove();
         } else if (c.tick() < this.ticks && !c.wasRail() && this.lastUseTick >= c.tick() && isRail(mc, c.pos())) {
            this.cands.clear();
            this.startJob(mc, now, c.pos(), true, false);
            return;
         }
      }

      if (isRailItem(mc.player.getMainHandItem()) && mc.hitResult instanceof BlockHitResult bh && bh.getType() == Type.BLOCK) {
         BlockPos hp = bh.getBlockPos();
         BlockPos cand = mc.level.getBlockState(hp).canBeReplaced() ? hp : hp.relative(bh.getDirection());
         this.cands.addLast(new AutoXbow.RailCand(cand, isRail(mc, cand), this.ticks));

         while (this.cands.size() > 6) {
            this.cands.removeFirst();
         }
      }
   }

   private void startBind(Minecraft mc, long now) {
      if (this.autoAim()) {
         this.startAuto(mc, now);
      } else if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == Type.BLOCK) {
         BlockPos hp = bh.getBlockPos();
         if (isRail(mc, hp)) {
            this.startJob(mc, now, hp, true, true);
         } else {
            BlockPos spot = mc.level.getBlockState(hp).canBeReplaced() ? hp : hp.relative(bh.getDirection());
            this.startJob(mc, now, spot, isRail(mc, spot), true);
         }
      } else {
         note("Look at a block to place the rail on");
      }
   }

   private void startAuto(Minecraft mc, long now) {
      LocalPlayer p = mc.player;
      Vec3 want = this.autoTarget(mc);
      Vec3 eyes = p.getEyePosition();
      double reachSq = sq(Reach.vanillaBlockReach(p));
      BlockPos base = p.blockPosition();
      List<AutoXbow.Spot> spots = new ArrayList<>();

      for (int dx = -6; dx <= 6; dx++) {
         for (int dz = -6; dz <= 6; dz++) {
            for (int dy = -2; dy <= 2; dy++) {
               BlockPos pos = base.offset(dx, dy, dz);
               Vec3 ctr = Vec3.atCenterOf(pos);
               if (!(Math.hypot(ctr.x - eyes.x, ctr.z - eyes.z) < 1.5) && !(eyes.distanceToSqr(ctr) > reachSq)) {
                  boolean rail = isRail(mc, pos);
                  if (rail ? this.railClick(mc, pos) != null : this.validRailSpot(mc, pos) && this.findPlacement(mc, pos) != null) {
                     spots.add(new AutoXbow.Spot(pos, ctr.distanceTo(want) - (rail ? 0.5 : 0.0)));
                  }
               }
            }
         }
      }

      if (spots.isEmpty()) {
         note("Auto Aim: no rail spot in reach");
      } else {
         spots.sort((a, b) -> Double.compare(a.score(), b.score()));
         this.rollJitter();
         BlockPos best = null;
         BlockPos fallback = null;
         String why = "";
         int tried = 0;

         for (AutoXbow.Spot sp : spots) {
            if (tried++ >= 10) {
               break;
            }

            BlockPos fire = this.chooseFire(mc, sp.pos());
            if (fire == null) {
               why = this.fireDiag;
            } else {
               if (!this.safe() || this.chooseSlab(mc, fire, sp.pos()) != null) {
                  best = sp.pos();
                  break;
               }

               if (fallback == null) {
                  fallback = sp.pos();
               }
            }
         }

         if (best == null) {
            best = fallback;
         }

         if (best == null) {
            note("Auto Aim: no spot works" + (why.isEmpty() ? "" : " - " + why));
         } else {
            this.startJob(mc, now, best, isRail(mc, best), true);
         }
      }
   }

   private Vec3 autoTarget(Minecraft mc) {
      LocalPlayer p = mc.player;
      Entity nearest = null;
      double bd = 256.0;

      for (Entity e : mc.level.players()) {
         if (e != p && e.isAlive() && !e.isSpectator()) {
            double d = e.distanceToSqr(p);
            if (d < bd) {
               bd = d;
               nearest = e;
            }
         }
      }

      if (nearest != null) {
         return nearest.position();
      }

      Vec3 look = AimAssist.getRotationVector(0.0F, p.getYRot());
      return p.position().add(look.x * 3.5, 0.0, look.z * 3.5);
   }

   private void startJob(Minecraft mc, long now, BlockPos rail, boolean hasRail, boolean explicit) {
      LocalPlayer p = mc.player;
      if (mc.gui.screen() == null && !p.isSpectator() && p.isAlive() && !p.isUsingItem()) {
         String fail = null;
         if (findSlot(mc, Items.TNT_MINECART) < 0) {
            fail = "No TNT minecart in your hotbar";
         } else if (findSlot(mc, Items.FLINT_AND_STEEL) < 0) {
            fail = "No flint and steel in your hotbar";
         } else if (findLoadedCrossbow(mc) < 0) {
            fail = "No loaded crossbow in your hotbar";
         } else if (!hasRail && findRailSlot(mc) < 0) {
            fail = "No rails in your hotbar";
         }

         if (fail != null) {
            if (explicit) {
               note(fail);
            }
         } else {
            if (!this.validRailSpot(mc, rail)) {
               fail = "Rail spot isn't valid or is out of reach";
            }

            if (fail != null) {
               if (explicit) {
                  note(fail);
               }
            } else {
               this.rollJitter();
               this.railPos = rail;
               this.firePos = null;
               this.slabPos = null;
               if (hasRail ? this.railClick(mc, rail) != null : this.findPlacement(mc, rail) != null) {
                  this.firePos = this.chooseFire(mc, rail);
                  if (this.firePos == null) {
                     fail = "No clear shot through fire: " + this.fireDiag;
                  }
               } else {
                  fail = "Can't see a face to click for the rail";
               }

               if (fail != null) {
                  this.railPos = null;
                  this.firePos = null;
                  if (explicit) {
                     note(fail);
                  }
               } else {
                  this.jobStart = this.ticks;
                  this.setStage(hasRail ? AutoXbow.Stage.CART : AutoXbow.Stage.RAIL);
                  this.actAfterMs = now + (this.humanize() ? this.randomMs("Reaction Delay", 60, 180) : 0L);
               }
            }
         }
      }
   }

   private void planRail(Minecraft mc, long now) {
      if (isRail(mc, this.railPos)) {
         this.advance(AutoXbow.Stage.CART, now, "Step Delay");
      } else {
         BlockState st = mc.level.getBlockState(this.railPos);
         if (st.canBeReplaced() && st.getFluidState().isEmpty()) {
            int slot = findRailSlot(mc);
            if (slot < 0) {
               this.finish(now);
            } else if (this.ensureSelected(mc, slot, now)) {
               AutoXbow.Click c = this.findPlacement(mc, this.railPos);
               if (c == null) {
                  this.finish(now);
               } else {
                  if (this.blockStep(mc, c, c.hit().getBlockPos(), c.hit().getDirection())) {
                     this.advance(AutoXbow.Stage.CART, now, "Step Delay");
                  }
               }
            }
         } else {
            this.finish(now);
         }
      }
   }

   private void planCart(Minecraft mc, long now) {
      if (!isRail(mc, this.railPos)) {
         this.finish(now);
      } else if (findCart(mc, this.railPos) != null) {
         this.advance(AutoXbow.Stage.FIRE, now, "Step Delay");
      } else {
         int slot = findSlot(mc, Items.TNT_MINECART);
         if (slot < 0) {
            this.finish(now);
         } else if (this.ensureSelected(mc, slot, now)) {
            AutoXbow.Click c = this.railClick(mc, this.railPos);
            if (c == null) {
               this.finish(now);
            } else {
               if (this.blockStep(mc, c, this.railPos, null)) {
                  this.advance(AutoXbow.Stage.FIRE, now, "Step Delay");
               }
            }
         }
      }
   }

   private void planFire(Minecraft mc, long now) {
      if (!isRail(mc, this.railPos)) {
         this.finish(now);
      } else {
         if (this.firePos == null || !this.firePlaceable(mc, this.firePos)) {
            this.firePos = this.chooseFire(mc, this.railPos);
            if (this.firePos == null) {
               this.finish(now);
               return;
            }
         }

         if (isFire(mc, this.firePos)) {
            this.afterFire(now);
         } else {
            int slot = findSlot(mc, Items.FLINT_AND_STEEL);
            if (slot < 0) {
               this.finish(now);
            } else if (this.ensureSelected(mc, slot, now)) {
               AutoXbow.Click c = this.findPlacement(mc, this.firePos);
               if (c == null) {
                  this.finish(now);
               } else {
                  if (this.blockStep(mc, c, c.hit().getBlockPos(), c.hit().getDirection())) {
                     this.afterFire(now);
                  }
               }
            }
         }
      }
   }

   private void afterFire(long now) {
      if (this.safe()) {
         this.advance(AutoXbow.Stage.SLAB, now, "Step Delay");
      } else {
         this.advance(AutoXbow.Stage.SHOOT, now, "Shot Delay");
      }
   }

   private void planSlab(Minecraft mc, long now) {
      if (!this.safe()) {
         this.advance(AutoXbow.Stage.SHOOT, now, "Shot Delay");
      } else {
         if (this.slabPos == null) {
            this.slabPos = this.chooseSlab(mc, this.firePos, this.railPos);
         }

         if (this.slabPos == null) {
            this.advance(AutoXbow.Stage.SHOOT, now, "Shot Delay");
         } else if (isSlab(mc, this.slabPos)) {
            this.advance(AutoXbow.Stage.SHOOT, now, "Shot Delay");
         } else if (!mc.level.getBlockState(this.slabPos).isAir()) {
            this.slabPos = null;
            this.advance(AutoXbow.Stage.SHOOT, now, "Shot Delay");
         } else {
            int slot = findSlabSlot(mc);
            if (slot < 0) {
               this.advance(AutoXbow.Stage.SHOOT, now, "Shot Delay");
            } else if (this.ensureSelected(mc, slot, now)) {
               AutoXbow.Click c = this.topClick(mc, this.slabPos);
               if (c == null) {
                  this.slabPos = null;
                  this.advance(AutoXbow.Stage.SHOOT, now, "Shot Delay");
               } else {
                  if (this.blockStep(mc, c, c.hit().getBlockPos(), Direction.UP)) {
                     this.advance(AutoXbow.Stage.SHOOT, now, "Shot Delay");
                  }
               }
            }
         }
      }
   }

   private BlockPos chooseSlab(Minecraft mc, BlockPos fire, BlockPos rail) {
      if (fire != null && rail != null && findSlabSlot(mc) >= 0) {
         int ox = fire.getX() - rail.getX();
         int oz = fire.getZ() - rail.getZ();
         BlockPos s = fire.offset(ox, 0, oz);
         Vec3 eyes = mc.player.getEyePosition();
         if ((eyes.x - (fire.getX() + 0.5)) * ox + (eyes.z - (fire.getZ() + 0.5)) * oz <= 0.0) {
            return null;
         }

         if (mc.level.getBlockState(s).isAir() && sturdyTop(mc, s.below())) {
            AABB box = slabBox(s);
            if (mc.player.getBoundingBox().intersects(box)) {
               return null;
            }

            if (this.topClick(mc, s) == null) {
               return null;
            }

            Entity cart = findCart(mc, rail);
            return this.bestShot(mc, cart != null ? cart.getBoundingBox() : assumedCartBox(rail), fire, cart, box) != null ? s : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static AABB slabBox(BlockPos s) {
      return new AABB(s.getX(), s.getY(), s.getZ(), s.getX() + 1, s.getY() + 0.5, s.getZ() + 1);
   }

   private void planShoot(Minecraft mc, long now) {
      Entity cart = findCart(mc, this.railPos);
      if (cart == null) {
         if (this.ticks - this.stageStart > 30) {
            this.finish(now);
         }
      } else if (!isFire(mc, this.firePos)) {
         if (this.ticks - this.stageStart > 12) {
            this.finish(now);
         }
      } else {
         int slot = findLoadedCrossbow(mc);
         if (slot < 0) {
            this.finish(now);
         } else if (this.ensureSelected(mc, slot, now)) {
            AABB box = cart.getBoundingBox();
            if (this.haveSent && this.shotWorks(mc, this.sentYaw, this.sentPitch, this.firePos, box, cart, null)) {
               this.holdAim(mc);
               if (this.aimStable >= this.shotHold()) {
                  this.fireShot(mc, now);
               }
            } else {
               AutoXbow.Shot s = this.bestShot(mc, box, this.firePos, cart, null);
               if (s != null) {
                  this.rotateToward(mc, s.yaw(), s.pitch());
               }
            }
         }
      }
   }

   private void fireShot(Minecraft mc, long now) {
      LocalPlayer p = mc.player;
      if (findLoadedCrossbow(mc) == InventoryHelper.getSelected(p.getInventory())) {
         float vy = p.getYRot();
         float vp = p.getXRot();
         p.setYRot(this.sentYaw);
         p.setXRot(this.sentPitch);

         InteractionResult res;
         try {
            res = mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
         } finally {
            p.setYRot(vy);
            p.setXRot(vp);
         }

         if (res.consumesAction()) {
            this.finish(now);
         }
      }
   }

   private boolean blockStep(Minecraft mc, AutoXbow.Click c, BlockPos pos, Direction face) {
      LocalPlayer p = mc.player;
      if (this.haveSent) {
         BlockHitResult r = lookHit(mc, p, this.sentYaw, this.sentPitch);
         if (r != null && r.getBlockPos().equals(pos) && (face == null || r.getDirection() == face)) {
            this.holdAim(mc);
            return this.useOn(mc, r);
         }
      }

      if (this.haveSent && this.aimStable >= 1) {
         this.stuckTicks++;
         if (this.stuckTicks == 3) {
            this.centerOnly = true;
         } else if (this.stuckTicks >= 8) {
            this.centerOnly = false;
            this.stuckTicks = 0;
            this.jA = this.rng.nextDouble() * 2.0 - 1.0;
            this.jB = this.rng.nextDouble() * 2.0 - 1.0;
         }
      } else {
         this.stuckTicks = 0;
      }

      this.rotateToward(mc, c.yaw(), c.pitch());
      return false;
   }

   private void holdAim(Minecraft mc) {
      boolean silent = this.silent();
      if (this.aiming && this.aimSilent && !silent) {
         AimAssist.clearSilentRotation();
      }

      this.aiming = true;
      this.aimSilent = silent;
      this.aimYaw = this.sentYaw;
      this.aimPitch = this.sentPitch;
      if (silent) {
         AimAssist.setSilentRotation(this.sentYaw, this.sentPitch);
      } else {
         mc.player.setYRot(this.sentYaw);
         mc.player.setXRot(this.sentPitch);
      }
   }

   private void onPostTick() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p != null && this.isEnabled()) {
         float y = p.getYRot();
         float x = p.getXRot();
         if (this.haveSent && Math.abs(Mth.wrapDegrees(y - this.sentYaw)) < 0.01F && Math.abs(x - this.sentPitch) < 0.01F) {
            this.aimStable++;
         } else {
            this.aimStable = 0;
         }

         this.sentYaw = y;
         this.sentPitch = x;
         this.haveSent = true;
      } else {
         this.haveSent = false;
         this.aimStable = 0;
      }
   }

   private void timedOut() {
      String why = switch (this.stage) {
         case RAIL, CART, FIRE, SLAB -> " - couldn't line up a click on the face";
         case SHOOT -> " - " + (this.bestWhy.isEmpty() ? "no clear shot" : this.bestWhy);
         default -> "";
      };
      note("Timed out at " + this.stage.name().toLowerCase() + why);
   }

   private void finish(long now) {
      this.stopAim();
      this.stage = AutoXbow.Stage.IDLE;
      this.railPos = null;
      this.firePos = null;
      this.slabPos = null;
      this.cands.clear();
      this.cooldownUntil = now + Math.max(0, this.getSetting("Cooldown", 1000));
      if (this.savedSlot != -1 && this.getSetting("Swap Back", true)) {
         this.swapBackAt = now + this.randomMs("Swap Back Delay", 50, 150);
      } else {
         this.savedSlot = -1;
         this.lastSetSlot = -1;
         this.swapBackAt = 0L;
      }
   }

   private void advance(AutoXbow.Stage next, long now, String delayKey) {
      this.setStage(next);
      this.actAfterMs = now + (delayKey.equals("Shot Delay") ? this.randomMs(delayKey, 60, 160) : this.randomMs(delayKey, 50, 130));
   }

   private void setStage(AutoXbow.Stage s) {
      this.stage = s;
      this.stageStart = this.ticks;
      this.centerOnly = false;
      this.stuckTicks = 0;
      this.jA = this.rng.nextDouble() * 2.0 - 1.0;
      this.jB = this.rng.nextDouble() * 2.0 - 1.0;
   }

   private void rollJitter() {
      this.jx = this.rng.nextDouble() * 2.0 - 1.0;
      this.jy = this.rng.nextDouble() * 2.0 - 1.0;
      this.jz = this.rng.nextDouble() * 2.0 - 1.0;
      this.jA = this.rng.nextDouble() * 2.0 - 1.0;
      this.jB = this.rng.nextDouble() * 2.0 - 1.0;
   }

   private BlockPos chooseFire(Minecraft mc, BlockPos rail) {
      Vec3 eyes = mc.player.getEyePosition();
      double cx = rail.getX() + 0.5;
      double cz = rail.getZ() + 0.5;
      double dx = eyes.x - cx;
      double dz = eyes.z - cz;
      double len = Math.hypot(dx, dz);
      if (len < 0.001) {
         this.fireDiag = "you are standing on the rail";
         return null;
      }

      double ux = dx / len;
      double uz = dz / len;
      List<BlockPos> spots = new ArrayList<>();

      for (int x = -1; x <= 1; x++) {
         for (int z = -1; z <= 1; z++) {
            if ((x != 0 || z != 0) && !(x * ux + z * uz <= 0.0)) {
               spots.add(rail.offset(x, 0, z));
            }
         }
      }

      spots.sort((a, b) -> Double.compare(lineScore(a, cx, cz, ux, uz), lineScore(b, cx, cz, ux, uz)));
      StringBuilder diag = new StringBuilder();
      int shown = 0;

      for (BlockPos f : spots) {
         if (this.fireOk(mc, f, rail)) {
            return f;
         }

         if (shown++ < 3) {
            if (diag.length() > 0) {
               diag.append("; ");
            }

            diag.append('(').append(f.getX() - rail.getX()).append(',').append(f.getZ() - rail.getZ()).append(") ").append(this.fireWhy);
         }
      }

      this.fireDiag = spots.isEmpty() ? "no spot on your side of the rail" : diag.toString();
      return null;
   }

   private static double lineScore(BlockPos f, double cx, double cz, double ux, double uz) {
      double rx = f.getX() + 0.5 - cx;
      double rz = f.getZ() + 0.5 - cz;
      double along = rx * ux + rz * uz;
      double off = Math.abs(rx * uz - rz * ux);
      return off + 0.05 * along;
   }

   private boolean fireOk(Minecraft mc, BlockPos f, BlockPos rail) {
      if (!isFire(mc, f)) {
         BlockState st = mc.level.getBlockState(f);
         if (!st.isAir()) {
            this.fireWhy = "not empty";
            return false;
         }

         if (!sturdyTop(mc, f.below())) {
            this.fireWhy = "no solid floor";
            return false;
         }

         if (mc.player.getBoundingBox().intersects(new AABB(f).inflate(0.1))) {
            this.fireWhy = "you are in it";
            return false;
         }

         if (this.findPlacement(mc, f) == null) {
            this.fireWhy = "can't click a face (reach/view)";
            return false;
         }
      }

      Entity cart = findCart(mc, rail);
      AABB box = cart != null ? cart.getBoundingBox() : assumedCartBox(rail);
      if (this.bestShot(mc, box, f, cart, null) != null) {
         return true;
      }

      this.fireWhy = "shot: " + this.bestWhy;
      return false;
   }

   private boolean firePlaceable(Minecraft mc, BlockPos f) {
      if (isFire(mc, f)) {
         return true;
      } else if (!mc.level.getBlockState(f).isAir()) {
         return false;
      } else {
         return !sturdyTop(mc, f.below()) ? false : this.findPlacement(mc, f) != null;
      }
   }

   private static AABB assumedCartBox(BlockPos rail) {
      double cx = rail.getX() + 0.5;
      double cz = rail.getZ() + 0.5;
      double y = rail.getY() + 0.0625;
      return new AABB(cx - 0.49, y, cz - 0.49, cx + 0.49, y + 0.7, cz + 0.49);
   }

   private static Vec3 shotStart(LocalPlayer p) {
      return new Vec3(p.getX(), p.getEyeY() - 0.1, p.getZ());
   }

   private static Vec3 shotVelocity(LocalPlayer p, float yaw, float pitch) {
      Vec3 dir = AimAssist.getRotationVector(pitch, yaw);
      Vec3 mv = p.getDeltaMovement();
      return dir.scale(3.15).add(mv.x, p.onGround() ? 0.0 : mv.y, mv.z);
   }

   private AutoXbow.Shot bestShot(Minecraft mc, AABB cartBox, BlockPos fire, Entity cart, AABB extra) {
      Vec3 c = cartBox.getCenter();
      List<Vec3> pts = new ArrayList<>();
      if (this.humanize()) {
         double j = Mth.clamp(this.getSetting("Aim Jitter", 40), 0, 100) / 100.0;
         pts.add(c.add(this.jx * 0.3 * j, this.jy * 0.2 * j, this.jz * 0.3 * j));
      }

      pts.add(c);
      double[] o = new double[]{-0.4, -0.2, 0.0, 0.2, 0.4};
      double[] oy = new double[]{0.0, 0.2, -0.15, 0.3, -0.25};

      for (double h : oy) {
         for (double a : o) {
            for (double b : o) {
               if (h != 0.0 || a != 0.0 || b != 0.0) {
                  pts.add(c.add(a, h, b));
               }
            }
         }
      }

      String first = null;

      for (Vec3 pt : pts) {
         AutoXbow.Shot s = this.solve(mc, pt);
         if (s == null) {
            if (first == null) {
               first = "no aim angle (too close or too steep)";
            }
         } else {
            if (this.shotWorks(mc, s.yaw(), s.pitch(), fire, cartBox, cart, extra)) {
               return s;
            }

            if (first == null) {
               first = this.shotWhy;
            }
         }
      }

      this.bestWhy = first == null ? "no aim points" : first;
      return null;
   }

   private AutoXbow.Shot solve(Minecraft mc, Vec3 aim) {
      LocalPlayer p = mc.player;
      Vec3 start = shotStart(p);
      double range = Math.hypot(aim.x - start.x, aim.z - start.z);
      if (range < 0.75) {
         return null;
      }

      Vec3 want = aim;
      float[] rot = lookAt(start, want);

      for (int i = 0; i < 6; i++) {
         rot = lookAt(start, want);
         Vec3 q = trajectoryAtRange(start, shotVelocity(p, rot[0], rot[1]), range);
         if (q == null) {
            return null;
         }

         Vec3 err = aim.subtract(q);
         if (err.lengthSqr() < 1.0E-4) {
            break;
         }

         want = want.add(err);
      }

      return Math.abs(rot[1]) > 89.0F ? null : new AutoXbow.Shot(rot[0], rot[1]);
   }

   private static Vec3 trajectoryAtRange(Vec3 start, Vec3 vel, double range) {
      Vec3 pos = start;
      Vec3 v = vel;

      for (int t = 0; t < 120; t++) {
         Vec3 next = pos.add(v);
         double h0 = Math.hypot(pos.x - start.x, pos.z - start.z);
         double h1 = Math.hypot(next.x - start.x, next.z - start.z);
         if (h1 >= range) {
            double f = h1 - h0 < 1.0E-6 ? 1.0 : (range - h0) / (h1 - h0);
            return pos.add(next.subtract(pos).scale(f));
         }

         pos = next;
         v = v.scale(0.99).add(0.0, -0.05, 0.0);
      }

      return null;
   }

   private boolean shotWorks(Minecraft mc, float yaw, float pitch, BlockPos fire, AABB cartBox, Entity cart, AABB extra) {
      LocalPlayer p = mc.player;
      Vec3 pos = shotStart(p);
      Vec3 v = shotVelocity(p, yaw, pitch);
      AABB fireBox = new AABB(fire).inflate(0.05);
      AABB hitBox = cartBox.inflate(0.3);
      boolean throughFire = false;

      for (int t = 0; t < 100; t++) {
         Vec3 next = pos.add(v);
         BlockHitResult bh = mc.level.clip(new ClipContext(pos, next, Block.COLLIDER, Fluid.NONE, p));
         boolean blocked = bh.getType() == Type.BLOCK;
         Vec3 end = blocked ? bh.getLocation() : next;
         double cartD = distanceInto(hitBox, pos, end);
         if (extra != null) {
            double ex = distanceInto(extra, pos, end);
            if (ex >= 0.0 && (cartD < 0.0 || ex <= cartD)) {
               return this.no("the slab is in the way");
            }
         }

         double fireD = throughFire ? -1.0 : distanceInto(fireBox, pos, end);
         double other = Double.MAX_VALUE;
         AABB sweep = new AABB(pos, end).inflate(1.0);

         for (Entity e : mc.level.getEntities(p, sweep, en -> en != cart && en.isAlive() && en.isPickable() && !en.isSpectator())) {
            double d = distanceInto(e.getBoundingBox().inflate(0.3), pos, end);
            if (d < other) {
               other = d;
            }
         }

         if (cartD >= 0.0) {
            if (other < cartD) {
               return this.no("an entity is in the way");
            }

            if (!throughFire && (!(fireD >= 0.0) || !(fireD <= cartD))) {
               return this.no("the arrow reaches the cart without crossing the fire block");
            }

            return true;
         }

         if (other < Double.MAX_VALUE) {
            return this.no("an entity is in the way");
         }

         if (fireD >= 0.0) {
            throughFire = true;
         }

         if (blocked) {
            BlockPos bp = bh.getBlockPos();
            return this.no("blocked by " + mc.level.getBlockState(bp).getBlock().getName().getString() + " at " + bp.getX() + " " + bp.getY() + " " + bp.getZ());
         }

         pos = next;
         v = v.scale(0.99).add(0.0, -0.05, 0.0);
         if (pos.y < mc.level.getMinY() - 8) {
            return this.no("the arrow falls out of the world");
         }
      }

      return this.no("the arrow never reaches the cart");
   }

   private static double distanceInto(AABB box, Vec3 from, Vec3 to) {
      if (box.contains(from)) {
         return 0.0;
      }

      Optional<Vec3> hit = box.clip(from, to);
      return hit.<Double>map(vec3 -> from.distanceToSqr(vec3)).orElse(-1.0);
   }

   private void rotateToward(Minecraft mc, float tYaw, float tPitch) {
      boolean silent = this.silent();
      float fromYaw;
      float fromPitch;
      if (this.aiming && this.aimSilent == silent) {
         fromYaw = this.aimYaw;
         fromPitch = this.aimPitch;
      } else {
         fromYaw = mc.player.getYRot();
         fromPitch = mc.player.getXRot();
      }

      float speed = Math.max(1.0F, this.getSetting("Rotation Speed", 60.0F));
      boolean instant = speed >= 180.0F;
      boolean hum = this.humanize() && !instant;
      if (hum) {
         float var = Mth.clamp(this.getSetting("Speed Variation", 25), 0, 100) / 100.0F;
         speed *= 1.0F + (this.rng.nextFloat() * 2.0F - 1.0F) * var;
         speed = Math.max(1.0F, speed);
      }

      float dy = Mth.wrapDegrees(tYaw - fromYaw);
      float dp = tPitch - fromPitch;
      float dist = Math.max(Math.abs(dy), Math.abs(dp));
      float stepMax = dist;
      if (dist > 1.0E-4F) {
         stepMax = hum ? Math.min(speed, Math.max(dist * 0.55F, Math.min(dist, 1.5F))) : Math.min(dist, speed);
      }

      float k = dist < 1.0E-4F ? 1.0F : Math.min(1.0F, stepMax / dist);
      float newYaw = fromYaw + dy * k;
      float newPitch = Mth.clamp(fromPitch + dp * k, -90.0F, 90.0F);
      float[] cr = CustomRotations.shape(this, fromYaw, fromPitch, tYaw, tPitch, newYaw, newPitch);
      newYaw = cr[0];
      newPitch = Mth.clamp(cr[1], -90.0F, 90.0F);
      if (this.aiming && this.aimSilent && !silent) {
         AimAssist.clearSilentRotation();
      }

      if (!silent) {
         newYaw = RotationGcd.snapYaw(mc.player.getYRot(), newYaw);
         newPitch = RotationGcd.snapPitch(mc.player.getXRot(), newPitch);
      }

      this.aiming = true;
      this.aimSilent = silent;
      this.aimYaw = newYaw;
      this.aimPitch = newPitch;
      if (silent) {
         AimAssist.setSilentRotation(newYaw, newPitch);
      } else {
         mc.player.setYRot(newYaw);
         mc.player.setXRot(newPitch);
      }
   }

   private void stopAim() {
      if (this.aiming && this.aimSilent) {
         AimAssist.clearSilentRotation();
      }

      this.aiming = false;
   }

   private static float[] lookAt(Vec3 eyes, Vec3 p) {
      Vec3 d = p.subtract(eyes);
      double h = Math.sqrt(d.x * d.x + d.z * d.z);
      return new float[]{(float)(Math.atan2(-d.x, d.z) * 180.0 / Math.PI), (float)(Math.atan2(d.y, h) * -180.0 / Math.PI)};
   }

   private static BlockHitResult lookHit(Minecraft mc, LocalPlayer p, float yaw, float pitch) {
      Vec3 eyes = p.getEyePosition();
      Vec3 end = eyes.add(AimAssist.getRotationVector(pitch, yaw).scale(Reach.vanillaBlockReach(p)));
      BlockHitResult r = mc.level.clip(new ClipContext(eyes, end, Block.OUTLINE, Fluid.NONE, p));
      return r.getType() == Type.BLOCK ? r : null;
   }

   private AutoXbow.Click findPlacement(Minecraft mc, BlockPos pos) {
      Vec3 eyes = mc.player.getEyePosition();
      double reachSq = sq(Reach.vanillaBlockReach(mc.player));
      AutoXbow.Click best = null;
      double bestScore = Double.MAX_VALUE;

      for (Direction dir : Direction.values()) {
         BlockPos support = pos.relative(dir);
         BlockState s = mc.level.getBlockState(support);
         if (s.isSolid() && !s.canBeReplaced() && !s.hasBlockEntity() && s.isCollisionShapeFullBlock(mc.level, support)) {
            Direction face = dir.getOpposite();
            AutoXbow.Click c = this.visiblePoint(
               mc, eyes, reachSq, support, face, Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5)
            );
            if (c != null) {
               double score = this.angleCost(mc, c) + (dir == Direction.DOWN ? 0 : 5);
               if (score < bestScore) {
                  bestScore = score;
                  best = c;
               }
            }
         }
      }

      return best;
   }

   private AutoXbow.Click railClick(Minecraft mc, BlockPos rail) {
      Vec3 eyes = mc.player.getEyePosition();
      double reachSq = sq(Reach.vanillaBlockReach(mc.player));
      double x = rail.getX() + 0.5;
      double z = rail.getZ() + 0.5;
      double top = rail.getY() + 0.0625;
      double jitter = this.humanize() && !this.centerOnly ? Mth.clamp(this.getSetting("Aim Jitter", 40), 0, 100) / 100.0 * 0.35 : 0.0;
      List<Vec3> pts = new ArrayList<>();
      if (jitter > 0.0) {
         pts.add(new Vec3(x + this.jA * jitter, top, z + this.jB * jitter));
      }

      pts.add(new Vec3(x, top, z));
      double[] o = new double[]{-0.4, 0.0, 0.4};

      for (double a : o) {
         for (double b : o) {
            if (a != 0.0 || b != 0.0) {
               pts.add(new Vec3(x + a, top, z + b));
            }
         }
      }

      pts.add(new Vec3(x, rail.getY() + 0.03, z));

      for (Vec3 pt : pts) {
         if (!(eyes.distanceToSqr(pt) > reachSq)) {
            Vec3 end = pt.add(pt.subtract(eyes).normalize().scale(0.3));
            BlockHitResult r = mc.level.clip(new ClipContext(eyes, end, Block.OUTLINE, Fluid.NONE, mc.player));
            if (r.getType() == Type.BLOCK && r.getBlockPos().equals(rail)) {
               float[] rot = lookAt(eyes, r.getLocation());
               return new AutoXbow.Click(new BlockHitResult(r.getLocation(), r.getDirection(), rail, false), rot[0], rot[1]);
            }
         }
      }

      return null;
   }

   private AutoXbow.Click topClick(Minecraft mc, BlockPos spot) {
      Vec3 eyes = mc.player.getEyePosition();
      double reachSq = sq(Reach.vanillaBlockReach(mc.player));
      BlockPos support = spot.below();
      return this.visiblePoint(mc, eyes, reachSq, support, Direction.UP, Vec3.atCenterOf(support).add(0.0, 0.5, 0.0));
   }

   private AutoXbow.Click visiblePoint(Minecraft mc, Vec3 eyes, double reachSq, BlockPos block, Direction face, Vec3 centre) {
      Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
      if (eyes.subtract(centre).dot(n) < 0.05) {
         return null;
      }

      double jitter = this.humanize() && !this.centerOnly ? Mth.clamp(this.getSetting("Aim Jitter", 40), 0, 100) / 100.0 * 0.35 : 0.0;
      List<Vec3> pts = new ArrayList<>();
      if (jitter > 0.0) {
         pts.add(offsetOnFace(centre, face, this.jA * jitter, this.jB * jitter));
      }

      pts.add(centre);
      double[] o = new double[]{-0.3, 0.0, 0.3};

      for (double a : o) {
         for (double b : o) {
            if (a != 0.0 || b != 0.0) {
               pts.add(offsetOnFace(centre, face, a, b));
            }
         }
      }

      for (Vec3 pt : pts) {
         if (!(eyes.distanceToSqr(pt) > reachSq)) {
            Vec3 end = pt.add(pt.subtract(eyes).normalize().scale(0.05));
            BlockHitResult r = mc.level.clip(new ClipContext(eyes, end, Block.OUTLINE, Fluid.NONE, mc.player));
            if (r.getType() == Type.BLOCK
               && r.getBlockPos().equals(block)
               && (face == Direction.UP && mc.level.getBlockState(block).getBlock() instanceof BaseRailBlock || r.getDirection() == face)) {
               float[] rot = lookAt(eyes, pt);
               return new AutoXbow.Click(new BlockHitResult(pt, face, block, false), rot[0], rot[1]);
            }
         }
      }

      return null;
   }

   private static Vec3 offsetOnFace(Vec3 c, Direction face, double a, double b) {
      return switch (face.getAxis()) {
         case X -> c.add(0.0, a, b);
         case Y -> c.add(a, 0.0, b);
         case Z -> c.add(a, b, 0.0);
         default -> throw new MatchException(null, null);
      };
   }

   private float angleCost(Minecraft mc, AutoXbow.Click c) {
      float y = this.aiming ? this.aimYaw : mc.player.getYRot();
      float p = this.aiming ? this.aimPitch : mc.player.getXRot();
      return Math.abs(Mth.wrapDegrees(c.yaw() - y)) + Math.abs(c.pitch() - p);
   }

   private static boolean isRail(Minecraft mc, BlockPos pos) {
      return mc.level.getBlockState(pos).getBlock() instanceof BaseRailBlock;
   }

   private static boolean isSlab(Minecraft mc, BlockPos pos) {
      return pos != null && mc.level.getBlockState(pos).getBlock() instanceof SlabBlock;
   }

   private static boolean isFire(Minecraft mc, BlockPos pos) {
      return pos != null && mc.level.getBlockState(pos).getBlock() instanceof BaseFireBlock;
   }

   private static boolean sturdyTop(Minecraft mc, BlockPos pos) {
      return mc.level.getBlockState(pos).isFaceSturdy(mc.level, pos, Direction.UP);
   }

   private boolean validRailSpot(Minecraft mc, BlockPos pos) {
      BlockState st = mc.level.getBlockState(pos);
      if (!(st.getBlock() instanceof BaseRailBlock)) {
         if (!st.canBeReplaced() || !st.getFluidState().isEmpty()) {
            return false;
         }

         if (!sturdyTop(mc, pos.below())) {
            return false;
         }
      }

      double reach = Reach.vanillaBlockReach(mc.player);
      return mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= (reach + 0.5) * (reach + 0.5);
   }

   private static Entity findCart(Minecraft mc, BlockPos pos) {
      List<Entity> list = mc.level.getEntities((Entity)null, new AABB(pos).inflate(0.2), e -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("tnt_minecart") && e.isAlive());
      return list.isEmpty() ? null : list.get(0);
   }

   private static boolean isRailItem(ItemStack s) {
      for (Item r : RAILS) {
         if (s.is(r)) {
            return true;
         }
      }

      return false;
   }

   private static int findSlot(Minecraft mc, Item item) {
      Inventory inv = mc.player.getInventory();
      int held = InventoryHelper.getSelected(inv);
      if (inv.getItem(held).is(item)) {
         return held;
      }

      for (int i = 0; i < 9; i++) {
         if (inv.getItem(i).is(item)) {
            return i;
         }
      }

      return -1;
   }

   private static int findSlabSlot(Minecraft mc) {
      Inventory inv = mc.player.getInventory();
      int held = InventoryHelper.getSelected(inv);
      if (isSlabItem(inv.getItem(held))) {
         return held;
      }

      for (int i = 0; i < 9; i++) {
         if (isSlabItem(inv.getItem(i))) {
            return i;
         }
      }

      return -1;
   }

   private static boolean isSlabItem(ItemStack s) {
      return !s.isEmpty() && s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof SlabBlock;
   }

   private static int findRailSlot(Minecraft mc) {
      Inventory inv = mc.player.getInventory();
      int held = InventoryHelper.getSelected(inv);
      if (isRailItem(inv.getItem(held))) {
         return held;
      }

      for (int i = 0; i < 9; i++) {
         if (isRailItem(inv.getItem(i))) {
            return i;
         }
      }

      return -1;
   }

   private static int findLoadedCrossbow(Minecraft mc) {
      Inventory inv = mc.player.getInventory();
      int held = InventoryHelper.getSelected(inv);
      ItemStack h = inv.getItem(held);
      if (h.is(Items.CROSSBOW) && CrossbowItem.isCharged(h)) {
         return held;
      }

      for (int i = 0; i < 9; i++) {
         ItemStack s = inv.getItem(i);
         if (s.is(Items.CROSSBOW) && CrossbowItem.isCharged(s)) {
            return i;
         }
      }

      return -1;
   }

   private boolean ensureSelected(Minecraft mc, int slot, long now) {
      Inventory inv = mc.player.getInventory();
      int cur = InventoryHelper.getSelected(inv);
      if (cur == slot) {
         this.lastSetSlot = slot;
         return true;
      }

      if (this.savedSlot == -1) {
         this.savedSlot = cur;
      }

      InventoryHelper.setSelected(inv, slot);
      this.lastSetSlot = slot;
      this.swapBackAt = 0L;
      long d = this.humanize() ? this.randomMs("Step Delay", 50, 130) : 0L;
      if (d <= 0L) {
         return true;
      }

      this.actAfterMs = now + d;
      return false;
   }

   private void restoreSlot(Minecraft mc) {
      if (this.savedSlot >= 0 && mc.player != null) {
         InventoryHelper.setSelected(mc.player.getInventory(), this.savedSlot);
      }

      this.savedSlot = -1;
      this.lastSetSlot = -1;
      this.swapBackAt = 0L;
   }

   private boolean useOn(Minecraft mc, BlockHitResult hit) {
      setRightClickDelay(mc, 0);
      InteractionResult res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
      setRightClickDelay(mc, 4);
      if (!res.consumesAction()) {
         return false;
      }

      nz.temple.client.compat.Players.swing(mc.player, InteractionHand.MAIN_HAND);
      return true;
   }

   private static void setRightClickDelay(Minecraft mc, int v) {
      if (mc != null) {
         try {
            ((nz.temple.client.mixin.MinecraftClickDelayAccessor) mc).temple$setRightClickDelay(v);
         } catch (RuntimeException var3) {
         }
      }
   }

   private static boolean isBindDown(Minecraft mc, String name) {
      return nz.temple.client.input.BindStrings.isDown(name);
   }

   private long randomMs(String key, int loDef, int hiDef) {
      int lo = this.getSetting(key + "_lo", loDef);
      int hi = this.getSetting(key + "_hi", hiDef);
      return hi > lo && this.humanize() ? lo + this.rng.nextInt(hi - lo + 1) : Math.max(0, lo);
   }

   private static void note(String msg) {
      try {
         nz.temple.client.TempleNzClient.notifications().info("Auto Xbow", msg);
      } catch (Throwable var2) {
      }
   }

   private void onWorldRender(PoseStack ps, float partialTick) {
      if (this.isEnabled() && this.getSetting("Render", true) && this.stage != AutoXbow.Stage.IDLE) {
         int rgb = ColorStrings.resolveRgb(this.getSetting("Render Color", "#ff5533"), 16733491, false);
         List<AABB> boxes = new ArrayList<>();
         if (this.railPos != null) {
            boxes.add(
               new AABB(
                  this.railPos.getX(), this.railPos.getY(), this.railPos.getZ(), this.railPos.getX() + 1, this.railPos.getY() + 0.125, this.railPos.getZ() + 1
               )
            );
         }

         if (this.firePos != null) {
            boxes.add(new AABB(this.firePos));
         }

         if (this.slabPos != null) {
            boxes.add(slabBox(this.slabPos));
         }

         if (!boxes.isEmpty()) {
            WorldDraw.drawBoxes(ps, boxes, 1426063360 | rgb & 16777215, -587202560 | rgb & 16777215, true, true);
         }
      }
   }

   private static double sq(double v) {
      return v * v;
   }

   static {
      Field f = null;

      try {
         f = nz.temple.client.util.AccessorCompat.noField();
         f.setAccessible(true);
      } catch (NoSuchFieldException var2) {
      }

      RIGHT_CLICK_DELAY_FIELD = f;
   }

   private record Click(BlockHitResult hit, float yaw, float pitch) {
   }

   private record RailCand(BlockPos pos, boolean wasRail, int tick) {
   }

   private record Shot(float yaw, float pitch) {
   }

   private record Spot(BlockPos pos, double score) {
   }

   private enum Stage {
      IDLE,
      RAIL,
      CART,
      FIRE,
      SLAB,
      SHOOT;
   }
}
