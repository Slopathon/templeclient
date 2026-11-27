package nz.temple.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import nz.temple.TempleNz;
import nz.temple.module.Category;

public final class Icons {
	private static final Identifier ATLAS = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "textures/gui/icons.png");
	/** The atlas at full, half and quarter size (cells of 32, 16 and 8 pixels), so small icons stay smooth. */
	private static final Identifier[] LEVELS = {ATLAS,
			Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "textures/gui/icons_16.png"),
			Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "textures/gui/icons_8.png")};
	private static final int[] LEVEL_CELLS = {32, 16, 8};
	private static final int CELL = 32;
	private static final int COLS = 8;
	private static final int ATLAS_W = 256;
	private static final int ATLAS_H = 512;
	public enum Icon {
		CLOSE, CHEVRON_DOWN, CHEVRON_RIGHT, SHARE, DOWNLOAD, LIGHTBULB, SEARCH, SWORDS,
		SKULL, WIND, EYE, SPARKLES, PICKAXE, USER, SETTINGS, MOVE,
		CHECK, RESET, LAYOUT, SLIDERS, KEYBOARD, PLAY, COPY, PASTE,
		UNDO, INFO, GRIP, SHIELD, HEART, GAUGE, ROCKET, CROSSHAIR,
		PALETTE, REFRESH, SMILE, PACKAGE, TRASH, DROPLET, MAP,
		MENU_STYLE,
		PAUSE, SKIP_BACK, SKIP_FORWARD, MUSIC, GLOBE, SERVER, PLUS, PENCIL,
		LOG_OUT, ARROW_LEFT, USERS, MOUNTAIN, CLOCK, FOLDER, SIGNAL, LINK,
		SUN, MOON,
		DISCORD, MONITOR, NAVIGATION, CLOUD, WIFI, ZAP,
		STAR, LAYERS, CHEVRONS_UP_DOWN, PANEL_LEFT, LANGUAGES, MAXIMIZE, SPARKLE, PIPETTE,
		TIMER, LAYOUT_GRID, SETTINGS_2, LIST_FILTER, FOOTPRINTS, WAND, BOX, MAP_PIN,
		TYPE, HASH, TOGGLE, LIST, CIRCLE_DOT, SQUARE_CHECK, MINIMIZE, BELL
	}
	private Icons() {
	}

	public static void draw(GuiGraphicsExtractor g, Icon icon, float cx, float cy, float size, int argb) {
		if (size <= 0.5f || (argb >>> 24) == 0) {
			return;
		}
		int i = icon.ordinal();
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx - size / 2, cy - size / 2);
		pose.scale(size / CELL, size / CELL);
		blitCell(g, i, size, argb);
		pose.popMatrix();
	}
	/** Draws cell {@code i} into the unit-cell space set up by the caller, from the atlas level that fits its size. */
	private static void blitCell(GuiGraphicsExtractor g, int i, float size, int argb) {
		int level = MipLevels.pick(LEVEL_CELLS, MipLevels.screenPixels(g, CELL));
		int cell = LEVEL_CELLS[level];
		int atlasW = ATLAS_W * cell / CELL;
		int atlasH = ATLAS_H * cell / CELL;
		g.pose().scale((float) CELL / cell, (float) CELL / cell);
		g.blit(RenderPipelines.GUI_TEXTURED, LEVELS[level], 0, 0, (i % COLS) * cell, (i / COLS) * cell, cell, cell, cell, cell,
				atlasW, atlasH, argb);
	}
	public static void drawFlipped(GuiGraphicsExtractor g, Icon icon, float cx, float cy, float size, int argb) {
		if (size <= 0.5f || (argb >>> 24) == 0) {
			return;
		}
		int i = icon.ordinal();
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx + size / 2, cy + size / 2);
		pose.rotate((float) Math.PI);
		pose.scale(size / CELL, size / CELL);
		blitCell(g, i, size, argb);
		pose.popMatrix();
	}
	public static Icon of(Category category) {
		if (category == null) {
			return Icon.SEARCH;
		}
		return switch (category) {
			case COMBAT -> Icon.SWORDS;
			case HVH -> Icon.SKULL;
			case MOVEMENT -> Icon.WIND;
			case RENDER -> Icon.EYE;
			case COSMETICS -> Icon.SPARKLES;
			case WORLD -> Icon.PICKAXE;
			case PLAYER -> Icon.USER;
			case HUD -> Icon.LAYOUT;
			case CLIENT -> Icon.SETTINGS;
		};
	}
}
