package nz.temple.client.modules.combat;

import java.util.LinkedHashMap;
import java.util.Map;
import nz.temple.client.event.AttackStartEvent;
import nz.temple.client.event.AttackEvents;
import nz.temple.client.event.MovementInputEvents;
import nz.temple.client.modules.SchemaModule;
import nz.temple.client.modules.combat.combo.ComboHelper;
import nz.temple.client.modules.combat.combo.ComboMode;
import nz.temple.client.modules.combat.combo.STap;
import nz.temple.client.modules.combat.combo.WTap;
import nz.temple.client.modules.movement.MoveFix;

public class SprintReset extends SchemaModule {
   private static final String W_TAP = "W-Tap";
   private static final String S_TAP = "S-Tap";
   private final Map<String, ComboMode> modes = new LinkedHashMap<>();

   public SprintReset() {
      super("Sprint Reset", "Combat");
      ComboHelper.instance = this;
      this.modes.put("W-Tap", new WTap());
      this.modes.put("S-Tap", new STap());
      AttackEvents.register(this::onAttack);
      MovementInputEvents.register(this::onMovement);
   }

   @Override
   public void onEnable() {
      this.modes.values().forEach(ComboMode::onEnable);
   }

   @Override
   public void onDisable() {
      this.modes.values().forEach(ComboMode::onDisable);
   }

   private void onAttack(AttackStartEvent e) {
      if (this.isEnabled()) {
         ComboMode mode = this.getCurrentMode();
         if (mode != null) {
            mode.onAttack(e);
         }
      }
   }

   private void onMovement(MoveFix.EventMovementInput e) {
      if (this.isEnabled()) {
         ComboMode mode = this.getCurrentMode();
         if (mode != null) {
            mode.onMovement(e);
         }
      }
   }

   private ComboMode getCurrentMode() {
      String mode = this.getSetting("Mode", "W-Tap");
      return this.modes.get(mode);
   }

   @Override
   public <T> T getSetting(String key, T defaultValue) {
      return super.getSetting(key, defaultValue);
   }
}
