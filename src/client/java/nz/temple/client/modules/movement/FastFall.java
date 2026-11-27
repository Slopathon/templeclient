package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class FastFall extends Module {
	private final NumberSetting speed;

	public FastFall() {
		super("Fast Fall", Category.MOVEMENT, "Drops you quickly after stepping off a ledge.");
		speed = add(new NumberSetting("Fall speed", 0.8, 0.15, 2, 0.05, " b/t")
				.describe("Maximum downward velocity while falling."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying() || player.onClimbable()) {
			return;
		}
		Vec3 velocity = player.getDeltaMovement();
		if (velocity.y < -0.08) {
			player.setDeltaMovement(velocity.x, Math.min(velocity.y, -speed.get()), velocity.z);
		}
	}
}
