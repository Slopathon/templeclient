package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class FastLadder extends Module {
	private final NumberSetting climbSpeed;

	public FastLadder() {
		super("Fast Ladder", Category.MOVEMENT, "Climbs ladders and vines much faster than vanilla. Hold Space to climb faster still.");
		climbSpeed = add(new NumberSetting("Climb speed", 0.35, 0.1, 1, 0.05, " b/t")
				.describe("Upward speed on climbable blocks."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !player.onClimbable()) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		double y = mc.options.keyShift.isDown() ? -climbSpeed.get() : climbSpeed.get();
		if (mc.options.keyJump.isDown()) {
			y = climbSpeed.get() * 1.6;
		}
		player.setDeltaMovement(v.x, y, v.z);
		player.fallDistance = 0;
	}
}
