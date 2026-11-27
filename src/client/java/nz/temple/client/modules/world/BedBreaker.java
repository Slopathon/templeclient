package nz.temple.client.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Place;
import nz.temple.client.util.Mining;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class BedBreaker extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting range;

	public BedBreaker() {
		super("Bed Breaker", Category.WORLD, "Breaks beds and dragon eggs around you.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Only breaks beds you can see and reach, looking at them with the best tool, never through walls."));
		range = add(new NumberSetting("Range", 4, 1, 6, 1, " b")
				.describe("Break radius."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		int r = (int) Math.ceil(range.get());
		double maxSq = range.get() * range.get();
		BlockPos origin = player.blockPosition();
		BlockPos best = null;
		double bestSq = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
			String p = Place.path(mc.level.getBlockState(pos));
			if (!p.contains("bed") && !p.contains("dragon_egg") && !p.contains("respawn_anchor")) {
				continue;
			}
			double d = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
			if (d < maxSq && d < bestSq && (!legit.get() || nz.temple.client.util.LegitAim.visiblePoint(player, pos, player.blockInteractionRange()) != null)) {
				bestSq = d;
				best = pos.immutable();
			}
		}
		if (best == null) {
			return;
		}
		if (legit.get()) {
			nz.temple.client.util.LegitAim.mine(player, best, 35);
		} else if (player.isCreative()) {
			mc.gameMode.destroyBlock(best);
		} else {
			Mining.dig(mc, best, Direction.UP);
			nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
		}
	}
}
