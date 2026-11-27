package nz.temple.client.modules.player;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import nz.temple.client.render.streamproof.StreamproofRenderer;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

public final class AutoReconnect extends Module {
	private final NumberSetting delay;
	private final NumberSetting attempts;
	private final BoolSetting growDelay;
	private ServerData lastServer;
	private int tries;
	private long deadline;
	private boolean cancelled;
	private Button countdown;
	private long joinedAt;
	private static volatile long leftOnPurpose;
	public static void leftOnPurpose() {
		leftOnPurpose = System.currentTimeMillis();
	}
	public AutoReconnect() {
		super("Auto Reconnect", Category.PLAYER, "Rejoins the server after a kick or disconnect. The countdown can be skipped or cancelled "
				+ "on the disconnect screen.");
		delay = add(new NumberSetting("Delay", 5, 1, 60, 1, " s").describe("How long to wait before rejoining."));
		attempts = add(new NumberSetting("Attempts", 0, 0, 50, 1, "").describe("Stops after this many failed attempts in a row. 0 keeps "
				+ "trying."));
		growDelay = add(new BoolSetting("Wait longer each try", true)
				.describe("Waits longer after each failed attempt (up to a minute) to avoid flooding a restarting server."));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			ServerData current = mc.getCurrentServer();
			if (current != null) {
				ServerData copy = new ServerData(current.name, current.ip, current.type());
				copy.copyFrom(current);
				lastServer = copy;
			}
			joinedAt = System.currentTimeMillis();
		});
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof DisconnectedScreen) {
				onDisconnected(mc, screen);
			}
		});
	}
	private void onDisconnected(Minecraft mc, Screen screen) {
		countdown = null;
		if (!isEnabled() || lastServer == null || System.currentTimeMillis() - leftOnPurpose < 10_000) {
			return;
		}
		if (System.currentTimeMillis() - joinedAt > 30_000) {
			tries = 0;
		}
		int max = (int) Math.round(attempts.get());
		if (max > 0 && tries >= max) {
			return;
		}
		cancelled = false;
		double wait = delay.get();
		if (growDelay.get()) {
			wait = Math.min(60, wait * Math.pow(1.5, tries));
		}
		deadline = System.currentTimeMillis() + Math.round(wait * 1000);
		int bottom = 0;
		for (AbstractWidget w : Screens.getWidgets(screen)) {
			bottom = Math.max(bottom, w.getY() + w.getHeight());
		}
		int y = bottom == 0 ? screen.height / 2 + 40 : bottom + 6;
		int x = screen.width / 2 - 100;
		countdown = Button.builder(label(), b -> reconnect(mc)).bounds(x, y, 150, 20).build();
		Button stop = Button.builder(Component.literal("Cancel"), b -> {
			cancelled = true;
			countdown.setMessage(Component.literal("Auto reconnect stopped"));
			countdown.active = false;
			b.active = false;
		}).bounds(x + 154, y, 46, 20).build();
		Screens.getWidgets(screen).add(StreamproofRenderer.hideFromRecording(countdown));
		Screens.getWidgets(screen).add(StreamproofRenderer.hideFromRecording(stop));
		ScreenEvents.afterTick(screen).register(s -> tick(mc, s));
	}
	private Component label() {
		long left = Math.max(0, deadline - System.currentTimeMillis());
		String server = lastServer.name == null || lastServer.name.isBlank() ? lastServer.ip : lastServer.name;
		return Component.literal("Rejoining " + server + " in " + (left + 999) / 1000 + "s");
	}
	private void tick(Minecraft mc, Screen screen) {
		if (countdown == null || cancelled || mc.gui.screen() != screen) {
			return;
		}
		if (!isEnabled()) {
			countdown.setMessage(Component.literal("Auto reconnect is off"));
			countdown.active = false;
			return;
		}
		countdown.setMessage(label());
		if (System.currentTimeMillis() >= deadline) {
			reconnect(mc);
		}
	}
	private void reconnect(Minecraft mc) {
		if (lastServer == null) {
			return;
		}
		tries++;
		countdown = null;
		Screen parent = new JoinMultiplayerScreen(new TitleScreen());
		ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(lastServer.ip), lastServer, false, null);
	}
}
