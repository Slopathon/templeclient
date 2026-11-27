package nz.temple.client.modules.client;

import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class HudModule extends Module {
	private final ModeSetting visualTheme;

	public String visualTheme() {
		return visualTheme.get();
	}

	public ModeSetting visualThemeSetting() {
		return visualTheme;
	}

	public void setVisualTheme(String label) {
		visualTheme.trySet(label);
	}

	public static final int GOLD = 0xFFE9B949;
	public static final int GOLD_LIGHT = 0xFFFFE3A1;

	public final ModeSetting style;
	public final ModeSetting hudTheme;
	public final ModeSetting hudStyle;
	public final BoolSetting infoTps;
	public final BoolSetting infoLogo;
	public final BoolSetting infoCoords;
	public final BoolSetting infoDate;
	public final BoolSetting infoUser;
	public final BoolSetting lyricsBar;
	public final BoolSetting keystrokes;
	public final BoolSetting keysMouse;
	public final BoolSetting keysSpace;
	public final ModeSetting listSort;
	public final BoolSetting listHideVisuals;
	public final BoolSetting keybindsOnlyOn;
	public final NumberSetting keybindsMax;
	public final ColorSetting hudColor;
	public final NumberSetting panelTint;
	public final BoolSetting infoPing;
	public final BoolSetting infoServer;
	public final BoolSetting infoTime;
	public final BoolSetting twentyFourHour;
	public final ModeSetting themeMode;
	public final ColorSetting accent;
	public final ColorSetting accent2;
	public final NumberSetting themeSpeed;
	public final NumberSetting hudScale;
	public final NumberSetting effectIntensity;
	public final BoolSetting moduleList;
	public final BoolSetting visualReach;
	public final ModeSetting listStyle;
	public final BoolSetting moduleInfo;
	public final BoolSetting targetHud;
	public final ModeSetting targetPosition;
	public final BoolSetting targetArmor;
	public final BoolSetting targetEffects;
	public final ModeSetting blockInfoPosition;
	public final BoolSetting notifications;
	public final NumberSetting notificationScale;
	public final BoolSetting watermark;
	public final BoolSetting keybinds;
	public final BoolSetting potions;
	public final BoolSetting armor;
	public final BoolSetting coordinates;
	public final BoolSetting fps;
	public final BoolSetting speed;
	public final BoolSetting speedGraph;
	public final BoolSetting adaptLight;
	public final NumberSetting graphLength;
	public final BoolSetting elytraHud;
	public final BoolSetting enemyArmor;
	public final NumberSetting enemyCount;
	public final NumberSetting enemyRange;
	public final BoolSetting enemyFriends;

	public static final String[][] THEMES = {
			{"Temple gold", "E9B949", "FFE3A1"}, {"Rose", "FF5C8A", "FFC2D4"}, {"Ocean", "3FA9F5", "A6E1FF"},
			{"Emerald", "2FBF71", "B4F5CF"}, {"Violet", "9B6BFF", "D9C4FF"}, {"Crimson", "E5484D", "FF9E8A"},
			{"Sunset", "FF7A3D", "FFD36B"}, {"Aqua", "22C7B8", "A7F3E6"}, {"Ice", "7CC8FF", "EAF7FF"},
			{"Mono", "C9CED6", "FFFFFF"}};

	private int lastAccent = GOLD;

	public static int companion(int argb) {
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		r += (255 - r) * 55 / 100;
		g += (255 - g) * 55 / 100;
		b += (255 - b) * 55 / 100;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private void followAccent() {
		int second = accent2.get() & 0xFFFFFF;
		boolean automatic = second == (GOLD_LIGHT & 0xFFFFFF) || second == (companion(lastAccent) & 0xFFFFFF);
		for (String[] t : THEMES) {
			automatic |= Integer.parseInt(t[1], 16) == (lastAccent & 0xFFFFFF) && Integer.parseInt(t[2], 16) == second;
		}
		lastAccent = accent.get();
		if (automatic && (accent.get() & 0xFFFFFF) != (GOLD & 0xFFFFFF)) {
			accent2.set(companion(accent.get()));
		} else if (automatic) {
			accent2.set(GOLD_LIGHT);
		}
	}
	public void applyTheme(int index) {
		String[] t = THEMES[index];
		accent.set(0xFF000000 | Integer.parseInt(t[1], 16));
		accent2.set(0xFF000000 | Integer.parseInt(t[2], 16));
		if (themeMode.is("Rainbow")) {
			themeMode.set("Gradient");
		}
	}
	public int currentTheme() {
		for (int i = 0; i < THEMES.length; i++) {
			if ((accent.get() & 0xFFFFFF) == Integer.parseInt(THEMES[i][1], 16)
					&& (accent2.get() & 0xFFFFFF) == Integer.parseInt(THEMES[i][2], 16)) {
				return i;
			}
		}
		return -1;
	}
	public HudModule() {
		super("HUD", Category.CLIENT, "Theme colours and HUD elements. Use the Edit HUD button next to the menu search bar to move and "
				+ "resize widgets.", true);
		visualTheme = (new ModeSetting("Style", List.of("Onyx", "Glass", "Frost"), "Onyx")
				.describe("How the menus and the HUD look, without changing any colours: Onyx is matte black, Glass is dark blurred "
						+ "glass, Frost is lighter frosted glass. Colours come from the theme colour."));
		style = (new ModeSetting("HUD look", List.of("Glass", "Dark"), "Glass").visibleWhen(() -> false));
		hudStyle = new ModeSetting("HUD style", List.of("Glass", "Dark"), "Dark").visibleWhen(() -> false)
				.describe("Glass: frosted glass panels tinted by the HUD theme. Dark: solid dark pills and cards, with the theme colour on "
						+ "icons, keys and bars.");
		infoTps = new BoolSetting("TPS", true).describe("The server's ticks per second.");
		infoLogo = new BoolSetting("Logo", true).describe("The turning Temple logo.");
		infoCoords = new BoolSetting("Coords", false).describe("Your X, Y and Z.");
		infoDate = new BoolSetting("Date", false).describe("Today's date.");
		infoUser = new BoolSetting("Username", true).describe("Your name (with your head in the Glass style).");
		lyricsBar = new BoolSetting("Lyrics bar", true);
		keystrokes = new BoolSetting("Keystrokes", false);
		keysMouse = new BoolSetting("Mouse buttons", true).describe("Shows the left, middle and right mouse buttons.");
		keysSpace = new BoolSetting("Space bar", true).describe("Shows the space bar under the keys.");
		listSort = new ModeSetting("Sort", List.of("Length", "A-Z"), "Length").describe("Longest names first, or alphabetical.");
		listHideVisuals = new BoolSetting("Hide visuals", false).describe("Leaves render and cosmetic modules out of the list.");
		keybindsOnlyOn = new BoolSetting("Only enabled", false).describe("Lists only bound modules that are on.");
		keybindsMax = new NumberSetting("Max shown", 14, 3, 30, 1, "").describe("How many bound modules are listed at most.");
		hudTheme = (new ModeSetting("HUD theme", nz.temple.client.hud.HudTheme.NAMES, "White")
				.describe("The theme colour: a single colour or a gradient. It colours the HUD and the menus, and every module colour "
						+ "set to follow the theme. Custom uses the HUD colour below."));
		hudColor = (new ColorSetting("HUD colour", 0xFF2F9BFF).noTheme().describe("Main colour of the Custom HUD theme. The lighter "
				+ "and darker shades are worked out from it.").visibleWhen(() -> hudTheme.is("Custom")));
		panelTint = (new NumberSetting("Panel tint", 76, 0, 100, 2, "%")
				.describe("How strongly HUD panels are tinted and shaded. Lower is clearer glass, higher is darker and more coloured."));
		themeMode = add(new ModeSetting("Accent mode", List.of("Static", "Gradient", "Rainbow"), "Static").visibleWhen(() -> false)
				.describe("How accent colours are applied across the HUD, menu and effects. Gradient blends between the two accent colours."));
		accent = add(new ColorSetting("Accent color", GOLD).noTheme().describe("Main accent colour. Gold matches the Temple.nz logo.")
				.visibleWhen(() -> false));
		accent2 = add(new ColorSetting("Accent color 2", GOLD_LIGHT).noTheme().describe("Second colour used by the gradient.")
				.visibleWhen(() -> false));
		accent.addListener(this::followAccent);
		themeSpeed = add(new NumberSetting("Gradient speed", 1, 0.1, 4, 0.1, "x").describe("How fast a gradient theme flows.")
				.visibleWhen(() -> themeMode.is("Gradient")));
		hudScale = add(new NumberSetting("HUD scale", 100, 60, 150, 5, "%")
				.describe("Size of HUD widgets. This is independent of Minecraft's GUI scale."));
		visualReach = add(new BoolSetting("Visuals reach render distance", true)
				.describe("ESP, chams, tracers, nametags, item ESP and storage ESP reach at least your render distance, regardless of their "
						+ "own Range setting. Turn off to use each module's own range."));
		adaptLight = add(new BoolSetting("Adapt to light", true)
				.describe("Darkens glass widgets in bright daylight so white text stays readable, and lightens them at night and "
						+ "underground."));
		effectIntensity = add(new NumberSetting("Effect intensity", 100, 0, 150, 5, "%")
				.describe("Scales the strength of all visual effects at once, without changing each module's own brightness."));
		group();
		moduleList = (new BoolSetting("Module list", true)
				.describe("Lists enabled modules in the top-right corner."));
		listStyle = (new ModeSetting("List style", List.of("Chips", "Sidebar"), "Chips").visibleWhen(() -> false));
		moduleInfo = (new BoolSetting("Module info", true)
				.describe("Shows a short detail after some module names, such as how many chunks Chunk Finder has flagged.")
				.visibleWhen(moduleList::get));
		targetHud = (new BoolSetting("Target HUD", true)
				.describe("Shows your combat target's face, health, armour, distance and win chance."));
		targetPosition = (new ModeSetting("Target HUD placement", List.of("Fixed", "Follow target"), "Fixed")
				.describe("Fixed keeps the card just below your crosshair, or wherever you drag it in Edit HUD. "
						+ "Follow target keeps the card next to your target.")
				.visibleWhen(targetHud::get));
		targetArmor = (new BoolSetting("Armor on target card", true)
				.describe("Shows your target's armour and held items with durability next to the target card. Enemy armour then leaves your "
						+ "target out.")
				.visibleWhen(targetHud::get));
		targetEffects = (new BoolSetting("Target effects", true)
				.describe("Shows your target's potion effects, with level and remaining time when the server provides it.")
				.visibleWhen(targetHud::get));
		blockInfoPosition = add(new ModeSetting("Block info position", List.of("Follow block", "Fixed"), "Follow block")
				.describe("Where Block Highlight's info card goes. Follow block keeps it next to the block you are looking at. Fixed keeps "
						+ "it where you place it in Edit HUD."));
		notifications = (new BoolSetting("Notifications", true)
				.describe("Shows a notification when a module is turned on or off. Errors are always shown."));
		notificationScale = (new NumberSetting("Notification size", 55, 30, 150, 5, "%")
				.describe("Size of the notifications in the bottom-right corner."));

		watermark = (new BoolSetting("Info bar", true)
				.describe("A pill at the top of the screen with your head and name, FPS, ping, server and the time."));
		infoPing = (new BoolSetting("Ping", true).describe("Your ping, on servers.").visibleWhen(watermark::get));
		infoServer = (new BoolSetting("Server", true).describe("The server you are on.").visibleWhen(watermark::get));
		infoTime = (new BoolSetting("Time", true).describe("The time of day.").visibleWhen(watermark::get));
		twentyFourHour = (new BoolSetting("24 hour clock", true).describe("Shows 14:05 instead of 2:05 PM.")
				.visibleWhen(() -> watermark.get() && infoTime.get()));
		keybinds = (new BoolSetting("Keybinds", true)
				.describe("Lists bound modules with their keys and on/off state."));
		potions = (new BoolSetting("Potions", true)
				.describe("Active potion effects with level and remaining time."));
		armor = (new BoolSetting("Armor", true)
				.describe("Your armour and its durability above the hotbar."));
		coordinates = (new BoolSetting("Coordinates", true)
				.describe("A pill at the top with your X, Y and Z and an arrow that turns with you."));
		fps = (new BoolSetting("FPS", true)
				.describe("Your FPS, in the info bar."));
		speed = (new BoolSetting("Speed", false)
				.describe("Your speed in blocks per second, in the info bar."));

		speedGraph = (new BoolSetting("Speed graph", false)
				.describe("A graph of your speed over the last few seconds, with current and peak speed."));
		graphLength = (new NumberSetting("Graph length", 10, 3, 30, 1, " s").describe("How many seconds the graph covers.")
				.visibleWhen(speedGraph::get));
		elytraHud = (new BoolSetting("Elytra HUD", true)
				.describe("While gliding, shows speed, altitude, climb rate, elytra durability and remaining fireworks, and warns you "
						+ "before you hit the ground."));
		enemyArmor = (new BoolSetting("Enemy armor", false)
				.describe("Shows nearby players' armour and held items with durability, armour points, health and totem pops."));
		enemyCount = (new NumberSetting("Players shown", 3, 1, 6, 1, "").describe("How many nearby players are shown.")
				.visibleWhen(enemyArmor::get));
		enemyRange = (new NumberSetting("Enemy range", 32, 8, 128, 4, " m").describe("Maximum distance for a player to be shown.")
				.visibleWhen(enemyArmor::get));
		enemyFriends = (new BoolSetting("Include friends", false).describe("Include friends.")
				.visibleWhen(enemyArmor::get));
		// Every visual's accent follows the HUD theme, so trails, ESP and effects match the HUD (as in x.monolith's client).
		hudTheme.addListener(this::followHudTheme);
		hudColor.addListener(this::followHudTheme);
	}

	/** The theme colour feeds the accent colours that the menus, the HUD and every theme-following module colour read. */
	private void followHudTheme() {
		String name = hudTheme.get();
		if (nz.temple.client.hud.HudTheme.isGradient(name)) {
			int[] ends = nz.temple.client.hud.HudTheme.ends(name);
			accent.set(ends[0]);
			accent2.set(ends[1]);
			themeMode.set("Gradient");
		} else {
			int[] colours = nz.temple.client.hud.HudTheme.target(name, hudColor.get());
			accent.set(colours[0]);
			accent2.set(colours[1]);
			themeMode.set("Static");
		}
	}

	/** Puts the accent colours back in line with the theme colour if they drifted (an older config, a manual edit). */
	public void syncTheme() {
		String name = hudTheme.get();
		boolean gradient = nz.temple.client.hud.HudTheme.isGradient(name);
		int want = gradient ? nz.temple.client.hud.HudTheme.ends(name)[0] : nz.temple.client.hud.HudTheme.target(name, hudColor.get())[0];
		if ((accent.get() & 0xFFFFFF) != (want & 0xFFFFFF) || themeMode.is("Gradient") != gradient) {
			followHudTheme();
		}
	}

	/** Matte black HUD pills (the Onyx style) instead of glass. */
	public boolean darkHud() {
		return visualTheme.is("Onyx");
	}
}
