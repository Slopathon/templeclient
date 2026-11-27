package nz.temple.client.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface HudSource {
	void render(GuiGraphicsExtractor g, DeltaTracker delta, Projection projection);
}
