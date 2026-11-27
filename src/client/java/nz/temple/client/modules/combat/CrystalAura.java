package nz.temple.client.modules.combat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import nz.temple.client.util.SilentRotationState;
import nz.temple.client.event.FrameEvents;
import nz.temple.client.util.Friends;
import nz.temple.client.modules.SchemaModule;
import nz.temple.client.modules.client.CustomRotations;
import nz.temple.client.modules.player.Targets;
import nz.temple.client.util.InventoryHelper;
import nz.temple.client.util.RotationGcd;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CrystalAura extends SchemaModule {
   private static final double CRYSTAL_POWER_DIAMETER = 12.0;
   private static final float SELF_WEIGHT = 0.5F;
   private static final float SELF_WEIGHT_MINIMIZE = 2.5F;
   private static final int SCAN_RADIUS = 3;
   private static final int EVAL_LIMIT = 12;
   private static final int FACE_LIMIT = 40;
   private static final long HOLD_AIM_MS = 80L;
   private static final double PLACE_REACH = 3.0;
   private static final double PLAN_REACH = 2.85;
   private static final double MAX_SPOT_TARGET_DIST = 3.5;
   private static final float TURN_PENALTY_PER_DEG = 0.045F;
   private static final float MAX_YAW_TURN = 110.0F;
   private static final int RESCAN_TICKS = 3;
   private static final long AIRBORNE_GRACE_MS = 300L;
   private static final long GIVE_UP_MS = 1200L;
   private static final float HYST = 1.5F;
   private static final float BEST_ARMOR_VALUE = 20.0F;
   private static final float BEST_TOUGHNESS_VALUE = 8.0F;
   private static final int BEST_EPF = 20;
   private static final Direction[] DIRS = Direction.values();
   private static final Predicate<Entity> LIVE = e -> !e.isRemoved();
   private static final Comparator<CrystalAura.Cand> BY_BOUND = (a, b) -> {
      int c = Float.compare(b.bound, a.bound);
      if (c != 0) {
         return c;
      }

      boolean sa = a.sortKey > a.ub + 500.0F;
      boolean sb = b.sortKey > b.ub + 500.0F;
      return sa == sb ? 0 : (sa ? -1 : 1);
   };
   private static final BiFunction<CrystalAura.RayCtx, BlockPos, Boolean> RAY_TEST = (c, pos) -> {
      BlockState st = c.level.getBlockState(pos);
      if (st.isAir()) {
         return null;
      } else {
         VoxelShape shape = st.getCollisionShape(c.level, pos, c.cc);
         if (shape.isEmpty()) {
            return null;
         } else {
            return shape.clip(c.from, c.to, pos) != null ? Boolean.TRUE : null;
         }
      }
   };
   private static final Function<CrystalAura.RayCtx, Boolean> RAY_MISS = c -> Boolean.FALSE;
   private final CrystalAura.RayCtx rayCtx = new CrystalAura.RayCtx();
   private final SplittableRandom rng = new SplittableRandom();
   private final CrystalAura.Cfg cfg = new CrystalAura.Cfg();
   private int cfgRev = Integer.MIN_VALUE;
   private Holder<Enchantment> protHolder;
   private Holder<Enchantment> blastHolder;
   private RegistryAccess cachedRegAccess;
   private static volatile CrystalAura INSTANCE;
   private String lastDebug = "";
   private boolean dbg = false;
   private volatile CrystalAura.Plan plan = null;
   private CrystalAura.PlanType planType = null;
   private long planId = Long.MIN_VALUE;
   private long planSince = 0L;
   private long planSeed = 0L;
   private long lastPlanAt = 0L;
   private long lastActionAt = 0L;
   private long engagedSince = 0L;
   private BlockPos stickyBase = null;
   private boolean releaseAim = false;
   private Player airTarget = null;
   private long targetAirAt = 0L;
   private long lastSuccessAt = 0L;
   private long reactUntil = 0L;
   private final Map<Long, Long>[] blacklist = new Map[]{new HashMap(), new HashMap(), new HashMap()};
   private final Map<Integer, Long> brokenIds = new HashMap<>();
   private final Map<BlockPos, Long> recentPlaces = new HashMap<>();
   private final Map<Integer, Long> swungIds = new HashMap<>();
   private BlockPos pendingObsidian = null;
   private long pendingUntil = 0L;
   private int tick = 0;
   private int breakTick = -1000;
   private int placeTick = -1000;
   private int swapTick = -1000;
   private long lastTickNs = -1L;
   private long waitMs = 100L;
   private double lagTicks = 1.0;
   private double spawnEma = -1.0;
   private final ArrayList<CrystalAura.Pend> pend = new ArrayList<>();
   private int pendTargetId = -1;
   private float lastMyHp = -1.0F;
   private float lastTgtHp = -1.0F;
   private int lastScanTick = -1000;
   private Player lastScanTarget = null;
   private Vec3 lastScanTargetPos = Vec3.ZERO;
   private Vec3 lastScanEyes = Vec3.ZERO;
   private Vec3 lastExplosionPos = null;
   private long lastExplosionAt = 0L;
   private final Map<Integer, Vec3> seenCrystals = new HashMap<>();
   private Player checkTarget = null;
   private float checkHealth = 0.0F;
   private float checkEst = 0.0F;
   private long checkAt = 0L;
   private int noEffect = 0;
   private long suspendUntil = 0L;
   private BlockPos lastCrystalBase = null;
   private long lastCrystalAt = 0L;
   private long nextPlaceAt = 0L;
   private long nextBreakAt = 0L;
   private long nextObsidianAt = 0L;
   private long nextSwapAt = 0L;
   private long swapBackAt = 0L;
   private int savedSlot = -1;
   private boolean rotating = false;
   private float curYaw = 0.0F;
   private float curPitch = 0.0F;
   private float sentYaw = 0.0F;
   private float sentPitch = 0.0F;
   private boolean sentValid = false;
   private double speedMul = 1.0;
   private double speedMulGoal = 1.0;
   private long nextSpeedAt = 0L;
   private final double[] aimOff = new double[2];
   private AABB hypoBox = null;
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

   public CrystalAura() {
      super("Crystal Aura", "Combat");
      INSTANCE = this;
      FrameEvents.register(this::onFrame);
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)c -> {
         try {
            this.onTickEnd();
         } catch (Throwable var3) {
         }
      });
   }

   private void refreshCfg() {
      int rev = SchemaModule.settingsRevision();
      if (rev != this.cfgRev) {
         this.cfgRev = rev;
         CrystalAura.Cfg c = this.cfg;
         c.targetRange = this.getSetting("Target Range", 9.0F);
         c.breakRange = this.getSetting("Break Range", 4.5F);
         c.minDmg = this.getSetting("Min Damage", 6.0F);
         c.maxSelf = this.getSetting("Max Self Damage", 8.0F);
         c.antiSuicide = this.getSetting("Anti Suicide", true);
         c.margin = this.getSetting("Safety Margin", 2.0F);
         c.placeObs = this.getSetting("Place Obsidian", true);
         c.autoSwitch = this.getSetting("Auto Switch", true);
         c.swapBack = this.getSetting("Swap Back", true);
         c.humanize = this.getSetting("Humanize", true);
         c.randAim = this.getSetting("Randomize Aim Point", true);
         c.instantBreak = this.getSetting("Instant Break", true);
         c.airborneOnly = this.getSetting("Airborne Only", true);
         c.pauseIneffective = this.getSetting("Pause When Ineffective", false);
         c.jitter = this.getSetting("Jitter", 20);
         c.speedVar = this.getSetting("Speed Variation", 25);
         c.rotSpeed = this.getSetting("Rotation Speed", 30);
         c.maxTurn = this.getSetting("Max Turn Speed", 1800);
         c.actionsPerTick = Mth.clamp(this.getSetting("Actions Per Tick", 1), 1, 2);
         c.pingComp = this.getSetting("Ping Compensation", true);
         long[] r = this.range("Obsidian Delay", 0L, 0L);
         c.obsLo = r[0];
         c.obsHi = r[1];
         r = this.range("Place Delay", 0L, 0L);
         c.placeLo = r[0];
         c.placeHi = r[1];
         r = this.range("Break Delay", 0L, 0L);
         c.breakLo = r[0];
         c.breakHi = r[1];
         r = this.range("Swap Delay", 0L, 0L);
         c.swapLo = r[0];
         c.swapHi = r[1];
         r = this.range("Swap Back Delay", 50L, 100L);
         c.swapBackLo = r[0];
         c.swapBackHi = r[1];
         r = this.range("Reaction Delay", 0L, 50L);
         c.reactLo = r[0];
         c.reactHi = r[1];
         c.facePlaceHealth = this.getSetting("Face Place Health", 6.0F);
         c.overrideSelfDmg = this.getSetting("Override Self Damage", false);
         c.minDmgFactor = Mth.clamp(this.getSetting("Min Damage Factor", 0.0F), 0.0F, 1.0F);
         c.pauseMining = this.getSetting("Pause Mining", false);
         c.pauseEating = this.getSetting("Pause Eating", true);
         c.pauseInventory = this.getSetting("Pause Inventory", true);
         c.grimBreakRange = this.getSetting("Grim Break Range", false);
         if (c.grimBreakRange) {
            c.breakRange = Math.min(c.breakRange, 4.0F);
         }

         c.assumeBestArmor = this.getSetting("Assume Best Armor", false);
         c.targetSort = this.getSetting("Target Sort", "Damage");
         c.ignoreNaked = this.getSetting("Ignore Naked", false);
         c.swingOnce = this.getSetting("Swing Once", false);
         c.onlyBreakingSurround = this.getSetting("Only Breaking Surround", false);
         c.predictTicks = Mth.clamp(this.getSetting("Predict Ticks", 0), 0, 10);
         c.minimizeSelfDmg = this.getSetting("Minimize Self Damage", false);
         c.gappleSwitch = this.getSetting("Gapple Switch", false);
         c.antiWeakness = this.getSetting("Anti Weakness", false);
      }
   }

   private long[] range(String key, long loDefault, long hiDefault) {
      long lo = this.getSetting(key + "_lo", loDefault);
      long hi = this.getSetting(key + "_hi", hiDefault);
      if (hi < lo) {
         hi = lo;
      }

      return new long[]{lo, hi};
   }

   private long randMs(long lo, long hi) {
      return hi <= lo ? lo : lo + this.rng.nextLong(hi - lo + 1L);
   }

   private boolean breakBudget() {
      return this.breakTick == this.tick ? false : this.cfg.actionsPerTick >= 2 || this.placeTick != this.tick;
   }

   private boolean placeBudget() {
      return this.placeTick != this.tick && this.swapTick != this.tick ? this.cfg.actionsPerTick >= 2 || this.breakTick != this.tick : false;
   }

   private boolean swapBudget() {
      return this.swapTick != this.tick && this.placeTick != this.tick ? this.cfg.actionsPerTick >= 2 || this.breakTick != this.tick : false;
   }

   public static void onLocalAttack(Entity e) {
      CrystalAura i = INSTANCE;
      if (i != null && i.isEnabled()) {
         if (e instanceof Player pl && pl != Minecraft.getInstance().player) {
            i.airTarget = pl;
            i.targetAirAt = System.currentTimeMillis();
         }
      }
   }

   public static void onEntitySpawn(int id) {
      CrystalAura i = INSTANCE;
      if (i != null && i.isEnabled()) {
         i.refreshCfg();
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.level != null && mc.gameMode != null) {
            i.noteSpawn(mc, id);
            if (i.cfg.instantBreak && mc.gui.screen() == null) {
               if (!mc.player.isUsingItem() && mc.player.isAlive()) {
                  if (mc.level.getEntity(id) instanceof EndCrystal ec) {
                     try {
                        i.instantBreak(mc, ec);
                     } catch (Throwable var6) {
                     }
                  }
               }
            }
         }
      }
   }

   private void noteSpawn(Minecraft mc, int id) {
      if (this.lastCrystalBase != null && this.lastCrystalAt != 0L) {
         if (mc.level.getEntity(id) instanceof EndCrystal ec && ec.blockPosition().below().equals(this.lastCrystalBase)) {
            double sample = Mth.clamp(System.currentTimeMillis() - this.lastCrystalAt, 20.0, 800.0);
            this.spawnEma = this.spawnEma < 0.0 ? sample : this.spawnEma * 0.7 + sample * 0.3;
         }
      }
   }

   private void instantBreak(Minecraft mc, EndCrystal ec) {
      long now = System.currentTimeMillis();
      if (now >= this.suspendUntil && now >= this.nextBreakAt && this.breakBudget() && this.sentValid) {
         if (!this.brokenIds.containsKey(ec.getId())) {
            BlockPos under = ec.blockPosition().below();
            if (this.lastCrystalBase != null && under.equals(this.lastCrystalBase)) {
               CrystalAura.Plan last = this.plan;
               Player target = last != null ? last.target : null;
               if (target != null && target.isAlive() && !target.isRemoved()) {
                  Vec3 eyes = mc.player.getEyePosition();
                  if (!(eyes.distanceToSqr(closestPoint(ec.getBoundingBox(), eyes)) > (double)this.cfg.breakRange * this.cfg.breakRange)) {
                     Vec3 center = ec.position();
                     float t = this.targetDamage(mc, center, target);
                     float s = this.damageAt(mc, center, mc.player, true);
                     float tHealth = this.effectiveHealth(target);
                     float myHp = this.effectiveHealth(mc.player);
                     if (this.valid(t, s, tHealth, myHp, this.cfg.minDmg, this.cfg.maxSelf, this.cfg.antiSuicide, this.cfg.margin, true)) {
                        if (!this.hurtsFriend(mc, center)) {
                           CrystalAura.Plan p = new CrystalAura.Plan();
                           p.type = CrystalAura.PlanType.BREAK;
                           p.id = ec.getId();
                           p.crystal = ec;
                           p.target = target;
                           p.aim = ec.getBoundingBox().getCenter();
                           p.targetDmg = t;
                           p.selfDmg = s;
                           if (this.doBreak(mc, p, now) && this.dbg) {
                              this.debug(mc, "instant break dmg=" + String.format("%.1f", t));
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void debug(Minecraft mc, String msg) {
   }

   @Override
   public void onEnable() {
      this.resetAll();
   }

   @Override
   public void onDisable() {
      Minecraft mc = Minecraft.getInstance();
      this.plan = null;
      this.rotating = false;
      AimAssist.clearSilentRotation();
      this.restoreSlotNow(mc);
      this.resetAll();
   }

   private void resetAll() {
      this.plan = null;
      this.planType = null;
      this.planId = Long.MIN_VALUE;
      this.planSince = 0L;
      this.lastPlanAt = 0L;
      this.lastActionAt = 0L;
      this.reactUntil = 0L;
      this.engagedSince = 0L;
      this.lastSuccessAt = 0L;
      this.stickyBase = null;
      this.releaseAim = false;
      this.airTarget = null;
      this.targetAirAt = 0L;

      for (Map<Long, Long> m : this.blacklist) {
         m.clear();
      }

      this.brokenIds.clear();
      this.recentPlaces.clear();
      this.swungIds.clear();
      this.pendingObsidian = null;
      this.pendingUntil = 0L;
      this.lastCrystalBase = null;
      this.lastCrystalAt = 0L;
      this.checkTarget = null;
      this.noEffect = 0;
      this.suspendUntil = 0L;
      this.breakTick = this.placeTick = this.swapTick = -1000;
      this.lastScanTick = -1000;
      this.lastScanTarget = null;
      this.lastExplosionPos = null;
      this.lastExplosionAt = 0L;
      this.seenCrystals.clear();
      this.nextPlaceAt = this.nextBreakAt = this.nextObsidianAt = this.nextSwapAt = this.swapBackAt = 0L;
      this.lastTickNs = -1L;
      this.pend.clear();
      this.pendTargetId = -1;
      this.lastMyHp = this.lastTgtHp = -1.0F;
      this.spawnEma = -1.0;
      this.sentValid = false;
      this.speedMul = this.speedMulGoal = 1.0;
      this.cfgRev = Integer.MIN_VALUE;
      this.cachedRegAccess = null;
      this.protHolder = this.blastHolder = null;
   }

   @Override
   public void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (this.isEnabled()) {
         if (mc.player != null && mc.level != null && mc.gameMode != null) {
            this.tick++;
            long now = System.currentTimeMillis();
            long nowNs = System.nanoTime();
            double dtMs = this.lastTickNs < 0L ? 50.0 : Mth.clamp((nowNs - this.lastTickNs) / 1000000.0, 20.0, 100.0);
            this.lastTickNs = nowNs;
            this.refreshCfg();
            this.dbg = false;
            this.waitMs = this.pingWait(mc);
            if (!this.sentValid) {
               this.captureSent(mc);
               this.sentValid = true;
            }

            try {
               this.think(mc, now);
            } finally {
               this.rotateTick(mc, now, nowNs, dtMs);
            }
         } else {
            this.plan = null;
            this.rotating = false;
            this.sentValid = false;
         }
      }
   }

   private void think(Minecraft mc, long now) {
      this.prune(now);
      this.settlePending(mc, now);
      boolean screenOpen = mc.gui.screen() != null;
      boolean usingItem = mc.player.isUsingItem();
      boolean mining = this.cfg.pauseMining && this.isPlayerMining(mc);
      if ((!this.cfg.pauseInventory || !screenOpen) && (!this.cfg.pauseEating || !usingItem) && !mining && mc.player.isAlive()) {
         this.evaluateEffect(mc, now);
         if (now < this.suspendUntil) {
            this.setPlan(null, now);
            this.handleSwapBack(mc, now);
         } else {
            this.handleGappleSwitch(mc, now);
            CrystalAura.Plan p = this.computePlan(mc, now);
            if (this.dbg) {
               if (p == null) {
                  this.debug(mc, "no good play (airborne/damage/safety/inventory)");
               } else {
                  this.debug(mc, p.type + " dmg=" + String.format("%.1f", p.targetDmg) + " self=" + String.format("%.1f", p.selfDmg));
               }
            }

            this.setPlan(p, now);
            if (p == null) {
               if (now - this.lastPlanAt >= 80L) {
                  this.handleSwapBack(mc, now);
               }
            } else {
               this.swapBackAt = 0L;
               this.lastPlanAt = now;
               if (now - Math.max(this.engagedSince, this.lastSuccessAt) > 1200L) {
                  this.black(p.type, p.id, now + 1200L);
                  this.stickyBase = null;
                  this.releaseAim = true;
                  this.suspendUntil = now + 400L;
                  if (this.dbg) {
                     this.debug(mc, "no successful place/break in 1200ms - dropping " + p.type + " " + p.id);
                  }

                  this.setPlan(null, now);
               } else {
                  long ref = Math.max(this.planSince, this.lastActionAt);
                  boolean converged = this.aimHitsPlan(mc, p);
                  long limit = converged ? 250L : 500L;
                  if (now - ref > limit) {
                     if (this.dbg && p.type != CrystalAura.PlanType.BREAK) {
                        BlockHitResult r = this.rayBlock(mc, 3.0);
                        this.debug(
                           mc,
                           "stall detail: aimErr="
                              + String.format("%.2f", this.aimError(mc, p.aim))
                              + " ray="
                              + (r == null ? "MISS" : r.getBlockPos().toShortString() + " " + r.getDirection())
                              + " want="
                              + p.pos.toShortString()
                              + " aimDist="
                              + String.format("%.2f", mc.player.getEyePosition().distanceTo(p.aim))
                        );
                     }

                     this.black(p.type, p.id, now + (converged ? 700L : 1200L));
                     if (this.dbg) {
                        this.debug(mc, "stalled on " + p.type + (converged ? " (aimed but could not act)" : " (never got aimed)") + " - skipping");
                     }

                     this.setPlan(null, now);
                  } else {
                     boolean acted = this.act(mc, p, now);
                     if (acted && p.type == CrystalAura.PlanType.BREAK && this.cfg.actionsPerTick >= 2) {
                        CrystalAura.Plan p2 = this.computePlan(mc, now);
                        this.setPlan(p2, now);
                        if (p2 != null) {
                           this.lastPlanAt = now;
                           if (p2.type != CrystalAura.PlanType.BREAK && this.aimHitsPlan(mc, p2)) {
                              this.act(mc, p2, now);
                           }
                        }
                     }

                     this.prepare(mc, this.plan, now);
                  }
               }
            }
         }
      } else {
         this.setPlan(null, now);
         this.handleSwapBack(mc, now);
      }
   }

   private boolean isPlayerMining(Minecraft mc) {
      try {
         MultiPlayerGameMode gm = mc.gameMode;
         if (gm == null) {
            return false;
         }

         return gm.isDestroying();
      } catch (Throwable ignored) {
         return false;
      }
   }

   private void evaluateEffect(Minecraft mc, long now) {
      if (this.checkTarget != null) {
         if (now - this.checkAt >= this.waitMs + 350L) {
            Player t = this.checkTarget;
            this.checkTarget = null;
            if (t.isAlive() && !t.isRemoved()) {
               float after = t.getHealth() + t.getAbsorptionAmount();
               float lost = this.checkHealth - after;
               if (!this.cfg.pauseIneffective || !(lost < Math.min(this.checkEst * 0.25F, 2.0F))) {
                  this.noEffect = 0;
               } else if (++this.noEffect >= 5) {
                  this.suspendUntil = now + 1500L;
                  this.noEffect = 0;
                  if (this.dbg) {
                     this.debug(mc, "explosions are not hurting the target - pausing");
                  }
               }
            } else {
               this.noEffect = 0;
            }
         }
      }
   }

   private void setPlan(CrystalAura.Plan p, long now) {
      if (p == null) {
         this.plan = null;
      } else {
         boolean wasIdle = this.plan == null && now - this.lastPlanAt > 80L;
         if (wasIdle) {
            this.engagedSince = now;
            this.planSeed = this.rng.nextLong();
            this.reactUntil = this.cfg.humanize ? now + this.randMs(this.cfg.reactLo, this.cfg.reactHi) : 0L;
         }

         this.releaseAim = false;
         this.stickyBase = p.type == CrystalAura.PlanType.BREAK && p.crystal != null ? p.crystal.blockPosition().below() : p.pos;
         if (this.planType != p.type || this.planId != p.id) {
            this.planType = p.type;
            this.planId = p.id;
            this.planSince = now;
         }

         this.plan = p;
      }
   }

   private void prune(long now) {
      if (!this.brokenIds.isEmpty()) {
         long keep = Math.max(300L, this.waitMs + 100L);
         this.brokenIds.values().removeIf(t -> now - t > keep);
      }

      if (!this.recentPlaces.isEmpty()) {
         this.recentPlaces.values().removeIf(t -> now - t > 600L);
      }

      for (Map<Long, Long> m : this.blacklist) {
         if (!m.isEmpty()) {
            m.values().removeIf(t -> t < now);
         }
      }

      if (this.pendingObsidian != null && now > this.pendingUntil) {
         this.pendingObsidian = null;
      }

      if (!this.swungIds.isEmpty()) {
         this.swungIds.values().removeIf(t -> now - t > 2000L);
      }
   }

   private boolean isBlack(CrystalAura.PlanType type, long id) {
      return this.blacklist[type.ordinal()].containsKey(id);
   }

   private void black(CrystalAura.PlanType type, long id, long until) {
      this.blacklist[type.ordinal()].put(id, until);
   }

   private CrystalAura.Plan computePlan(Minecraft mc, long now) {
      Player me = mc.player;
      Vec3 eyes = me.getEyePosition();
      Player target = this.pickTarget(mc);
      if (target == null) {
         this.loseTarget();
         return null;
      }

      if (this.cfg.onlyBreakingSurround && !this.isSurrounded(mc, target)) {
         if (this.dbg) {
            this.debug(mc, "target not surrounded - waiting");
         }

         return null;
      } else {
         float breakRange = this.cfg.breakRange;
         float minDmg = this.cfg.minDmg;
         float maxSelf = this.cfg.maxSelf;
         boolean antiSuicide = this.cfg.antiSuicide;
         float margin = this.cfg.margin;
         float tHealth = this.effectiveHealth(target);
         float myHp = this.effectiveHealth(me);
         AABB region = target.getBoundingBox().inflate(6.0, 4.0, 6.0);
         List<Entity> ents = mc.level.getEntities((Entity)null, region, LIVE);
         AABB crystalBox = me.getBoundingBox().inflate(breakRange + 1.0).minmax(region);
         List<EndCrystal> crystals = mc.level.getEntitiesOfClass(EndCrystal.class, crystalBox, Entity::isAlive);
         if (!this.seenCrystals.isEmpty()) {
            for (Entry<Integer, Vec3> en : this.seenCrystals.entrySet()) {
               boolean still = false;

               for (int i = 0; i < crystals.size(); i++) {
                  if (crystals.get(i).getId() == en.getKey()) {
                     still = true;
                     break;
                  }
               }

               if (!still) {
                  this.noteExplosion(en.getValue(), now);
               }
            }

            this.seenCrystals.clear();
         }

         for (int i = 0; i < crystals.size(); i++) {
            EndCrystal ec = crystals.get(i);
            this.seenCrystals.put(ec.getId(), ec.position());
         }

         CrystalAura.Plan bestBreak = null;
         float bestBreakScore = -Float.MAX_VALUE;

         for (int ci = 0; ci < crystals.size(); ci++) {
            EndCrystal ec = crystals.get(ci);
            if (!this.brokenIds.containsKey(ec.getId()) && !this.isBlack(CrystalAura.PlanType.BREAK, ec.getId())) {
               AABB box = ec.getBoundingBox();
               Vec3 closest = closestPoint(box, eyes);
               if (!(eyes.distanceToSqr(closest) > (double)breakRange * breakRange)) {
                  Vec3 center = ec.position();
                  float t = this.targetDamage(mc, center, target);
                  if (!(t <= 0.0F)) {
                     float s = this.damageAt(mc, center, me, true);
                     boolean stickyB = this.sticky(ec.blockPosition().below());
                     if (this.valid(t, s, tHealth, myHp, minDmg, maxSelf, antiSuicide, margin, stickyB)
                        && !this.hurtsFriend(mc, center)
                        && this.passesFactorCheck(t, center, target)) {
                        BlockPos under = ec.blockPosition().below();
                        Vec3 aim = null;
                        if (eyes.y > under.getY() + 1.02) {
                           double[] off = this.aimOffsets(under.asLong());
                           Vec3 top = facePoint(under, Direction.UP, off[0], off[1]);
                           Vec3 dir = top.subtract(eyes);
                           double len = dir.length();
                           if (len > 1.0E-4) {
                              Vec3 far = top.add(dir.scale(0.3 / len));
                              if (box.clip(eyes, far).isPresent() && (this.visibleFace(mc, eyes, under, Direction.UP, top) || this.lineClear(mc, eyes, top))) {
                                 aim = top;
                              }
                           }
                        }

                        if (aim == null) {
                           aim = box.getCenter();
                           if (!this.lineClear(mc, eyes, aim)) {
                              continue;
                           }
                        }

                        float score = t - s * this.selfWeight();
                        if (score > bestBreakScore) {
                           bestBreakScore = score;
                           CrystalAura.Plan p = new CrystalAura.Plan();
                           p.type = CrystalAura.PlanType.BREAK;
                           p.id = ec.getId();
                           p.crystal = ec;
                           p.target = target;
                           p.aim = aim;
                           p.targetDmg = t;
                           p.selfDmg = s;
                           bestBreak = p;
                        }
                     }
                  }
               }
            }
         }

         if (bestBreak != null) {
            return bestBreak;
         }

         if (target != this.airTarget) {
            this.airTarget = target;
            this.targetAirAt = 0L;
         }

         if (!target.onGround()) {
            this.targetAirAt = now;
         }

         boolean targetAirborne = now - this.targetAirAt <= 300L;
         if (me.distanceTo(target) > 7.5) {
            this.loseTarget();
            return null;
         }

         boolean haveCrystal = this.canUse(mc, Items.END_CRYSTAL);
         boolean haveObsidian = this.cfg.placeObs && this.canUse(mc, Items.OBSIDIAN);
         if (!haveCrystal) {
            return null;
         }

         if (this.lastCrystalBase != null) {
            if (now - this.lastCrystalAt < this.waitMs) {
               boolean visible = false;

               for (int i = 0; i < crystals.size(); i++) {
                  if (crystals.get(i).blockPosition().below().equals(this.lastCrystalBase)) {
                     visible = true;
                     break;
                  }
               }

               if (!visible) {
                  this.lastPlanAt = now;
                  return null;
               }
            }

            this.lastCrystalBase = null;
         }

         CrystalAura.Scan s = new CrystalAura.Scan();
         s.target = target;
         s.eyes = eyes;
         s.tpos = this.predictedTargetPos(target);
         s.reach = 2.85F;
         s.reachSqr = 14.822500000000002;
         s.tHealth = tHealth;
         s.myHp = myHp;
         s.minDmg = minDmg;
         s.maxSelf = maxSelf;
         s.margin = margin;
         s.antiSuicide = antiSuicide;
         s.haveObsidian = haveObsidian;
         s.obsidianBusy = this.pendingObsidian != null && now <= this.pendingUntil;
         s.airborneOnly = this.cfg.airborneOnly;
         s.targetAirborne = targetAirborne;
         s.now = now;
         s.ents = ents;
         CrystalAura.Cand chosen = null;
         if (this.stickyBase != null
            && this.lastScanTarget == target
            && this.tick - this.lastScanTick < 3
            && s.tpos.distanceToSqr(this.lastScanTargetPos) < 0.01
            && eyes.distanceToSqr(this.lastScanEyes) < 0.01) {
            CrystalAura.Cand c = this.makeCand(mc, this.stickyBase, s);
            if (c != null) {
               List<CrystalAura.Cand> one = new ArrayList<>(1);
               one.add(c);
               chosen = this.evaluate(mc, one, s);
            }
         }

         if (chosen == null) {
            List<CrystalAura.Cand> existing = new ArrayList<>();
            List<CrystalAura.Cand> fresh = new ArrayList<>();
            BlockPos tp = target.blockPosition();
            MutableBlockPos m = new MutableBlockPos();

            for (int dx = -3; dx <= 3; dx++) {
               for (int dz = -3; dz <= 3; dz++) {
                  for (int dy = -2; dy <= 0; dy++) {
                     m.set(tp.getX() + dx, tp.getY() + dy, tp.getZ() + dz);
                     CrystalAura.Cand c = this.makeCand(mc, m, s);
                     if (c != null) {
                        (c.fresh ? fresh : existing).add(c);
                     }
                  }
               }
            }

            this.lastScanTick = this.tick;
            this.lastScanTarget = target;
            this.lastScanTargetPos = s.tpos;
            this.lastScanEyes = eyes;
            chosen = this.evaluate(mc, existing, s);
            if (chosen == null && !existing.isEmpty() && this.pendingObsidian != null && now <= this.pendingUntil) {
               return null;
            }

            if (chosen == null) {
               chosen = this.evaluate(mc, fresh, s);
            }
         }

         if (chosen == null) {
            return null;
         }

         CrystalAura.Plan p = new CrystalAura.Plan();
         p.type = chosen.fresh ? CrystalAura.PlanType.OBSIDIAN : CrystalAura.PlanType.CRYSTAL;
         p.id = chosen.id;
         p.pos = chosen.base;
         p.clickPos = chosen.clickPos;
         p.face = chosen.face;
         p.aim = chosen.aim;
         p.target = target;
         p.targetDmg = chosen.t;
         p.selfDmg = chosen.s;
         return p;
      }
   }

   private boolean isSurrounded(Minecraft mc, Player target) {
      BlockPos feet = target.blockPosition();

      for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
         BlockState st = mc.level.getBlockState(feet.relative(d));
         if (!st.isSolid()) {
            return false;
         }
      }

      return true;
   }

   private boolean passesFactorCheck(float t, Vec3 center, LivingEntity target) {
      if (this.cfg.minDmgFactor <= 0.0F) {
         return true;
      }

      float ub = this.rawDamage(center.x, center.y, center.z, target);
      return ub <= 0.0F ? true : t >= ub * this.cfg.minDmgFactor;
   }

   private float selfWeight() {
      return this.cfg.minimizeSelfDmg ? 2.5F : 0.5F;
   }

   private Vec3 predictedTargetPos(LivingEntity e) {
      int ticks = this.cfg.predictTicks;
      if (ticks <= 0) {
         return e.position();
      }

      double dx = (e.getX() - e.xo) * ticks;
      double dz = (e.getZ() - e.zo) * ticks;
      double cap = 0.5 * ticks;
      double len = Math.sqrt(dx * dx + dz * dz);
      if (len > cap) {
         dx *= cap / len;
         dz *= cap / len;
      }

      return e.position().add(dx, 0.0, dz);
   }

   private CrystalAura.Cand makeCand(Minecraft mc, BlockPos base, CrystalAura.Scan s) {
      double cx = base.getX() + 0.5;
      double cy = base.getY() + 1.0;
      double cz = base.getZ() + 0.5;
      if (s.eyes.distanceToSqr(cx, cy - 0.5, cz) > s.reachSqr) {
         return null;
      }

      if (s.tpos.distanceToSqr(cx, cy, cz) > 12.25) {
         return null;
      }

      float ub = this.rawDamage(cx, cy, cz, s.target);
      if (ub < Math.min(s.minDmg, s.tHealth)) {
         return null;
      }

      BlockState st = mc.level.getBlockState(base);
      boolean isExisting = this.isBase(st);
      boolean isFresh = !isExisting && (st.isAir() || st.canBeReplaced());
      if (!isExisting && !isFresh) {
         return null;
      }

      boolean sticky = this.sticky(base);
      if (s.airborneOnly && !s.targetAirborne && isFresh && !sticky) {
         return null;
      }

      if (!isFresh || s.haveObsidian && !s.obsidianBusy) {
         long id = base.asLong();
         if (this.isBlack(isFresh ? CrystalAura.PlanType.OBSIDIAN : CrystalAura.PlanType.CRYSTAL, id)) {
            return null;
         }

         if (!mc.level.getBlockState(base.above()).isAir()) {
            return null;
         }

         if (this.crystalSpotBlocked(mc, s.ents, base, s.now)) {
            return null;
         }

         if (isFresh && this.blockedByEntity(mc, s.ents, base)) {
            return null;
         }

         CrystalAura.Cand c = new CrystalAura.Cand();
         c.base = base.immutable();
         c.fresh = isFresh;
         c.id = id;
         c.ub = ub;
         c.sortKey = ub + (sticky ? 1000.0F : 0.0F);
         c.bound = ub + (sticky ? 6.0F : 0.0F);
         return c;
      } else {
         return null;
      }
   }

   private CrystalAura.Cand evaluate(Minecraft mc, List<CrystalAura.Cand> list, CrystalAura.Scan s) {
      if (list.isEmpty()) {
         return null;
      }

      list.sort(BY_BOUND);
      float refYaw = this.rotating ? this.curYaw : mc.player.getYRot();
      float refPitch = this.rotating ? this.curPitch : mc.player.getXRot();
      Vec3 eyes = s.eyes;
      CrystalAura.Cand best = null;
      int evaluated = 0;
      int faced = 0;

      for (int i = 0; i < list.size(); i++) {
         CrystalAura.Cand c = list.get(i);
         if (best != null && c.bound <= best.score || evaluated >= 12 || faced >= 40) {
            break;
         }

         faced++;
         if (this.pickFace(mc, c, eyes, s.reach, s.target, refYaw, refPitch)) {
            float[] a = angles(eyes, c.aim);
            c.yawTurn = Math.abs(Mth.wrapDegrees(a[0] - refYaw));
            c.turn = c.yawTurn + Math.abs(a[1] - refPitch) * 0.5F;
            evaluated++;
            Vec3 center = new Vec3(c.base.getX() + 0.5, c.base.getY() + 1.0, c.base.getZ() + 0.5);
            this.hypoBox = c.fresh ? new AABB(c.base).deflate(0.001) : null;

            float t;
            float sd;
            try {
               t = this.targetDamage(mc, center, s.target);
               if (t <= 0.0F || t < Math.min(s.minDmg, s.tHealth) || !this.passesFactorCheck(t, center, s.target)) {
                  continue;
               }

               boolean stk = this.sticky(c.base);
               if (best != null && t - c.turn * 0.045F + (stk ? 6.0F : 0.0F) - (c.yawTurn > 110.0F ? 1000.0F : 0.0F) <= best.score) {
                  continue;
               }

               sd = this.damageAt(mc, center, mc.player, true);
               if (!this.valid(t, sd, s.tHealth, s.myHp, s.minDmg, s.maxSelf, s.antiSuicide, s.margin, stk) || this.hurtsFriend(mc, center)) {
                  continue;
               }
            } finally {
               this.hypoBox = null;
            }

            c.t = t;
            c.s = sd;
            c.score = t - sd * this.selfWeight() - c.turn * 0.045F + (this.sticky(c.base) ? 6.0F : 0.0F) - (c.yawTurn > 110.0F ? 1000.0F : 0.0F);
            if (best == null || c.score > best.score) {
               best = c;
            }
         }
      }

      return best;
   }

   private void loseTarget() {
      this.stickyBase = null;
      this.planType = null;
      this.planId = Long.MIN_VALUE;
      this.releaseAim = true;
   }

   private boolean sticky(BlockPos base) {
      return this.stickyBase != null && this.stickyBase.equals(base);
   }

   private boolean valid(float t, float s, float tHealth, float myHp, float minDmg, float maxSelf, boolean antiSuicide, float margin, boolean sticky) {
      float origTHealth = tHealth;
      if (!sticky) {
         tHealth += 1.5F;
         minDmg += 1.5F;
         maxSelf -= 1.5F;
         margin += 1.5F;
      }

      boolean lethal = t >= tHealth;
      boolean facePlacing = this.cfg.facePlaceHealth > 0.0F && origTHealth <= this.cfg.facePlaceHealth;
      if (!lethal && !facePlacing && t < minDmg) {
         return false;
      } else if (this.cfg.overrideSelfDmg && lethal) {
         return true;
      } else {
         return antiSuicide && s >= myHp - margin ? false : lethal || !(s > maxSelf);
      }
   }

   private Player pickTarget(Minecraft mc) {
      float range = this.cfg.targetRange;
      List<Player> cands = new ArrayList<>();

      for (Player p : mc.level.players()) {
         if (p != mc.player
            && !AimAssist.isSelfLike(p)
            && p.isAlive()
            && !p.isSpectator()
            && !p.isCreative()
            && Targets.isTargetAllowed(p, mc.player)
            && !(mc.player.distanceTo(p) > range)
            && (!this.cfg.ignoreNaked || !this.isNaked(p))) {
            cands.add(p);
         }
      }

      if (cands.isEmpty()) {
         return null;
      }

      switch (this.cfg.targetSort) {
         case "Health":
            cands.sort(Comparator.comparingDouble(px -> px.getHealth() + px.getAbsorptionAmount()));
            break;
         case "Distance":
            cands.sort(Comparator.comparingDouble(px -> mc.player.distanceTo(px)));
            break;
         default:
            return Targets.getTarget(cands, mc.player) instanceof Player pl ? pl : null;
      }

      return cands.get(0);
   }

   private boolean isNaked(Player p) {
      for (EquipmentSlot slot : ARMOR_SLOTS) {
         if (!p.getItemBySlot(slot).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   private boolean isBase(BlockState st) {
      return st.is(Blocks.OBSIDIAN) || st.is(Blocks.BEDROCK);
   }

   private boolean selfIn(Minecraft mc, AABB box) {
      if (mc.player == null) {
         return false;
      }

      AABB me = mc.player.getBoundingBox();
      if (me.intersects(box)) {
         return true;
      }

      Vec3 v = mc.player.getDeltaMovement();
      return me.move(v.x, v.y, v.z).intersects(box);
   }

   private boolean crystalSpotBlocked(Minecraft mc, List<Entity> ents, BlockPos base, long now) {
      Long recent = this.recentPlaces.get(base);
      if (recent != null && now - recent < this.waitMs) {
         return true;
      }

      AABB spot = new AABB(base.getX(), base.getY() + 1.0, base.getZ(), base.getX() + 1.0, base.getY() + 3.0, base.getZ() + 1.0);
      if (this.selfIn(mc, spot)) {
         return true;
      }

      for (int i = 0; i < ents.size(); i++) {
         Entity e = ents.get(i);
         if (!e.isRemoved() && e != mc.player && (!(e instanceof EndCrystal) || !this.brokenIds.containsKey(e.getId())) && e.getBoundingBox().intersects(spot)) {
            return true;
         }
      }

      return false;
   }

   private boolean blockedByEntity(Minecraft mc, List<Entity> ents, BlockPos pos) {
      AABB box = new AABB(pos);
      if (this.selfIn(mc, box)) {
         return true;
      }

      for (int i = 0; i < ents.size(); i++) {
         Entity e = ents.get(i);
         if (!e.isRemoved() && !e.isSpectator() && e != mc.player && !(e instanceof ItemEntity) && e.getBoundingBox().intersects(box)) {
            return true;
         }
      }

      return false;
   }

   private static Vec3 closestPoint(AABB box, Vec3 p) {
      return new Vec3(Mth.clamp(p.x, box.minX, box.maxX), Mth.clamp(p.y, box.minY, box.maxY), Mth.clamp(p.z, box.minZ, box.maxZ));
   }

   private boolean lineClear(Minecraft mc, Vec3 from, Vec3 to) {
      HitResult r = mc.level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, mc.player));
      return r.getType() == Type.MISS;
   }

   private boolean pickFace(Minecraft mc, CrystalAura.Cand c, Vec3 eyes, float reach, Player target, float baseYaw, float basePitch) {
      double[] off = this.aimOffsets(c.id);
      float bestScore = Float.MAX_VALUE;
      BlockPos bestClick = null;
      Direction bestFace = null;
      Vec3 bestAim = null;
      double reachSqr = (double)reach * reach;
      AABB tbox = target != null ? target.getBoundingBox().inflate(0.1) : null;

      for (int di = 0; di < DIRS.length; di++) {
         Direction d = DIRS[di];
         BlockPos click;
         Direction face;
         if (c.fresh) {
            click = c.base.relative(d);
            BlockState ns = mc.level.getBlockState(click);
            if (ns.isAir()
               || ns.canBeReplaced()
               || ns.getBlock() instanceof LiquidBlock
               || this.unreliableSupport(click, ns)
               || click.equals(this.pendingObsidian) && System.currentTimeMillis() <= this.pendingUntil) {
               continue;
            }

            face = d.getOpposite();
         } else {
            click = c.base;
            face = d;
            if (mc.level.getBlockState(c.base.relative(d)).isSolid()) {
               continue;
            }
         }

         Vec3 point = facePoint(click, face, off[0], off[1]);
         if (!(eyes.distanceToSqr(point) > reachSqr)) {
            if (!this.visibleFace(mc, eyes, click, face, point)) {
               point = facePoint(click, face, 0.0, 0.0);
               if (!this.visibleFace(mc, eyes, click, face, point) || eyes.distanceToSqr(point) > reachSqr) {
                  continue;
               }
            }

            if (tbox != null) {
               Optional<Vec3> entHit = tbox.clip(eyes, point);
               if (entHit.isPresent() && eyes.distanceToSqr(entHit.get()) < eyes.distanceToSqr(point)) {
                  continue;
               }
            }

            float[] a = angles(eyes, point);
            float score = Math.abs(Mth.wrapDegrees(a[0] - baseYaw)) + Math.abs(a[1] - basePitch);
            if (!c.fresh) {
               if (face == Direction.UP) {
                  score -= 10000.0F;
               }
            } else {
               score += 12.0F * (float)point.distanceTo(new Vec3(c.base.getX() + 0.5, c.base.getY() + 1.0, c.base.getZ() + 0.5));
               if (new AABB(c.base).clip(eyes, point).filter(h -> Math.abs(h.y - (c.base.getY() + 1.0)) < 1.0E-6).isPresent()) {
                  score -= 25.0F;
               }
            }

            if (score < bestScore) {
               bestScore = score;
               bestClick = click;
               bestFace = face;
               bestAim = point;
            }
         }
      }

      if (bestClick == null) {
         return false;
      }

      c.clickPos = bestClick;
      c.face = bestFace;
      c.aim = bestAim;
      return true;
   }

   private static Vec3 facePoint(BlockPos pos, Direction face, double a, double b) {
      double x = pos.getX() + 0.5 + face.getStepX() * 0.5;
      double y = pos.getY() + 0.5 + face.getStepY() * 0.5;
      double z = pos.getZ() + 0.5 + face.getStepZ() * 0.5;
      switch (face.getAxis()) {
         case X:
            y += a;
            z += b;
            break;
         case Y:
            x += a;
            z += b;
            break;
         case Z:
            x += a;
            y += b;
      }

      return new Vec3(x, y, z);
   }

   private boolean visibleFace(Minecraft mc, Vec3 eyes, BlockPos pos, Direction face, Vec3 point) {
      Vec3 dir = point.subtract(eyes);
      double len = dir.length();
      if (len < 1.0E-4) {
         return false;
      }

      Vec3 end = point.add(dir.scale(0.15 / len));
      HitResult r = mc.level.clip(new ClipContext(eyes, end, Block.OUTLINE, Fluid.NONE, mc.player));
      return r instanceof BlockHitResult b && b.getType() == Type.BLOCK && b.getBlockPos().equals(pos) && b.getDirection() == face;
   }

   private double[] aimOffsets(long spotId) {
      if (this.cfg.humanize && this.cfg.randAim) {
         long seed = Long.hashCode(spotId) * 31L + this.planSeed;
         this.aimOff[0] = (unit(seed, 0) * 2.0 - 1.0) * 0.15;
         this.aimOff[1] = (unit(seed, 2) * 2.0 - 1.0) * 0.15;
         return this.aimOff;
      } else {
         this.aimOff[0] = 0.0;
         this.aimOff[1] = 0.0;
         return this.aimOff;
      }
   }

   private static double unit(long seed, int i) {
      long z = seed + -7046029254386353131L * (i + 1);
      z = (z ^ z >>> 30) * -4658895280553007687L;
      z = (z ^ z >>> 27) * -7723592293110705685L;
      z ^= z >>> 31;
      return (z >>> 11) * 1.110223E-16F;
   }

   private static float[] angles(Vec3 eyes, Vec3 target) {
      Vec3 d = target.subtract(eyes);
      double h = Math.sqrt(d.x * d.x + d.z * d.z);
      float yaw = (float)(Math.atan2(-d.x, d.z) * 180.0 / Math.PI);
      float pitch = (float)(-Math.atan2(d.y, h) * 180.0 / Math.PI);
      return new float[]{yaw, pitch};
   }

   private boolean hurtsFriend(Minecraft mc, Vec3 center) {
      List<Player> friends = Friends.explosionSensitive(mc, center, 12.0);
      if (friends.isEmpty()) {
         return false;
      }

      float max = 4.0F;

      for (Player p : friends) {
         float d = this.damageAt(mc, center, p, false);
         if (!(d <= 0.0F) && (d > max || d >= p.getHealth() + p.getAbsorptionAmount())) {
            return true;
         }
      }

      return false;
   }

   private float rawDamage(double cx, double cy, double cz, LivingEntity e) {
      double d = Math.sqrt(e.distanceToSqr(cx, cy, cz)) / 12.0;
      if (d > 1.0) {
         return 0.0F;
      }

      double impact = 1.0 - d;
      return (int)((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
   }

   private float damageAt(Minecraft mc, Vec3 center, LivingEntity e, boolean self) {
      return this.damageAt(mc, center, e, Vec3.ZERO, self);
   }

   private float damageAt(Minecraft mc, Vec3 center, LivingEntity e, Vec3 shift, boolean self) {
      double d = e.position().add(shift).distanceTo(center) / 12.0;
      if (d > 1.0) {
         return 0.0F;
      }

      double exposure = this.seenPercent(mc, center, e.getBoundingBox().move(shift), e);
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

      float armor;
      float tough;
      int epf;
      if (this.cfg.assumeBestArmor && !self && e instanceof Player) {
         armor = 20.0F;
         tough = 8.0F;
         epf = 20;
      } else {
         armor = (float)e.getAttributeValue(Attributes.ARMOR);
         tough = (float)e.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
         epf = -1;
      }

      float f = 2.0F + tough / 4.0F;
      float g = Mth.clamp(armor - dmg / f, armor * 0.2F, 20.0F);
      dmg *= 1.0F - g / 25.0F;
      if (epf >= 0) {
         dmg *= 1.0F - Mth.clamp(epf, 0, 20) / 25.0F;
      } else {
         dmg = this.applyProtection(mc, e, dmg);
      }

      if (self) {
         dmg *= 1.1F;
      }

      return Math.max(0.0F, dmg);
   }

   private void refreshEnchantHolders(Minecraft mc) {
      if (mc.level != null) {
         RegistryAccess ra = mc.level.registryAccess();
         if (ra != this.cachedRegAccess) {
            try {
               Registry<Enchantment> reg = ra.lookupOrThrow(Registries.ENCHANTMENT);
               this.protHolder = (Holder<Enchantment>)reg.get(Enchantments.PROTECTION).orElse(null);
               this.blastHolder = (Holder<Enchantment>)reg.get(Enchantments.BLAST_PROTECTION).orElse(null);
               this.cachedRegAccess = ra;
            } catch (Throwable ignored) {
               this.protHolder = this.blastHolder = null;
            }
         }
      }
   }

   private float applyProtection(Minecraft mc, LivingEntity e, float dmg) {
      try {
         for (MobEffectInstance inst : e.getActiveEffects()) {
            if (((MobEffect)inst.getEffect().value()).getDescriptionId().endsWith("resistance")) {
               int k = (inst.getAmplifier() + 1) * 5;
               dmg *= Math.max(0, 25 - k) / 25.0F;
            }
         }
      } catch (Throwable var13) {
      }

      this.refreshEnchantHolders(mc);
      Holder<Enchantment> prot = this.protHolder;
      Holder<Enchantment> blast = this.blastHolder;
      if (prot == null && blast == null) {
         return dmg;
      }

      try {
         int epf = 0;

         for (EquipmentSlot slot : ARMOR_SLOTS) {
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
      } catch (Throwable var12) {
      }

      return dmg;
   }

   private Vec3 predictShift(LivingEntity e) {
      double k;
      double cap;
      if (this.cfg.predictTicks > 0) {
         k = this.cfg.predictTicks;
         cap = 0.5 * this.cfg.predictTicks;
      } else {
         k = this.cfg.pingComp ? 1.0 + this.lagTicks : 2.0;
         cap = this.cfg.pingComp ? Math.min(1.6, 0.7 + 0.25 * this.lagTicks) : 0.7;
      }

      double sx = (e.getX() - e.xo) * k;
      double sz = (e.getZ() - e.zo) * k;
      double len = Math.sqrt(sx * sx + sz * sz);
      if (len > cap) {
         sx *= cap / len;
         sz *= cap / len;
      }

      return new Vec3(sx, 0.0, sz);
   }

   private float pendingSum(boolean self) {
      float sum = 0.0F;

      for (int i = 0; i < this.pend.size(); i++) {
         sum += self ? this.pend.get(i).self : this.pend.get(i).tgt;
      }

      return sum;
   }

   private float effectiveHealth(Player pl) {
      float hp = pl.getHealth() + pl.getAbsorptionAmount();
      if (this.pend.isEmpty()) {
         return hp;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (pl == mc.player) {
            return Math.max(0.5F, hp - this.pendingSum(true));
         } else {
            return pl.getId() == this.pendTargetId ? Math.max(0.5F, hp - this.pendingSum(false)) : hp;
         }
      }
   }

   private void settlePending(Minecraft mc, long now) {
      this.pend.removeIf(q -> now > q.until);
      float myHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      if (this.lastMyHp >= 0.0F && myHp < this.lastMyHp) {
         this.consume(this.lastMyHp - myHp, true);
      }

      this.lastMyHp = myHp;
      if ((this.pendTargetId >= 0 ? mc.level.getEntity(this.pendTargetId) : null) instanceof Player tp && tp.isAlive()) {
         float hp = tp.getHealth() + tp.getAbsorptionAmount();
         if (this.lastTgtHp >= 0.0F && hp < this.lastTgtHp) {
            this.consume(this.lastTgtHp - hp, false);
         }

         this.lastTgtHp = hp;
      } else {
         this.lastTgtHp = -1.0F;
      }
   }

   private void consume(float amount, boolean self) {
      for (int i = 0; i < this.pend.size() && amount > 0.0F; i++) {
         CrystalAura.Pend q = this.pend.get(i);
         float have = self ? q.self : q.tgt;
         float take = Math.min(have, amount);
         if (self) {
            q.self -= take;
         } else {
            q.tgt -= take;
         }

         amount -= take;
      }

      this.pend.removeIf(qx -> qx.self <= 0.01F && qx.tgt <= 0.01F);
   }

   private float targetDamage(Minecraft mc, Vec3 center, Player target) {
      float now = this.damageAt(mc, center, target, Vec3.ZERO, false);
      if (now <= 0.0F) {
         return 0.0F;
      }

      Vec3 sh = this.predictShift(target);
      if (sh.lengthSqr() < 0.0025) {
         return now;
      }

      float pred = this.damageAt(mc, center, target, sh, false);
      float w = this.cfg.predictTicks > 0 ? 0.75F : (this.cfg.pingComp ? Math.min(0.75F, 0.5F + 0.05F * (float)this.lagTicks) : 0.5F);
      return now * (1.0F - w) + pred * w;
   }

   private double seenPercent(Minecraft mc, Vec3 center, AABB box, Entity e) {
      double dx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
      double dy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
      double dz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
      if (!(dx < 0.0) && !(dy < 0.0) && !(dz < 0.0)) {
         double xo = (1.0 - Math.floor(1.0 / dx) * dx) / 2.0;
         double zo = (1.0 - Math.floor(1.0 / dz) * dz) / 2.0;
         CrystalAura.RayCtx rc = this.rayCtx;
         rc.level = mc.level;
         rc.cc = CollisionContext.of(e);
         rc.to = center;
         AABB hypo = this.hypoBox;
         int hit = 0;
         int total = 0;

         for (double x = 0.0; x <= 1.0; x += dx) {
            for (double y = 0.0; y <= 1.0; y += dy) {
               for (double z = 0.0; z <= 1.0; z += dz) {
                  Vec3 pt = new Vec3(Mth.lerp(x, box.minX, box.maxX) + xo, Mth.lerp(y, box.minY, box.maxY), Mth.lerp(z, box.minZ, box.maxZ) + zo);
                  rc.from = pt;
                  Boolean blocked = (Boolean)BlockGetter.traverseBlocks(pt, center, rc, RAY_TEST, RAY_MISS);
                  if (!blocked && (hypo == null || hypo.clip(pt, center).isEmpty())) {
                     hit++;
                  }

                  total++;
               }
            }
         }

         rc.level = null;
         rc.cc = null;
         rc.from = null;
         rc.to = null;
         return total == 0 ? 0.0 : (double)hit / total;
      } else {
         return 0.0;
      }
   }

   private boolean act(Minecraft mc, CrystalAura.Plan p, long now) {
      if (now < this.reactUntil) {
         return false;
      }

      return switch (p.type) {
         case BREAK -> this.doBreak(mc, p, now);
         case CRYSTAL -> this.doPlace(mc, p, now, Items.END_CRYSTAL);
         case OBSIDIAN -> this.doPlace(mc, p, now, Items.OBSIDIAN);
      };
   }

   private void prepare(Minecraft mc, CrystalAura.Plan p, long now) {
      if (p != null && p.type != CrystalAura.PlanType.BREAK && this.cfg.autoSwitch) {
         Item item = p.type == CrystalAura.PlanType.CRYSTAL ? Items.END_CRYSTAL : Items.OBSIDIAN;
         if (!this.holds(mc, item)) {
            if (this.swapBudget() && now >= this.nextSwapAt) {
               int slot = this.findSlot(mc, item);
               if (slot != -1) {
                  this.swapTo(mc, slot, now);
               }
            }
         }
      }
   }

   private boolean holds(Minecraft mc, Item item) {
      return mc.player.getMainHandItem().is(item) ? true : item == Items.END_CRYSTAL && mc.player.getOffhandItem().is(item);
   }

   private void swapTo(Minecraft mc, int slot, long now) {
      if (this.savedSlot == -1) {
         this.savedSlot = InventoryHelper.getSelected(mc.player.getInventory());
      }

      InventoryHelper.setSelected(mc.player.getInventory(), slot);
      this.swapTick = this.tick;
      this.nextSwapAt = now + this.randMs(this.cfg.swapLo, this.cfg.swapHi);
      this.lastActionAt = now;
   }

   private void noteExplosion(Vec3 pos, long now) {
      this.lastExplosionPos = pos;
      this.lastExplosionAt = now;
   }

   private static boolean blastProof(BlockState st) {
      return st.is(Blocks.OBSIDIAN)
         || st.is(Blocks.CRYING_OBSIDIAN)
         || st.is(Blocks.BEDROCK)
         || st.is(Blocks.NETHERITE_BLOCK)
         || st.is(Blocks.ANCIENT_DEBRIS)
         || st.is(Blocks.REINFORCED_DEEPSLATE);
   }

   private boolean unreliableSupport(BlockPos pos, BlockState st) {
      if (this.lastExplosionPos == null) {
         return false;
      } else {
         long now = System.currentTimeMillis();
         if (now - this.lastExplosionAt > this.waitMs + 250L) {
            return false;
         } else {
            return blastProof(st) ? false : Vec3.atCenterOf(pos).distanceToSqr(this.lastExplosionPos) <= 16.0;
         }
      }
   }

   private long pingWait(Minecraft mc) {
      long ping = 50L;

      try {
         if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) {
               ping = info.getLatency();
            }
         }
      } catch (Throwable var6) {
      }

      double rtt = Math.max(ping, this.spawnEma > 0.0 ? this.spawnEma - 25.0 : 0.0);
      this.lagTicks = Mth.clamp(rtt / 50.0, 0.0, 8.0);
      return Mth.clamp(Math.round(rtt * 1.25) + 40L, 60L, 450L);
   }

   private double aimError(Minecraft mc, Vec3 aim) {
      if (aim == null) {
         return 180.0;
      }

      Vec3 eyes = mc.player.getEyePosition();
      if (aim.subtract(eyes).lengthSqr() < 1.0E-6) {
         return 0.0;
      }

      float[] a = angles(eyes, aim);
      return Math.hypot(Mth.wrapDegrees(a[0] - this.sentYaw), a[1] - this.sentPitch);
   }

   private boolean aimHitsPlan(Minecraft mc, CrystalAura.Plan p) {
      if (p.type == CrystalAura.PlanType.BREAK) {
         return this.aimError(mc, p.aim) < 4.0;
      } else {
         BlockHitResult r = this.rayBlock(mc, 3.0);
         if (r == null) {
            return false;
         } else {
            return p.type == CrystalAura.PlanType.CRYSTAL ? r.getBlockPos().equals(p.pos) : r.getBlockPos().relative(r.getDirection()).equals(p.pos);
         }
      }
   }

   private Vec3 sentLook() {
      return AimAssist.getRotationVector(this.sentPitch, this.sentYaw);
   }

   private BlockHitResult rayBlock(Minecraft mc, double reach) {
      Vec3 eyes = mc.player.getEyePosition();
      Vec3 end = eyes.add(this.sentLook().scale(reach));
      HitResult r = mc.level.clip(new ClipContext(eyes, end, Block.OUTLINE, Fluid.NONE, mc.player));
      return r instanceof BlockHitResult b && b.getType() == Type.BLOCK ? b : null;
   }

   private boolean doBreak(Minecraft mc, CrystalAura.Plan p, long now) {
      if (now >= this.nextBreakAt && this.breakBudget()) {
         EndCrystal ec = p.crystal;
         if (ec != null && !ec.isRemoved() && ec.isAlive()) {
            Vec3 eyes = mc.player.getEyePosition();
            AABB cbox = ec.getBoundingBox().inflate(0.05);
            if (!cbox.contains(eyes)) {
               Vec3 end = eyes.add(this.sentLook().scale(this.cfg.breakRange));
               Optional<Vec3> hit = cbox.clip(eyes, end);
               if (hit.isEmpty()) {
                  return false;
               }

               if (!this.lineClear(mc, eyes, hit.get())) {
                  if (this.dbg) {
                     this.debug(mc, "break ray blocked by a block");
                  }

                  return false;
               }
            }

            mc.gameMode.attack(mc.player, ec);
            boolean shouldSwing = !this.cfg.swingOnce || !this.swungIds.containsKey(ec.getId());
            if (shouldSwing) {
               nz.temple.client.compat.Players.swing(mc.player, InteractionHand.MAIN_HAND);
               if (this.cfg.swingOnce) {
                  this.swungIds.put(ec.getId(), now);
               }
            }

            this.brokenIds.put(ec.getId(), now);
            if (p.target != null) {
               if (this.pendTargetId != p.target.getId()) {
                  for (CrystalAura.Pend q : this.pend) {
                     q.tgt = 0.0F;
                  }

                  this.pendTargetId = p.target.getId();
                  this.lastTgtHp = -1.0F;
               }

               CrystalAura.Pend q = new CrystalAura.Pend();
               q.until = now + this.waitMs + 100L;
               q.tgt = Math.max(0.0F, p.targetDmg);
               q.self = Math.max(0.0F, p.selfDmg);
               this.pend.add(q);
            }

            BlockPos underBroken = ec.blockPosition().below();
            this.recentPlaces.remove(underBroken);
            if (underBroken.equals(this.lastCrystalBase)) {
               this.lastCrystalBase = null;
            }

            this.breakTick = this.tick;
            this.noteExplosion(ec.position(), now);
            if (p.target != null && p.targetDmg >= 3.0F && this.checkTarget == null) {
               this.checkTarget = p.target;
               this.checkHealth = p.target.getHealth() + p.target.getAbsorptionAmount();
               this.checkEst = p.targetDmg;
               this.checkAt = now;
            }

            this.nextBreakAt = now + this.randMs(this.cfg.breakLo, this.cfg.breakHi);
            this.lastActionAt = now;
            this.lastSuccessAt = now;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean doPlace(Minecraft mc, CrystalAura.Plan p, long now, Item item) {
      boolean crystal = item == Items.END_CRYSTAL;
      if (now < (crystal ? this.nextPlaceAt : this.nextObsidianAt)) {
         return false;
      }

      if (!this.placeBudget()) {
         return false;
      }

      InteractionHand hand = InteractionHand.MAIN_HAND;
      if (crystal && mc.player.getOffhandItem().is(Items.END_CRYSTAL) && !mc.player.getMainHandItem().is(Items.END_CRYSTAL)) {
         hand = InteractionHand.OFF_HAND;
      } else if (!mc.player.getMainHandItem().is(item)) {
         if (!this.cfg.autoSwitch) {
            return false;
         }

         int slot = this.findSlot(mc, item);
         if (slot != -1 && this.swapBudget()) {
            this.swapTo(mc, slot, now);
            return false;
         }

         return false;
      }

      if (now < this.nextSwapAt) {
         return false;
      }

      BlockHitResult ray = this.rayBlock(mc, 3.0);
      if (ray == null) {
         if (this.dbg) {
            this.debug(mc, "aim ray hit nothing (out of reach?) for " + p.type);
         }

         return false;
      } else {
         BlockPos hitPos = ray.getBlockPos();
         BlockState hs = mc.level.getBlockState(hitPos);
         if (!hs.isAir() && !(hs.getBlock() instanceof LiquidBlock)) {
            if (crystal ? !this.isBase(hs) : hs.canBeReplaced() || this.unreliableSupport(hitPos, hs)) {
               if (this.dbg) {
                  this.debug(mc, "ray hit an unusable block for " + p.type);
               }

               return false;
            } else {
               if (crystal) {
                  if (!hitPos.equals(p.pos)) {
                     if (this.dbg) {
                        this.debug(mc, "ray hits a different block than the base");
                     }

                     return false;
                  }
               } else if (!hitPos.relative(ray.getDirection()).equals(p.pos)) {
                  if (this.dbg) {
                     this.debug(mc, "ray would put obsidian elsewhere");
                  }

                  return false;
               }

               BlockHitResult hit = new BlockHitResult(ray.getLocation(), ray.getDirection(), hitPos, false);
               InteractionResult res = mc.gameMode.useItemOn(mc.player, hand, hit);
               if (!res.consumesAction()) {
                  if (this.dbg) {
                     this.debug(mc, "server/client refused " + p.type);
                  }

                  return false;
               } else {
                  nz.temple.client.compat.Players.swing(mc.player, hand);
                  this.placeTick = this.tick;
                  this.lastActionAt = now;
                  this.lastSuccessAt = now;
                  if (crystal) {
                     this.recentPlaces.put(p.pos, now);
                     this.lastCrystalBase = p.pos;
                     this.lastCrystalAt = now;
                     if (p.pos.equals(this.pendingObsidian)) {
                        this.pendingObsidian = null;
                     }

                     this.nextPlaceAt = now + this.randMs(this.cfg.placeLo, this.cfg.placeHi);
                  } else {
                     this.pendingObsidian = p.pos;
                     this.pendingUntil = now + 900L;
                     this.nextObsidianAt = now + this.randMs(this.cfg.obsLo, this.cfg.obsHi);
                  }

                  return true;
               }
            }
         } else {
            return false;
         }
      }
   }

   private boolean canUse(Minecraft mc, Item item) {
      if (mc.player.getMainHandItem().is(item)) {
         return true;
      } else {
         return item == Items.END_CRYSTAL && mc.player.getOffhandItem().is(item) ? true : this.cfg.autoSwitch && this.findSlot(mc, item) != -1;
      }
   }

   private int findSlot(Minecraft mc, Item item) {
      for (int i = 0; i < 9; i++) {
         if (mc.player.getInventory().getItem(i).is(item)) {
            return i;
         }
      }

      return -1;
   }

   private void handleSwapBack(Minecraft mc, long now) {
      if (this.savedSlot >= 0 && mc.player != null) {
         if (!this.cfg.swapBack) {
            this.savedSlot = -1;
         } else if (!this.cfg.antiWeakness || !this.hasWeakness(mc)) {
            if (this.swapBackAt == 0L) {
               this.swapBackAt = now + this.randMs(this.cfg.swapBackLo, this.cfg.swapBackHi);
            }

            if (now >= this.swapBackAt && this.swapBudget()) {
               this.swapTick = this.tick;
               this.restoreSlotNow(mc);
            }
         }
      }
   }

   private void handleGappleSwitch(Minecraft mc, long now) {
      if (this.cfg.gappleSwitch && mc.player != null) {
         float myHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
         if (!(myHp > this.cfg.maxSelf)) {
            int slot = this.findSlot(mc, Items.ENCHANTED_GOLDEN_APPLE);
            if (slot == -1) {
               slot = this.findSlot(mc, Items.GOLDEN_APPLE);
            }

            if (slot != -1) {
               if (this.swapBudget() && now >= this.nextSwapAt) {
                  this.swapTo(mc, slot, now);
               }
            }
         }
      }
   }

   private boolean hasWeakness(Minecraft mc) {
      if (mc.player == null) {
         return false;
      }

      try {
         for (MobEffectInstance inst : mc.player.getActiveEffects()) {
            if (((MobEffect)inst.getEffect().value()).getDescriptionId().contains("weakness")) {
               return true;
            }
         }
      } catch (Throwable var4) {
      }

      return false;
   }

   private void restoreSlotNow(Minecraft mc) {
      if (this.savedSlot >= 0 && mc.player != null) {
         InventoryHelper.setSelected(mc.player.getInventory(), this.savedSlot);
      }

      this.savedSlot = -1;
      this.swapBackAt = 0L;
   }

   private void onTickEnd() {
      if (this.isEnabled()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.level != null) {
            this.captureSent(mc);
            this.sentValid = true;
         } else {
            this.sentValid = false;
         }
      }
   }

   private void captureSent(Minecraft mc) {
      if (this.rotating && AimAssist.hasSilentRotation()) {
         this.sentYaw = SilentRotationState.tickServerYaw;
         this.sentPitch = SilentRotationState.tickServerPitch;
      } else {
         this.sentYaw = mc.player.getYRot();
         this.sentPitch = mc.player.getXRot();
      }
   }

   private void onFrame() {
      if (this.isEnabled() && this.rotating) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.level != null) {
            AimAssist.setSilentRotation(this.curYaw, this.curPitch);
         } else {
            this.rotating = false;
         }
      }
   }

   private void rotateTick(Minecraft mc, long now, long nowNs, double dtMs) {
      if (mc.player == null) {
         this.rotating = false;
      } else {
         CrystalAura.Plan p = this.plan;
         if (p == null) {
            this.idleRotation(mc, now, dtMs);
         } else {
            this.aimRotation(mc, p, now, nowNs, dtMs);
         }
      }
   }

   private void idleRotation(Minecraft mc, long now, double dtMs) {
      if (this.rotating) {
         if (KillAura.isActive() && KillAura.getTarget() != null && now - this.lastPlanAt >= 80L) {
            this.rotating = false;
         } else if (!this.releaseAim && now - this.lastPlanAt < 80L) {
            AimAssist.setSilentRotation(this.curYaw, this.curPitch);
         } else {
            this.returnToCamera(mc, dtMs);
         }
      }
   }

   private void aimRotation(Minecraft mc, CrystalAura.Plan p, long now, long nowNs, double dtMs) {
      if (!this.rotating) {
         this.curYaw = mc.player.getYRot();
         this.curPitch = mc.player.getXRot();
         this.rotating = true;
      }

      if (now < this.reactUntil) {
         AimAssist.setSilentRotation(this.curYaw, this.curPitch);
      } else {
         Vec3 dv = mc.player.getDeltaMovement();
         Vec3 eyes = mc.player.getEyePosition().add(dv.x, mc.player.onGround() ? 0.0 : dv.y, dv.z);
         Vec3 aim = p.aim;
         if (aim != null) {
            if (!(aim.subtract(eyes).lengthSqr() < 1.0E-6)) {
               float[] ang = angles(eyes, aim);
               float targetYaw = ang[0];
               float targetPitch = Mth.clamp(ang[1], -90.0F, 90.0F);
               boolean human = this.cfg.humanize;
               double tSec = nowNs / 1.0E9;
               if (human) {
                  float amp = Math.min(0.5F, this.cfg.jitter / 100.0F * 0.5F);
                  targetYaw += (float)(noise(tSec, 0) * amp);
                  targetPitch += (float)(noise(tSec, 1) * amp * 0.6);
                  if (now >= this.nextSpeedAt) {
                     this.nextSpeedAt = now + 250L + (long)(this.rng.nextFloat() * 650.0F);
                     this.speedMulGoal = 1.0 + (this.rng.nextDouble() * 2.0 - 1.0) * (this.cfg.speedVar / 100.0) * 0.5;
                  }

                  this.speedMul = this.speedMul + (this.speedMulGoal - this.speedMul) * Math.min(1.0, dtMs / 1000.0 * 3.0);
               } else {
                  this.speedMul = 1.0;
               }

               float yawDiff = Mth.wrapDegrees(targetYaw - this.curYaw);
               float pitchDiff = targetPitch - this.curPitch;
               double factor = human ? 1.0 - Math.exp(-(this.cfg.rotSpeed * 2.0) * (dtMs / 1000.0) * this.speedMul) : 1.0;
               double stepY = yawDiff * factor;
               double stepP = pitchDiff * factor;
               if (human) {
                  double minStep = 360.0 * (dtMs / 1000.0);
                  double mag0 = Math.sqrt(stepY * stepY + stepP * stepP);
                  double full = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
                  if (mag0 < minStep && full > 1.0E-6) {
                     double k = Math.min(full, minStep) / full;
                     stepY = yawDiff * k;
                     stepP = pitchDiff * k;
                  }
               }

               double maxStep = this.cfg.maxTurn * (dtMs / 1000.0);
               double mag = Math.sqrt(stepY * stepY + stepP * stepP);
               if (mag > maxStep && mag > 1.0E-6) {
                  stepY *= maxStep / mag;
                  stepP *= maxStep / mag;
               }

               if (Math.abs(yawDiff) < 0.05F) {
                  stepY = yawDiff;
               }

               if (Math.abs(pitchDiff) < 0.05F) {
                  stepP = pitchDiff;
               }

               float newYaw = this.curYaw + (float)stepY;
               float newPitch = Mth.clamp(this.curPitch + (float)stepP, -90.0F, 90.0F);
               float[] cr = CustomRotations.shape(this, this.curYaw, this.curPitch, targetYaw, targetPitch, newYaw, newPitch);
               newYaw = cr[0];
               newPitch = Mth.clamp(cr[1], -90.0F, 90.0F);
               newYaw = RotationGcd.snapYaw(this.curYaw, newYaw);
               newPitch = RotationGcd.snapPitch(this.curPitch, newPitch);
               this.curYaw = Mth.wrapDegrees(newYaw);
               this.curPitch = newPitch;
               AimAssist.setSilentRotation(this.curYaw, this.curPitch);
            }
         }
      }
   }

   private void returnToCamera(Minecraft mc, double dtMs) {
      float visYaw = mc.player.getYRot();
      float visPitch = mc.player.getXRot();
      float yawDiff = Mth.wrapDegrees(visYaw - this.curYaw);
      float pitchDiff = Mth.wrapDegrees(visPitch - this.curPitch);
      if (Math.abs(yawDiff) < 0.5F && Math.abs(pitchDiff) < 0.5F) {
         this.rotating = false;
         AimAssist.clearSilentRotation();
      } else {
         double factor = this.cfg.humanize ? 1.0 - Math.exp(-(this.cfg.rotSpeed * 2.0) * (dtMs / 1000.0)) : 1.0;
         float[] cr = CustomRotations.shape(
            this, this.curYaw, this.curPitch, visYaw, visPitch, this.curYaw + (float)(yawDiff * factor), this.curPitch + (float)(pitchDiff * factor)
         );
         this.curYaw = Mth.wrapDegrees(cr[0]);
         this.curPitch = Mth.clamp(cr[1], -90.0F, 90.0F);
         AimAssist.setSilentRotation(this.curYaw, this.curPitch);
         AimAssist.clearSilentRotation();
      }
   }

   private static double noise(double t, int seed) {
      return 0.6 * Math.sin(t * 1.3 + seed * 7.1) + 0.4 * Math.sin(t * 2.9 + seed * 3.3 + 1.7);
   }

   private static final class Cand {
      BlockPos base;
      boolean fresh;
      BlockPos clickPos;
      Direction face;
      Vec3 aim;
      float ub;
      float sortKey;
      float bound;
      float t;
      float s;
      float score;
      float turn;
      float yawTurn;
      long id;
   }

   private static final class Cfg {
      float targetRange = 9.0F;
      float breakRange = 4.5F;
      float minDmg = 6.0F;
      float maxSelf = 8.0F;
      float margin = 2.0F;
      boolean antiSuicide = true;
      boolean placeObs = true;
      boolean autoSwitch = true;
      boolean swapBack = true;
      boolean humanize = true;
      boolean randAim = true;
      boolean instantBreak = true;
      boolean airborneOnly = true;
      boolean pauseIneffective = false;
      int jitter = 20;
      int speedVar = 25;
      int rotSpeed = 30;
      int maxTurn = 1800;
      int actionsPerTick = 1;
      boolean pingComp = true;
      long obsLo;
      long obsHi;
      long placeLo;
      long placeHi;
      long breakLo;
      long breakHi;
      long swapLo;
      long swapHi;
      long swapBackLo = 50L;
      long swapBackHi = 100L;
      long reactLo;
      long reactHi = 50L;
      float facePlaceHealth = 6.0F;
      boolean overrideSelfDmg = false;
      float minDmgFactor = 0.0F;
      boolean pauseMining = false;
      boolean pauseEating = true;
      boolean pauseInventory = true;
      boolean grimBreakRange = false;
      boolean assumeBestArmor = false;
      String targetSort = "Damage";
      boolean ignoreNaked = false;
      boolean swingOnce = false;
      boolean onlyBreakingSurround = false;
      int predictTicks = 0;
      boolean minimizeSelfDmg = false;
      boolean gappleSwitch = false;
      boolean antiWeakness = false;
   }

   private static final class Pend {
      long until;
      float tgt;
      float self;
   }

   private static final class Plan {
      CrystalAura.PlanType type;
      long id;
      BlockPos pos;
      BlockPos clickPos;
      Direction face;
      EndCrystal crystal;
      Player target;
      Vec3 aim;
      float targetDmg;
      float selfDmg;
   }

   private enum PlanType {
      BREAK,
      CRYSTAL,
      OBSIDIAN;
   }

   private static final class RayCtx {
      Level level;
      CollisionContext cc;
      Vec3 from;
      Vec3 to;
   }

   private static final class Scan {
      Player target;
      Vec3 eyes;
      Vec3 tpos;
      double reachSqr;
      float reach;
      float tHealth;
      float myHp;
      float minDmg;
      float maxSelf;
      float margin;
      boolean antiSuicide;
      boolean haveObsidian;
      boolean obsidianBusy;
      boolean airborneOnly;
      boolean targetAirborne;
      long now;
      List<Entity> ents;
   }
}
