package nz.temple.client.util;

/** Colour parsing for ported modules. Colours come from Temple ColorSettings (which follow Temple's theme by default). */
public final class ColorStrings {
	private ColorStrings() {
	}

	public static int resolveRgb(Object value, int fallback, boolean secondary) {
		if (value == null) {
			return fallback;
		}
		String s = String.valueOf(value).trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		try {
			return (int) (Long.parseLong(s, 16) & 0xFFFFFF);
		} catch (NumberFormatException e) {
			return secondary ? nz.temple.client.render.Theme.secondary() & 0xFFFFFF : nz.temple.client.render.Theme.primary() & 0xFFFFFF;
		}
	}
}
