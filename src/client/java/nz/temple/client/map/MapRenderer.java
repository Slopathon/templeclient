package nz.temple.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2fStack;
import nz.temple.client.render.Draw2D;
import nz.temple.client.util.ServerKey;
import nz.temple.client.util.Waypoints;

import java.util.List;

public final class MapRenderer {
	private MapRenderer() {
	}
	public record View(float sx, float sy, double cx, double cz, float scale, float angle) {

		public float[] toScreen(double x, double z) {
			double dx = (x - cx) * scale;
			double dz = (z - cz) * scale;
			if (angle != 0) {
				double c = Math.cos(angle);
				double s = Math.sin(angle);
				double rx = dx * c - dz * s;
				dz = dx * s + dz * c;
				dx = rx;
			}
			return new float[] {(float) (sx + dx), (float) (sy + dz)};
		}
		public double[] toWorld(double px, double py) {
			double dx = px - sx;
			double dz = py - sy;
			if (angle != 0) {
				double c = Math.cos(-angle);
				double s = Math.sin(-angle);
				double rx = dx * c - dz * s;
				dz = dx * s + dz * c;
				dx = rx;
			}
			return new double[] {cx + dx / scale, cz + dz / scale};
		}
	}
	public static void tiles(GuiGraphicsExtractor g, View v, float radiusPx, int limit) {
		MapStore store = MapStore.get();
		double half = radiusPx / v.scale();
		int tx0 = (int) Math.floor((v.cx() - half) / MapStore.TILE);
		int tx1 = (int) Math.floor((v.cx() + half) / MapStore.TILE);
		int tz0 = (int) Math.floor((v.cz() - half) / MapStore.TILE);
		int tz1 = (int) Math.floor((v.cz() + half) / MapStore.TILE);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(v.sx(), v.sy());
		if (v.angle() != 0) {
			pose.rotate(v.angle());
		}
		for (MapStore.Tile t : store.tilesIn(tx0, tz0, tx1, tz1, limit)) {
			var id = store.texture(t);
			if (id == null) {
				continue;
			}
			float px = (float) ((t.tx * (double) MapStore.TILE - v.cx()) * v.scale());
			float pz = (float) ((t.tz * (double) MapStore.TILE - v.cz()) * v.scale());
			pose.pushMatrix();
			pose.translate(px, pz);
			pose.scale(v.scale(), v.scale());
			g.blit(id, 0, 0, MapStore.TILE, MapStore.TILE, 0, 1, 0, 1);
			pose.popMatrix();
		}
		pose.popMatrix();
	}
	public static void trail(GuiGraphicsExtractor g, View v, List<int[]> trail, float x0, float y0, float x1, float y1, int argb, float width) {
		float[] last = null;
		float minStep = 2.5f;
		for (int[] p : trail) {
			if (p[0] == MapStore.BREAK) {
				last = null;
				continue;
			}
			float[] s = v.toScreen(p[0] + 0.5, p[1] + 0.5);
			if (last != null) {
				float dx = s[0] - last[0];
				float dy = s[1] - last[1];
				if (dx * dx + dy * dy < minStep * minStep) {
					continue;
				}
				boolean visible = !(Math.max(s[0], last[0]) < x0 || Math.min(s[0], last[0]) > x1
						|| Math.max(s[1], last[1]) < y0 || Math.min(s[1], last[1]) > y1);
				if (visible) {
					nz.temple.client.render.Sprites.line(g, last[0], last[1], s[0], s[1], width, argb);
				}
			}
			last = s;
		}
	}
	public static void players(GuiGraphicsExtractor g, View v, float dot, int argb) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		for (Player p : mc.level.players()) {
			if (p == mc.player) {
				continue;
			}
			float[] s = v.toScreen(p.getX(), p.getZ());
			Draw2D.circle(g, s[0], s[1], dot + 1, 0xC0000000);
			Draw2D.circle(g, s[0], s[1], dot, argb);
		}
	}
	public static double[] where(Waypoints.Waypoint w, String dim) {
		return w.showsIn(dim) ? w.in(dim) : null;
	}
	public static String dimension() {
		String d = ServerKey.dimension();
		return d == null ? "overworld" : d;
	}
}
