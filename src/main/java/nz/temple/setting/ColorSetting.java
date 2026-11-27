package nz.temple.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;
import java.util.function.IntUnaryOperator;

/**
 * A colour. Any colour can follow the theme instead of holding its own value: it then reads the theme's main colour (or its
 * second colour, for settings made with {@link #themedSecondary()}), so modules match the menus and the HUD. Picking a
 * colour stops it following; choosing the theme again resumes.
 */
public final class ColorSetting extends Setting<Integer> {
	public static final int[] SWATCHES = {0xFFFF5A5A, 0xFFFFB340, 0xFFFFD60A, 0xFF34C759, 0xFF5AC8FA, 0xFFAF52DE, 0xFFFFFFFF};
	private static final String THEME = "theme";
	/** Stands for "follows the theme" in compact config codes: no real colour is fully transparent and this dark. */
	private static final int THEME_CODE = 0x00000001;

	private static IntUnaryOperator palette;

	/** The theme colour followed by default: 0 for none, 1 for the main colour, 2 for the second. */
	private int defaultRole;
	private int role = 1;
	private boolean following;
	private boolean themeable = true;

	private static final int GOLD = 0xE9B949;
	private static final int GOLD_LIGHT = 0xFFE3A1;

	public ColorSetting(String name, int defaultArgb) {
		super(name, defaultArgb);
		int rgb = defaultArgb & 0xFFFFFF;
		defaultRole = rgb == GOLD ? 1 : rgb == GOLD_LIGHT ? 2 : 0;
		role = Math.max(1, defaultRole);
		following = defaultRole != 0;
	}

	/** Starts out as its own colour (it can still be set to follow the theme). */
	public ColorSetting fixed() {
		defaultRole = 0;
		role = 1;
		following = false;
		return this;
	}

	/** Never follows the theme: for the colours the theme itself is made from. */
	public ColorSetting noTheme() {
		fixed();
		themeable = false;
		return this;
	}

	public static void palette(IntUnaryOperator source) {
		palette = source;
	}

	public ColorSetting themed() {
		defaultRole = 1;
		role = 1;
		following = true;
		return this;
	}

	public ColorSetting themedSecondary() {
		defaultRole = 2;
		role = 2;
		following = true;
		return this;
	}

	public boolean followsTheme() {
		return following && themeable;
	}

	/** Whether this colour may follow the theme. */
	public boolean canFollowTheme() {
		return themeable;
	}

	/** Makes the colour follow the theme from now on. */
	public void followTheme() {
		if (themeable && !following) {
			following = true;
			fireChanged();
		}
	}

	@Override
	public Integer get() {
		IntUnaryOperator source = palette;
		if (followsTheme() && source != null) {
			return value & 0xFF000000 | source.applyAsInt(role) & 0xFFFFFF;
		}
		return value;
	}

	@Override
	public void set(Integer newValue) {
		boolean was = following;
		if (newValue != null) {
			following = false;
		}
		super.set(newValue);
		if (was && !following && newValue != null && newValue.equals(value)) {
			fireChanged();
		}
	}

	@Override
	public void reset() {
		super.set(defaultValue());
		following = defaultRole != 0 && themeable;
	}

	@Override
	public boolean isDefaultValue() {
		return defaultRole != 0 ? following : !following && super.isDefaultValue();
	}

	@Override
	protected Integer validate(Integer candidate) {
		return candidate;
	}

	@Override
	public JsonElement toJson() {
		if (followsTheme()) {
			return new JsonPrimitive(THEME);
		}
		return new JsonPrimitive(String.format(Locale.ROOT, "#%08X", value));
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
			return;
		}
		String s = json.getAsString();
		if (THEME.equals(s)) {
			following = themeable;
			return;
		}
		if (s.length() == 9 && s.charAt(0) == '#') {
			try {
				int parsed = (int) Long.parseLong(s.substring(1), 16);
				if (defaultRole != 0 && parsed == defaultValue()) {
					reset();
				} else {
					set(parsed);
				}
			} catch (NumberFormatException ignored) {
			}
		}
	}

	@Override
	public void writeCompact(java.io.DataOutput out) throws java.io.IOException {
		out.writeInt(followsTheme() ? THEME_CODE : value);
	}

	@Override
	public void readCompact(java.io.DataInput in) throws java.io.IOException {
		int v = in.readInt();
		if (v == THEME_CODE) {
			following = themeable;
		} else {
			set(v);
		}
	}

	@Override
	public boolean trySet(String raw) {
		String s = raw.trim().replace("#", "");
		if (THEME.equalsIgnoreCase(s)) {
			if (!themeable) {
				return false;
			}
			following = true;
			fireChanged();
			return true;
		}
		try {
			long v = Long.parseLong(s, 16);
			if (s.length() <= 6) {
				v |= 0xFF000000L;
			}
			set((int) v);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	@Override
	public String describeValue() {
		if (followsTheme()) {
			return "theme";
		}
		return String.format(java.util.Locale.ROOT, "#%08X", value);
	}
}
