package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import nz.temple.client.util.Motion;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;

public final class KeepSprint extends Module {
	private final BoolSetting spoofPacket;
	private final BoolSetting legit;

	public KeepSprint() {
		super("Keep Sprint", Category.MOVEMENT, "Keeps you sprinting when you attack, so every hit deals full knockback. Does not change "
				+ "your motion.");
		legit = add(new BoolSetting("Legit", false)
				.describe("Holds your sprint key whenever vanilla allows sprinting (moving forward, enough food, not sneaking or using an "
						+ "item), so sprint resumes after a hit normally. Sends no extra sprint packets."));
		spoofPacket = add(new BoolSetting("Spoof sprint packets", true)
				.describe("Tells the server you started sprinting, to pass sprint checks on NCP-style anticheats."));
		hideWhenLegit(legit, spoofPacket);
		on(TickEvent.class, e -> tick());
	}

	@Override
	protected void onDisable() {
		if (legit.get()) {
			Minecraft.getInstance().options.keySprint.setDown(false);
		}
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		if (legit.get()) {
			boolean may = Motion.keys(player).forward() && player.getFoodData().getFoodLevel() > 6 && !player.isShiftKeyDown()
					&& !player.isUsingItem();
			mc.options.keySprint.setDown(may);
			return;
		}
		if (Motion.movingHorizontally(player) && !player.isSprinting()) {
			player.setSprinting(true);
			if (spoofPacket.get()) {
				player.connection.send(new ServerboundPlayerCommandPacket(player,
						ServerboundPlayerCommandPacket.Action.START_SPRINTING));
			}
		}
	}
}
