package nz.temple.client.modules.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.StreamerMode;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Projection;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.KeyEdge;
import nz.temple.client.util.ServerKey;
import nz.temple.client.util.Waypoints;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ActionSetting;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.KeybindSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class WaypointsModule extends Module {
	private static final double BEAM_RANGE = 160;
	private final KeybindSetting addKey;
	private final BoolSetting beams;
	private final BoolSetting tags;
	private final BoolSetting otherDimension;
	private final NumberSetting maxTags;
	private final NumberSetting hideWithin;
	private final KeyEdge addEdge;
	public WaypointsModule() {
		super("Waypoints", Category.RENDER, "Saved locations shown in the world with a beam and a distance tag. Add one at your position "
				+ "with the key, the button below, \".wp add <name>\" in chat, or by right-clicking the map. Saved per server.");
		addKey = add(new KeybindSetting("Add waypoint key", 78)
				.describe("Adds a waypoint at your position."));
		add(new ActionSetting("Add here", "Add waypoint here", () -> added(Waypoints.addHere(null))));
		group();
		beams = add(new BoolSetting("Beams", true).describe("A coloured beam at each waypoint, visible through terrain."));
		tags = add(new BoolSetting("Tags", true).describe("Shows the name and distance at each waypoint."));
		otherDimension = add(new BoolSetting("Other dimension", true)
				.describe("Shows Overworld waypoints in the Nether at the matching position (divided by 8), and Nether waypoints in the "
						+ "Overworld."));
		maxTags = add(new NumberSetting("Max tags", 10, 1, 40, 1, "").describe("Only the nearest waypoints, up to this many, show a tag."));
		hideWithin = add(new NumberSetting("Hide within", 3, 0, 32, 1, " m").describe("Hides a waypoint once you are this close."));
		addEdge = new KeyEdge(addKey);
		on(TickEvent.class, e -> {
			if (addEdge.pressed()) {
				added(Waypoints.addHere(null));
			}
		});
	}
	public static void added(Waypoints.Waypoint w) {
		Minecraft mc = Minecraft.getInstance();
		if (w == null || mc.player == null) {
			return;
		}
		nz.temple.client.compat.Players.message(mc.player, Component.literal("§8[§bTemple§8] §fWaypoint §7\"" + w.name + "\" added at §f"
				+ StreamerMode.xyz(w.x, w.y, w.z)));
		if (TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().info("Waypoint added", w.name);
		}
	}
	private List<Waypoints.Waypoint> shown(Vec3 eye) {
		String dim = ServerKey.dimension();
		if (dim == null) {
			return List.of();
		}
		List<Waypoints.Waypoint> out = new ArrayList<>();
		for (Waypoints.Waypoint w : Waypoints.visibleIn(dim)) {
			if (!otherDimension.get() && !w.dimension.equals(dim)) {
				continue;
			}
			double[] p = w.in(dim);
			if (Math.hypot(p[0] - eye.x, p[2] - eye.z) >= hideWithin.get()) {
				out.add(w);
			}
		}
		out.sort(Comparator.comparingDouble(w -> {
			double[] p = w.in(dim);
			return Math.hypot(p[0] - eye.x, p[2] - eye.z);
		}));
		return out;
	}
	public void lines(List<WorldEsp.Line> out, CameraRenderState camera, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !beams.get() || mc.player == null || mc.level == null) {
			return;
		}
		Vec3 eye = mc.player.getEyePosition(partial);
		String dim = ServerKey.dimension();
		for (Waypoints.Waypoint w : shown(eye)) {
			double[] p = w.in(dim);
			double dx = p[0] - eye.x;
			double dz = p[2] - eye.z;
			double d = Math.hypot(dx, dz);
			double f = d > BEAM_RANGE ? BEAM_RANGE / d : 1;
			double x = eye.x + dx * f;
			double z = eye.z + dz * f;
			double bottom = d > BEAM_RANGE ? mc.level.getMinY() : p[1];
			int argb = (w.dimension.equals(dim) ? 0xD0000000 : 0x70000000) | (w.color & 0xFFFFFF);
			out.add(new WorldEsp.Line(x, bottom, z, x, mc.level.getMaxY() + 64, z, argb, 2.2f));
		}
	}
	public void render(GuiGraphicsExtractor g, DeltaTracker delta, Projection projection, Ui ui) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !tags.get() || mc.player == null || mc.level == null || !projection.ready()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(true);
		Vec3 eye = mc.player.getEyePosition(partial);
		String dim = ServerKey.dimension();
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		float s = UiScale.factor();
		ui.begin(g);
		int drawn = 0;
		for (Waypoints.Waypoint w : shown(eye)) {
			if (drawn >= maxTags.get()) {
				break;
			}
			double[] p = w.in(dim);
			double dx = p[0] - eye.x;
			double dy = p[1] + 1.2 - eye.y;
			double dz = p[2] - eye.z;
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			double f = d > BEAM_RANGE ? BEAM_RANGE / d : 1;
			float[] sp = projection.toScreen(eye.x + dx * f, eye.y + dy * f, eye.z + dz * f, gw, gh);
			if (sp == null) {
				continue;
			}
			drawn++;
			String name = w.name;
			String sub = distance(Math.hypot(dx, dz)) + (w.dimension.equals(dim) ? "" : ServerKey.isNether(w.dimension) ? "  ·  Nether" : "  ·  Overworld");
			float size = 9 * s;
			float small = 7.5f * s;
			float tw = Math.max(ui.textWidth(name, Fonts.SEMIBOLD, size), ui.textWidth(sub, Fonts.MEDIUM, small)) + 22 * s;
			float th = 25 * s;
			float x = sp[0] - tw / 2;
			float y = sp[1] - th - 4 * s;
			ui.glass(x, y, tw, th, 8 * s);
			ui.fill(x, y, tw, th, 8 * s, 0x59000000);
			ui.fill(x + 7 * s, y + th / 2 - 3 * s, 6 * s, 6 * s, 3 * s, 0xFF000000 | (w.color & 0xFFFFFF));
			ui.textMiddle(name, x + 17 * s, y + 8.5f * s, size, 0xFFFFFFFF, Fonts.SEMIBOLD);
			ui.textMiddle(sub, x + 17 * s, y + 18 * s, small, 0xB3FFFFFF, Fonts.MEDIUM);
		}
	}
	static String distance(double blocks) {
		return blocks >= 10_000 ? String.format(Locale.ROOT, "%.1f km", blocks / 1000)
				: blocks >= 1000 ? String.format(Locale.ROOT, "%.2f km", blocks / 1000) : Math.round(blocks) + " m";
	}
}
