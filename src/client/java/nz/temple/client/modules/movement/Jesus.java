package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Motion;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ModeSetting;

import java.util.List;

public final class Jesus extends Module {
	private final ModeSetting mode;

	public Jesus() {
		super("Jesus", Category.MOVEMENT, "Lets you walk on water.");
		mode = add(new ModeSetting("Mode", List.of("Solid", "Float"), "Solid")
				.describe("Solid lets you stand on the surface like ground. Float keeps you bobbing at the surface. Crouch to go under in "
						+ "either mode."));
		on(TickEvent.class, e -> tick());
	}

	private static boolean waterBelow(LocalPlayer player) {
		net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
		return player.level().getFluidState(below).is(net.minecraft.tags.FluidTags.WATER)
				&& player.getY() - below.getY() < 1.12;
	}

	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		if (Motion.keys(player).shift()) {
			return;
		}
		if (mode.is("Solid")) {
			Vec3 v = player.getDeltaMovement();
			if (player.isInWater()) {
				player.setDeltaMovement(v.x, Math.max(v.y, 0.11), v.z);
				player.fallDistance = 0;
			} else if (v.y <= 0 && waterBelow(player)) {
				player.setDeltaMovement(v.x, 0, v.z);
				player.fallDistance = 0;
			}
			return;
		}
		if (player.isInWater()) {
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x, Math.max(v.y, 0.1), v.z);
			player.fallDistance = 0;
		}
	}
}
