package nz.temple.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import nz.temple.TempleNz;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.cosmetic.CustomModels;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Icons;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;
import nz.temple.client.render.vrm.JigglePaint;
import nz.temple.client.render.vrm.VrmPreview;

import java.util.ArrayList;
import java.util.List;

public final class JiggleEditorScreen extends Screen {
	private static final float W = 600;
	private static final float H = 380;
	private static final float VIEW_W = 330;
	private static final float VIEW_H = 330;
	private static JiggleEditorScreen open;
	private final Screen parent;
	private final CustomModels module;
	private final JigglePaint paint;
	private final VrmPreview preview;
	private final Identifier id = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "jiggle_preview");
	private DynamicTexture texture;
	private int[] pixels;
	private int texW;
	private int texH;
	private boolean dirty = true;
	private final List<float[]> buttons = new ArrayList<>();
	private final List<Runnable> actions = new ArrayList<>();
	private float s = 1;
	private float viewX;
	private float viewY;
	private float yaw;
	private float pitch = 8;
	private float zoom = 1;
	private float lift;
	private int drag = -1;
	private double lastX;
	private double lastY;
	public JiggleEditorScreen(Screen parent, CustomModels module, JigglePaint paint, VrmPreview preview) {
		super(Component.literal("Jiggle painter"));
		this.parent = parent;
		this.module = module;
		this.paint = paint;
		this.preview = preview;
		open = this;
	}
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		module.saveJiggle();
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(id);
			texture = null;
		}
		if (open == this) {
			open = null;
		}
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
		ui.textMiddle("Jiggle painter", x0 + 18, y0 + 20, 13, 0xFFFFFFFF, Fonts.SEMIBOLD);
		String hint = "Left drag paints, right drag turns, scroll zooms";
		ui.textMiddle(hint, x0 + W - 16 - ui.textWidth(hint, Fonts.MEDIUM, 8), y0 + 20, 8, 0x99FFFFFF, Fonts.MEDIUM);
		viewX = x0 + 16;
		viewY = y0 + 36;
		ui.fill(viewX, viewY, VIEW_W, VIEW_H, 10, 0x40000000);
		ensureTexture(ui);
		if (dirty) {
			preview.render(pixels, texW, texH, yaw, pitch, zoom, lift);
			NativeImage img = texture.getPixels();
			for (int y = 0; y < texH; y++) {
				for (int x = 0; x < texW; x++) {
					img.setPixel(x, y, pixels[y * texW + x]);
				}
			}
			texture.upload();
			dirty = false;
		}
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(ui.mapX(viewX), ui.mapY(viewY));
		pose.scale(VIEW_W * s / 1000f, VIEW_H * s / 1000f);
		g.blit(id, 0, 0, 1000, 1000, 0, 1, 0, 1);
		pose.popMatrix();
		double mx = mouseX / s;
		double my = mouseY / s;
		if (mx >= viewX && my >= viewY && mx <= viewX + VIEW_W && my <= viewY + VIEW_H) {
			float r = brushRadius();
			ui.fill((float) mx - r, (float) my - r, r * 2, r * 2, r, switch (module.jiggleTool()) {
				case JIGGLE -> 0x40FF8A30;
				case MASS -> 0x4046AAFF;
				default -> 0x40FFFFFF;
			});
		}
		float px = viewX + VIEW_W + 18;
		float pw = x0 + W - 16 - px;
		float y = viewY;
		float half = (pw - 6) / 2;
		label(ui, "Brush", px, y);
		y += 14;
		tool(ui, px, y, half, "Jiggle", Icons.Icon.PENCIL, VrmPreview.Tool.JIGGLE, accent);
		tool(ui, px + half + 6, y, half, "Erase", Icons.Icon.TRASH, VrmPreview.Tool.ERASE, accent);
		y += 27;
		tool(ui, px, y, half, "Mass", Icons.Icon.PLUS, VrmPreview.Tool.MASS, accent);
		tool(ui, px + half + 6, y, half, "Deflate", Icons.Icon.UNDO, VrmPreview.Tool.DEFLATE, accent);
		y += 28;
		y = stepper(ui, px, y, pw, "Size", Math.round(module.jiggleBrushSize()) + " px", () -> module.nudgeJiggleBrush(-2, 0), () -> module.nudgeJiggleBrush(2, 0));
		y = stepper(ui, px, y, pw, "Strength", Math.round(module.jiggleBrushStrength() * 100) + "%", () -> module.nudgeJiggleBrush(0, -0.05f),
				() -> module.nudgeJiggleBrush(0, 0.05f));
		button(ui, px, y, pw, "Mirror: " + (module.jiggleMirror() ? "on" : "off"), Icons.Icon.LAYOUT, module.jiggleMirror(), accent,
				() -> module.setJiggleMirror(!module.jiggleMirror()));
		y += 32;
		label(ui, "Spring", px, y);
		y += 14;
		y = stepper(ui, px, y, pw, "Stiffness", Math.round(paint.stiffness * 100) + "%", () -> spring(0, -0.05f), () -> spring(0, 0.05f));
		y = stepper(ui, px, y, pw, "Damping", Math.round(paint.damping * 100) + "%", () -> spring(1, -0.05f), () -> spring(1, 0.05f));
		stepper(ui, px, y, pw, "Amount", Math.round(paint.amount * 100) + "%", () -> spring(2, -0.1f), () -> spring(2, 0.1f));
		float by = y0 + H - 34;
		boolean massTool = module.jiggleTool() == VrmPreview.Tool.MASS || module.jiggleTool() == VrmPreview.Tool.DEFLATE;
		button(ui, px, by - 29, half, massTool ? "Clear mass" : "Clear jiggle", Icons.Icon.RESET, false, accent, () -> {
			if (massTool) {
				paint.clearMass();
			} else {
				paint.clear();
			}
			dirty = true;
		});
		button(ui, px + half + 6, by - 29, half, "Reset view", Icons.Icon.REFRESH, false, accent, () -> {
			yaw = 0;
			pitch = 8;
			zoom = 1;
			lift = 0;
			dirty = true;
		});
		button(ui, px, by, pw, "Done", Icons.Icon.CHECK, false, accent, this::onClose);
		ui.resetTransform();
	}
	private void tool(Ui ui, float x, float y, float w, String label, Icons.Icon icon, VrmPreview.Tool tool, int accent) {
		button(ui, x, y, w, label, icon, module.jiggleTool() == tool, accent, () -> module.setJiggleTool(tool));
	}
	private void spring(int which, float by) {
		switch (which) {
			case 0 -> paint.stiffness = clamp(paint.stiffness + by, 0, 1);
			case 1 -> paint.damping = clamp(paint.damping + by, 0, 1);
			default -> paint.amount = clamp(paint.amount + by, 0, 2);
		}
		module.jiggleSpringChanged();
	}
	private static float clamp(float v, float lo, float hi) {
		return Math.round(Math.max(lo, Math.min(hi, v)) * 100) / 100f;
	}

	private void ensureTexture(Ui ui) {
		double gs = minecraft.getWindow().getGuiScale();
		int w = (int) Math.max(64, Math.min(640, VIEW_W * s * gs));
		int h = (int) Math.max(64, Math.min(640, VIEW_H * s * gs));
		if (texture != null && w == texW && h == texH) {
			return;
		}
		texW = w;
		texH = h;
		pixels = new int[w * h];
		var manager = Minecraft.getInstance().getTextureManager();
		if (texture != null) {
			manager.release(id);
		}
		texture = new DynamicTexture(() -> "Temple jiggle preview", w, h, true);
		manager.register(id, texture);
		dirty = true;
	}
	private float brushRadius() {
		return module.jiggleBrushSize() * VIEW_W / Math.max(1, texW);
	}

	private void label(Ui ui, String text, float x, float y) {
		ui.textMiddle(text, x, y + 5, 9, 0xB3FFFFFF, Fonts.SEMIBOLD);
	}
	private float stepper(Ui ui, float x, float y, float w, String name, String value, Runnable less, Runnable more) {
		ui.fill(x, y, w, 22, 11, 0x24FFFFFF);
		ui.textMiddle(name, x + 10, y + 11, 9, 0xFFFFFFFF, Fonts.SEMIBOLD);
		float vw = ui.textWidth(value, Fonts.MEDIUM, 9);
		ui.textMiddle(value, x + w - 52 - vw, y + 11, 9, 0xCCFFFFFF, Fonts.MEDIUM);
		small(ui, x + w - 46, y + 2, "-", less);
		small(ui, x + w - 22, y + 2, "+", more);
		return y + 27;
	}
	private void small(Ui ui, float x, float y, String text, Runnable action) {
		double mx = minecraft.mouseHandler.xpos() / minecraft.getWindow().getGuiScale() / s;
		double my = minecraft.mouseHandler.ypos() / minecraft.getWindow().getGuiScale() / s;
		boolean over = mx >= x && my >= y && mx <= x + 18 && my <= y + 18;
		ui.fill(x, y, 18, 18, 9, over ? 0x50FFFFFF : 0x30FFFFFF);
		ui.textMiddle(text, x + 9 - ui.textWidth(text, Fonts.SEMIBOLD, 10) / 2, y + 9, 10, 0xFFFFFFFF, Fonts.SEMIBOLD);
		buttons.add(new float[] {x, y, x + 18, y + 18});
		actions.add(action);
	}
	private void button(Ui ui, float x, float y, float w, String label, Icons.Icon icon, boolean on, int accent, Runnable action) {
		double mx = minecraft.mouseHandler.xpos() / minecraft.getWindow().getGuiScale() / s;
		double my = minecraft.mouseHandler.ypos() / minecraft.getWindow().getGuiScale() / s;
		boolean over = mx >= x && my >= y && mx <= x + w && my <= y + 22;
		ui.fill(x, y, w, 22, 11, on ? (accent & 0x00FFFFFF) | 0x80000000 : over ? 0x40FFFFFF : 0x24FFFFFF);
		float tw = ui.textWidth(label, Fonts.SEMIBOLD, 9);
		float cx = x + (w - tw - 14) / 2;
		Icons.draw(ui.graphics(), icon, ui.mapX(cx + 5), ui.mapY(y + 11), 10 * s, 0xE6FFFFFF);
		ui.textMiddle(label, cx + 14, y + 11, 9, 0xFFFFFFFF, Fonts.SEMIBOLD);
		buttons.add(new float[] {x, y, x + w, y + 22});
		actions.add(action);
	}
	private boolean inView(double mx, double my) {
		return mx >= viewX && my >= viewY && mx <= viewX + VIEW_W && my <= viewY + VIEW_H;
	}

	private void paintAt(double mx, double my) {
		float x = (float) ((mx - viewX) / VIEW_W * texW);
		float y = (float) ((my - viewY) / VIEW_H * texH);
		if (preview.stroke(x, y, module.jiggleBrushSize(), module.jiggleBrushStrength(), module.jiggleTool(), module.jiggleMirror())) {
			dirty = true;
		}
	}
	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		double mx = e.x() / s;
		double my = e.y() / s;
		if (e.button() == 0) {
			for (int i = 0; i < buttons.size(); i++) {
				float[] b = buttons.get(i);
				if (mx >= b[0] && my >= b[1] && mx <= b[2] && my <= b[3]) {
					actions.get(i).run();
					return true;
				}
			}
		}
		if (inView(mx, my)) {
			drag = e.button();
			lastX = mx;
			lastY = my;
			if (drag == 0) {
				paintAt(mx, my);
			}
		}
		return true;
	}
	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		double mx = e.x() / s;
		double my = e.y() / s;
		if (drag == 0) {
			double dist = Math.hypot(mx - lastX, my - lastY);
			float step = Math.max(1, brushRadius() * 0.5f);
			int n = Math.max(1, (int) Math.ceil(dist / step));
			for (int i = 1; i <= n; i++) {
				paintAt(lastX + (mx - lastX) * i / n, lastY + (my - lastY) * i / n);
			}
		} else if (drag == 1) {
			yaw += (float) (mx - lastX) * 0.8f;
			pitch = Math.max(-80, Math.min(80, pitch + (float) (my - lastY) * 0.6f));
			dirty = true;
		} else if (drag == 2) {
			lift += (float) (my - lastY) / VIEW_H / zoom;
			dirty = true;
		}
		lastX = mx;
		lastY = my;
		return true;
	}
	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		drag = -1;
		return true;
	}
	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		zoom = Math.max(0.5f, Math.min(5, zoom * (scrollY > 0 ? 1.12f : 1 / 1.12f)));
		double my = y / s;
		if (inView(x / s, my) && scrollY > 0) {
			lift += (float) ((viewY + VIEW_H / 2 - my) / VIEW_H) * 0.12f / zoom;
		}
		dirty = true;
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
	public static boolean showcaseStroke(double fx0, double fy0, double fx1, double fy1) {
		JiggleEditorScreen e = open;
		if (e == null || e.texW == 0) {
			return false;
		}
		for (int i = 0; i <= 24; i++) {
			double t = i / 24.0;
			e.paintAt(e.viewX + (fx0 + (fx1 - fx0) * t) * VIEW_W, e.viewY + (fy0 + (fy1 - fy0) * t) * VIEW_H);
		}
		return true;
	}
	public static void showcaseView(float yaw, float pitch) {
		if (open != null) {
			open.yaw = yaw;
			open.pitch = pitch;
			open.dirty = true;
		}
	}
}
