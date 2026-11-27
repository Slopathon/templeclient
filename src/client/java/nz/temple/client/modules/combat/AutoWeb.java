package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import nz.temple.client.util.Place;
import nz.temple.client.util.Targets;
import nz.temple.event.TickEvent;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.social.FriendManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class AutoWeb extends PlaceModule {
	private final FriendManager friends;
	private final NumberSetting targetRange;
	private final BoolSetting feet;
	private final BoolSetting headToo;
	private final BoolSetting allTargets;
	private final BoolSetting onlyMoving;
	private final BoolSetting ignoreNaked;

	public AutoWeb(FriendManager friends) {
		super("Auto Web", "Places cobwebs in enemies to slow them down.");
		this.friends = friends;
		block.internal();
		group();
		targetRange = add(new NumberSetting("Target range", 4.5, 2, 6, 0.5, " m").describe("How far away the enemy can be."));
		feet = add(new BoolSetting("Feet", true).describe("Put a web where their feet are."));
		headToo = add(new BoolSetting("Head", false).describe("Also put a web at their head."));
		allTargets = add(new BoolSetting("Every enemy", false).describe("Web everyone in range, not only the nearest."));
		onlyMoving = add(new BoolSetting("Only when moving", false)
				.describe("Only webs moving enemies, to save webs."));
		ignoreNaked = add(new BoolSetting("Ignore naked", false).describe("Skip players with no armour."));
		on(TickEvent.class, e -> tick());
	}

	@Override
	protected Predicate<ItemStack> item() {
		return Place.item("cobweb");
	}

	@Override
	protected boolean needsRoom() {
		return false;
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null || !hasBlocks(player)) {
			return;
		}
		List<Player> enemies = Targets.enemies(player, targetRange.get(), friends, ignoreNaked.get());
		List<BlockPos> wanted = new ArrayList<>();
		for (Player enemy : enemies) {
			if (onlyMoving.get() && enemy.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4 && enemy.xo == enemy.getX()) {
				continue;
			}
			BlockPos f = Targets.feet(enemy);
			if (feet.get()) {
				wanted.add(f);
			}
			if (headToo.get()) {
				wanted.add(f.above());
			}
			if (!allTargets.get()) {
				break;
			}
		}
		placeAll(player, wanted);
	}
}
