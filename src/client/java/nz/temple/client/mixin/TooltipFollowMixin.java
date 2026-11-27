package nz.temple.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import nz.temple.client.modules.client.MenuModule;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public abstract class TooltipFollowMixin {
	private static final String INTERNAL =
			"setTooltipForNextFrameInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Z)V";
	private static float temple$lagX;
	private static float temple$lagY;
	private static long temple$last;
	private static boolean temple$seeded;

	private static void temple$advance(int x, int y) {
		long now = System.nanoTime();
		float dt = temple$last == 0 ? 0 : Math.min(0.1f, (now - temple$last) / 1e9f);
		boolean fresh = temple$last == 0 || now - temple$last > 150_000_000L;
		temple$last = now;
		if (!temple$seeded || fresh || Math.abs(x - temple$lagX) > 220 || Math.abs(y - temple$lagY) > 160) {
			temple$lagX = x;
			temple$lagY = y;
			temple$seeded = true;
			return;
		}
		float follow = 1 - (float) Math.exp(-6.5f * dt);
		temple$lagX += (x - temple$lagX) * follow;
		temple$lagY += (y - temple$lagY) * follow;
	}
	@ModifyVariable(method = INTERNAL, at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private int temple$lagX(int x, Font font, List<ClientTooltipComponent> lines, int px, int py,
			ClientTooltipPositioner positioner, Identifier sprite, boolean focused) {
		if (!MenuModule.styledTooltips()) {
			return x;
		}
		temple$advance(px, py);
		return Math.round(temple$lagX);
	}
	@ModifyVariable(method = INTERNAL, at = @At("HEAD"), ordinal = 1, argsOnly = true)
	private int temple$lagY(int y, Font font, List<ClientTooltipComponent> lines, int px, int py,
			ClientTooltipPositioner positioner, Identifier sprite, boolean focused) {
		return MenuModule.styledTooltips() ? Math.round(temple$lagY) : y;
	}
}
