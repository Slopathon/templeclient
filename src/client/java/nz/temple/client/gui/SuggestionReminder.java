package nz.temple.client.gui;

import nz.temple.config.ConfigManager;

import java.util.function.LongSupplier;

public final class SuggestionReminder {
	private static final String USED_SECONDS = "suggestions.usedSeconds";
	private static final String LAST_SHOWN = "suggestions.lastShown";
	private static final long FLUSH_INTERVAL_MS = 60_000;

	private static final long REPEAT_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000;

	private final ConfigManager config;
	private final LongSupplier clock;
	private long lastTick;
	private long pendingMillis;

	public SuggestionReminder(ConfigManager config, LongSupplier clock) {
		this.config = config;
		this.clock = clock;
	}

	public void tick() {
		long now = clock.getAsLong();
		if (lastTick != 0) {
			pendingMillis += Math.max(0, Math.min(5_000, now - lastTick));
		}
		lastTick = now;
		if (pendingMillis >= FLUSH_INTERVAL_MS) {
			flush();
		}
	}

	public boolean menuOpened() {
		tick();
		flush();
		long now = clock.getAsLong();
		double used = config.getExtra(USED_SECONDS, 0);
		long lastShown = (long) config.getExtra(LAST_SHOWN, 0);
		if (lastShown > 0 && now - lastShown < REPEAT_INTERVAL_MS) {
			return false;
		}
		config.putExtra(LAST_SHOWN, now);
		return true;
	}

	public void flush() {
		if (pendingMillis == 0) {
			return;
		}
		config.putExtra(USED_SECONDS, config.getExtra(USED_SECONDS, 0) + pendingMillis / 1000.0);
		pendingMillis = 0;
	}
}
