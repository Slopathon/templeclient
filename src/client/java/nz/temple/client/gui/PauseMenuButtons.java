package nz.temple.client.gui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class PauseMenuButtons {
	private PauseMenuButtons() {
	}
	public static void register() {
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (!(screen instanceof PauseScreen)) {
				return;
			}
			List<AbstractWidget> widgets = Screens.getWidgets(screen);
			AbstractWidget bottom = null;
			for (AbstractWidget w : widgets) {
				if (w instanceof Button && (bottom == null || w.getY() > bottom.getY())) {
					bottom = w;
				}
			}
			if (bottom == null) {
				return;
			}
			Button history = nz.temple.client.render.streamproof.StreamproofRenderer.hideFromRecording(
					Button.builder(Component.literal("Player History"), b -> mc.gui.setScreen(new PlayerHistoryScreen(screen)))
							.bounds(bottom.getX(), bottom.getY(), bottom.getWidth(), bottom.getHeight()).build());
			bottom.setY(bottom.getY() + bottom.getHeight() + 4);
			widgets.add(history);
		});
	}
}
