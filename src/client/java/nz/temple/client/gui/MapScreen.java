package nz.temple.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import nz.temple.client.TempleNzClient;
import nz.temple.client.map.MapRenderer;
import nz.temple.client.map.MapStore;
import nz.temple.client.modules.client.StreamerMode;
import nz.temple.client.modules.movement.ElytraAutopilot;
import nz.temple.client.modules.render.WaypointsModule;
import nz.temple.client.modules.render.WorldMap;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Sprites;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.client.util.ServerKey;
import nz.temple.client.util.Waypoints;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MapScreen extends Screen {
	private static final float MIN_SCALE = 1 / 64f;
	private static final float MAX_SCALE = 512;
	private static float savedScale = 1;
	private final Screen parent;
	private double centerX;
	private double centerZ;
	private float scale;
	private boolean follow = true;
	private boolean showList;
	private boolean showTrail = true;
	private boolean dragging;
	private double dragX;
	private double dragY;
	private double dragCX;
	private double dragCZ;
	private boolean moved;
	private double mouseX;
	private double mouseY;
	private float s = 1;
	private Menu menu;
	private Waypoints.Waypoint selected;

	private Waypoints.Waypoint renaming;
	private StringBuilder renameText;
	private final List<Button> buttons = new ArrayList<>();
	private record Button(float x0, float y0, float x1, float y1, Runnable action) {
	}
	private record Menu(float x, float y, double wx, double wz, Waypoints.Waypoint waypoint, List<String> labels, List<Runnable> actions) {
	}
	public MapScreen(Screen parent) {
		super(Component.literal("Map"));
		this.parent = parent;
		scale = savedScale;
	}
	@Override
	protected void init() {
		if (minecraft.player != null && follow) {
			centerX = minecraft.player.getX();
			centerZ = minecraft.player.getZ();
		}
	}
	@Override
	public void added() {
		nz.temple.client.compat.Input.startTyping(this);
	}
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	@Override
	public void onClose() {
		savedScale = scale;
		minecraft.gui.setScreen(parent);
	}
	private MapRenderer.View view() {
		return new MapRenderer.View(width / 2f, height / 2f, centerX, centerZ, scale, 0);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		Ui ui = TempleNzClient.ui();
		if (ui == null || minecraft.player == null) {
			return;
		}
		mouseX = mx;
		mouseY = my;
		s = UiScale.factor();
		buttons.clear();
		if (follow) {
			var at = minecraft.player.getPosition(delta);
			centerX = at.x;
			centerZ = at.z;
		}
		String dim = MapRenderer.dimension();
		MapRenderer.View view = view();
		int accent = 0xFF000000 | (Theme.primary() & 0xFFFFFF);
		g.fill(0, 0, width, height, 0xFF0B0D10);
		grid(g, view);

		MapRenderer.tiles(g, view, (float) Math.hypot(width, height) / 2 + 2, 400);
		if (showTrail) {
			MapRenderer.trail(g, view, MapStore.get().trail(dim), 0, 0, width, height, 0xFF000000 | (accent & 0xFFFFFF), 1.6f * s);
		}
		WorldMap map = WorldMap.instance();
		if (map == null || map.showPlayers()) {
			MapRenderer.players(g, view, 3 * s, 0xFFFF5A5F);
		}
		ui.begin(g);
		if (map == null || map.showPlayers()) {
			for (Player p : minecraft.level.players()) {
				if (p != minecraft.player) {
					float[] sp = view.toScreen(p.getX(), p.getZ());
					ui.textMiddle(p.getName().getString(), sp[0] + 5 * s, sp[1], 7.5f * s, 0xE6FFFFFF, Fonts.MEDIUM);
				}
			}
		}
		Waypoints.Waypoint hover = null;
		for (Waypoints.Waypoint w : Waypoints.visibleIn(dim)) {
			double[] p = w.in(dim);
			float[] sp = view.toScreen(p[0], p[2]);
			boolean over = Math.abs(mx - sp[0]) <= 7 * s && my <= sp[1] + 2 * s && my >= sp[1] - 20 * s;
			if (over) {
				hover = w;
			}
			boolean other = !w.dimension.equals(dim);
			float size = (w == selected || over ? 20 : 17) * s;
			float top = Sprites.marker(g, sp[0], sp[1], size, (other ? 0x99000000 : 0xFF000000) | (w.color & 0xFFFFFF));
			String label = w == renaming ? renameText + ((System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "") : w.name;
			WorldMap.label(g, ui, label, sp[0], top - 2 * s, 8.5f * s, s);
		}
		var at = minecraft.player.getPosition(delta);
		float[] me = view.toScreen(at.x, at.z);
		double[] target = ElytraAutopilot.targetIn(dim);
		if (target != null) {
			float[] tp = view.toScreen(target[0], target[1]);
			Draw2D.line(g, me[0], me[1], tp[0], tp[1], 1, 0x80FFFFFF);
			float r = 5 * s;
			Draw2D.outline(g, tp[0] - r, tp[1] - r, tp[0] + r, tp[1] + r, 1.5f, 0xFFFFFFFF);
		}
		Sprites.arrow(g, me[0], me[1], 18 * s, (float) Math.toRadians(minecraft.player.getViewYRot(delta) + 180), 0xFFFFFFFF);
		double[] cursor = view.toWorld(mx, my);
		String title = "Map  ·  " + dimensionName(dim);
		String sub = "Cursor " + StreamerMode.xz((int) Math.floor(cursor[0]), (int) Math.floor(cursor[1])) + "   ·   " + zoomLabel();
		float cardW = Math.max(ui.textWidth(title, Fonts.SEMIBOLD, 11 * s), ui.textWidth(sub, Fonts.MEDIUM, 8 * s)) + 24 * s;
		panel(g, 10 * s, 10 * s, cardW, 36 * s);
		ui.textMiddle(title, 22 * s, 22 * s, 11 * s, 0xFFFFFFFF, Fonts.SEMIBOLD);
		ui.textMiddle(sub, 22 * s, 36 * s, 8 * s, 0xB3FFFFFF, Fonts.MEDIUM);
		float bx = width - 10 * s;
		bx = button(g, ui, bx, "Close", this::onClose);
		bx = button(g, ui, bx, showList ? "Hide list" : "Waypoints", () -> showList = !showList);
		bx = button(g, ui, bx, showTrail ? "Hide path" : "Show path", () -> showTrail = !showTrail);
		bx = button(g, ui, bx, "Add here", () -> WaypointsModule.added(Waypoints.addHere(null)));
		button(g, ui, bx, follow ? "Following" : "Centre on me", () -> follow = true);
		if (showList) {
			list(g, ui, dim);
		}
		if (selected != null && Waypoints.here().contains(selected)) {
			details(g, ui, dim, selected);
		}
		if (menu != null) {
			drawMenu(g, ui);
		} else if (hover != null && hover != selected) {
			tip(g, ui, mx + 12, my + 12, hover.name, StreamerMode.xyz(hover.x, hover.y, hover.z) + "  ·  " + distanceTo(hover, dim));
		}
	}
	private void grid(GuiGraphicsExtractor g, MapRenderer.View v) {
		int step = scale >= 8 ? 1 : scale >= 0.5f ? 16 : 0;
		if (step == 0) {
			return;
		}
		int color = step == 1 ? 0x14FFFFFF : 0x10FFFFFF;
		double[] tl = v.toWorld(0, 0);
		double[] br = v.toWorld(width, height);
		int x0 = (int) Math.floor(tl[0] / step) * step;
		int z0 = (int) Math.floor(tl[1] / step) * step;
		for (int x = x0; x <= br[0]; x += step) {
			float sx = v.toScreen(x, 0)[0];
			g.fill(Math.round(sx), 0, Math.round(sx) + 1, height, color);
		}
		for (int z = z0; z <= br[1]; z += step) {
			float sy = v.toScreen(0, z)[1];
			g.fill(0, Math.round(sy), width, Math.round(sy) + 1, color);
		}
	}
	private String zoomLabel() {
		return scale >= 1 ? String.format(Locale.ROOT, "%s px per block", trim(scale))
				: String.format(Locale.ROOT, "1 px per %s blocks", trim(1 / scale));
	}
	private static String trim(float f) {
		return f == Math.round(f) ? Integer.toString(Math.round(f)) : String.format(Locale.ROOT, "%.1f", f);
	}

	private static String dimensionName(String dim) {
		return switch (dim) {
			case "overworld" -> "Overworld";
			case "the_nether" -> "Nether";
			case "the_end" -> "End";
			default -> dim;
		};
	}
	private String distanceTo(Waypoints.Waypoint w, String dim) {
		double[] p = w.in(dim);
		double d = Math.hypot(p[0] - minecraft.player.getX(), p[2] - minecraft.player.getZ());
		return d >= 1000 ? String.format(Locale.ROOT, "%.2f km", d / 1000) : Math.round(d) + " m";
	}
	private void panel(GuiGraphicsExtractor g, float x, float y, float w, float h) {

		Draw2D.pixelRound(g, x - 1, y - 1, x + w + 1, y + h + 1, 9 * s + 1, 0x24FFFFFF);
		Draw2D.pixelRound(g, x, y, x + w, y + h, 9 * s, 0xE6181B20);
	}
	private float button(GuiGraphicsExtractor g, Ui ui, float right, String label, Runnable action) {
		float h = 22 * s;
		float w = ui.textWidth(label, Fonts.SEMIBOLD, 8.5f * s) + 22 * s;
		float x = right - w;
		float y = 10 * s;
		boolean over = mouseX >= x && mouseY >= y && mouseX <= x + w && mouseY <= y + h;
		Draw2D.pixelRound(g, x - 1, y - 1, x + w + 1, y + h + 1, h / 2 + 1, 0x24FFFFFF);
		Draw2D.pixelRound(g, x, y, x + w, y + h, h / 2, over ? 0xF02A2E36 : 0xE6181B20);
		ui.textMiddle(label, x + 11 * s, y + h / 2, 8.5f * s, over ? 0xFFFFFFFF : 0xE0FFFFFF, Fonts.SEMIBOLD);
		buttons.add(new Button(x, y, x + w, y + h, action));
		return x - 6 * s;
	}
	private void list(GuiGraphicsExtractor g, Ui ui, String dim) {
		List<Waypoints.Waypoint> all = Waypoints.here();
		float w = 210 * s;
		float row = 24 * s;
		float x = width - w - 10 * s;
		float y = 42 * s;
		float h = Math.min(height - y - 10 * s, 34 * s + Math.max(1, all.size()) * row);
		panel(g, x, y, w, h);
		ui.textMiddle("Waypoints", x + 12 * s, y + 15 * s, 10 * s, 0xFFFFFFFF, Fonts.SEMIBOLD);
		float ry = y + 30 * s;
		if (all.isEmpty()) {
			ui.textMiddle("None yet: right-click the map to add one", x + 12 * s, ry + row / 2, 8 * s, 0x99FFFFFF, Fonts.MEDIUM);
			return;
		}
		for (Waypoints.Waypoint wp : all) {
			if (ry + row > y + h) {
				break;
			}
			boolean over = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY <= ry + row;
			if (over || wp == selected) {
				Draw2D.pixelRound(g, x + 4 * s, ry, x + w - 4 * s, ry + row, 6 * s, wp == selected ? 0x30FFFFFF : 0x18FFFFFF);
			}
			float d = 4 * s;
			Draw2D.circle(g, x + 12 * s + d, ry + row / 2, d, (wp.visible ? 0xFF000000 : 0x60000000) | (wp.color & 0xFFFFFF));
			ui.textMiddle(wp.name, x + 26 * s, ry + row / 2, 8.5f * s, wp.visible ? 0xFFFFFFFF : 0x80FFFFFF, Fonts.SEMIBOLD);
			String right = wp.showsIn(dim) ? distanceTo(wp, dim) : dimensionName(wp.dimension);
			ui.textMiddle(right, x + w - 12 * s - ui.textWidth(right, Fonts.MEDIUM, 7.5f * s), ry + row / 2, 7.5f * s, 0x99FFFFFF, Fonts.MEDIUM);
			Waypoints.Waypoint target = wp;
			buttons.add(new Button(x, ry, x + w, ry + row, () -> {
				selected = target;
				if (target.showsIn(dim)) {
					double[] p = target.in(dim);
					follow = false;
					centerX = p[0];
					centerZ = p[2];
				}
			}));
			ry += row;
		}
	}
	private void details(GuiGraphicsExtractor g, Ui ui, String dim, Waypoints.Waypoint w) {
		float cw = 230 * s;
		float ch = 74 * s;
		float x = 10 * s;
		float y = height - ch - 10 * s;
		panel(g, x, y, cw, ch);
		Draw2D.circle(g, x + 16 * s, y + 15 * s, 4 * s, 0xFF000000 | (w.color & 0xFFFFFF));
		ui.textMiddle(w == renaming ? renameText + "|" : w.name, x + 26 * s, y + 15 * s, 10 * s, 0xFFFFFFFF, Fonts.SEMIBOLD);
		ui.textMiddle(StreamerMode.xyz(w.x, w.y, w.z) + "  ·  " + dimensionName(w.dimension)
				+ (w.showsIn(dim) ? "  ·  " + distanceTo(w, dim) : ""), x + 12 * s, y + 31 * s, 8 * s, 0xB3FFFFFF, Fonts.MEDIUM);
		float by = y + ch - 30 * s;
		float bx = x + 10 * s;
		bx = smallButton(g, ui, bx, by, "Fly here", () -> fly(w));
		bx = smallButton(g, ui, bx, by, w == renaming ? "Save name" : "Rename", () -> {
			if (renaming == w) {
				finishRename();
			} else {
				renaming = w;
				renameText = new StringBuilder(w.name);
			}
		});
		bx = smallButton(g, ui, bx, by, w.visible ? "Hide" : "Show", () -> {
			w.visible = !w.visible;
			Waypoints.save();
		});
		bx = smallButton(g, ui, bx, by, "Colour", () -> {
			int[] palette = {0xFFFFD60A, 0xFF64D2FF, 0xFF30D158, 0xFFFF6482, 0xFFBF5AF2, 0xFFFF9F0A, 0xFFFFFFFF};
			int i = 0;
			while (i < palette.length && palette[i] != w.color) {
				i++;
			}
			w.color = palette[(i + 1) % palette.length];
			Waypoints.save();
		});
		smallButton(g, ui, bx, by, "Delete", () -> {
			Waypoints.remove(w);
			selected = null;
			renaming = null;
		});
	}
	private float smallButton(GuiGraphicsExtractor g, Ui ui, float x, float y, String label, Runnable action) {
		float h = 20 * s;
		float w = ui.textWidth(label, Fonts.SEMIBOLD, 8 * s) + 16 * s;
		boolean over = mouseX >= x && mouseY >= y && mouseX <= x + w && mouseY <= y + h;
		Draw2D.pixelRound(g, x, y, x + w, y + h, h / 2, over ? 0x40FFFFFF : 0x22FFFFFF);
		ui.textMiddle(label, x + 8 * s, y + h / 2, 8 * s, 0xFFFFFFFF, Fonts.SEMIBOLD);
		buttons.add(new Button(x, y, x + w, y + h, action));
		return x + w + 5 * s;
	}
	private void tip(GuiGraphicsExtractor g, Ui ui, float x, float y, String title, String sub) {
		float w = Math.max(ui.textWidth(title, Fonts.SEMIBOLD, 9 * s), ui.textWidth(sub, Fonts.MEDIUM, 7.5f * s)) + 16 * s;
		float h = 28 * s;
		x = Math.min(x, width - w - 4);
		y = Math.min(y, height - h - 4);
		panel(g, x, y, w, h);
		ui.textMiddle(title, x + 8 * s, y + 9 * s, 9 * s, 0xFFFFFFFF, Fonts.SEMIBOLD);
		ui.textMiddle(sub, x + 8 * s, y + 20 * s, 7.5f * s, 0xB3FFFFFF, Fonts.MEDIUM);
	}
	private void drawMenu(GuiGraphicsExtractor g, Ui ui) {
		float row = 22 * s;
		float w = 10 * s;
		for (String l : menu.labels()) {
			w = Math.max(w, ui.textWidth(l, Fonts.SEMIBOLD, 8.5f * s) + 24 * s);
		}
		float h = menu.labels().size() * row + 8 * s;
		float x = Math.min(menu.x(), width - w - 4);
		float y = Math.min(menu.y(), height - h - 4);
		panel(g, x, y, w, h);
		for (int i = 0; i < menu.labels().size(); i++) {
			float ry = y + 4 * s + i * row;
			boolean over = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY <= ry + row;
			if (over) {
				Draw2D.pixelRound(g, x + 4 * s, ry, x + w - 4 * s, ry + row, 6 * s, 0x2AFFFFFF);
			}
			ui.textMiddle(menu.labels().get(i), x + 12 * s, ry + row / 2, 8.5f * s, 0xFFFFFFFF, Fonts.SEMIBOLD);
			Runnable action = menu.actions().get(i);
			buttons.add(new Button(x, ry, x + w, ry + row, () -> {
				menu = null;
				action.run();
			}));
		}
	}
	private void fly(Waypoints.Waypoint w) {
		ElytraAutopilot.flyTo(w.x, w.z, w.dimension, w.name);
	}

	private void openMenu(double mx, double my) {
		String dim = MapRenderer.dimension();
		double[] world = view().toWorld(mx, my);
		Waypoints.Waypoint on = waypointAt(mx, my, dim);
		List<String> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		if (on != null) {
			selected = on;
			labels.add("Fly here");
			actions.add(() -> fly(on));
			labels.add("Rename");
			actions.add(() -> {
				renaming = on;
				renameText = new StringBuilder(on.name);
			});
			labels.add(on.visible ? "Hide" : "Show");
			actions.add(() -> {
				on.visible = !on.visible;
				Waypoints.save();
			});
			labels.add("Delete");
			actions.add(() -> {
				Waypoints.remove(on);
				selected = null;
			});
		} else {
			int x = (int) Math.floor(world[0]);
			int z = (int) Math.floor(world[1]);
			labels.add("Add waypoint here");
			actions.add(() -> {
				int y = minecraft.player.getBlockY();
				if (minecraft.level.hasChunk(x >> 4, z >> 4)) {
					y = minecraft.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
				}
				Waypoints.Waypoint w = Waypoints.add(null, x, y, z);
				WaypointsModule.added(w);
				selected = w;
				renaming = w;
				renameText = new StringBuilder(w == null ? "" : w.name);
			});
			labels.add("Fly here");
			actions.add(() -> ElytraAutopilot.flyTo(x, z, dim, null));
			labels.add("Centre here");
			actions.add(() -> {
				follow = false;
				centerX = world[0];
				centerZ = world[1];
			});
		}
		menu = new Menu((float) mx, (float) my, world[0], world[1], on, labels, actions);
	}
	private Waypoints.Waypoint waypointAt(double mx, double my, String dim) {
		MapRenderer.View v = view();
		for (Waypoints.Waypoint w : Waypoints.visibleIn(dim)) {
			double[] p = w.in(dim);
			float[] sp = v.toScreen(p[0], p[2]);
			if (Math.abs(mx - sp[0]) <= 7 * s && my <= sp[1] + 2 * s && my >= sp[1] - 20 * s) {
				return w;
			}
		}
		return null;
	}
	private void finishRename() {
		if (renaming != null && renameText != null && !renameText.toString().isBlank()) {
			renaming.name = renameText.toString().trim();
			Waypoints.save();
		}
		renaming = null;
	}
	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		double mx = e.x();
		double my = e.y();
		for (int i = buttons.size() - 1; i >= 0; i--) {
			Button b = buttons.get(i);
			if (e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && mx >= b.x0() && my >= b.y0() && mx <= b.x1() && my <= b.y1()) {
				b.action().run();
				return true;
			}
		}
		if (menu != null) {
			menu = null;
			return true;
		}
		if (renaming != null) {
			finishRename();
		}
		if (e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
			openMenu(mx, my);
			return true;
		}
		if (e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			Waypoints.Waypoint on = waypointAt(mx, my, MapRenderer.dimension());
			if (on != null) {
				selected = on;
				return true;
			}
			dragging = true;
			moved = false;
			dragX = mx;
			dragY = my;
			dragCX = centerX;
			dragCZ = centerZ;
		}
		return true;
	}
	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging) {
			if (Math.hypot(e.x() - dragX, e.y() - dragY) > 2) {
				moved = true;
				follow = false;
			}
			if (moved) {
				centerX = dragCX - (e.x() - dragX) / scale;
				centerZ = dragCZ - (e.y() - dragY) / scale;
			}
		}
		return true;
	}
	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragging && !moved && e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			selected = null;
		}
		dragging = false;
		return true;
	}
	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (scrollY == 0) {
			return true;
		}
		double[] before = view().toWorld(mx, my);
		scale = (float) Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * Math.pow(1.25, scrollY)));
		if (!follow) {
			double[] after = view().toWorld(mx, my);
			centerX += before[0] - after[0];
			centerZ += before[1] - after[1];
		}
		return true;
	}
	@Override
	public boolean keyPressed(KeyEvent e) {
		if (renaming != null) {
			if (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER) {
				finishRename();
			} else if (e.key() == InputConstants.KEY_ESCAPE) {
				renaming = null;
			} else if (e.key() == InputConstants.KEY_BACKSPACE && !renameText.isEmpty()) {
				renameText.setLength(renameText.length() - 1);
			}
			return true;
		}
		if (e.key() == InputConstants.KEY_ESCAPE) {
			if (menu != null) {
				menu = null;
			} else {
				onClose();
			}
			return true;
		}
		WorldMap map = WorldMap.instance();
		if (map != null && map.openKey().isBound() && e.key() == map.openKey().get()) {
			onClose();
			return true;
		}
		if (e.key() == InputConstants.KEY_C) {
			follow = true;
			return true;
		}
		return super.keyPressed(e);
	}
	@Override
	public boolean charTyped(CharacterEvent e) {
		if (renaming != null) {
			if (e.isAllowedChatCharacter() && renameText.length() < 32) {
				renameText.append(e.codepointAsString());
			}
			return true;
		}
		return super.charTyped(e);
	}
	static String where() {
		return ServerKey.current();
	}
}
