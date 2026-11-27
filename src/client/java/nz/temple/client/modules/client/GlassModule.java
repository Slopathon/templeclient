package nz.temple.client.modules.client;

import nz.temple.client.render.GlassRenderer;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class GlassModule extends Module {
	private final NumberSetting bend;
	private final NumberSetting bendSize;
	private final NumberSetting flowSpeed;
	private final NumberSetting softness;
	private final NumberSetting stretch;
	private final NumberSetting edgeWidth;
	private final NumberSetting colorSplit;
	private final NumberSetting dim;
	private final ModeSetting preset;
	private final NumberSetting roundness;
	private final ColorSetting tint;
	private final NumberSetting tintAmount;
	private final NumberSetting shine;
	private final NumberSetting saturation;
	private final NumberSetting brightness;
	private final NumberSetting cursorLight;

	public GlassModule() {
		super("Glass", Category.CLIENT, "Settings for the glass used by the menu, HUD and Minecraft's screens.", true);
		preset = add(new ModeSetting("Preset", List.of("Custom", "Liquid", "Crystal", "Frosted", "Smoked", "Gilded"), "Custom")
				.describe("Applies a set of values to the sliders below. You can adjust them afterwards."));
		group();
		bend = add(new NumberSetting("Bend", 100, 0, 100, 1, "")
				.describe("How much the glass distorts what is behind it."));
		bendSize = add(new NumberSetting("Bend size", 20, 5, 200, 1, "")
				.describe("Size of the distortion. Low values give tight curves, high values give long waves."));
		flowSpeed = add(new NumberSetting("Flow speed", 100, 0, 100, 1, "")
				.describe("How fast the distortion moves. 0 stops it."));
		softness = add(new NumberSetting("Softness", 8, 0, 8, 0.5, "")
				.describe("Blur behind the glass. 0 is fully clear."));
		group();
		stretch = add(new NumberSetting("Stretch", 50, 0, 50, 1, "")
				.describe("How far the rounded edge stretches the image toward the rim."));
		edgeWidth = add(new NumberSetting("Edge width", 44, 8, 90, 1, "")
				.describe("Width of the curved edge on each glass panel."));
		colorSplit = add(new NumberSetting("Color split", 30, 0, 30, 1, "")
				.describe("Colour fringing along the edges."));
		dim = add(new NumberSetting("Dim", 0, 0, 40, 1, "")
				.describe("Darkens the glass to keep text readable over bright backgrounds."));
		group();
		roundness = add(new NumberSetting("Roundness", 100, 0, 200, 5, "%")
				.describe("Corner rounding on menu panels. 0 is square, 200 is fully rounded."));
		shine = add(new NumberSetting("Shine", 100, 0, 250, 5, "%")
				.describe("Brightness of the highlight along the edges."));
		cursorLight = add(new NumberSetting("Cursor light", 30, 0, 100, 5, "%")
				.describe("How much the edge highlight follows your cursor."));
		saturation = add(new NumberSetting("Saturation", 110, 0, 200, 5, "%")
				.describe("Colour saturation behind the glass. 0 is greyscale."));
		brightness = add(new NumberSetting("Brightness", 0, -50, 50, 1, "")
				.describe("Brightness behind the glass."));
		group();
		tint = add(new ColorSetting("Tint", 0xFFE9B949).themed()
				.describe("Tint colour."));
		tintAmount = add(new NumberSetting("Tint amount", 0, 0, 100, 1, "%")
				.describe("Tint strength. 0 is untinted."));
		preset.addListener(this::applyPreset);
		for (NumberSetting n : List.of(bend, bendSize, flowSpeed, softness, stretch, edgeWidth, colorSplit, dim, shine, saturation, tintAmount)) {
			n.addListener(this::markCustom);
		}
	}

	public GlassRenderer.Params params() {
		return new GlassRenderer.Params(bend.getFloat(), bendSize.getFloat(), flowSpeed.getFloat(), softness.getFloat(),
				stretch.getFloat(), edgeWidth.getFloat(), colorSplit.getFloat(), dim.getFloat(), tint.get(), tintAmount.getFloat(),
				shine.getFloat(), saturation.getFloat(), cursorLight.getFloat(), brightness.getFloat(), roundness.getFloat());
	}

	private boolean applying;

	private void markCustom() {
		if (!applying && !preset.is("Custom")) {
			applying = true;
			preset.set("Custom");
			applying = false;
		}
	}

	private void applyPreset() {
		if (applying) {
			return;
		}
		double[] v = switch (preset.get()) {
			case "Liquid" -> new double[] {100, 20, 100, 8, 50, 44, 30, 0, 100, 110, 0};
			case "Crystal" -> new double[] {25, 60, 40, 0, 50, 60, 30, 0, 170, 120, 0};
			case "Frosted" -> new double[] {40, 40, 60, 8, 30, 30, 8, 8, 70, 80, 0};
			case "Smoked" -> new double[] {70, 30, 60, 6, 45, 44, 15, 30, 80, 70, 0};
			case "Gilded" -> new double[] {80, 25, 80, 6, 50, 50, 20, 6, 150, 100, 28};
			default -> null;
		};
		if (v == null) {
			return;
		}
		applying = true;
		List<NumberSetting> order = List.of(bend, bendSize, flowSpeed, softness, stretch, edgeWidth, colorSplit, dim, shine, saturation, tintAmount);
		for (int i = 0; i < order.size(); i++) {
			order.get(i).set(v[i]);
		}
		if (preset.is("Gilded")) {
			tint.set(0xFFE9B949);
		}
		applying = false;
	}
}
