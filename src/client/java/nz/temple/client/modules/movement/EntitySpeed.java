package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Motion;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class EntitySpeed extends Module {
	private final NumberSetting speed;

	public EntitySpeed() {
		super("Entity Speed", Category.MOVEMENT, "Makes the mount you are riding move faster.");
		speed = add(new NumberSetting("Speed", 0.45, 0.1, 2, 0.05, " b/t")
				.describe("Mount speed. Lower values rubber-band less."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Entity vehicle = player.getVehicle();
		Vec3 direction = Motion.wishDirection(player);
		if (vehicle != null && direction.lengthSqr() > 1e-6) {
			Vec3 velocity = vehicle.getDeltaMovement();
			vehicle.setDeltaMovement(direction.x * speed.get(), velocity.y, direction.z * speed.get());
		}
	}
}
