package nz.temple.client.modules.combat;

import nz.temple.client.modules.SchemaModule;
import nz.temple.client.modules.SchemaModules;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public class Reach extends SchemaModule {
   public Reach() {
      super("Reach", "Combat");
   }

   public static float getBlockReach() {
      Reach m = (Reach)SchemaModules.get("Reach");
      return m != null && m.isEnabled() ? m.getSetting("Block Reach", 4.5F) : -1.0F;
   }

   public static float getEntityReach() {
      Reach m = (Reach)SchemaModules.get("Reach");
      return m != null && m.isEnabled() ? m.getSetting("Entity Reach", 3.0F) : -1.0F;
   }

   public static double vanillaBlockReach(Player p) {
      try {
         return p.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
      } catch (Throwable t) {
         return 4.5;
      }
   }

   public static double vanillaEntityReach(Player p) {
      try {
         return p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
      } catch (Throwable t) {
         return 3.0;
      }
   }
}
