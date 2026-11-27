package nz.temple.cosmetic;

public final class SurfacePacking {
	private SurfacePacking() {
	}
	public static int finishAndPattern(int finish, int pattern) {
		return (finish & 0xFF) | (pattern & 0xFF) << 8;
	}
	public static int accentAndOcclusion(double accent, double occlusion) {
		return byteOf(accent) | byteOf(occlusion) << 8;
	}
	public static float accentColour(int rgb) {
		return (float) (rgb & 0xFFFFFF);
	}
	public static int finish(int packed) {
		return packed & 0xFF;
	}
	public static int pattern(int packed) {
		return (packed >> 8) & 0xFF;
	}

	public static double accent(int packed) {
		return (packed & 0xFF) / 255.0;
	}

	public static double occlusion(int packed) {
		return ((packed >> 8) & 0xFF) / 255.0;
	}

	public static int accentColour(float packed) {
		return Math.round(packed) & 0xFFFFFF;
	}

	public static int throughShort(int packed) {
		return ((short) packed) & 0xFFFF;
	}
	private static int byteOf(double v) {
		return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
	}
}
