package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import nz.temple.client.util.Place;
import nz.temple.client.util.Targets;
import nz.temple.event.TickEvent;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.social.FriendManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HoleFiller extends PlaceModule {
	private final FriendManager friends;
	private final BoolSetting nearEnemy;
	private final NumberSetting enemyDistance;
	private final BoolSetting skipOwn;
	private final BoolSetting doubles;
	private final NumberSetting scanRate;

	private final List<BlockPos> holes = new ArrayList<>();
	private int scan;

	public HoleFiller(FriendManager friends) {
		super("Hole Filler", "Fills the safe 1x1 holes around enemies so they can't escape crystal damage.");
		this.friends = friends;
		group();
		nearEnemy = add(new BoolSetting("Only near enemies", true)
				.describe("Only fills holes an enemy could reach soon. Off fills every hole in range."));
		enemyDistance = add(new NumberSetting("Enemy distance", 3, 1, 8, 0.5, " m")
				.describe("How close an enemy must be to a hole for it to be filled.")
				.visibleWhen(nearEnemy::get));
		skipOwn = add(new BoolSetting("Keep yours", true)
				.describe("Never fills the hole you are standing in or next to."));
		doubles = add(new BoolSetting("Double holes", true).describe("Also fill 2x1 holes."));
		scanRate = add(new NumberSetting("Scan every", 4, 1, 20, 1, " ticks").describe("How often to scan for holes."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null || !hasBlocks(player)) {
			return;
		}
		List<Player> enemies = Targets.enemies(player, range.get() + enemyDistance.get() + 2, friends, false);
		if (nearEnemy.get() && enemies.isEmpty()) {
			return;
		}
		if (--scan <= 0) {
			scan = (int) Math.round(scanRate.get());
			rescan(mc, player, enemies);
		}
		placeAll(player, holes);
	}

	private void rescan(Minecraft mc, LocalPlayer player, List<Player> enemies) {
		holes.clear();
		int r = (int) Math.ceil(range.get());
		BlockPos origin = player.blockPosition();
		BlockPos mine = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
			if (!isHole(mc, pos)) {
				continue;
			}
			if (skipOwn.get() && pos.distManhattan(mine) <= 1) {
				continue;
			}
			if (nearEnemy.get() && !enemyNear(enemies, pos)) {
				continue;
			}
			holes.add(pos.immutable());
		}
		holes.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(player.position())));
	}

	private boolean enemyNear(List<Player> enemies, BlockPos pos) {
		double d = enemyDistance.get();
		for (Player e : enemies) {
			if (Targets.feet(e).equals(pos)) {
				return false;
			}
			if (e.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= d * d) {
				return true;
			}
		}
		return false;
	}

	private boolean isHole(Minecraft mc, BlockPos pos) {
		if (!mc.level.isEmptyBlock(pos) || !mc.level.isEmptyBlock(pos.above()) || !mc.level.isEmptyBlock(pos.above(2))
				|| !hard(mc, pos.below())) {
			return false;
		}
		int open = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(d);
			if (hard(mc, side)) {
				continue;
			}
			if (!doubles.get() || !mc.level.isEmptyBlock(side) || !hard(mc, side.below())) {
				return false;
			}
			for (Direction e : Direction.Plane.HORIZONTAL) {
				if (e != d.getOpposite() && !hard(mc, side.relative(e))) {
					return false;
				}
			}
			open++;
		}
		return open <= 1;
	}

	private static boolean hard(Minecraft mc, BlockPos pos) {
		String id = Place.path(mc.level.getBlockState(pos));
		return id.equals("obsidian") || id.equals("bedrock") || id.equals("crying_obsidian") || id.equals("ender_chest")
				|| id.equals("netherite_block") || id.equals("respawn_anchor") || id.equals("reinforced_deepslate");
	}
}
