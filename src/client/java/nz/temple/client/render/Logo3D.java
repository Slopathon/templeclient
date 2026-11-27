package nz.temple.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import nz.temple.TempleNz;
import org.joml.Matrix3x2fStack;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The Temple logo as a solid 3D object: its outline extruded into a gold slab, turning slowly with a gentle tilt, lit from the
 * top left with a metallic highlight. It is drawn into a texture at the exact screen size every frame, so it stays sharp.
 */
public final class Logo3D {
	private static final float DEPTH = 20;
	private static final float CAMERA = 340;
	private static final float[] LIGHT = normalize(-0.45f, -0.75f, -0.55f);

	private record Face(float[][] points, float[] normal, boolean cap, float depth) {
	}

	private static List<float[][]> sides;

	private final Identifier id;
	private DynamicTexture texture;
	private BufferedImage image;
	private int[] pixels;
	private int size;

	public Logo3D(String name) {
		id = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "logo3d/" + name);
	}

	/**
	 * Draws the turning logo in a {@code size} square at (x, y) in the current pose's units. {@code time} is in seconds;
	 * {@code alpha} fades it.
	 */
	public void draw(GuiGraphicsExtractor g, float x, float y, float size, double time, float alpha) {
		if (size <= 1 || alpha <= 0.01f) {
			return;
		}
		Matrix3x2fStack pose = g.pose();
		float scale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
		float gs = (float) Minecraft.getInstance().getWindow().getGuiScale();
		int px = Math.max(16, Math.min(512, Math.round(size * scale * gs)));
		ensure(px);
		render(time);
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / px, size / px);
		int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
		g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, px, px, px, px, px, px, a << 24 | 0xFFFFFF);
		pose.popMatrix();
	}

	private void render(double time) {
		float yaw = (float) (time * 1.05);
		float pitch = (float) (Math.sin(time * 0.8) * 0.16);
		float cy = (float) Math.cos(yaw);
		float sy = (float) Math.sin(yaw);
		float cp = (float) Math.cos(pitch);
		float sp = (float) Math.sin(pitch);

		List<Face> faces = new ArrayList<>();
		for (float[] shape : LogoMark.shapes()) {
			int n = shape.length / 2;
			float[][] front = new float[n][];
			float[][] back = new float[n][];
			for (int i = 0; i < n; i++) {
				front[i] = rotate(shape[i * 2] - 64, shape[i * 2 + 1] - 64, -DEPTH / 2, cy, sy, cp, sp);
				back[i] = rotate(shape[i * 2] - 64, shape[i * 2 + 1] - 64, DEPTH / 2, cy, sy, cp, sp);
			}
			add(faces, front, rotateDir(0, 0, -1, cy, sy, cp, sp), true);
			add(faces, back, rotateDir(0, 0, 1, cy, sy, cp, sp), true);
		}
		for (float[][] side : sides()) {
			float[] a = side[0];
			float[] b = side[1];
			float[] nrm = side[2];
			float[][] quad = {
					rotate(a[0], a[1], -DEPTH / 2, cy, sy, cp, sp), rotate(b[0], b[1], -DEPTH / 2, cy, sy, cp, sp),
					rotate(b[0], b[1], DEPTH / 2, cy, sy, cp, sp), rotate(a[0], a[1], DEPTH / 2, cy, sy, cp, sp)};
			add(faces, quad, rotateDir(nrm[0], nrm[1], 0, cy, sy, cp, sp), false);
		}
		faces.sort((f1, f2) -> Float.compare(f2.depth(), f1.depth()));

		Graphics2D g2 = image.createGraphics();
		g2.setComposite(AlphaComposite.Clear);
		g2.fillRect(0, 0, size, size);
		g2.setComposite(AlphaComposite.SrcOver);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		float fit = size / 150f;
		for (Face f : faces) {
			Path2D path = new Path2D.Float();
			float top = Float.MAX_VALUE;
			float bottom = -Float.MAX_VALUE;
			for (int i = 0; i < f.points().length; i++) {
				float[] p = f.points()[i];
				float k = CAMERA / (CAMERA + p[2]);
				float sx = size / 2f + p[0] * k * fit;
				float sy2 = size / 2f + p[1] * k * fit;
				top = Math.min(top, sy2);
				bottom = Math.max(bottom, sy2);
				if (i == 0) {
					path.moveTo(sx, sy2);
				} else {
					path.lineTo(sx, sy2);
				}
			}
			path.closePath();
			float[] c = shade(f);
			if (f.cap()) {
				Color hi = color(c, 1.18f);
				Color lo = color(c, 0.78f);
				g2.setPaint(new GradientPaint(0, size * 0.1f, hi, 0, size * 0.9f, lo));
				g2.fill(path);
				g2.setStroke(new BasicStroke(Math.max(0.6f, size / 90f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.setColor(new Color(255, 244, 214, 70));
				g2.draw(path);
			} else {
				g2.setColor(color(c, 1));
				g2.fill(path);
				g2.setStroke(new BasicStroke(Math.max(0.5f, size / 160f)));
				g2.draw(path);
			}
		}
		g2.dispose();
		image.getRGB(0, 0, size, size, pixels, 0, size);
		NativeImage target = texture.getPixels();
		for (int yy = 0; yy < size; yy++) {
			for (int xx = 0; xx < size; xx++) {
				target.setPixel(xx, yy, pixels[yy * size + xx]);
			}
		}
		texture.upload();
	}

	/** Gold, lit by a soft light from the top left with a bright metallic highlight. */
	private static float[] shade(Face f) {
		float[] n = f.normal();
		float diffuse = Math.max(0, n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]);
		float[] h = normalize(LIGHT[0], LIGHT[1], LIGHT[2] - 1);
		float spec = (float) Math.pow(Math.max(0, n[0] * h[0] + n[1] * h[1] + n[2] * h[2]), 28);
		float[] base = f.cap() ? new float[] {236, 186, 72} : new float[] {176, 122, 38};
		float light = 0.32f + 0.78f * diffuse;
		return new float[] {base[0] * light + 255 * spec * 0.75f, base[1] * light + 240 * spec * 0.75f, base[2] * light + 200 * spec * 0.75f};
	}

	private static Color color(float[] c, float m) {
		return new Color(clamp(c[0] * m), clamp(c[1] * m), clamp(c[2] * m));
	}

	private static int clamp(float v) {
		return Math.max(0, Math.min(255, Math.round(v)));
	}

	/** Adds the face if it faces the camera, sitting at (0, 0, -CAMERA) and looking down +z. */
	private static void add(List<Face> faces, float[][] pts, float[] normal, boolean cap) {
		float cx = 0;
		float cy = 0;
		float cz = 0;
		for (float[] p : pts) {
			cx += p[0];
			cy += p[1];
			cz += p[2];
		}
		cx /= pts.length;
		cy /= pts.length;
		cz /= pts.length;
		float toCamera = normal[0] * -cx + normal[1] * -cy + normal[2] * (-CAMERA - cz);
		if (toCamera <= 0) {
			return;
		}
		faces.add(new Face(pts, normal, cap, cz));
	}

	private static float[] rotate(float x, float y, float z, float cy, float sy, float cp, float sp) {
		float x1 = x * cy + z * sy;
		float z1 = -x * sy + z * cy;
		float y2 = y * cp - z1 * sp;
		float z2 = y * sp + z1 * cp;
		return new float[] {x1, y2, z2};
	}

	private static float[] rotateDir(float x, float y, float z, float cy, float sy, float cp, float sp) {
		return rotate(x, y, z, cy, sy, cp, sp);
	}

	/** Each outline edge of the logo: its two ends (centred on 0) and its outward normal. */
	private static synchronized List<float[][]> sides() {
		if (sides != null) {
			return sides;
		}
		List<float[][]> out = new ArrayList<>();
		for (float[] shape : LogoMark.shapes()) {
			int n = shape.length / 2;
			for (int i = 0; i < n; i++) {
				int j = (i + 1) % n;
				float ax = shape[i * 2] - 64;
				float ay = shape[i * 2 + 1] - 64;
				float bx = shape[j * 2] - 64;
				float by = shape[j * 2 + 1] - 64;
				float dx = bx - ax;
				float dy = by - ay;
				float len = (float) Math.sqrt(dx * dx + dy * dy);
				if (len < 1e-3f) {
					continue;
				}
				float nx = dy / len;
				float ny = -dx / len;
				if (inside(shape, (ax + bx) / 2 + 64 + nx * 0.6f, (ay + by) / 2 + 64 + ny * 0.6f)) {
					nx = -nx;
					ny = -ny;
				}
				out.add(new float[][] {{ax, ay}, {bx, by}, {nx, ny}});
			}
		}
		sides = out;
		return out;
	}

	private static boolean inside(float[] poly, float x, float y) {
		boolean in = false;
		int n = poly.length / 2;
		for (int i = 0, j = n - 1; i < n; j = i++) {
			float xi = poly[i * 2];
			float yi = poly[i * 2 + 1];
			float xj = poly[j * 2];
			float yj = poly[j * 2 + 1];
			if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
				in = !in;
			}
		}
		return in;
	}

	private static float[] normalize(float x, float y, float z) {
		float l = (float) Math.sqrt(x * x + y * y + z * z);
		return new float[] {x / l, y / l, z / l};
	}

	private void ensure(int px) {
		if (texture != null && px == size) {
			return;
		}
		size = px;
		image = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
		pixels = new int[px * px];
		var manager = Minecraft.getInstance().getTextureManager();
		if (texture != null) {
			manager.release(id);
		}
		texture = new DynamicTexture(() -> "Temple 3D logo", px, px, true);
		manager.register(id, texture);
	}
}
