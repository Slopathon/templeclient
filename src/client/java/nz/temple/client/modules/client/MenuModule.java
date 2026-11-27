package nz.temple.client.modules.client;

import nz.temple.client.TempleNzClient;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class MenuModule extends Module {
	private final NumberSetting size;
	private final BoolSetting glassButtons;
	private final BoolSetting tooltips;
	private final BoolSetting hidePauseIcons;
	private final BoolSetting transitions;
	private final BoolSetting smoothScroll;
	private final ModeSetting theme;
	private final BoolSetting themedSettings;
	private final ModeSetting sceneQuality;
	private final ModeSetting clickGui;
	private final BoolSetting showHvh;
	private final BoolSetting sounds;
	private final NumberSetting soundVolume;

	public MenuModule() {
		super("Menu", Category.CLIENT, "Appearance and behaviour of menus and screens: menu theme, click GUI size, restyled Minecraft "
				+ "buttons and tooltips, and screen transitions.", true);
		group("Theme");
		theme = add(new ModeSetting("Menu theme", List.of("Default", "Landscape", "Black hole"), "Landscape")
				.describe("Default keeps Minecraft's title screen. Landscape uses Temple's title screen with an animated scene beside the "
						+ "menu. Black hole uses an animated black hole behind the menu. Screens opened from the title screen use the same "
						+ "background, blurred."));
		sceneQuality = add(new ModeSetting("Scene quality", List.of("High", "Low"), "High")
				.describe("High renders the title background at full detail. Low is lighter for older graphics cards.")
				.visibleWhen(() -> !theme.is("Default")));
		themedSettings = add(new BoolSetting("Themed settings", true)
				.describe("Replaces Minecraft's Options screen with Temple's layout and uses the client font for vanilla buttons, sliders "
						+ "and switches."));
		group("Click GUI");
		clickGui = add(new ModeSetting("Click GUI style", List.of("Modern", "Legacy"), "Modern")
				.describe("Modern: one window with a category sidebar and the modules as cards. Legacy: the floating glass tabs."));
		showHvh = add(new BoolSetting("Show HvH tab", true)
				.describe("Shows the HvH tab (blatant modules such as Fly, Reach and Crystal Aura) in the click GUI. Hiding it does "
						+ "not turn any module off."));
		size = add(new NumberSetting("Size", 65, 50, 100, 5, "%")
				.describe("Scales all click GUI windows. The menu keeps the same size regardless of Minecraft's GUI scale; this sets what "
						+ "that size is."));
		sounds = add(new BoolSetting("Menu sounds", true)
				.describe("Click sounds for buttons and module toggles, and ticks when dragging sliders."));
		soundVolume = add(new NumberSetting("Menu sound volume", 40, 5, 100, 5, "%")
				.describe("Volume of menu sounds, relative to Minecraft's master volume.")
				.visibleWhen(sounds::get));
		group("Minecraft's menus");
		glassButtons = add(new BoolSetting("Glass buttons", true)
				.describe("Restyles Minecraft's buttons, sliders, checkboxes and lists to match the client."));
		tooltips = add(new BoolSetting("Glass tooltips", true)
				.describe("Draws Minecraft's tooltips in the client's style instead of the vanilla purple box."));
		hidePauseIcons = add(new BoolSetting("Hide pause icons", true)
				.describe("Removes the row of small icon buttons (bug reports, feedback, friends, player reporting) from the pause menu."));
		transitions = add(new BoolSetting("Screen transitions", true)
				.describe("Fades screens in when they open."));
		smoothScroll = add(new BoolSetting("Smooth scrolling", true)
				.describe("Scrolls smoothly instead of jumping a fixed amount per notch."));
	}

	private static MenuModule get() {
		if (nz.temple.client.render.streamproof.StreamproofRenderer.cleanPass()) {
			return null;
		}
		var modules = TempleNzClient.modules();
		return modules == null ? null : modules.get(MenuModule.class);
	}

	public static float soundVolume() {
		MenuModule m = get();
		return m == null || !m.sounds.get() ? 0 : m.soundVolume.getFloat() / 100f;
	}

	public double scale() {
		return size.get() / 100.0;
	}

	/** Whether the menu key opens the Modern GUI (otherwise the Legacy glass-tab GUI). */
	public boolean modernGui() {
		return clickGui.is("Modern");
	}

	public boolean showHvhTab() {
		return showHvh.get();
	}

	public void setClickGui(String style) {
		clickGui.trySet(style);
	}

	public boolean glassButtons() {
		return glassButtons.get();
	}

	public static boolean styledTooltips() {
		MenuModule m = get();
		return m != null && m.tooltips.get();
	}

	public static boolean glassWidgets() {
		MenuModule m = get();
		return m != null && m.glassButtons.get();
	}

	public static boolean hidePauseIcons() {
		MenuModule m = get();
		return m != null && m.hidePauseIcons.get();
	}

	public static boolean transitions() {
		MenuModule m = get();
		return m != null && m.transitions.get();
	}

	public static boolean smoothScrolling() {
		MenuModule m = get();
		return m != null && m.smoothScroll.get();
	}

	public static boolean uncappedMenus() {
		return get() != null;
	}

	public static float transitionMillis() {
		return 900;
	}

	public static String theme() {
		MenuModule m = get();
		return m == null ? null : m.theme.get();
	}

	public static boolean templeTitle() {
		MenuModule m = get();
		return m != null && !m.theme.is("Default");
	}

	public static void swapScene() {
		MenuModule m = get();
		if (m != null) {
			m.theme.trySet(m.theme.is("Black hole") ? "Landscape" : "Black hole");
		}
	}

	public static boolean blackHole() {
		MenuModule m = get();
		return m != null && m.theme.is("Black hole");
	}

	public static boolean themedSettings() {
		MenuModule m = get();
		return m != null && m.themedSettings.get();
	}

	public static boolean sceneHighQuality() {
		MenuModule m = get();
		return m == null || m.sceneQuality.is("High");
	}
}
