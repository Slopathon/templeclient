package nz.temple.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.cosmetic.Cape;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Icons;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.client.render.UserTexture;
import nz.temple.client.util.FilePicker;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class CapeEditorScreen extends Screen {
	private static final float W = 560;
	private static final float H = 340;
	private static final float IMG_W = 330;
	private static final float IMG_H = 280;
	private final Screen parent;
	private final Cape cape;
	private final UserTexture texture;
	private final List<float[]> buttons = new ArrayList<>();
	private final List<Runnable> actions = new ArrayList<>();
	private float s = 1;
	private float imgX;
	private float imgY;
	private float imgScale;
	private int dragMode;
	private double dragOX;
	private double dragOY;
	private double[] dragFrame;
	public CapeEditorScreen(Screen parent, Cape cape) {
		super(Component.literal("Cape editor"));
		this.parent = parent;
		this.cape = cape;
		this.texture = new UserTexture("cape_editor", () -> FilePicker.path(cape.imageSetting()));
	}
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Ui ui = TempleNzClient.ui();
		if (ui == null) {
			return;
		}
		s = UiScale.factor() * 1.25f;
		buttons.clear();
		actions.clear();
		ui.begin(g);
		ui.glassRenderer().placeLayer(g);
		g.fill(0, 0, width, height, 0x90000000);
		float dw = width / s;
		float dh = height / s;
		float x0 = (dw - W) / 2;
		float y0 = (dh - H) / 2;
		ui.transform(0, 0, s, s);
		ui.glass(x0, y0, W, H, 18);
		ui.fill(x0, y0, W, H, 18, 0x50000000);
		int accent = 0xFF000000 | (Theme.primary() & 0xFFFFFF);
		ui.textMiddle("Cape editor", x0 + 18, y0 + 20, 13, 0xFFFFFFFF, Fonts.SEMIBOLD);
		String hint = "Drag the frame to move it, drag its corner or scroll to size it";
		ui.textMiddle(hint, x0 + W - 16 - ui.textWidth(hint, Fonts.MEDIUM, 8), y0 + 20, 8, 0x99FFFFFF, Fonts.MEDIUM);
		float ax = x0 + 16;
		float ay = y0 + 38;
		ui.fill(ax, ay, IMG_W, IMG_H, 10, 0x40000000);
		BufferedImage picture = cape.picture();
		boolean ready = texture.ready() && picture != null;
		if (!ready) {
			String msg = FilePicker.path(cape.imageSetting()) == null ? "Choose an image to start" : "Loading…";
			ui.textMiddle(msg, ax + IMG_W / 2 - ui.textWidth(msg, Fonts.MEDIUM, 10) / 2, ay + IMG_H / 2, 10, 0xB3FFFFFF, Fonts.MEDIUM);
		} else {
			cape.fitFrame(picture);
			int iw = picture.getWidth();
			int ih = picture.getHeight();
			imgScale = Math.min((IMG_W - 16) / iw, (IMG_H - 16) / ih);
			float dispW = iw * imgScale;
			float dispH = ih * imgScale;
			imgX = ax + (IMG_W - dispW) / 2;
			imgY = ay + (IMG_H - dispH) / 2;
			image(g, ui, imgX, imgY, dispW, dispH, 0, 0, 1, 1);
			double[] f = cape.frame();
			float fx = (float) (imgX + f[0] * dispW);
			float fy = (float) (imgY + f[1] * dispH);
			float fw = (float) (f[2] * dispW);
			float fh = (float) (f[2] * iw * Cape.ASPECT * imgScale);
			int dim = 0x99000000;
			rect(g, ui, imgX, imgY, imgX + dispW, fy, dim);
			rect(g, ui, imgX, fy + fh, imgX + dispW, imgY + dispH, dim);
			rect(g, ui, imgX, fy, fx, fy + fh, dim);
			rect(g, ui, fx + fw, fy, imgX + dispW, fy + fh, dim);
			float t = 1.2f;
			rect(g, ui, fx, fy, fx + fw, fy + t, accent);
			rect(g, ui, fx, fy + fh - t, fx + fw, fy + fh, accent);
			rect(g, ui, fx, fy, fx + t, fy + fh, accent);
			rect(g, ui, fx + fw - t, fy, fx + fw, fy + fh, accent);
			rect(g, ui, fx + fw - 5, fy + fh - 5, fx + fw + 5, fy + fh + 5, accent);
			rect(g, ui, fx + fw - 3.5f, fy + fh - 3.5f, fx + fw + 3.5f, fy + fh + 3.5f, 0xFFFFFFFF);
		}
		float px = ax + IMG_W + 24;
		float py = ay;
		ui.textMiddle("Preview", px, py + 6, 9, 0xB3FFFFFF, Fonts.SEMIBOLD);
		float pw = 100;
		float ph = pw * Cape.ASPECT;
		float pcx = px + (W - (px - x0) - 16 - pw) / 2;
		ui.fill(pcx - 6, py + 18, pw + 12, ph + 12, 8, 0x40000000);
		if (ready) {
			double[] f = cape.frame();
			double v1 = f[1] + f[2] * picture.getWidth() * Cape.ASPECT / picture.getHeight();
			image(g, ui, pcx, py + 24, pw, ph, (float) f[0], (float) f[1], (float) (f[0] + f[2]), (float) v1);
		}
		float by = y0 + H - 34;
		float bx = px;
		float bw = W - (px - x0) - 16;
		button(ui, bx, by - 58, bw, "Choose image", Icons.Icon.DOWNLOAD, () -> FilePicker.choose(cape.imageSetting()));
		float half = (bw - 6) / 2;
		button(ui, bx, by - 29, half, "Fit width", Icons.Icon.LAYOUT, this::fitWidth);
		button(ui, bx + half + 6, by - 29, half, "Centre", Icons.Icon.MOVE, this::centre);
		button(ui, bx, by, bw, "Done", Icons.Icon.CHECK, this::onClose);
		ui.resetTransform();
	}
	private static void rect(GuiGraphicsExtractor g, Ui ui, float x0, float y0, float x1, float y1, int argb) {
		if (x1 > x0 && y1 > y0) {
			nz.temple.client.render.Draw2D.rect(g, ui.mapX(x0), ui.mapY(y0), ui.mapX(x1), ui.mapY(y1), argb);
		}
	}
	private void image(GuiGraphicsExtractor g, Ui ui, float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(ui.mapX(x), ui.mapY(y));
		pose.scale(w * s / 1000f, h * s / 1000f);
		g.blit(texture.id(), 0, 0, 1000, 1000, u0, u1, v0, v1);
		pose.popMatrix();
	}
	private void button(Ui ui, float x, float y, float w, String label, Icons.Icon icon, Runnable action) {
		double mx = minecraft.mouseHandler.xpos() / minecraft.getWindow().getGuiScale() / s;
		double my = minecraft.mouseHandler.ypos() / minecraft.getWindow().getGuiScale() / s;
		boolean over = mx >= x && my >= y && mx <= x + w && my <= y + 22;
		ui.fill(x, y, w, 22, 11, over ? 0x40FFFFFF : 0x24FFFFFF);
		float tw = ui.textWidth(label, Fonts.SEMIBOLD, 9);
		float cx = x + (w - tw - 14) / 2;
		Icons.draw(ui.graphics(), icon, ui.mapX(cx + 5), ui.mapY(y + 11), 10 * s, 0xE6FFFFFF);
		ui.textMiddle(label, cx + 14, y + 11, 9, 0xFFFFFFFF, Fonts.SEMIBOLD);
		buttons.add(new float[] {x, y, x + w, y + 22});
		actions.add(action);
	}
	private void fitWidth() {
		BufferedImage p = cape.picture();
		if (p == null) {
			return;
		}
		double w = Math.min(1, p.getHeight() / (p.getWidth() * (double) Cape.ASPECT));
		double[] f = cape.frame();
		cape.setFrame(0, clampY(f[1], w, p), w);
	}
	private void centre() {
		BufferedImage p = cape.picture();
		if (p == null) {
			return;
		}
		double[] f = cape.frame();
		double h = f[2] * p.getWidth() * Cape.ASPECT / p.getHeight();
		cape.setFrame((1 - f[2]) / 2, Math.max(0, (1 - h) / 2), f[2]);
	}
	private static double clampY(double y, double w, BufferedImage p) {
		double h = w * p.getWidth() * Cape.ASPECT / p.getHeight();
		return Math.max(0, Math.min(Math.max(0, 1 - h), y));
	}
	private void setClamped(double x, double y, double w) {
		BufferedImage p = cape.picture();
		if (p == null) {
			return;
		}
		double maxW = Math.min(1, p.getHeight() / (p.getWidth() * (double) Cape.ASPECT));
		w = Math.max(2.0 / p.getWidth(), Math.min(maxW, w));
		x = Math.max(0, Math.min(1 - w, x));
		cape.setFrame(x, clampY(y, w, p), w);
	}
	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		double mx = e.x() / s;
		double my = e.y() / s;
		for (int i = 0; i < buttons.size(); i++) {
			float[] b = buttons.get(i);
			if (mx >= b[0] && my >= b[1] && mx <= b[2] && my <= b[3]) {
				actions.get(i).run();
				return true;
			}
		}
		BufferedImage p = cape.picture();
		if (p == null || imgScale <= 0) {
			return true;
		}
		double[] f = cape.frame();
		double dispW = p.getWidth() * imgScale;
		double dispH = p.getHeight() * imgScale;
		double fx = imgX + f[0] * dispW;
		double fy = imgY + f[1] * dispH;
		double fw = f[2] * dispW;
		double fh = f[2] * p.getWidth() * Cape.ASPECT * imgScale;
		if (Math.abs(mx - (fx + fw)) < 8 && Math.abs(my - (fy + fh)) < 8) {
			dragMode = 2;
		} else if (mx >= fx && mx <= fx + fw && my >= fy && my <= fy + fh) {
			dragMode = 1;
		} else if (mx >= imgX && mx <= imgX + dispW && my >= imgY && my <= imgY + dispH) {
			setClamped((mx - imgX) / dispW - f[2] / 2, (my - imgY) / dispH - fh / dispH / 2, f[2]);
			dragMode = 1;
			f = cape.frame();
		} else {
			dragMode = 0;
		}
		dragOX = mx;
		dragOY = my;
		dragFrame = f;
		return true;
	}
	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		BufferedImage p = cape.picture();
		if (p == null || dragMode == 0 || dragFrame == null) {
			return true;
		}
		double mx = e.x() / s;
		double my = e.y() / s;
		double dispW = p.getWidth() * imgScale;
		double dispH = p.getHeight() * imgScale;
		if (dragMode == 1) {
			setClamped(dragFrame[0] + (mx - dragOX) / dispW, dragFrame[1] + (my - dragOY) / dispH, dragFrame[2]);
		} else {
			double grow = Math.max((mx - dragOX) / dispW, (my - dragOY) / (p.getWidth() * Cape.ASPECT * imgScale));
			setClamped(dragFrame[0], dragFrame[1], dragFrame[2] + grow);
		}
		return true;
	}
	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragMode = 0;
		return true;
	}
	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		double[] f = cape.frame();
		double w = f[2] * (scrollY > 0 ? 0.92 : 1.08);
		BufferedImage p = cape.picture();
		if (p != null) {
			double h0 = f[2] * p.getWidth() * Cape.ASPECT / p.getHeight();
			double h1 = w * p.getWidth() * Cape.ASPECT / p.getHeight();
			setClamped(f[0] + (f[2] - w) / 2, f[1] + (h0 - h1) / 2, w);
		}
		return true;
	}
	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(e);
	}
}
