package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;

public final class SafeWalk extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final BoolSetting onlySneak;

	public SafeWalk() {
		super("Safe Walk", Category.MOVEMENT, "Stops you at block edges so you do not walk off.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Holds sneak while you are at the edge of a block, as you would when bridging manually."));
		onlySneak = add(new BoolSetting("Only while sneaking", false)
				.describe("Only stops at edges while you hold sneak."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !player.onGround()) {
			return;
		}
		if (onlySneak.get() && !player.isShiftKeyDown()) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		if (v.horizontalDistanceSqr() < 1e-6) {
			return;
		}
		double nextX = player.getX() + v.x;
		double nextZ = player.getZ() + v.z;
		BlockPos feet = BlockPos.containing(nextX, player.getY() - 0.6, nextZ);
		if (mc.level.isEmptyBlock(feet)) {
			if (legit.get()) {
				nz.temple.client.util.Keys.press(mc.options.keyShift, 3);
				return;
			}
			player.setDeltaMovement(0, v.y, 0);
		}
	}
}
