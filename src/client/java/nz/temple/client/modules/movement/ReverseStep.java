package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class ReverseStep extends Module {
	private final NumberSetting speed;
	private final NumberSetting height;

	public ReverseStep() {
		super("Reverse Step", Category.MOVEMENT, "Pulls you down quickly when you walk off a ledge so you land sooner.");
		speed = add(new NumberSetting("Fall speed", 1.2, 0.3, 3, 0.1, " b/t")
				.describe("Drop speed after leaving the edge."));
		height = add(new NumberSetting("Max drop", 3, 1, 6, 0.5, " b")
				.describe("Only slam when the ground is within this many blocks."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || player.onGround() || player.getDeltaMovement().y > 0
				|| player.isInWater() || player.isInLava() || player.isFallFlying() || player.onClimbable()) {
			return;
		}
		int h = (int) Math.ceil(height.get());
		for (int i = 1; i <= h; i++) {
			if (!mc.level.isEmptyBlock(BlockPos.containing(player.getX(), player.getY() - i, player.getZ()))) {
				Vec3 v = player.getDeltaMovement();
				player.setDeltaMovement(v.x, Math.min(v.y, -speed.get()), v.z);
				return;
			}
		}
	}
}
