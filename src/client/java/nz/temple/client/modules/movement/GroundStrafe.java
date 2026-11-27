package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Motion;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class GroundStrafe extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting speed;

	public GroundStrafe() {
		super("Ground Strafe", Category.MOVEMENT, "Keeps a steady strafe speed on the ground without jumping.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Sprint-jumps with the jump key while you strafe so you keep speed through turns."));
		speed = add(new NumberSetting("Speed", 0.32, 0.1, 0.8, 0.01, " b/t")
				.describe("Ground movement speed. Vanilla sprint is about 0.287."));
		hideWhenLegit(legit, speed);
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !player.onGround()) {
			return;
		}
		if (legit.get()) {
			if (Motion.wishDirection(player).lengthSqr() > 1e-6 && !player.isShiftKeyDown()) {
				var options = Minecraft.getInstance().options;
				nz.temple.client.util.Keys.press(options.keySprint, 2);
				nz.temple.client.util.Keys.press(options.keyJump, 1);
			}
			return;
		}
		Vec3 steered = nz.temple.client.util.Steering.direction(player);
		Vec3 direction = steered != null ? steered : Motion.wishDirection(player);
		if (direction.lengthSqr() > 1e-6) {
			Vec3 velocity = player.getDeltaMovement();
			player.setDeltaMovement(direction.x * speed.get(), velocity.y, direction.z * speed.get());
		}
	}
}
