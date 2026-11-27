package nz.temple.client.cosmetic;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import nz.temple.client.hud.Notifications;
import nz.temple.client.render.Projection;

public interface PetsAccess {
	boolean say(String title, String detail, Notifications.Kind kind);
	void renderBubble(GuiGraphicsExtractor g, DeltaTracker delta, Projection projection);
}
