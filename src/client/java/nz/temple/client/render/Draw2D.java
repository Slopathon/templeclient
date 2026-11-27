package nz.temple.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

public final class Draw2D {
	private Draw2D() {
	}

	static float guiScale() {
		return Math.max(1, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
	}

	public interface Rects {
		void fill(int x0, int y0, int x1, int y1, int argb);
	}

	private static void direct(GuiGraphicsExtractor g, java.util.function.Consumer<Rects> shape) {
		float s = guiScale();
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(1 / s, 1 / s);
		shape.accept(g::fill);
		pose.popMatrix();
	}

	public static void pixelRect(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int argb) {
		float s = guiScale();
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(1 / s, 1 / s);
		g.fill(Math.round(x0 * s), Math.round(y0 * s), Math.round(x1 * s), Math.round(y1 * s), argb);
		pose.popMatrix();
	}

	public static void pixelRect(Rects out, float x0, float y0, float x1, float y1, int argb) {
		float s = guiScale();
		out.fill(Math.round(x0 * s), Math.round(y0 * s), Math.round(x1 * s), Math.round(y1 * s), argb);
	}

	public static void pixelRound(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float radius, int argb) {
		direct(g, out -> pixelRound(out, x0, y0, x1, y1, radius, argb));
	}
	public static void pixelRound(Rects out, float x0, float y0, float x1, float y1, float radius, int argb) {
		float s = guiScale();
		int ax = Math.round(x0 * s), ay = Math.round(y0 * s), bx = Math.round(x1 * s), by = Math.round(y1 * s);
		int r = Math.max(0, Math.min(Math.round(radius), Math.min((bx - ax) / 2, (by - ay) / 2)));
		int i = 0;
		int flat = r;
		while (i < r) {
			int inset = arcInset(r, i);
			if (inset == 0) {
				flat = i;
				break;
			}
			int j = i + 1;
			while (j < r && arcInset(r, j) == inset) {
				j++;
			}
			out.fill(ax + inset, ay + i, bx - inset, ay + j, argb);
			out.fill(ax + inset, by - j, bx - inset, by - i, argb);
			i = j;
		}
		out.fill(ax, ay + flat, bx, by - flat, argb);
	}
	private static int arcInset(int r, int i) {
		double dy = r - i - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
	}
	public static void circle(GuiGraphicsExtractor g, float cx, float cy, float r, int argb) {
		pixelRound(g, cx - r, cy - r, cx + r, cy + r, r * guiScale(), argb);
	}
	public static void pixelOutline(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int px, int argb) {
		direct(g, out -> pixelOutline(out, x0, y0, x1, y1, px, argb));
	}
	public static void pixelOutline(Rects out, float x0, float y0, float x1, float y1, int px, int argb) {
		float s = guiScale();
		int ax = Math.round(x0 * s), ay = Math.round(y0 * s), bx = Math.round(x1 * s), by = Math.round(y1 * s);
		out.fill(ax, ay, bx, ay + px, argb);
		out.fill(ax, by - px, bx, by, argb);
		out.fill(ax, ay + px, ax + px, by - px, argb);
		out.fill(bx - px, ay + px, bx, by - px, argb);
	}
	public static void pixelCorners(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int px, float len, int argb) {
		direct(g, out -> pixelCorners(out, x0, y0, x1, y1, px, len, argb));
	}
	public static void pixelCorners(Rects out, float x0, float y0, float x1, float y1, int px, float len, int argb) {
		float s = guiScale();
		int ax = Math.round(x0 * s), ay = Math.round(y0 * s), bx = Math.round(x1 * s), by = Math.round(y1 * s);
		int lh = Math.round(Math.min(len, (x1 - x0) / 2) * s);
		int lv = Math.round(Math.min(len, (y1 - y0) / 2) * s);
		out.fill(ax, ay, ax + lh, ay + px, argb);
		out.fill(ax, ay, ax + px, ay + lv, argb);
		out.fill(bx - lh, ay, bx, ay + px, argb);
		out.fill(bx - px, ay, bx, ay + lv, argb);
		out.fill(ax, by - px, ax + lh, by, argb);
		out.fill(ax, by - lv, ax + px, by, argb);
		out.fill(bx - lh, by - px, bx, by, argb);
		out.fill(bx - px, by - lv, bx, by, argb);
	}
	public static void rect(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int argb) {
		g.fill(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), argb);
	}
	public static void outline(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float thickness, int argb) {
		float t = Math.max(1, thickness);
		rect(g, x0, y0, x1, y0 + t, argb);
		rect(g, x0, y1 - t, x1, y1, argb);
		rect(g, x0, y0, x0 + t, y1, argb);
		rect(g, x1 - t, y0, x1, y1, argb);
	}
	public static void corners(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float thickness, float len, int argb) {
		float t = Math.max(1, thickness);
		float l = Math.min(len, (x1 - x0) / 2);
		float lv = Math.min(len, (y1 - y0) / 2);
		rect(g, x0, y0, x0 + l, y0 + t, argb);
		rect(g, x0, y0, x0 + t, y0 + lv, argb);
		rect(g, x1 - l, y0, x1, y0 + t, argb);
		rect(g, x1 - t, y0, x1, y0 + lv, argb);
		rect(g, x0, y1 - t, x0 + l, y1, argb);
		rect(g, x0, y1 - lv, x0 + t, y1, argb);
		rect(g, x1 - l, y1 - t, x1, y1, argb);
		rect(g, x1 - t, y1 - lv, x1, y1, argb);
	}
	public static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float thickness, int argb) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.5f) {
			return;
		}
		float t = Math.max(1, thickness);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x0, y0);
		pose.rotate((float) Math.atan2(dy, dx));
		g.fill(0, Math.round(-t / 2), Math.round(len), Math.round(t / 2 + 0.5f), argb);
		pose.popMatrix();
	}
	public static void navigationArrow(GuiGraphicsExtractor g, float cx, float cy, float angle, int argb) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.rotate(angle);
		line(g, 0, 6, 0, -6, 3.4f, 0x96000000);
		line(g, 0, 6, 0, -6, 1.1f, argb);
		line(g, 0, -6, -3.3f, 0.8f, 1.1f, argb);
		line(g, 0, -6, 3.3f, 0.8f, 1.1f, argb);
		line(g, -2.4f, 4.2f, 0, 6, 1, 0xB8FFFFFF);
		line(g, 2.4f, 4.2f, 0, 6, 1, 0xB8FFFFFF);
		pose.popMatrix();
	}
	public static void bar(GuiGraphicsExtractor g, float x, float y, float w, float h, float fraction, int fillArgb, int backArgb) {
		rect(g, x, y, x + w, y + h, backArgb);
		rect(g, x, y, x + w * Math.max(0, Math.min(1, fraction)), y + h, fillArgb);
	}
}
