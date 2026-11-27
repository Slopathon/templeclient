package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class ElytraBoost extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting boost;
	private final NumberSetting upSpeed;

	public ElytraBoost() {
		super("Elytra Boost", Category.MOVEMENT, "Boosts elytra flight without rockets. Steer with your mouse; Space climbs.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Uses a firework rocket from your hotbar (a normal slot switch and use) whenever your glide slows."));
		boost = add(new NumberSetting("Boost", 0.08, 0.01, 0.3, 0.01, " b/t")
				.describe("Speed added in your look direction each tick."));
		upSpeed = add(new NumberSetting("Climb", 0.6, 0, 1.5, 0.1, " b/t")
				.describe("Upward speed while holding Space."));
		hideWhenLegit(legit, boost, upSpeed);
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (legit.get()) {
			if (player != null) {
				nz.temple.client.util.Elytra.boost(player, 0.9);
			}
			return;
		}
		if (player == null || !player.isFallFlying()) {
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 v = player.getDeltaMovement().add(look.scale(boost.get()));
		double y = v.y;
		if (Minecraft.getInstance().options.keyJump.isDown()) {
			y = Math.max(y, upSpeed.get());
		}
		if (Minecraft.getInstance().options.keyShift.isDown()) {
			y = Math.min(y, -upSpeed.get());
		}
		player.setDeltaMovement(v.x, y, v.z);
		player.fallDistance = 0;
	}
}
