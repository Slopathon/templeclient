package nz.temple.client.modules.combat;

import java.util.Random;
import nz.temple.client.event.MovementInputEvents;
import nz.temple.client.event.PacketReceiveEvents;
import nz.temple.client.event.PacketReceiveEvent;
import nz.temple.client.modules.SchemaModule;
import nz.temple.client.modules.movement.MoveFix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

public class AutoJumpReset extends SchemaModule {
   private final Random rng = new Random();
   private volatile int delayTicks = -1;
   private volatile boolean injectJump = false;

   public AutoJumpReset() {
      super("Auto Jump Reset", "Combat");
      PacketReceiveEvents.register(this::onPacketReceive);
      MovementInputEvents.register(this::onMovementInput);
   }

   @Override
   public void onDisable() {
      this.delayTicks = -1;
      this.injectJump = false;
   }

   private void onPacketReceive(PacketReceiveEvent event) {
      if (this.isEnabled()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.level != null) {
            boolean isKnockback = false;
            if (event.getPacket() instanceof ClientboundSetEntityMotionPacket velPkt && nz.temple.client.util.PacketCompat.motionEntityId(velPkt) == mc.player.getId()) {
               isKnockback = true;
            }

            if (event.getPacket() instanceof ClientboundExplodePacket) {
               isKnockback = true;
            }

            if (isKnockback) {
               LocalPlayer player = mc.player;
               if (player.onGround()) {
                  if (!player.isInWater() && !player.isInLava() && !player.onClimbable()) {
                     int chance = this.getSetting("Chance", 100);
                     if (chance >= 100 || this.rng.nextInt(100) < chance) {
                        if (this.delayTicks < 0) {
                           this.delayTicks = 1 + this.rng.nextInt(4);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void onTick() {
      if (this.delayTicks >= 0) {
         this.delayTicks--;
         if (this.delayTicks <= 0) {
            this.delayTicks = -1;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
               LocalPlayer player = mc.player;
               if (player.onGround()) {
                  if (!player.isInWater() && !player.isInLava() && !player.onClimbable()) {
                     this.injectJump = true;
                  }
               }
            }
         }
      }
   }

   private void onMovementInput(MoveFix.EventMovementInput event) {
      if (this.injectJump) {
         this.injectJump = false;
         event.jump = true;
      }
   }
}
