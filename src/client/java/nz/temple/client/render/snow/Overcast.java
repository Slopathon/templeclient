package nz.temple.client.render.snow;

public final class Overcast {
	private static final float STRENGTH = 0.85f;
	private static final double EASE_SECONDS = 4;
	private static volatile float target;
	private static float amount;
	private static long last = System.nanoTime();
	private Overcast() {
	}
	public static void want(boolean overcast) {
		target = overcast ? STRENGTH : 0;
	}

	public static synchronized float amount() {
		long now = System.nanoTime();
		double dt = Math.min(1, (now - last) / 1e9);
		last = now;
		float t = target;
		float step = (float) (dt / EASE_SECONDS) * STRENGTH;
		amount = amount < t ? Math.min(t, amount + step) : Math.max(t, amount - step);
		return amount;
	}
	public static boolean active() {
		return target > 0 || amount > 0.001f;
	}
}
