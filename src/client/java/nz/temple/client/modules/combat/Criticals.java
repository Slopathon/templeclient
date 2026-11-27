package nz.temple.client.modules.combat;

import nz.temple.client.event.AttackStartEvent;
import nz.temple.client.event.AttackEvents;
import nz.temple.client.event.MovementInputEvents;
import nz.temple.client.util.Friends;
import nz.temple.client.modules.SchemaModule;
import nz.temple.client.modules.movement.MoveFix;
import nz.temple.client.util.AccessorCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

public class Criticals extends SchemaModule {
   private static final String NORMAL = "Normal";
   private static final String STOP_SPRINT = "Stop Sprint";
   private boolean suppressSprint = false;
   private LivingEntity pendingTarget = null;
   private boolean justAttacked = false;

   public Criticals() {
      super("Criticals", "Combat");
      AttackEvents.register(this::onAttack);
      MovementInputEvents.register(this::onMovement);
   }

   @Override
   public void onEnable() {
      this.suppressSprint = false;
      this.pendingTarget = null;
      this.justAttacked = false;
   }

   @Override
   public void onDisable() {
      this.suppressSprint = false;
      this.pendingTarget = null;
      this.justAttacked = false;
   }

   @Override
   public void onTick() {
      this.justAttacked = false;
      if (this.pendingTarget != null) {
         Minecraft mc = Minecraft.getInstance();
         LivingEntity t = this.pendingTarget;
         this.pendingTarget = null;
         LocalPlayer p = mc.player;
         if (p != null && mc.level != null && mc.gameMode != null && mc.gui.screen() == null) {
            if (t.isAlive() && !t.isRemoved()) {
               if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() == t) {
                  mc.gameMode.attack(p, t);
                  nz.temple.client.compat.Players.swing(p, InteractionHand.MAIN_HAND);
                  this.justAttacked = true;
               }
            }
         }
      }
   }

   private void onMovement(MoveFix.EventMovementInput e) {
      if (this.suppressSprint) {
         this.suppressSprint = false;
         if (this.isEnabled()) {
            e.sprint = false;
         }
      }
   }

   private void onAttack(AttackStartEvent e) {
      if (this.isEnabled()) {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer p = mc.player;
         if (p != null && mc.level != null && mc.gui.screen() == null) {
            if (this.justAttacked) {
               e.cancel();
            } else if (this.pendingTarget != null) {
               e.cancel();
            } else if (e.getTarget() instanceof LivingEntity living && living.isAlive()) {
               if (!Friends.isProtected(living)) {
                  if (this.canCritHere(p)) {
                     String mode = this.getSetting("Mode", "Normal");
                     if ("Stop Sprint".equals(mode)) {
                        this.stopSprintMode(p, e, living);
                     } else {
                        this.normalMode(p);
                     }
                  }
               }
            }
         }
      }
   }

   private void stopSprintMode(LocalPlayer p, AttackStartEvent e, LivingEntity target) {
      if (!p.onGround() && !(p.fallDistance <= 0.0)) {
         if (p.isSprinting()) {
            e.cancel();
            this.pendingTarget = target;
            p.setSprinting(false);
            this.suppressSprint = true;
         }
      }
   }

   private void normalMode(LocalPlayer p) {
      this.dropSprint(p);
      if (p.onGround()) {
         double x = p.getX();
         double y = p.getY();
         double z = p.getZ();
         boolean hc = p.horizontalCollision;
         p.connection.send(new Pos(x, y + 0.0625, z, false, hc));
         p.connection.send(new Pos(x, y, z, false, hc));
      }
   }

   private void dropSprint(LocalPlayer p) {
      if (p.isSprinting()) {
         p.setSprinting(false);
         this.suppressSprint = true;
         p.connection.send(new ServerboundPlayerCommandPacket(p, Action.STOP_SPRINTING));
         AccessorCompat.setWasSprinting(p, false);
      }
   }

   private boolean canCritHere(LocalPlayer p) {
      if (p.getAbilities().flying || p.isFallFlying() || p.isPassenger()) {
         return false;
      } else {
         return p.isInWater() || p.isInLava() || p.onClimbable() ? false : !p.hasEffect(MobEffects.BLINDNESS);
      }
   }
}
