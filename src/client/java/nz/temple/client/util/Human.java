package nz.temple.client.util;

import java.util.concurrent.ThreadLocalRandom;

public final class Human {
	private Human() {
	}
	public static double around(double mean, double sd, double min, double max) {
		return Math.max(min, Math.min(max, mean + ThreadLocalRandom.current().nextGaussian() * sd));
	}
	public static long reactionMs() {
		return (long) around(200, 45, 120, 350);
	}
	public static int reactionTicks() {
		return (int) Math.round(reactionMs() / 50.0);
	}
	public static long clickGapMs() {
		return (long) around(150, 40, 80, 300);
	}
	public static int ticks(int min, int max) {
		return (int) Math.round(around((min + max) / 2.0, (max - min) / 4.0, min, max));
	}
	public static boolean chance(double p) {
		return ThreadLocalRandom.current().nextDouble() < p;
	}
}
