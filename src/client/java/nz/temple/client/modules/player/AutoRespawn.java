package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class AutoRespawn extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting delay;
	private int deadTicks;

	public AutoRespawn() {
		super("Auto Respawn", Category.PLAYER, "Respawns automatically when you die.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Waits a random one to three seconds before respawning."));
		delay = add(new NumberSetting("Delay", 20, 0, 100, 1, " t")
				.describe("Ticks to wait before respawning."));
		on(TickEvent.class, e -> tick());
	}

	private int legitWait = -1;

	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			deadTicks = 0;
			return;
		}
		if (!player.isDeadOrDying()) {
			deadTicks = 0;
			legitWait = -1;
			return;
		}
		if (legit.get() && legitWait < 0) {
			legitWait = Math.max((int) Math.round(delay.get()), nz.temple.client.util.Human.ticks(20, 60));
		}
		if (++deadTicks >= (legit.get() ? legitWait : delay.get())) {
			legitWait = -1;
			player.respawn();
			deadTicks = 0;
		}
	}
}
