package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class Glide extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting fallSpeed;

	public Glide() {
		super("Glide", Category.MOVEMENT, "Slows your fall, similar to Slow Falling.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Opens your elytra when you start falling with one equipped."));
		fallSpeed = add(new NumberSetting("Fall speed", 0.12, 0.02, 0.5, 0.01, " b/t")
				.describe("Fall speed. Lower values glide longer."));
		hideWhenLegit(legit, fallSpeed);
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (legit.get()) {
			if (player != null && player.fallDistance > 2) {
				nz.temple.client.util.Elytra.open(player);
			}
			return;
		}
		if (player == null || player.onGround() || player.isInWater() || player.isInLava()
				|| player.isFallFlying() || player.onClimbable()) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		if (v.y < -fallSpeed.get()) {
			player.setDeltaMovement(v.x, -fallSpeed.get(), v.z);
			player.fallDistance = Math.min(player.fallDistance, 2.5f);
		}
	}
}
