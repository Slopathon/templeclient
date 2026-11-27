package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Motion;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class LongJump extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting boost;
	private final NumberSetting height;
	private boolean wasGround = true;

	public LongJump() {
		super("Long Jump", Category.MOVEMENT, "Launches you further when you jump. Best for short use on servers without an anticheat.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Jumps at the last moment before a ledge while sprinting, for the longest jump vanilla allows."));
		boost = add(new NumberSetting("Boost", 1.4, 0.5, 3, 0.1, "x")
				.describe("Horizontal boost compared to a normal jump."));
		height = add(new NumberSetting("Height", 0.42, 0.2, 1, 0.02, " b/t")
				.describe("Jump velocity. 0.42 matches a vanilla jump."));
		hideWhenLegit(legit, boost, height);
		on(TickEvent.class, e -> tick());
	}

	private void edgeJump(LocalPlayer player) {
		if (!player.onGround() || !Motion.keys(player).forward() || player.isShiftKeyDown()) {
			return;
		}
		var options = Minecraft.getInstance().options;
		nz.temple.client.util.Keys.press(options.keySprint, 2);
		Vec3 v = player.getDeltaMovement();
		Vec3 next = player.position().add(v.x * 1.6, 0, v.z * 1.6);
		net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(next.x, player.getY() - 0.5, next.z);
		if (player.level().getBlockState(below).getCollisionShape(player.level(), below).isEmpty()) {
			nz.temple.client.util.Keys.press(options.keyJump, 1);
		}
	}

	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		if (legit.get()) {
			edgeJump(player);
			return;
		}
		boolean ground = player.onGround();
		if (!ground && wasGround && Motion.movingHorizontally(player) && player.getDeltaMovement().y > 0.1) {
			Vec3 dir = Motion.wishDirection(player).scale(0.28 * boost.get());
			player.setDeltaMovement(dir.x, Math.max(player.getDeltaMovement().y, height.get()), dir.z);
		}
		wasGround = ground;
	}
}
