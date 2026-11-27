package nz.temple.client.cosmetic;

import nz.temple.client.modules.cosmetic.Look;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.cosmetic.Colours;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.Setting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class GlowLook {
	private final ColorSetting colour;
	private final NumberSetting brightness;
	public GlowLook(Consumer<Setting<?>> add, String piece, BooleanSupplier visible) {
		colour = new ColorSetting(piece + " colour", 0xFFE9B949).themed()
				.describe("Colour of the light. Follows the theme until you pick one.")
				.visibleWhen(visible);
		brightness = new NumberSetting(piece + " brightness", 85, 10, 100, 5, "%")
				.describe("How bright and opaque the light is.")
				.visibleWhen(visible);
		add.accept(colour);
		add.accept(brightness);
	}
	public int colour(double t) {
		int c = colour.get() | 0xFF000000;
		if (colour.followsTheme()) {
			c = Colours.vivid(c);
		}
		return Theme.flow(c, Colours.lerp(c, 0xFFFFFFFF, 0.35), t);
	}
	public int colour(double t, double fade) {
		return Fx.withAlpha(colour(t), (float) (255 * Math.max(0, Math.min(1, fade)) * alpha()));
	}
	public float alpha() {
		return brightness.getFloat() / 100f * Theme.intensity();
	}
	public void markGlow() {
		Look.markGlow();
	}
}
