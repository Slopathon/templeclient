package nz.temple.client.util;

import net.minecraft.world.entity.LivingEntity;
import nz.temple.client.modules.combat.AimAssist;
import nz.temple.client.modules.combat.KillAura;

/** The entity the ported combat modules are fighting, for Temple's target HUD, nametags and Target ESP. */
public final class CombatTarget {
	private CombatTarget() {
	}

	public static LivingEntity current() {
		LivingEntity t = KillAura.isActive() ? KillAura.getTarget() : null;
		if (t == null && AimAssist.isActive()) {
			t = AimAssist.getCurrentTarget();
		}
		return t != null && t.isAlive() ? t : null;
	}
}
