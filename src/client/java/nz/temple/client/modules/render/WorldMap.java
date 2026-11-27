package nz.temple.client.modules.render;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.ChunkPos;
import nz.temple.client.gui.MapScreen;
import nz.temple.client.map.MapRenderer;
import nz.temple.client.map.MapStore;
import nz.temple.client.modules.client.StreamerMode;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Sprites;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.util.KeyEdge;
import nz.temple.client.util.Waypoints;
import nz.temple.client.util.WorldEvents;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ActionSetting;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.KeybindSetting;
import nz.temple.setting.NumberSetting;

public final class WorldMap extends Module {
	private static WorldMap instance;
	private final KeybindSetting openKey;
	private final BoolSetting terrain;
	private final BoolSetting trail;
	private final BoolSetting players;
	private final BoolSetting minimap;
	private final NumberSetting minimapSize;
	private final NumberSetting minimapZoom;
	private final BoolSetting rotate;
	private final BoolSetting minimapWaypoints;
	private final BoolSetting minimapCoords;
	private final KeyEdge openEdge;
	public WorldMap() {
		super("Map", Category.RENDER, "Records explored terrain and the path you travel, per server, saved between sessions. Press its key "
				+ "for a full-screen map with your waypoints; the minimap shows the same in the HUD.");
		instance = this;
		openKey = add(new KeybindSetting("Open map key", 77).describe("Opens the full-screen map. Press it again or Esc to close."));
		add(new ActionSetting("Open", "Open map", () -> Minecraft.getInstance().execute(WorldMap::open)));
		terrain = add(new BoolSetting("Record terrain", true).describe("Records each loaded chunk to the map."));
		trail = add(new BoolSetting("Record path", true).describe("Records where you have travelled and draws it as a line."));
		players = add(new BoolSetting("Show players", true).describe("Shows other players within view distance on both maps."));
		group("Minimap");
		minimap = add(new BoolSetting("Minimap", true).describe("A minimap in the HUD. Move it in Edit HUD."));
		minimapSize = add(new NumberSetting("Size", 120, 60, 320, 5, " px").describe("Minimap size. You can also scroll over it in Edit HUD "
				+ "to resize it.")
				.visibleWhen(minimap::get));
		minimapZoom = add(new NumberSetting("Zoom", 1, 0.25, 8, 0.25, "x").describe("Pixels per block on the minimap.")
				.visibleWhen(minimap::get));
		rotate = add(new BoolSetting("Rotate with view", false).describe("Rotates the minimap so the direction you face is always up.")
				.visibleWhen(minimap::get));
		minimapWaypoints = add(new BoolSetting("Waypoints", true).describe("Shows waypoints on the minimap, pinned to the edge when out of "
				+ "range.")
				.visibleWhen(minimap::get));
		minimapCoords = add(new BoolSetting("Coordinates", true).describe("Shows your coordinates under the minimap.")
				.visibleWhen(minimap::get));
		openEdge = new KeyEdge(openKey);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			if (isEnabled() && terrain.get()) {
				MapStore.get().queue(chunk.getPos());
			}
		});
		WorldEvents.onBlockChanged((pos, oldState, newState) -> {
			if (isEnabled() && terrain.get()) {
				MapStore.get().queue(ChunkPos.containing(pos));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> MapStore.get().leave()));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> MapStore.get().flush());
		ClientTickEvents.END_CLIENT_TICK.register(mc -> MapStore.get().trim());
		on(TickEvent.class, e -> {
			MapStore.get().tick(terrain.get(), trail.get());
			if (openEdge.pressed()) {
				open();
			}
		});
	}
	public static WorldMap instance() {
		return instance;
	}
	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.gui.setScreen(new MapScreen(mc.gui.screen()));
		}
	}
	public KeybindSetting openKey() {
		return openKey;
	}
	public boolean showPlayers() {
		return players.get();
	}
	public boolean minimapOn() {
		return isEnabled() && minimap.get();
	}
	public float minimapSize() {
		return minimapSize.getFloat();
	}

	public void drawMinimap(GuiGraphicsExtractor g, Ui ui, float x, float y, float size, float k, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		int accent = Theme.primary() & 0xFFFFFF;
		float r = 11 * k;
		ui.glass(x, y, size, size, r);
		ui.fill(x, y, size, size, r, 0x2E000000);
		float inset = 4 * k;
		float mx0 = x + inset;
		float my0 = y + inset;
		float mx1 = x + size - inset;
		float my1 = y + size - inset;
		var at = mc.player.getPosition(partial);
		double px = at.x;
		double pz = at.z;
		float yaw = mc.player.getViewYRot(partial);
		float angle = rotate.get() ? (float) Math.toRadians(180 - yaw) : 0;
		float cx = x + size / 2;
		float cy = y + size / 2;
		float scale = minimapZoom.getFloat() * k;
		MapRenderer.View view = new MapRenderer.View(cx, cy, px, pz, scale, angle);
		g.enableScissor(Math.round(mx0), Math.round(my0), Math.round(mx1), Math.round(my1));
		Draw2D.rect(g, mx0, my0, mx1, my1, 0x8C0E1013);
		chunkGrid(g, view, mx0, my0, mx1, my1, scale);
		MapRenderer.tiles(g, view, size * 0.75f, 64);
		if (trail.get()) {
			MapRenderer.trail(g, view, MapStore.get().trail(MapRenderer.dimension()), mx0, my0, mx1, my1, 0xFF000000 | accent, 1.3f * k);
		}
		if (players.get()) {
			MapRenderer.players(g, view, 2.4f * k, 0xFFFF5A5F);
		}
		g.disableScissor();
		Draw2D.outline(g, mx0, my0, mx1, my1, Math.max(0.5f, 0.6f * k), 0x26FFFFFF);
		float arrowAngle = rotate.get() ? 0 : (float) Math.toRadians(yaw + 180);
		if (minimapWaypoints.get()) {
			String dim = MapRenderer.dimension();
			float m = 5 * k;
			g.enableScissor(Math.round(mx0), Math.round(my0), Math.round(mx1), Math.round(my1));
			for (Waypoints.Waypoint w : Waypoints.visibleIn(dim)) {
				double[] p = w.in(dim);
				float[] s = view.toScreen(p[0], p[2]);
				float sx = Math.max(mx0 + m, Math.min(mx1 - m, s[0]));
				float sy = Math.max(my0 + 2 * m, Math.min(my1 - 1, s[1]));
				boolean pinned = sx != s[0] || sy != s[1];
				int colour = (pinned ? 0xB0000000 : 0xFF000000) | (w.color & 0xFFFFFF);
				float top = Sprites.marker(g, sx, sy, (pinned ? 11 : 14) * k, colour);
				if (!pinned) {
					label(g, ui, w.name, sx, top - 1.5f * k, 6.5f * k, k);
				}
			}
			g.disableScissor();
		}
		Sprites.arrow(g, cx, cy, 13 * Math.max(0.8f, k), arrowAngle, 0xFFFFFFFF);
		float[] north = view.toScreen(px, pz - 10_000);
		double nx = north[0] - cx;
		double ny = north[1] - cy;
		double len = Math.max(1e-3, Math.hypot(nx, ny));
		float reach = size / 2 - inset - 7 * k;
		double t = Math.min(reach / Math.max(1e-3, Math.abs(nx / len)), reach / Math.max(1e-3, Math.abs(ny / len)));
		float nX = (float) (cx + nx / len * t);
		float nY = (float) (cy + ny / len * t);
		float tw = ui.textWidth("N", Fonts.SEMIBOLD, 8 * k);
		ui.textMiddle("N", nX - tw / 2 + 0.5f * k, nY + 0.6f * k, 8 * k, 0x8C000000, Fonts.SEMIBOLD);
		ui.textMiddle("N", nX - tw / 2, nY, 8 * k, 0xF2FFFFFF, Fonts.SEMIBOLD);
		if (minimapCoords.get()) {
			String c = StreamerMode.xyz(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
			float cw = ui.textWidth(c, Fonts.MEDIUM, 7.5f * k) + 14 * k;
			float ch = 14 * k;
			float cy0 = y + size + 4 * k;
			ui.glass(cx - cw / 2, cy0, cw, ch, ch / 2);
			ui.fill(cx - cw / 2, cy0, cw, ch, ch / 2, 0x2E000000);
			ui.textMiddle(c, cx - cw / 2 + 7 * k, cy0 + ch / 2, 7.5f * k, 0xE6FFFFFF, Fonts.MEDIUM);
		}
	}
	public static void label(GuiGraphicsExtractor g, Ui ui, String name, float x, float bottom, float size, float k) {
		float tw = ui.textWidth(name, Fonts.SEMIBOLD, size);
		float h = size + 4 * k;
		float x0 = x - tw / 2 - 3.5f * k;
		float y0 = bottom - h;
		float gs = (float) Minecraft.getInstance().getWindow().getGuiScale();
		Draw2D.pixelRound(g, x0, y0, x0 + tw + 7 * k, bottom, h / 2 * gs, 0xB8101216);
		ui.textMiddle(name, x0 + 3.5f * k, y0 + h / 2, size, 0xFFFFFFFF, Fonts.SEMIBOLD);
	}
	private static void chunkGrid(GuiGraphicsExtractor g, MapRenderer.View view, float x0, float y0, float x1, float y1, float scale) {
		if (scale * 16 < 6 || view.angle() != 0) {
			return;
		}
		double[] tl = view.toWorld(x0, y0);
		double[] br = view.toWorld(x1, y1);
		for (int gx = (int) Math.floor(tl[0] / 16) * 16; gx <= br[0]; gx += 16) {
			float sx = view.toScreen(gx, 0)[0];
			Draw2D.rect(g, sx, y0, sx + 0.5f, y1, 0x0FFFFFFF);
		}
		for (int gz = (int) Math.floor(tl[1] / 16) * 16; gz <= br[1]; gz += 16) {
			float sy = view.toScreen(0, gz)[1];
			Draw2D.rect(g, x0, sy, x1, sy + 0.5f, 0x0FFFFFFF);
		}
	}
}
