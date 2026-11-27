package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Motion;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Spider extends Module {
	private final ModeSetting mode;
	private final NumberSetting speed;

	public Spider() {
		super("Spider", Category.MOVEMENT, "Climb walls by walking into them.");
		mode = add(new ModeSetting("Mode", List.of("Smooth", "Ladder", "Hop"), "Smooth")
				.describe("Smooth climbs at the set speed. Ladder climbs at ladder speed. Hop jumps each time you start to fall."));
		speed = add(new NumberSetting("Speed", 0.2, 0.05, 0.6, 0.01, " b/t")
				.describe("Climb speed.")
				.visibleWhen(() -> mode.is("Smooth")));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !player.horizontalCollision) {
			return;
		}
		if (Motion.wishDirection(player).lengthSqr() > 1e-6) {
			Vec3 v = player.getDeltaMovement();
			switch (mode.get()) {
				case "Ladder" -> player.setDeltaMovement(v.x, 0.1176, v.z);
				case "Hop" -> {
					if (v.y < 0) {
						player.setDeltaMovement(v.x, 0.42, v.z);
					}
				}
				default -> player.setDeltaMovement(v.x, speed.get(), v.z);
			}
			player.fallDistance = 0;
		}
	}
}
