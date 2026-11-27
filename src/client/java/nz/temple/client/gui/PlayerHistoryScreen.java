package nz.temple.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.StreamerMode;
import nz.temple.client.modules.render.WaypointsModule;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Icons;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.client.util.PlayerHistory;
import nz.temple.client.util.ServerKey;
import nz.temple.client.util.Waypoints;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class PlayerHistoryScreen extends Screen {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ROOT);
	private final Screen parent;
	private final StringBuilder search = new StringBuilder();
	private final Map<String, Supplier<PlayerSkin>> skins = new HashMap<>();
	private final List<float[]> hitBoxes = new ArrayList<>();
	private final List<Runnable> hitActions = new ArrayList<>();
	private PlayerHistory.Record selected;
	private float scroll;
	private float s = 1;
	private double mouseX;
	private double mouseY;
	private RemotePlayer puppet;
	private String puppetFor;
	private PlayerSkinWidget skinWidget;
	private String widgetFor;
	private float[] modelBox;
	public PlayerHistoryScreen(Screen parent) {
		super(Component.literal("Player history"));
		this.parent = parent;
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
		PlayerHistory.save();
		minecraft.gui.setScreen(parent);
	}
	private List<PlayerHistory.Record> filtered() {
		String q = search.toString().toLowerCase(Locale.ROOT).trim();
		List<PlayerHistory.Record> list = new ArrayList<>();
		for (PlayerHistory.Record r : PlayerHistory.all()) {
			if (q.isEmpty() || r.name.toLowerCase(Locale.ROOT).contains(q)
					|| r.otherNames.stream().anyMatch(n -> n.toLowerCase(Locale.ROOT).contains(q))) {
				list.add(r);
			}
		}
		list.sort(Comparator.comparingLong((PlayerHistory.Record r) -> r.lastSeen).reversed());
		return list;
	}
	private Supplier<PlayerSkin> skin(PlayerHistory.Record r) {
		return skins.computeIfAbsent(r.uuid, k -> minecraft.getSkinManager().createLookup(r.profile(), false));
	}
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		Ui ui = TempleNzClient.ui();
		if (ui == null) {
			return;
		}
		mouseX = mx;
		mouseY = my;
		s = UiScale.factor();
		hitBoxes.clear();
		hitActions.clear();
		ui.begin(g);
		ui.glassRenderer().placeLayer(g);
		g.fill(0, 0, width, height, 0x99000000);
		float w = Math.min(width - 24 * s, 720 * s);
		float h = Math.min(height - 24 * s, 420 * s);
		float x0 = (width - w) / 2;
		float y0 = (height - h) / 2;
		ui.glass(x0, y0, w, h, 16 * s);
		ui.fill(x0, y0, w, h, 16 * s, 0x66000000);
		Icons.draw(g, Icons.Icon.USER, x0 + 20 * s, y0 + 20 * s, 12 * s, 0xFF000000 | (Theme.primary() & 0xFFFFFF));
		ui.textMiddle("Player history", x0 + 32 * s, y0 + 20 * s, 12 * s, 0xFFFFFFFF, Fonts.SEMIBOLD);
		List<PlayerHistory.Record> list = filtered();
		String count = PlayerHistory.all().size() + " players seen";
		ui.textMiddle(count, x0 + w - 16 * s - ui.textWidth(count, Fonts.MEDIUM, 8 * s), y0 + 20 * s, 8 * s, 0x99FFFFFF, Fonts.MEDIUM);
		float listW = 200 * s;
		list(g, ui, list, x0 + 10 * s, y0 + 36 * s, listW, h - 46 * s);
		if (selected != null && !PlayerHistory.all().contains(selected)) {
			selected = null;
		}
		if (selected == null && !list.isEmpty()) {
			selected = list.getFirst();
		}
		float dx = x0 + listW + 20 * s;
		if (selected != null) {
			details(g, ui, selected, dx, y0 + 36 * s, x0 + w - 12 * s - dx, h - 48 * s, delta);
		} else {
			String empty = "Nobody yet: everyone you get near is remembered here.";
			ui.textMiddle(empty, dx + 10 * s, y0 + h / 2, 9 * s, 0x99FFFFFF, Fonts.MEDIUM);
		}
	}
	private void list(GuiGraphicsExtractor g, Ui ui, List<PlayerHistory.Record> list, float x, float y, float w, float h) {
		float fh = 22 * s;
		ui.fill(x, y, w, fh, fh / 2, 0x33FFFFFF);
		Icons.draw(g, Icons.Icon.SEARCH, x + 12 * s, y + fh / 2, 9 * s, 0xB3FFFFFF);
		String shown = search.isEmpty() ? "Search names" : search.toString();
		ui.textMiddle(shown + (search.isEmpty() || (System.currentTimeMillis() / 500) % 2 == 1 ? "" : "|"), x + 22 * s, y + fh / 2, 8.5f * s,
				search.isEmpty() ? 0x80FFFFFF : 0xFFFFFFFF, Fonts.MEDIUM);
		float top = y + fh + 6 * s;
		float row = 30 * s;
		float maxScroll = Math.max(0, list.size() * row - (y + h - top));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		g.enableScissor(Math.round(x), Math.round(top), Math.round(x + w), Math.round(y + h));
		float ry = top - scroll;
		long now = System.currentTimeMillis();
		for (PlayerHistory.Record r : list) {
			if (ry > y + h) {
				break;
			}
			if (ry + row >= top) {
				boolean over = mouseX >= x && mouseX <= x + w && mouseY >= Math.max(ry, top) && mouseY <= Math.min(ry + row, y + h);
				if (r == selected || over) {
					Draw2D.pixelRound(g, x, ry + 1, x + w, ry + row - 1, 7 * s, r == selected ? 0x33FFFFFF : 0x1AFFFFFF);
				}
				int face = Math.round(20 * s);
				PlayerFaceExtractor.extractRenderState(g, skin(r).get(), Math.round(x + 6 * s), Math.round(ry + (row - face) / 2), face);
				ui.textMiddle(r.name, x + 32 * s, ry + 10 * s, 9 * s, 0xFFFFFFFF, Fonts.SEMIBOLD);
				ui.textMiddle(ago(now - r.lastSeen) + "  ·  " + r.encounters + (r.encounters == 1 ? " time" : " times"), x + 32 * s, ry + 21 * s,
						7.5f * s, 0x99FFFFFF, Fonts.MEDIUM);
				PlayerHistory.Record target = r;
				hit(x, Math.max(ry, top), x + w, Math.min(ry + row, y + h), () -> selected = target);
			}
			ry += row;
		}
		g.disableScissor();
	}
	private void details(GuiGraphicsExtractor g, Ui ui, PlayerHistory.Record r, float x, float y, float w, float h, float delta) {
		float mw = 130 * s;
		ui.fill(x, y, mw, h, 12 * s, 0x33000000);
		modelBox = new float[] {x, y, x + mw, y + h};
		model(g, r, x, y, mw, h, delta);
		float cx = x + mw + 14 * s;
		float cw = w - mw - 14 * s;
		float ty = y + 8 * s;
		ui.textMiddle(r.name, cx, ty + 6 * s, 14 * s, 0xFFFFFFFF, Fonts.SEMIBOLD);
		if (!r.otherNames.isEmpty()) {
			String aka = "also " + String.join(", ", r.otherNames);
			ui.textMiddle(aka, cx + ui.textWidth(r.name, Fonts.SEMIBOLD, 14 * s) + 8 * s, ty + 7 * s, 8 * s, 0x99FFFFFF, Fonts.MEDIUM);
		}
		ty += 22 * s;
		long now = System.currentTimeMillis();
		PlayerHistory.Sighting last = r.last();
		String where = last == null ? "unknown" : StreamerMode.xyz(last.x, last.y, last.z) + "  ·  " + dimName(last.dimension)
				+ "  ·  " + StreamerMode.serverAddress(serverName(last.server));
		String[][] rows = {
				{"Last seen", ago(now - r.lastSeen) + "  (" + DATE.format(Instant.ofEpochMilli(r.lastSeen).atZone(ZoneId.systemDefault())) + ")"},
				{"Last seen at", where},
				{"First seen", DATE.format(Instant.ofEpochMilli(r.firstSeen).atZone(ZoneId.systemDefault()))},
				{"Encounters", r.encounters + "  ·  " + duration(r.millisNear) + " near you  ·  closest " + Math.round(r.closest) + " m"},
				{"Health", String.format(Locale.ROOT, "%.1f / %.0f", r.health + r.absorption, r.maxHealth)
						+ "  ·  ping " + r.ping + " ms" + (r.gameMode == null ? "" : "  ·  " + r.gameMode)},
				{"Totems popped", r.totemPops + "  ·  deaths seen " + r.deathsSeen},
				{"Servers", r.servers.stream().map(sv -> StreamerMode.serverAddress(serverName(sv))).reduce((a, b) -> a + ", " + b).orElse("-")},
				{"UUID", r.uuid},
		};
		for (String[] row : rows) {
			ui.textMiddle(row[0], cx, ty, 8 * s, 0x8CFFFFFF, Fonts.MEDIUM);
			ui.textMiddle(ellipsize(ui, row[1], cw - 80 * s), cx + 80 * s, ty, 8 * s, 0xF2FFFFFF, Fonts.MEDIUM);
			ty += 13 * s;
		}
		ty += 6 * s;
		ui.textMiddle("Gear when last seen", cx, ty, 8.5f * s, 0xFFFFFFFF, Fonts.SEMIBOLD);
		ty += 9 * s;
		float slot = 22 * s;
		for (int i = 0; i < 6; i++) {
			PlayerHistory.Item it = i < r.gear.size() ? r.gear.get(i) : null;
			itemSlot(g, ui, it, cx + i * (slot + 4 * s) + (i >= 4 ? 8 * s : 0), ty, slot);
		}
		ty += slot + 10 * s;
		ui.textMiddle("Seen holding", cx, ty, 8.5f * s, 0xFFFFFFFF, Fonts.SEMIBOLD);
		ty += 9 * s;
		int perRow = Math.max(1, (int) ((cw) / (slot + 4 * s)));
		int shownHeld = Math.min(r.held.size(), perRow * 2);
		for (int i = 0; i < shownHeld; i++) {
			itemSlot(g, ui, r.held.get(i), cx + (i % perRow) * (slot + 4 * s), ty + (i / perRow) * (slot + 4 * s), slot);
		}
		if (r.held.isEmpty()) {
			ui.textMiddle("nothing yet", cx, ty + slot / 2, 8 * s, 0x80FFFFFF, Fonts.MEDIUM);
		}
		float by = y + h - 22 * s;
		float bx = cx;
		boolean sameServer = last != null && last.server != null && last.server.equals(ServerKey.current());
		if (sameServer) {
			bx = button(g, ui, bx, by, "Waypoint at last seen", () -> {
				Waypoints.Waypoint wp = Waypoints.add(r.name + " last seen", last.x, last.y, last.z);
				if (wp != null) {
					wp.dimension = last.dimension;
					Waypoints.save();
				}
				WaypointsModule.added(wp);
			});
		}
		button(g, ui, bx, by, "Forget", () -> {
			PlayerHistory.forget(r);
			selected = null;
		});
	}
	private void model(GuiGraphicsExtractor g, PlayerHistory.Record r, float x, float y, float w, float h, float delta) {
		if (minecraft.level != null) {
			if (puppet == null || !r.uuid.equals(puppetFor) || puppet.level() != minecraft.level) {
				Supplier<PlayerSkin> skin = skin(r);
				puppet = new RemotePlayer(minecraft.level, r.profile()) {
					@Override
					public PlayerSkin getSkin() {
						return skin.get();
					}
				};
				puppetFor = r.uuid;
				puppet.setId(-1_000_000 - (r.uuid.hashCode() & 0xFFFF));
				EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
						EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
				for (int i = 0; i < slots.length; i++) {
					puppet.setItemSlot(slots[i], i < r.gear.size() ? PlayerHistory.stack(r.gear.get(i)) : ItemStack.EMPTY);
				}
			}
			int size = Math.round(Math.min(w * 0.42f, h * 0.36f));
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, Math.round(x), Math.round(y + 6 * s), Math.round(x + w), Math.round(y + h - 6 * s),
					size, 0.0625f, (float) mouseX, (float) mouseY, puppet);
			return;
		}
		if (skinWidget == null || !r.uuid.equals(widgetFor)) {
			skinWidget = new PlayerSkinWidget(Math.round(w), Math.round(h), minecraft.getEntityModels(), skin(r));
			widgetFor = r.uuid;
		}
		skinWidget.setPosition(Math.round(x), Math.round(y));
		skinWidget.setSize(Math.round(w), Math.round(h));
		skinWidget.extractRenderState(g, (int) mouseX, (int) mouseY, delta);
	}
	private void itemSlot(GuiGraphicsExtractor g, Ui ui, PlayerHistory.Item it, float x, float y, float size) {
		Draw2D.pixelRound(g, x, y, x + size, y + size, 5 * s, 0x33FFFFFF);
		if (it == null) {
			return;
		}
		ItemStack stack = PlayerHistory.stack(it);
		if (stack.isEmpty()) {
			return;
		}
		var pose = g.pose();
		pose.pushMatrix();
		float k = size / 20f;
		pose.translate(x + 2 * k, y + 2 * k);
		pose.scale(k, k);
		g.item(stack, 0, 0);
		g.itemDecorations(minecraft.font, stack, 0, 0);
		pose.popMatrix();
		if (mouseX >= x && mouseY >= y && mouseX <= x + size && mouseY <= y + size) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(it.name + (it.count > 1 ? " x" + it.count : "")));
			for (String e : it.enchantments) {
				lines.add(Component.literal("§7" + e));
			}
			if (it.maxDamage > 0) {
				lines.add(Component.literal("§8Durability " + (it.maxDamage - it.damage) + " / " + it.maxDamage));
			}
			g.setTooltipForNextFrame(minecraft.font, lines, java.util.Optional.empty(), (int) mouseX, (int) mouseY);
		}
	}
	private float button(GuiGraphicsExtractor g, Ui ui, float x, float y, String label, Runnable action) {
		float h = 20 * s;
		float w = ui.textWidth(label, Fonts.SEMIBOLD, 8.5f * s) + 20 * s;
		boolean over = mouseX >= x && mouseY >= y && mouseX <= x + w && mouseY <= y + h;
		ui.fill(x, y, w, h, h / 2, over ? 0x40FFFFFF : 0x26FFFFFF);
		ui.textMiddle(label, x + 10 * s, y + h / 2, 8.5f * s, 0xFFFFFFFF, Fonts.SEMIBOLD);
		hit(x, y, x + w, y + h, action);
		return x + w + 6 * s;
	}
	private void hit(float x0, float y0, float x1, float y1, Runnable action) {
		hitBoxes.add(new float[] {x0, y0, x1, y1});
		hitActions.add(action);
	}
	private String ellipsize(Ui ui, String text, float max) {
		if (ui.textWidth(text, Fonts.MEDIUM, 8 * s) <= max) {
			return text;
		}
		String t = text;
		while (t.length() > 1 && ui.textWidth(t + "…", Fonts.MEDIUM, 8 * s) > max) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "…";
	}
	private static String serverName(String key) {
		if (key == null) {
			return "-";
		}
		return key.startsWith("mp_") ? key.substring(3) : key.startsWith("sp_") ? "singleplayer: " + key.substring(3) : key;
	}
	private static String dimName(String dim) {
		return dim == null ? "?" : switch (dim) {
			case "overworld" -> "Overworld";
			case "the_nether" -> "Nether";
			case "the_end" -> "End";
			default -> dim;
		};
	}
	static String ago(long millis) {
		long sec = Math.max(0, millis / 1000);
		if (sec < 60) {
			return "just now";
		}
		long min = sec / 60;
		if (min < 60) {
			return min + " min ago";
		}
		long hours = min / 60;
		if (hours < 48) {
			return hours + " h ago";
		}
		return hours / 24 + " days ago";
	}
	private static String duration(long millis) {
		long min = millis / 60_000;
		return min < 1 ? "under a minute" : min < 60 ? min + " min" : String.format(Locale.ROOT, "%d h %02d min", min / 60, min % 60);
	}
	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		for (int i = hitBoxes.size() - 1; i >= 0; i--) {
			float[] b = hitBoxes.get(i);
			if (e.x() >= b[0] && e.y() >= b[1] && e.x() <= b[2] && e.y() <= b[3]) {
				hitActions.get(i).run();
				return true;
			}
		}
		if (skinWidget != null && minecraft.level == null && modelBox != null && e.x() >= modelBox[0] && e.x() <= modelBox[2]
				&& e.y() >= modelBox[1] && e.y() <= modelBox[3]) {
			return skinWidget.mouseClicked(e, doubleClick);
		}
		return true;
	}
	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (skinWidget != null && minecraft.level == null) {
			return skinWidget.mouseDragged(e, dx, dy);
		}
		return true;
	}
	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll -= (float) (scrollY * 30 * s);
		return true;
	}
	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (e.key() == InputConstants.KEY_BACKSPACE && !search.isEmpty()) {
			search.setLength(search.length() - 1);
			scroll = 0;
			return true;
		}
		return super.keyPressed(e);
	}
	@Override
	public boolean charTyped(CharacterEvent e) {
		if (e.isAllowedChatCharacter() && search.length() < 24) {
			search.append(e.codepointAsString());
			scroll = 0;
		}
		return true;
	}
}
