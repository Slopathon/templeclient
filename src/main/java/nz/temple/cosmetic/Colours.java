package nz.temple.cosmetic;

public final class Colours {
	private Colours() {
	}

	public static int lerp(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (int) Math.round(aa + (ba - aa) * t) << 24 | (int) Math.round(ar + (br - ar) * t) << 16
				| (int) Math.round(ag + (bg - ag) * t) << 8 | (int) Math.round(ab + (bb - ab) * t);
	}

	public static double luminance(int c) {
		return (0.2126 * ((c >> 16) & 0xFF) + 0.7152 * ((c >> 8) & 0xFF) + 0.0722 * (c & 0xFF)) / 255.0;
	}

	public static int scale(int c, double k) {
		int r = (int) Math.min(255, Math.round(((c >> 16) & 0xFF) * k));
		int g = (int) Math.min(255, Math.round(((c >> 8) & 0xFF) * k));
		int b = (int) Math.min(255, Math.round((c & 0xFF) * k));
		return (c & 0xFF000000) | r << 16 | g << 8 | b;
	}

	public static int withAlpha(int c, double alpha) {
		int a = (int) Math.max(0, Math.min(255, Math.round(alpha * 255)));
		return a << 24 | (c & 0xFFFFFF);
	}

	public static int vivid(int c) {
		int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
		int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		if (max - min < 10) {
			return c;
		}
		double span = max - min;
		double low = 0.25;
		int nr = (int) Math.round(255 * (low + (1 - low) * (r - min) / span));
		int ng = (int) Math.round(255 * (low + (1 - low) * (g - min) / span));
		int nb = (int) Math.round(255 * (low + (1 - low) * (b - min) / span));
		return (c & 0xFF000000) | nr << 16 | ng << 8 | nb;
	}
}
