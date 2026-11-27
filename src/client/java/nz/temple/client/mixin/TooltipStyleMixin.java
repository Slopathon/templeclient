package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.client.render.GoldPanel;

@Mixin(TooltipRenderUtil.class)
public abstract class TooltipStyleMixin {
	private static long temple$born;
	private static int temple$lastX = Integer.MIN_VALUE;
	private static int temple$lastY;
	private static long temple$lastFrame;

	@Inject(method = "extractTooltipBackground", at = @At("HEAD"), cancellable = true)
	private static void temple$goldTooltip(GuiGraphicsExtractor g, int x, int y, int width, int height, Identifier sprite, CallbackInfo ci) {
		if (!MenuModule.styledTooltips()) {
			return;
		}
		long now = System.nanoTime();
		if (temple$lastX == Integer.MIN_VALUE || now - temple$lastFrame > 120_000_000L
				|| Math.abs(x - temple$lastX) > 120 || Math.abs(y - temple$lastY) > 90) {
			temple$born = now;
		}
		temple$lastFrame = now;
		temple$lastX = x;
		temple$lastY = y;
		float age = (float) ((System.nanoTime() - temple$born) / 1e9);
		float t = Math.min(1, age / 0.38f);
		float ease = 1 - (1 - t) * (1 - t) * (1 - t);
		float swell = t >= 1 ? 1
				: 1 - 0.18f * (1 - ease) + 0.10f * (float) Math.sin(ease * Math.PI * 1.6) * (1 - ease);
		float alpha = Math.min(1, 0.25f + ease);
		int cx = x + width / 2;
		int cy = y + height / 2;
		int hw = Math.round((width / 2f + 3) * swell);
		int hh = Math.round((height / 2f + 3) * swell);
		GoldPanel.draw(g, cx - hw, cy - hh, cx + hw, cy + hh, alpha);
		ci.cancel();
	}
}
