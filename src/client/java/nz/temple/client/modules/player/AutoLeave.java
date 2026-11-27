package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class AutoLeave extends Module {
	private final NumberSetting health;

	public AutoLeave() {
		super("Auto Leave", Category.PLAYER, "Disconnects when your health is critically low.");
		health = add(new NumberSetting("Health", 4, 1, 19, 1, " hp")
				.describe("Disconnect at or below this health."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player != null && player.getHealth() <= health.get()) {
			AutoReconnect.leftOnPurpose();
			mc.disconnectFromWorld(Component.literal("Temple.nz Auto Leave"));
		}
	}
}
