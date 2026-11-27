package nz.temple.client.modules.client;

import nz.temple.module.Category;
import nz.temple.module.Module;

/** The client's looks: the HUD theme and its colour, how tinted the HUD glass is, and the menu style. */
public final class ThemesModule extends Module {
	public ThemesModule(HudModule hud) {
		super("Themes", Category.CLIENT, "Pick a look for the client: the HUD theme, its glass and the menu style. The theme tiles are "
				+ "also under Settings → Themes in the menu.", true);
		add(hud.visualThemeSetting());
		add(hud.hudTheme);
		add(hud.hudColor);
		add(hud.panelTint);
		add(hud.hudStyle);
	}
}
