package nz.temple.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import nz.temple.TempleNz;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

public final class SmoothGraph {
	private final Identifier id;
	private DynamicTexture texture;
	private BufferedImage image;
	private int[] pixels;
	private int width;
	private int height;
	public SmoothGraph(String name) {
		id = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "graph/" + name);
	}

	public void draw(GuiGraphicsExtractor g, float x, float y, float w, float h, float[] values, int count, int rgb, float lineWidth) {
		if (count < 2 || w < 2 || h < 2) {
			return;
		}
		float gs = (float) Minecraft.getInstance().getWindow().getGuiScale();
		int pw = Math.max(2, Math.round(w * gs));
		int ph = Math.max(2, Math.round(h * gs));
		ensure(pw, ph);
		Graphics2D g2 = image.createGraphics();
		g2.setComposite(AlphaComposite.Clear);
		g2.fillRect(0, 0, pw, ph);
		g2.setComposite(AlphaComposite.SrcOver);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		float line = Math.max(1, lineWidth * gs);
		float pad = line * 2.5f;
		float top = pad;
		float bottom = ph - 1;
		Path2D curve = new Path2D.Float();
		float[] px = new float[count];
		float[] py = new float[count];
		for (int i = 0; i < count; i++) {
			px[i] = (pw - pad) * i / (count - 1f);
			py[i] = bottom - (bottom - top) * Math.max(0, Math.min(1, values[i]));
		}
		curve.moveTo(px[0], py[0]);
		for (int i = 1; i < count - 1; i++) {
			curve.quadTo(px[i], py[i], (px[i] + px[i + 1]) / 2, (py[i] + py[i + 1]) / 2);
		}
		curve.lineTo(px[count - 1], py[count - 1]);
		Color solid = new Color(rgb & 0xFFFFFF);
		Path2D area = new Path2D.Float(curve);
		area.lineTo(px[count - 1], bottom);
		area.lineTo(px[0], bottom);
		area.closePath();
		g2.setPaint(new GradientPaint(0, top, new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 110), 0, bottom,
				new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 0)));
		g2.fill(area);
		g2.setStroke(new BasicStroke(line * 3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 46));
		g2.draw(curve);
		g2.setStroke(new BasicStroke(line, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(solid);
		g2.draw(curve);
		g2.setComposite(AlphaComposite.DstIn);
		g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 70), pw * 0.6f, 0, new Color(0, 0, 0, 255)));
		g2.fillRect(0, 0, pw, ph);
		g2.setComposite(AlphaComposite.SrcOver);
		float lx = px[count - 1];
		float ly = py[count - 1];
		float halo = line * 3.4f;
		g2.setColor(new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 80));
		g2.fill(new Ellipse2D.Float(lx - halo, ly - halo, halo * 2, halo * 2));
		float dot = line * 1.9f;
		g2.setColor(Color.WHITE);
		g2.fill(new Ellipse2D.Float(lx - dot, ly - dot, dot * 2, dot * 2));
		g2.dispose();
		image.getRGB(0, 0, pw, ph, pixels, 0, pw);
		NativeImage target = texture.getPixels();
		for (int yy = 0; yy < ph; yy++) {
			for (int xx = 0; xx < pw; xx++) {
				target.setPixel(xx, yy, pixels[yy * pw + xx]);
			}
		}
		texture.upload();
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(1 / gs, 1 / gs);
		g.blit(id, 0, 0, pw, ph, 0, 1, 0, 1);
		pose.popMatrix();
	}
	private void ensure(int pw, int ph) {
		if (texture != null && pw == width && ph == height) {
			return;
		}
		width = pw;
		height = ph;
		image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
		pixels = new int[pw * ph];
		var manager = Minecraft.getInstance().getTextureManager();
		if (texture != null) {
			manager.release(id);
		}
		texture = new DynamicTexture(() -> "Temple graph", pw, ph, true);
		manager.register(id, texture);
	}
}
