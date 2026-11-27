package nz.temple.client.util;

import net.minecraft.client.player.LocalPlayer;
import java.util.function.Predicate;

public final class UseSlowdown {
	private static volatile java.util.function.Function<LocalPlayer, Float> multiplier;
	private static volatile Predicate<LocalPlayer> unstuck;
	private UseSlowdown() {
	}
	public static void setMultiplier(java.util.function.Function<LocalPlayer, Float> source) {
		multiplier = source;
	}
	public static Float multiplier(LocalPlayer player) {
		java.util.function.Function<LocalPlayer, Float> source = multiplier;
		return source == null ? null : source.apply(player);
	}
	public static void setUnstuck(Predicate<LocalPlayer> check) {
		unstuck = check;
	}
	public static boolean unstuck(LocalPlayer player) {
		Predicate<LocalPlayer> check = unstuck;
		return check != null && check.test(player);
	}
}
