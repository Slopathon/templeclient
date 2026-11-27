package nz.temple.setting;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

public final class SettingHistory {
	private static final class Entry {
		final Object key;
		final String label;
		Runnable undo;
		Runnable redo;
		long at;
		Entry(Object key, String label, Runnable undo, Runnable redo, long at) {
			this.key = key;
			this.label = label;
			this.undo = undo;
			this.redo = redo;
			this.at = at;
		}
	}
	private static final int LIMIT = 200;
	static final long MERGE_MILLIS = 700;

	private static final Deque<Entry> UNDO = new ArrayDeque<>();
	private static final Deque<Entry> REDO = new ArrayDeque<>();
	private static boolean recording;
	private static boolean applying;
	private static LongSupplier clock = System::currentTimeMillis;
	private SettingHistory() {
	}

	public static void start() {
		recording = true;
	}
	public static void stop() {
		recording = false;
	}
	public static boolean isApplying() {
		return applying;
	}

	static void useClock(LongSupplier c) {
		clock = c;
	}

	public static void clear() {
		UNDO.clear();
		REDO.clear();
	}
	static <T> void changed(Setting<T> setting, T before, T after) {
		if (!recording || applying) {
			return;
		}
		long now = clock.getAsLong();
		Entry top = UNDO.peek();
		if (top != null && top.key == setting && now - top.at < MERGE_MILLIS) {
			top.redo = () -> setting.set(after);
			top.at = now;
			REDO.clear();
			return;
		}
		push(new Entry(setting, setting.name(), () -> setting.set(before), () -> setting.set(after), now));
	}
	public static void record(Object key, String label, Runnable undo, Runnable redo) {
		if (!recording || applying) {
			return;
		}
		push(new Entry(key, label, undo, redo, clock.getAsLong()));
	}
	private static void push(Entry e) {
		UNDO.push(e);
		while (UNDO.size() > LIMIT) {
			UNDO.removeLast();
		}
		REDO.clear();
	}
	public static String undo() {
		return move(UNDO, REDO, true);
	}
	public static String redo() {
		return move(REDO, UNDO, false);
	}
	private static String move(Deque<Entry> from, Deque<Entry> to, boolean undoing) {
		Entry e = from.poll();
		if (e == null) {
			return null;
		}
		applying = true;
		try {
			(undoing ? e.undo : e.redo).run();
		} finally {
			applying = false;
		}
		e.at = Long.MIN_VALUE / 2;
		to.push(e);
		return e.label;
	}
}
