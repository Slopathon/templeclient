package nz.temple.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.client.render.GlassWidgets;

/**
 * Tabs get the glass treatment, and the selection eases in rather than snapping, so switching tabs reads as the
 * highlight flowing across the bar. Only the background sprite is replaced; the label and underline stay vanilla.
 */
//? if >=26 {
@Mixin(net.minecraft.client.gui.components.tabs.MenuTabBar.MenuTabButton.class)
//?} else {
/*@Mixin(net.minecraft.client.gui.components.TabButton.class)
*///?}
public abstract class TabStyleMixin {
	//? if >=26 {
	private static final String RENDER = "extractWidgetRenderState";
	//?} else {
	/*private static final String RENDER = "renderWidget";
	*///?}

	@Unique
	private float temple$selected;
	@Unique
	private float temple$hover;
	@Unique
	private long temple$last;

	@Redirect(method = RENDER,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void temple$glassTab(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
		//? if >=26 {
		var tab = (net.minecraft.client.gui.components.tabs.MenuTabBar.MenuTabButton) (Object) this;
		//?} else {
		/*var tab = (net.minecraft.client.gui.components.TabButton) (Object) this;
		*///?}
		if (!MenuModule.glassWidgets()) {
			g.blitSprite(pipeline, sprite, x, y, w, h);
			return;
		}
		long now = System.nanoTime();
		float dt = temple$last == 0 ? 0 : Math.min(0.1f, (now - temple$last) / 1e9f);
		temple$last = now;
		temple$selected += ((tab.isSelected() ? 1 : 0) - temple$selected) * Math.min(1, dt * 4.5f);
		temple$hover += ((tab.isHoveredOrFocused() ? 1 : 0) - temple$hover) * Math.min(1, dt * 9);
		if (!GlassWidgets.tab(g, x, y, w, h, temple$selected, temple$hover, Math.max(0, Math.min(1, tab.getAlpha())))) {
			g.blitSprite(pipeline, sprite, x, y, w, h);
		}
	}
}
