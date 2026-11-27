package nz.temple.client.modules.combat.combo;

import nz.temple.client.event.AttackStartEvent;
import nz.temple.client.modules.movement.MoveFix;

public class STap extends ComboMode {
   @Override
   protected boolean validateAttack(AttackStartEvent e) {
      return mc.player != null && mc.player.zza > 0.0F;
   }

   @Override
   protected void applyMovement(MoveFix.EventMovementInput e) {
      int duration = ComboHelper.instance != null ? ComboHelper.instance.getSetting("S-Tap Duration", 3) : 3;
      int reaction = ComboHelper.instance != null ? ComboHelper.instance.getSetting("S-Tap Reaction", 0) : 0;
      boolean groundOnly = ComboHelper.instance != null && ComboHelper.instance.getSetting("S-Tap Ground Only", false);
      int ticksSinceAttack = mc.player.tickCount - this.attackTick;
      int endTick = reaction + duration;
      if (ticksSinceAttack > endTick) {
         this.attackTick = -1;
      } else if (ticksSinceAttack > reaction) {
         if (!groundOnly || mc.player.onGround()) {
            e.forward = false;
            e.sprint = false;
            e.back = true;
            if (mc.player.isSprinting()) {
               mc.player.setSprinting(false);
            }
         }
      }
   }
}
