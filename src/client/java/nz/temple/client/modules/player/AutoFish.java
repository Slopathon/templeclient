package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Inv;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

public final class AutoFish extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting recastDelay;
	private final BoolSetting autoSwap;
	private double lastVelY = Double.NaN;
	private int calmTicks;
	private int waitTicks;
	private int reelAt;

	public AutoFish() {
		super("Auto Fish", Category.PLAYER, "Fishes automatically: casts, detects bites, reels in and recasts.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Reels in after a short, varied reaction delay (around 0.2 seconds) and recasts after a random pause."));
		recastDelay = add(new NumberSetting("Recast delay", 15, 0, 60, 1, " t")
				.describe("Ticks to wait after reeling in before casting again."));
		autoSwap = add(new BoolSetting("Auto swap rod", true)
				.describe("Switches to a fishing rod in your hotbar automatically, then switches back."));
		on(TickEvent.class, e -> tick());
	}

	private int previous = -1;

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null) {
			return;
		}
		if (waitTicks > 0) {
			waitTicks--;
			return;
		}
		FishingHook hook = player.fishing;
		if (reelAt > 0 && hook != null && player.tickCount >= reelAt) {
			reelAt = 0;
			mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
			calmTicks = 0;
			waitTicks = (int) Math.round(recastDelay.get()) + nz.temple.client.util.Human.ticks(2, 20);
			return;
		}
		if (hook == null) {
			reelAt = 0;
		}
		if (hook == null) {
			cast(player, mc);
			lastVelY = Double.NaN;
			return;
		}
		Vec3 v = hook.getDeltaMovement();
		if (Double.isNaN(lastVelY)) {
			lastVelY = v.y;
			return;
		}
		if (Math.abs(v.y) < 0.05 && Math.abs(lastVelY) < 0.05) {
			calmTicks++;
		} else {
			calmTicks = 0;
		}
		boolean bite = calmTicks > 20 && lastVelY - v.y > 0.12;
		lastVelY = v.y;
		if (bite && legit.get()) {
			if (reelAt == 0) {
				reelAt = player.tickCount + nz.temple.client.util.Human.reactionTicks();
			}
			return;
		}
		if (bite) {
			mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
			calmTicks = 0;
			waitTicks = (int) Math.round(recastDelay.get());
			if (previous >= 0) {
				player.getInventory().setSelectedSlot(previous);
				previous = -1;
			}
		}
	}

	private void cast(LocalPlayer player, Minecraft mc) {
		if (!Inv.pathContains(player.getMainHandItem(), "fishing_rod") && autoSwap.get()) {
			for (int i = 0; i < 9; i++) {
				if (Inv.pathContains(player.getInventory().getItem(i), "fishing_rod")) {
					previous = player.getInventory().getSelectedSlot();
					player.getInventory().setSelectedSlot(i);
					break;
				}
			}
		}
		if (Inv.pathContains(player.getMainHandItem(), "fishing_rod")) {
			mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
			waitTicks = 10;
		}
	}

	@Override
	protected void onDisable() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && previous >= 0) {
			player.getInventory().setSelectedSlot(previous);
			previous = -1;
		}
	}
}
