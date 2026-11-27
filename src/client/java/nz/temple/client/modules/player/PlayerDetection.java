package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PlayerDetection extends Module {
	private final NumberSetting range;
	private Set<UUID> nearby = new HashSet<>();
	private ClientLevel level;
	public PlayerDetection() {
		super("Player Detection", Category.PLAYER, "Sends a local chat alert when a loaded player enters your detection radius. No webhooks "
				+ "or server commands.");
		range = add(new NumberSetting("Range", 96, 16, 256, 8, " m"));
		on(TickEvent.class, e -> tick());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) { nearby.clear(); level = null; return; }
		if (level != mc.level) { nearby.clear(); level = mc.level; }
		Set<UUID> seen = new HashSet<>();
		for (var player : mc.level.players()) {
			if (player == mc.player || mc.player.distanceToSqr(player) > range.get() * range.get()) continue;
			seen.add(player.getUUID());
			if (!nearby.contains(player.getUUID())) nz.temple.client.compat.Players.message(mc.player, Component.literal(
					"Player nearby: " + player.getName().getString() + " (" + Math.round(mc.player.distanceTo(player)) + " m)"));
		}
		nearby = seen;
	}
	@Override protected void onDisable() { nearby.clear(); level = null; }
}
