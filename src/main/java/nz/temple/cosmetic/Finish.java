package nz.temple.cosmetic;

public record Finish(String name, float roughness, float metalness, float transmission, float subsurface, float sheen,
		float clearcoat, float iridescence, int[] ramp, int legacy) {
	public static final int NOT_LEGACY = -1;
	public Finish {
		if (ramp == null || ramp.length != 3) {
			throw new IllegalArgumentException("A finish needs a three-stop colour ramp: " + name);
		}
	}
	public static Finish of(String name, float roughness, float metalness, int dark, int mid, int light) {
		return new Finish(name, roughness, metalness, 0, 0, 0, 0, 0, new int[] {dark, mid, light}, NOT_LEGACY);
	}
	public Finish transmission(float amount) {
		return new Finish(name, roughness, metalness, amount, subsurface, sheen, clearcoat, iridescence, ramp, legacy);
	}

	public Finish subsurface(float amount) {
		return new Finish(name, roughness, metalness, transmission, amount, sheen, clearcoat, iridescence, ramp, legacy);
	}

	public Finish sheen(float amount) {
		return new Finish(name, roughness, metalness, transmission, subsurface, amount, clearcoat, iridescence, ramp, legacy);
	}

	public Finish clearcoat(float amount) {
		return new Finish(name, roughness, metalness, transmission, subsurface, sheen, amount, iridescence, ramp, legacy);
	}

	public Finish iridescence(float amount) {
		return new Finish(name, roughness, metalness, transmission, subsurface, sheen, clearcoat, amount, ramp, legacy);
	}

	public boolean isLegacy() {
		return legacy != NOT_LEGACY;
	}

	public int natural(double t) {
		t = Math.max(0, Math.min(1, t));
		return t < 0.5 ? Colours.lerp(ramp[0], ramp[1], t * 2) : Colours.lerp(ramp[1], ramp[2], (t - 0.5) * 2);
	}
	public int tinted(double t, int tint) {
		int n = natural(t);
		double lum = Colours.luminance(n);
		double tintLum = Math.max(0.05, Colours.luminance(tint));
		int scaled = Colours.scale(tint, Math.min(2.2, lum / tintLum));
		return Colours.lerp(scaled, n, 0.15);
	}
}
