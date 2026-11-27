package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Motion;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;

public final class Parkour extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final BoolSetting sprintJump;

	public Parkour() {
		super("Parkour", Category.MOVEMENT, "Jumps automatically at ledges so you only need to steer.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Presses your jump key at the edge instead of jumping directly, so the inputs sent to the server match."));
		sprintJump = add(new BoolSetting("Sprint jumps", true)
				.describe("Sprints while jumping gaps for full distance."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !player.onGround()) {
			return;
		}
		if (!Motion.movingHorizontally(player)) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		BlockPos ahead = BlockPos.containing(player.getX() + v.x * 4, player.getY() - 0.6, player.getZ() + v.z * 4);
		BlockPos headroom = BlockPos.containing(player.getX(), player.getY() + 1, player.getZ());
		if (mc.level.isEmptyBlock(ahead) && mc.level.isEmptyBlock(headroom)) {
			if (sprintJump.get()) {
				mc.options.keySprint.setDown(true);
			}
			if (legit.get()) {
				nz.temple.client.util.Keys.press(mc.options.keyJump, 1);
			} else {
				player.jumpFromGround();
			}
		}
	}

	@Override
	protected void onDisable() {
		Minecraft.getInstance().options.keySprint.setDown(false);
	}
}
