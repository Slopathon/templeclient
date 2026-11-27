package nz.temple.client.util;

import net.minecraft.client.player.LocalPlayer;

import java.util.function.Function;

public final class SilentShot {
	private static volatile Function<LocalPlayer, float[]> aim;
	private SilentShot() {
	}

	public static void setAim(Function<LocalPlayer, float[]> source) {
		aim = source;
	}
	public static float[] aim(LocalPlayer player) {
		Function<LocalPlayer, float[]> source = aim;
		return source == null ? null : source.apply(player);
	}
}
