package nz.temple.client.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import nz.temple.client.util.Place;
import nz.temple.client.util.Rotations;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

public final class Tower extends Module {
	private static final int ROTATION_PRIORITY = 140;
	private final BoolSetting legit;
	private final BoolSetting onlyJump;
	private final NumberSetting jumpHeight;
	public Tower() {
		super("Tower", Category.WORLD, "Pillars straight up, jumping and placing a block under you at the top of each jump.");
		legit = add(new BoolSetting("Legit", false)
				.describe("Turns your real view down, presses jump and places a block under your feet near the top of each jump. No "
						+ "boosted jumps."));
		onlyJump = add(new BoolSetting("Hold jump", false)
				.describe("Only pillars while you hold jump. Off pillars the whole time it is on."));
		jumpHeight = add(new NumberSetting("Jump boost", 0.42, 0.3, 0.6, 0.02, " b/t")
				.describe("Jump velocity. Vanilla is 0.42."));
		hideWhenLegit(legit, jumpHeight);
		on(TickEvent.class, e -> tick());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) {
			return;
		}
		if (onlyJump.get() && !mc.options.keyJump.isDown()) {
			return;
		}
		if (legit.get()) {
			nz.temple.client.util.LegitAim.pillar(player, 40);
			return;
		}
		if (Place.blockHotbarSlot(player) < 0) {
			return;
		}
		Rotations.request(player.getYRot(), 90, ROTATION_PRIORITY);
		if (player.onGround()) {
			var v = player.getDeltaMovement();
			player.setDeltaMovement(v.x, jumpHeight.get(), v.z);
			return;
		}
		BlockPos under = BlockPos.containing(player.getX(), player.getY() - 1, player.getZ());
		if (player.getY() >= under.getY() + 1 && Place.replaceable(mc.level, under)) {
			Place.placeWith(player, under, Place::buildable, true, false);
		}
	}
}
