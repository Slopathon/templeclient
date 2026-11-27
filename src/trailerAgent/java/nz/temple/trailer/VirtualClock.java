package nz.temple.trailer;

public final class VirtualClock {
	private static final long START_NANOS = System.nanoTime();
	private static final long START_EPOCH_MS = System.currentTimeMillis();
	private static volatile boolean locked;
	private static volatile long offset;
	private static volatile long frozen;
	private VirtualClock() {
	}
	public static long nanoTime() {
		return locked ? frozen : System.nanoTime() + offset;
	}

	public static long currentTimeMillis() {
		return START_EPOCH_MS + (nanoTime() - START_NANOS) / 1_000_000L;
	}
	public static double adjustSeconds(double seconds) {
		return seconds + (nanoTime() - System.nanoTime()) / 1e9;
	}
	public static synchronized void lock() {
		if (!locked) {
			frozen = System.nanoTime() + offset;
			locked = true;
		}
	}

	public static synchronized void unlock() {
		if (locked) {
			offset = frozen - System.nanoTime();
			locked = false;
		}
	}

	public static synchronized void advanceTo(long nanos) {
		if (locked && nanos > frozen) {
			frozen = nanos;
		}
	}

	public static boolean locked() {
		return locked;
	}
}
