package nz.temple.client.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Place;
import nz.temple.client.util.Mining;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class VeinMiner extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting limit;
	private final NumberSetting blocksPerTick;

	public VeinMiner() {
		super("Vein Miner", Category.WORLD, "Mines the whole ore vein when you break one ore. Aim at an ore and hold attack.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Mines the rest of the vein one ore at a time, turning to each visible ore and breaking it with your crosshair, "
						+ "never through stone."));
		limit = add(new NumberSetting("Vein limit", 24, 4, 64, 1, " ore")
				.describe("Maximum blocks mined from one vein per tick."));
		blocksPerTick = add(new NumberSetting("Blocks per tick", 1, 1, 3, 1, " blocks")
				.describe("1 uses normal server pacing. Higher values are faster but more likely to be corrected."));
		hideWhenLegit(legit, blocksPerTick);
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null || !mc.options.keyAttack.isDown()) {
			return;
		}
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
			return;
		}
		BlockPos start = hit.getBlockPos();
		String kind = oreKind(Place.path(mc.level.getBlockState(start)));
		if (kind == null) {
			return;
		}
		Set<BlockPos> vein = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.immutable());
		vein.add(start.immutable());
		int max = (int) Math.round(limit.get());
		while (!queue.isEmpty() && vein.size() < max) {
			BlockPos at = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos n = at.offset(dx, dy, dz).immutable();
						if (vein.add(n) && kind.equals(oreKind(Place.path(mc.level.getBlockState(n))))) {
							queue.add(n);
							if (vein.size() >= max) {
								break;
							}
						}
					}
				}
			}
		}
		Vec3 eye = player.getEyePosition();
		if (legit.get()) {
			double reach = player.blockInteractionRange();
			vein.stream().filter(p -> kind.equals(oreKind(Place.path(mc.level.getBlockState(p)))))
					.sorted((a, b) -> Double.compare(eye.distanceToSqr(Vec3.atCenterOf(a)), eye.distanceToSqr(Vec3.atCenterOf(b))))
					.filter(p -> nz.temple.client.util.LegitAim.visiblePoint(player, p, reach) != null)
					.findFirst().ifPresent(p -> nz.temple.client.util.LegitAim.mine(player, p, 35));
			return;
		}
		vein.stream().sorted((a, b) -> Double.compare(eye.distanceToSqr(Vec3.atCenterOf(a)), eye.distanceToSqr(Vec3.atCenterOf(b))))
				.limit((long) Math.round(blocksPerTick.get())).forEach(pos -> {
					if (player.isCreative()) {
						mc.gameMode.destroyBlock(pos);
					} else {
						Mining.dig(mc, pos, Direction.UP);
					}
				});
		nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
	}

	private static String oreKind(String path) {
		if (!path.contains("ore") && !path.contains("debris")) {
			return null;
		}
		if (path.contains("diamond")) {
			return "diamond";
		}
		if (path.contains("gold")) {
			return "gold";
		}
		if (path.contains("iron")) {
			return "iron";
		}
		if (path.contains("coal")) {
			return "coal";
		}
		if (path.contains("redstone")) {
			return "redstone";
		}
		if (path.contains("lapis")) {
			return "lapis";
		}
		if (path.contains("emerald")) {
			return "emerald";
		}
		if (path.contains("quartz")) {
			return "quartz";
		}
		if (path.contains("copper")) {
			return "copper";
		}
		return "other";
	}
}
