package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public final class Wearer {
	private static volatile Player mirror;
	private Wearer() {
	}
	public static boolean mine(Entity entity) {
		if (entity == null) {
			return false;
		}
		Player m = mirror;
		return entity == Minecraft.getInstance().player || entity == m && !m.isRemoved();
	}
	public static List<Player> selves() {
		Minecraft mc = Minecraft.getInstance();
		Player m = mirror;
		if (mc.player == null) {
			return List.of();
		}
		if (m == null || m.isRemoved() || m.level() != mc.player.level()) {
			return List.of(mc.player);
		}
		List<Player> out = new ArrayList<>(2);
		out.add(mc.player);
		out.add(m);
		return out;
	}
	public static void mirror(Player dummy) {
		mirror = dummy;
	}
}
