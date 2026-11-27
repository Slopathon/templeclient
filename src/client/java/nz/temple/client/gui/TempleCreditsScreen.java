package nz.temple.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.LogoMark;
import nz.temple.client.render.Ui;
import nz.temple.client.render.UiScale;

import java.util.List;

/** Temple's credits: the names roll slowly up the screen, like Minecraft's own credits. */
public final class TempleCreditsScreen extends Screen {
	private static final List<String> NAMES = List.of("x.monolith", "nx09.sh", "Temple Team");
	private static final float SPEED = 16;

	private final Screen parent;
	private double time = 9;
	private long lastNanos = System.nanoTime();
	private float s = 1;
	private float linkX0;
	private float linkY0;
	private float linkX1;
	private float linkY1;

	public TempleCreditsScreen(Screen parent) {
		super(Component.literal("Credits"));
		this.parent = parent;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		long now = System.nanoTime();
		time += Math.min(0.5, (now - lastNanos) / 1e9);
		lastNanos = now;
		s = UiScale.factor() * 1.08f;

		Ui ui = TempleNzClient.ui();
		ui.begin(g);
		ui.transform(0, 0, s, s);
		float dw = width / s;
		float dh = height / s;
		ui.fill(0, 0, dw, dh, 0, 0xEB050608);

		// The roll: the logo, then each name, rising from the bottom of the screen and starting over once it has passed.
		float logo = 40;
		float content = logo + 44 + NAMES.size() * 26;
		float travel = dh + content;
		float y = dh - (float) (time * SPEED % travel);
		LogoMark.draw3d(g, "credits", ui.mapX(dw / 2 - logo / 2), ui.mapY(y), logo * s, 1);
		y += logo + 44;
		for (String name : NAMES) {
			float w = ui.textWidth(name, Fonts.SEMIBOLD, 13);
			ui.textMiddle(name, (dw - w) / 2, y, 13, 0xFFFFFFFF, Fonts.SEMIBOLD);
			y += 26;
		}

		// Minecraft's credits, as a small link in the corner.
		String link = "Minecraft credits";
		float lw = ui.textWidth(link, Fonts.MEDIUM, 8.5f);
		linkX0 = dw - 14 - lw;
		linkY0 = dh - 20;
		linkX1 = dw - 14;
		linkY1 = dh - 8;
		boolean over = mx / s >= linkX0 - 2 && mx / s <= linkX1 + 2 && my / s >= linkY0 && my / s <= linkY1;
		ui.textMiddle(link, linkX0, dh - 14, 8.5f, over ? 0xF2FFFFFF : 0x80FFFFFF, Fonts.MEDIUM);
		ui.resetTransform();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / s;
		double y = event.y() / s;
		if (x >= linkX0 - 2 && x <= linkX1 + 2 && y >= linkY0 && y <= linkY1) {
			minecraft.gui.setScreen(new CreditsAndAttributionScreen(this));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
