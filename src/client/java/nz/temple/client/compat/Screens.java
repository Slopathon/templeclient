package nz.temple.client.compat;

import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;

/** Vanilla screens whose constructors changed between versions. */
public final class Screens {
	private Screens() {
	}

	/** Vanilla's Options screen. From 26.3 it works out by itself whether a world is open, so {@code inWorld} goes unused. */
	public static OptionsScreen options(Screen parent, Options options, boolean inWorld) {
		//? if >=26.3 {
		/*return new OptionsScreen(parent, options);
		*///?} else if >=26 {
		return new OptionsScreen(parent, options, inWorld);
		//?} else {
		/*return new OptionsScreen(parent, options);
		*///?}
	}
}
