package nz.temple.client.modules.player;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import nz.temple.client.TempleNzClient;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

public final class AutoRelog extends Module {
	private final NumberSetting level;
	private final NumberSetting delay;
	private final BoolSetting overworldOnly;
	private static boolean armed = true;
	private ServerData server;
	private String world;
	private long rejoinAt;
	public AutoRelog() {
		super("Auto Relog", Category.PLAYER, "Disconnects and rejoins when you reach a set height (Y -5 by default), once per descent. "
				+ "Useful when digging down.");
		level = add(new NumberSetting("At or below Y", -5, -64, 320, 1, "")
				.describe("Relogs when your feet reach this height or lower."));
		delay = add(new NumberSetting("Rejoin after", 2, 0, 30, 0.5, " s").describe("How long to wait before rejoining."));
		overworldOnly = add(new BoolSetting("Overworld only", true)
				.describe("Only applies in the Overworld, since the Nether starts at Y 0."));
		on(TickEvent.class, e -> tick());
		ClientTickEvents.END_CLIENT_TICK.register(this::rejoinTick);
	}
	@Override
	protected void onEnable() {
		armed = true;
		rejoinAt = 0;
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || rejoinAt != 0) {
			return;
		}
		if (overworldOnly.get() && mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
			return;
		}
		double y = mc.player.getY();
		if (y > level.get() + 1) {
			armed = true;
			return;
		}
		if (!armed || y > level.get()) {
			return;
		}
		armed = false;
		if (mc.getSingleplayerServer() != null) {
			var root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			world = root.getFileName() == null ? null : root.getFileName().toString();
			server = null;
		} else if (mc.getCurrentServer() != null) {
			ServerData current = mc.getCurrentServer();
			server = new ServerData(current.name, current.ip, current.type());
			server.copyFrom(current);
			world = null;
		} else {
			return;
		}
		TempleNzClient.LOGGER.info("Auto Relog: reached y {}, relogging", Math.round(y));
		rejoinAt = System.currentTimeMillis() + Math.round(delay.get() * 1000);
		AutoReconnect.leftOnPurpose();
		mc.disconnectFromWorld(Component.literal("Temple.nz Auto Relog: reached y " + Math.round(y) + ", rejoining"));
	}
	private long confirmUntil;
	private void rejoinTick(Minecraft mc) {
		if (confirmUntil != 0 && System.currentTimeMillis() < confirmUntil
				&& mc.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen screen) {

			for (var child : screen.children()) {
				if (child instanceof net.minecraft.client.gui.components.AbstractButton button
						&& button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents key
						&& key.getKey().equals("selectWorld.backupJoinSkipButton")) {
					confirmUntil = 0;
					button.onPress(null);
					return;
				}
			}
		}
		if (rejoinAt == 0 || System.currentTimeMillis() < rejoinAt || mc.level != null) {
			return;
		}
		rejoinAt = 0;
		TempleNzClient.LOGGER.info("Auto Relog: rejoining {} (enabled {})", world != null ? "world " + world : "server", isEnabled());
		if (!isEnabled()) {
			return;
		}
		if (world != null) {
			String id = world;
			world = null;
			confirmUntil = System.currentTimeMillis() + 15_000;
			mc.createWorldOpenFlows().openWorld(id, () -> mc.gui.setScreen(new TitleScreen()));
		} else if (server != null) {
			ServerData target = server;
			server = null;
			ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(target.ip), target, false, null);
		}
	}
	public boolean pending() {
		return rejoinAt != 0;
	}
}
