package nz.temple.client.render.title;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class LetterMesh {
	public record Quad(float[] v, int side) {
	}
	public record Glyph(char c, List<Quad> quads, float width, float height, float bottom, float advance) {
	}
	private static final int RASTER = 256;
	private static final int STEP = 4;
	private LetterMesh() {
	}
	public static List<Glyph> build(String text, String fontPath, float capHeight, float depth) {
		Font font;
		try (InputStream in = LetterMesh.class.getResourceAsStream(fontPath)) {
			if (in == null) {
				return List.of();
			}
			font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(Font.PLAIN, (float) RASTER);
		} catch (Exception e) {
			return List.of();
		}
		FontRenderContext frc = new FontRenderContext(null, true, true);
		GlyphVector capProbe = font.createGlyphVector(frc, "H");
		double capPx = capProbe.getVisualBounds().getHeight();
		float scale = (float) (capHeight / capPx);
		List<Glyph> out = new ArrayList<>();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			GlyphVector gv = font.createGlyphVector(frc, String.valueOf(c));
			float advance = (float) gv.getGlyphMetrics(0).getAdvance() * scale;
			Rectangle2D bounds = gv.getVisualBounds();
			if (bounds.isEmpty()) {
				out.add(new Glyph(c, List.of(), 0, 0, 0, advance));
				continue;
			}
			int pad = STEP * 3;
			int w = (int) Math.ceil(bounds.getWidth()) + pad * 2;
			int h = (int) Math.ceil(bounds.getHeight()) + pad * 2;
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g.setFont(font);
			g.setColor(java.awt.Color.WHITE);
			float ox = (float) (pad - bounds.getX());
			float oy = (float) (pad - bounds.getY());
			g.drawGlyphVector(gv, ox, oy);
			g.dispose();
			int gw = w / STEP + 1;
			int gh = h / STEP + 1;
			float[][] field = new float[gw][gh];
			var raster = img.getRaster();
			for (int x = 0; x < gw; x++) {
				for (int y = 0; y < gh; y++) {
					float sum = 0;
					int n = 0;
					for (int dx = -STEP / 2; dx <= STEP / 2; dx++) {
						for (int dy = -STEP / 2; dy <= STEP / 2; dy++) {
							int px = x * STEP + dx;
							int py = y * STEP + dy;
							if (px >= 0 && py >= 0 && px < w && py < h) {
								sum += raster.getSample(px, py, 0) / 255f;
							}
							n++;
						}
					}
					field[x][y] = sum / n;
				}
			}
			float centre = (float) (bounds.getX() + bounds.getWidth() / 2);
			Builder b = new Builder(field, gw, gh, STEP, scale, ox + centre, oy, depth);
			b.trace();
			float gwBlocks = (float) bounds.getWidth() * scale;
			float top = (float) -bounds.getY() * scale;
			float bottom = (float) -(bounds.getY() + bounds.getHeight()) * scale;
			out.add(new Glyph(c, b.quads, gwBlocks, top, bottom, advance));
		}
		return out;
	}
	private static final class Builder {
		final float[][] f;
		final int gw;
		final int gh;
		final int step;
		final float scale;
		final float ox;
		final float oy;
		final float half;
		final List<Quad> quads = new ArrayList<>();
		Builder(float[][] f, int gw, int gh, int step, float scale, float ox, float oy, float depth) {
			this.f = f;
			this.gw = gw;
			this.gh = gh;
			this.step = step;
			this.scale = scale;
			this.ox = ox;
			this.oy = oy;
			this.half = depth / 2;
		}
		float x(float gx) {
			return (gx * step - ox) * scale;
		}
		float y(float gy) {
			return (oy - gy * step) * scale;
		}

		float at(float gx, float gy) {
			int x0 = Math.max(0, Math.min(gw - 1, (int) Math.floor(gx)));
			int y0 = Math.max(0, Math.min(gh - 1, (int) Math.floor(gy)));
			int x1 = Math.min(gw - 1, x0 + 1);
			int y1 = Math.min(gh - 1, y0 + 1);
			float tx = gx - x0;
			float ty = gy - y0;
			float a = f[x0][y0] + (f[x1][y0] - f[x0][y0]) * tx;
			float c = f[x0][y1] + (f[x1][y1] - f[x0][y1]) * tx;
			return a + (c - a) * ty;
		}
		float[] normal(float gx, float gy) {
			float e = 0.5f;
			float dx = at(gx + e, gy) - at(gx - e, gy);
			float dy = at(gx, gy + e) - at(gx, gy - e);
			float nx = -dx;
			float ny = dy;
			float len = (float) Math.sqrt(nx * nx + ny * ny);
			return len < 1e-6f ? new float[] {0, 0} : new float[] {nx / len, ny / len};
		}
		void trace() {
			for (int y = 0; y + 1 < gh; y++) {
				int runStart = -1;
				for (int x = 0; x + 1 <= gw; x++) {
					boolean full = x + 1 < gw && inside(x, y) && inside(x + 1, y) && inside(x, y + 1) && inside(x + 1, y + 1);
					if (full && runStart < 0) {
						runStart = x;
					}
					if (!full && runStart >= 0) {
						face(new float[][] {{runStart, y}, {x, y}, {x, y + 1}, {runStart, y + 1}});
						runStart = -1;
					}
					if (x + 1 < gw && !full) {
						cell(x, y);
					}
				}
			}
		}
		boolean inside(int x, int y) {
			return f[x][y] >= 0.5f;
		}
		void cell(int x, int y) {
			int[][] corners = {{x, y}, {x + 1, y}, {x + 1, y + 1}, {x, y + 1}};
			int mask = 0;
			for (int i = 0; i < 4; i++) {
				if (inside(corners[i][0], corners[i][1])) {
					mask |= 1 << i;
				}
			}
			if (mask == 0) {
				return;
			}
			List<float[]> poly = new ArrayList<>();
			List<float[]> crossings = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				int[] a = corners[i];
				int[] b = corners[(i + 1) % 4];
				float va = f[a[0]][a[1]];
				float vb = f[b[0]][b[1]];
				if (va >= 0.5f) {
					poly.add(new float[] {a[0], a[1]});
				}
				if ((va >= 0.5f) != (vb >= 0.5f)) {
					float t = (0.5f - va) / (vb - va);
					float[] p = {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
					poly.add(p);
					crossings.add(p);
				}
			}
			if (poly.size() >= 3) {
				face(poly.toArray(new float[0][]));
			}
			for (int i = 0; i + 1 < crossings.size(); i += 2) {
				side(crossings.get(i), crossings.get(i + 1));
			}
		}
		void face(float[][] p) {
			for (int i = 1; i + 1 < p.length; i += 2) {
				float[] a = p[0];
				float[] b = p[i];
				float[] c = p[i + 1];
				float[] d = i + 2 < p.length ? p[i + 2] : p[i + 1];
				quads.add(new Quad(new float[] {
						x(a[0]), y(a[1]), half, 0, 0, 1,
						x(b[0]), y(b[1]), half, 0, 0, 1,
						x(c[0]), y(c[1]), half, 0, 0, 1,
						x(d[0]), y(d[1]), half, 0, 0, 1}, 0));
				quads.add(new Quad(new float[] {
						x(d[0]), y(d[1]), -half, 0, 0, -1,
						x(c[0]), y(c[1]), -half, 0, 0, -1,
						x(b[0]), y(b[1]), -half, 0, 0, -1,
						x(a[0]), y(a[1]), -half, 0, 0, -1}, 0));
			}
		}
		void side(float[] a, float[] b) {
			float[] na = normal(a[0], a[1]);
			float[] nb = normal(b[0], b[1]);
			quads.add(new Quad(new float[] {
					x(a[0]), y(a[1]), half, na[0], na[1], 0,
					x(b[0]), y(b[1]), half, nb[0], nb[1], 0,
					x(b[0]), y(b[1]), -half, nb[0], nb[1], 0,
					x(a[0]), y(a[1]), -half, na[0], na[1], 0}, 1));
		}
	}
}
