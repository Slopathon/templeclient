package nz.temple.client.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import nz.temple.client.TempleNzClient;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

public final class SpeedMine extends Module {
	private static SpeedMine instance;
	private final BoolSetting noDelay;
	private final BoolSetting keepSwinging;
	private final NumberSetting breakAt;
	public SpeedMine() {
		super("Speed Mine", Category.WORLD, "Mines faster by removing the delay between blocks and finishing each block as early as the "
				+ "server accepts.");
		instance = this;
		noDelay = add(new BoolSetting("No delay between blocks", true)
				.describe("Removes the five-tick delay after each block breaks."));
		breakAt = add(new NumberSetting("Break at", 80, 70, 100, 1, "%")
				.describe("Finishes each block at this much progress. Vanilla servers accept blocks from 70% progress to allow for lag, so "
						+ "80% mines about 20% faster with room for ping. 100% is vanilla. Below about 75% on a laggy connection, the "
						+ "server may delay the break briefly."));
		keepSwinging = add(new BoolSetting("Keep swinging", true)
				.describe("Keeps your arm swinging while you hold attack, so mining looks continuous."));
		on(TickEvent.class, e -> tick());
	}
	public static boolean removesDelay() {
		if (instance == null && TempleNzClient.modules() != null) {
			instance = TempleNzClient.modules().get(SpeedMine.class);
		}
		return instance != null && instance.isEnabled() && instance.noDelay.get();
	}
	public static float breakThreshold() {
		if (instance == null && TempleNzClient.modules() != null) {
			instance = TempleNzClient.modules().get(SpeedMine.class);
		}
		return instance != null && instance.isEnabled() ? instance.breakAt.getFloat() / 100f : 1f;
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null || !mc.options.keyAttack.isDown() || mc.gui.screen() != null) {
			return;
		}
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
			return;
		}
		if (keepSwinging.get() && !nz.temple.client.compat.Players.isSwinging(player)) {
			nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
		}
	}
}
