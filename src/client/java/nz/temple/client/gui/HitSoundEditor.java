package nz.temple.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import nz.temple.client.TempleNzClient;
import nz.temple.client.audio.Pcm;
import nz.temple.client.audio.SoundOut;
import nz.temple.client.modules.render.HitSounds;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Icons;
import nz.temple.client.render.Theme;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HitSoundEditor extends Screen {
	private static final float W = 620;
	private static final float H = 300;
	private static final float WAVE_H = 150;
	private static final double MIN_LENGTH = 0.03;
	private final Screen parent;
	private final HitSounds sounds;
	private final List<float[]> buttons = new ArrayList<>();
	private final List<Runnable> actions = new ArrayList<>();
	private float s = 1;
	private float waveX;
	private float waveY;
	private float waveW;
	private double viewStart;
	private double viewEnd = -1;
	private int dragMode;
	private double dragAnchor;
	private double dragStart;
	private double dragLength;
	private SoundOut.Voice voice;
	private float[] peaks;
	private double peaksFrom;
	private double peaksTo;
	private Pcm peaksOf;
	public HitSoundEditor(Screen parent, HitSounds sounds) {
		super(Component.literal("Trim hit sound"));
		this.parent = parent;
		this.sounds = sounds;
	}
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	@Override
	public void onClose() {
		if (voice != null) {
			voice.stop();
		}
		minecraft.gui.setScreen(parent);
	}
	private Pcm pcm() {
		return sounds.customSound();
	}

	private double fileSeconds() {
		Pcm p = pcm();
		return p == null ? 1 : p.seconds();
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
		ui.textMiddle("Trim hit sound", x0 + 18, y0 + 20, 13, 0xFFFFFFFF, Fonts.SEMIBOLD);
		String name = sounds.fileName();
		ui.textMiddle(name, x0 + W - 18 - ui.textWidth(name, Fonts.MEDIUM, 9), y0 + 20, 9, 0x99FFFFFF, Fonts.MEDIUM);
		waveX = x0 + 16;
		waveY = y0 + 40;
		waveW = W - 32;
		ui.fill(waveX, waveY, waveW, WAVE_H, 10, 0x40000000);
		Pcm pcm = pcm();
		if (pcm == null) {
			String msg = sounds.customError() != null ? "Could not read that file: " + sounds.customError() : "Loading…";
			ui.textMiddle(msg, waveX + waveW / 2 - ui.textWidth(msg, Fonts.MEDIUM, 10) / 2, waveY + WAVE_H / 2, 10, 0xB3FFFFFF, Fonts.MEDIUM);
		} else {
			if (viewEnd < 0) {
				fitSelection();
			}
			double start = sounds.trimStart();
			double end = Math.min(pcm.seconds(), start + sounds.trimLength());
			float sx0 = xOf(start);
			float sx1 = xOf(end);
			float cx0 = Math.max(waveX, sx0);
			float cx1 = Math.min(waveX + waveW, sx1);
			if (cx1 > cx0) {
				ui.fill(cx0, waveY + 4, cx1 - cx0, WAVE_H - 8, 6, (0x30 << 24) | (accent & 0xFFFFFF));
			}
			drawWave(g, ui, pcm, start, end, accent);
			float mid = waveY + WAVE_H / 2;
			rect(g, ui, waveX + 6, mid - 0.25f, waveX + waveW - 6, mid + 0.25f, 0x26FFFFFF);
			handle(g, ui, sx0, accent);
			handle(g, ui, sx1, accent);
			if (voice != null && voice.playing()) {
				float px = xOf(start + voice.progress() * (end - start));
				rect(g, ui, px - 0.6f, waveY + 6, px + 0.6f, waveY + WAVE_H - 6, 0xFFFFFFFF);
			}
			double span = viewEnd - viewStart;
			double step = niceStep(span / 6);
			for (double t = Math.ceil(viewStart / step) * step; t <= viewEnd; t += step) {
				float tx = xOf(t);
				rect(g, ui, tx - 0.25f, waveY + WAVE_H - 7, tx + 0.25f, waveY + WAVE_H - 3, 0x55FFFFFF);
				String label = seconds(t, step);
				ui.textMiddle(label, tx + 2, waveY + WAVE_H + 7, 7, 0x80FFFFFF, Fonts.MEDIUM);
			}
		}
		float ry = waveY + WAVE_H + 26;
		readout(ui, waveX, ry, "Start", String.format(Locale.ROOT, "%.2f s", sounds.trimStart()));
		readout(ui, waveX + 110, ry, "Length", String.format(Locale.ROOT, "%.2f s", sounds.trimLength()));
		readout(ui, waveX + 220, ry, "File", pcm == null ? "-" : String.format(Locale.ROOT, "%.1f s", pcm.seconds()));
		String hint = "Drag the handles  ·  scroll to zoom  ·  right-drag to pan  ·  space to play";
		ui.textMiddle(hint, x0 + W - 16 - ui.textWidth(hint, Fonts.MEDIUM, 8), ry, 8, 0x80FFFFFF, Fonts.MEDIUM);
		float by = y0 + H - 38;
		float bw = (W - 32 - 4 * 8) / 5;
		float bx = x0 + 16;
		boolean playing = voice != null && voice.playing();
		button(ui, bx, by, bw, playing ? "Stop" : "Play", playing ? Icons.Icon.PAUSE : Icons.Icon.PLAY, this::togglePlay);
		button(ui, bx + (bw + 8), by, bw, "Zoom to trim", Icons.Icon.SEARCH, this::fitSelection);
		button(ui, bx + (bw + 8) * 2, by, bw, "Whole file", Icons.Icon.LAYOUT, this::showAll);
		button(ui, bx + (bw + 8) * 3, by, bw, "Other file", Icons.Icon.FOLDER, this::chooseFile);
		button(ui, bx + (bw + 8) * 4, by, bw, "Done", Icons.Icon.CHECK, this::onClose);
		ui.resetTransform();
	}
	private void drawWave(GuiGraphicsExtractor g, Ui ui, Pcm pcm, double start, double end, int accent) {
		float step = 1.25f;
		int columns = Math.max(8, (int) ((waveW - 12) / step));
		if (peaks == null || peaks.length != columns || peaksFrom != viewStart || peaksTo != viewEnd || peaksOf != pcm) {
			peaks = peaks(pcm, viewStart, viewEnd, columns);
			peaksFrom = viewStart;
			peaksTo = viewEnd;
			peaksOf = pcm;
		}
		float max = 1e-4f;
		for (float p : peaks) {
			max = Math.max(max, p);
		}
		float mid = waveY + WAVE_H / 2;
		float half = WAVE_H / 2 - 14;
		for (int i = 0; i < columns; i++) {
			float x = waveX + 6 + i * step;
			double t = viewStart + (viewEnd - viewStart) * (i + 0.5) / columns;
			boolean inside = t >= start && t <= end;
			float h = Math.max(0.5f, (float) Math.pow(peaks[i] / max, 0.7) * half);
			rect(g, ui, x, mid - h, x + step * 0.62f, mid + h, inside ? accent : 0x59FFFFFF);
		}
	}
	private static float[] peaks(Pcm pcm, double from, double to, int columns) {
		float[] samples = pcm.samples();
		float[] out = new float[columns];
		for (int c = 0; c < columns; c++) {
			int a = (int) Math.max(0, (from + (to - from) * c / columns) * pcm.rate());
			int b = (int) Math.min(samples.length, (from + (to - from) * (c + 1) / columns) * pcm.rate());
			float peak = 0;
			int stride = Math.max(1, (b - a) / 256);
			for (int i = a; i < b; i += stride) {
				peak = Math.max(peak, Math.abs(samples[i]));
			}
			out[c] = peak;
		}
		return out;
	}
	private void handle(GuiGraphicsExtractor g, Ui ui, float x, int accent) {
		if (x < waveX - 1 || x > waveX + waveW + 1) {
			return;
		}
		rect(g, ui, x - 0.75f, waveY + 4, x + 0.75f, waveY + WAVE_H - 4, 0xFFFFFFFF);
		rect(g, ui, x - 3.5f, waveY + 4, x + 3.5f, waveY + 12, 0xFFFFFFFF);
		rect(g, ui, x - 2.5f, waveY + 5, x + 2.5f, waveY + 11, accent);
	}
	private static void readout(Ui ui, float x, float y, String label, String value) {
		ui.textMiddle(label, x, y, 8, 0x80FFFFFF, Fonts.MEDIUM);
		ui.textMiddle(value, x + ui.textWidth(label, Fonts.MEDIUM, 8) + 6, y, 10, 0xFFFFFFFF, Fonts.SEMIBOLD);
	}
	private static String seconds(double t, double step) {
		return step < 0.1 ? String.format(Locale.ROOT, "%.2fs", t) : step < 1 ? String.format(Locale.ROOT, "%.1fs", t)
				: String.format(Locale.ROOT, "%.0fs", t);
	}
	private static double niceStep(double raw) {
		double p = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1e-4))));
		double n = raw / p;
		return (n < 1.5 ? 1 : n < 3.5 ? 2 : n < 7.5 ? 5 : 10) * p;
	}
	private static void rect(GuiGraphicsExtractor g, Ui ui, float x0, float y0, float x1, float y1, int argb) {
		if (x1 > x0 && y1 > y0) {
			Draw2D.rect(g, ui.mapX(x0), ui.mapY(y0), ui.mapX(x1), ui.mapY(y1), argb);
		}
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
	private float xOf(double t) {
		return (float) (waveX + 6 + (t - viewStart) / Math.max(1e-6, viewEnd - viewStart) * (waveW - 12));
	}

	private double timeAt(double x) {
		return viewStart + (x - waveX - 6) / (waveW - 12) * (viewEnd - viewStart);
	}

	private void fitSelection() {
		double len = sounds.trimLength();
		double span = Math.min(fileSeconds(), Math.max(0.5, len * 3));
		double mid = sounds.trimStart() + len / 2;
		setView(mid - span / 2, mid + span / 2);
	}
	private void showAll() {
		setView(0, fileSeconds());
	}

	private void setView(double from, double to) {
		double total = fileSeconds();
		double span = Math.max(0.05, Math.min(total, to - from));
		from = Math.max(0, Math.min(total - span, from));
		viewStart = from;
		viewEnd = from + span;
	}
	private void setSelection(double start, double length) {
		double total = fileSeconds();
		length = Math.max(MIN_LENGTH, Math.min(sounds.maxTrimLength(), Math.min(total, length)));
		start = Math.max(0, Math.min(total - length, start));
		sounds.setTrim(start, length);
	}
	private void togglePlay() {
		if (voice != null && voice.playing()) {
			voice.stop();
			voice = null;
		} else {
			voice = sounds.previewTrim();
		}
	}
	private void chooseFile() {
		nz.temple.client.util.FilePicker.choose(sounds.fileSetting());
		viewEnd = -1;
	}
	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		double mx = e.x() / s;
		double my = e.y() / s;
		for (int i = 0; i < buttons.size(); i++) {
			float[] b = buttons.get(i);
			if (mx >= b[0] && my >= b[1] && mx <= b[2] && my <= b[3]) {
				nz.temple.client.audio.UiSounds.tap();
				actions.get(i).run();
				return true;
			}
		}
		if (pcm() == null || mx < waveX || mx > waveX + waveW || my < waveY || my > waveY + WAVE_H) {
			return true;
		}
		dragAnchor = mx;
		dragStart = sounds.trimStart();
		dragLength = sounds.trimLength();
		if (e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
			dragMode = 4;
			dragStart = viewStart;
			return true;
		}
		float sx0 = xOf(dragStart);
		float sx1 = xOf(dragStart + dragLength);
		if (Math.abs(mx - sx0) <= 5) {
			dragMode = 1;
		} else if (Math.abs(mx - sx1) <= 5) {
			dragMode = 2;
		} else if (mx > sx0 && mx < sx1) {
			dragMode = 3;
		} else {
			setSelection(timeAt(mx), dragLength);
			dragStart = sounds.trimStart();
			dragMode = 2;
		}
		return true;
	}
	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragMode == 0) {
			return true;
		}
		double mx = e.x() / s;
		double dt = (mx - dragAnchor) / (waveW - 12) * (viewEnd - viewStart);
		switch (dragMode) {
			case 1 -> {
				double end = dragStart + dragLength;
				double start = Math.max(0, Math.min(end - MIN_LENGTH, dragStart + dt));
				start = Math.max(start, end - sounds.maxTrimLength());
				setSelection(start, end - start);
			}
			case 2 -> setSelection(dragStart, Math.max(MIN_LENGTH, timeAt(mx) - dragStart));
			case 3 -> setSelection(dragStart + dt, dragLength);
			case 4 -> setView(dragStart - dt, dragStart - dt + (viewEnd - viewStart));
			default -> {
			}
		}
		return true;
	}
	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		boolean changed = dragMode != 0 && dragMode != 4
				&& (sounds.trimStart() != dragStart || sounds.trimLength() != dragLength || dragMode == 2);
		dragMode = 0;
		if (changed) {
			if (voice != null) {
				voice.stop();
			}
			voice = sounds.previewTrim();
		}
		return true;
	}
	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (pcm() == null || scrollY == 0) {
			return true;
		}
		double at = timeAt(x / s);
		double k = scrollY > 0 ? 0.8 : 1.25;
		setView(at - (at - viewStart) * k, at + (viewEnd - at) * k);
		return true;
	}
	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == InputConstants.KEY_ESCAPE || e.key() == InputConstants.KEY_RETURN) {
			onClose();
			return true;
		}
		if (e.key() == InputConstants.KEY_SPACE) {
			togglePlay();
			return true;
		}
		return super.keyPressed(e);
	}
}
