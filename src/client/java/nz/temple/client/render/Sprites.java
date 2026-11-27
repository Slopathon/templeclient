package nz.temple.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import nz.temple.TempleNz;

public final class Sprites {
	private static final int SIZE = 128;
	private static final Identifier ARROW = id("nav_arrow");
	private static final Identifier ARROW_SHADOW = id("nav_arrow_shadow");
	private static final Identifier CHEVRON = id("waypoint_chevron");
	private static final Identifier CHEVRON_SHADOW = id("waypoint_chevron_shadow");
	private static final Identifier LINE = id("line");
	private static final Identifier DOT = id("dot");
	private static final float LINE_CORE = 22f / 32f;
	private Sprites() {
	}

	private static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "textures/gui/" + name + ".png");
	}
	public static void arrow(GuiGraphicsExtractor g, float cx, float cy, float size, float angle, int argb) {
		draw(g, ARROW_SHADOW, cx + size * 0.03f, cy + size * 0.07f, size, angle, 0.5f, 0.5f, 0x8C000000);
		draw(g, ARROW, cx, cy, size, angle, 0.5f, 0.5f, argb);
	}
	public static float marker(GuiGraphicsExtractor g, float x, float y, float size, int argb) {

		float ay = 92f / SIZE;
		draw(g, CHEVRON_SHADOW, x, y + size * 0.03f, size, 0, 0.5f, ay, ((argb >>> 24) * 3 / 5) << 24);
		draw(g, CHEVRON, x, y, size, 0, 0.5f, ay, argb);
		return y - size * (92f - 36f) / SIZE;
	}
	public static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float width, int argb) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		float full = width / LINE_CORE;
		if (len > 0.01f) {
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(x0, y0);
			pose.rotate((float) Math.atan2(dy, dx));
			pose.scale(len / 4f, full / 32f);
			pose.translate(0, -16);
			g.blit(RenderPipelines.GUI_TEXTURED, LINE, 0, 0, 0, 0, 4, 32, 4, 32, 4, 32, argb);
			pose.popMatrix();
		}
		dot(g, x1, y1, width, argb);
	}
	public static void dot(GuiGraphicsExtractor g, float x, float y, float width, int argb) {
		float full = width / LINE_CORE;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x - full / 2, y - full / 2);
		pose.scale(full / 32f, full / 32f);
		g.blit(RenderPipelines.GUI_TEXTURED, DOT, 0, 0, 0, 0, 32, 32, 32, 32, 32, 32, argb);
		pose.popMatrix();
	}
	private static void draw(GuiGraphicsExtractor g, Identifier sprite, float x, float y, float size, float angle, float ax, float ay, int argb) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		if (angle != 0) {
			pose.rotate(angle);
		}
		pose.scale(size / SIZE, size / SIZE);
		pose.translate(-ax * SIZE, -ay * SIZE);
		g.blit(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, 0, 0, SIZE, SIZE, SIZE, SIZE, SIZE, SIZE, argb);
		pose.popMatrix();
	}
}
