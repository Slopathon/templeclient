package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nz.temple.client.render.streamproof.StreamproofRenderer;

@Mixin(Gui.class)
public abstract class StreamproofGuiMixin {
	@WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private boolean temple$screen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		return !StreamproofRenderer.hiddenScreen(screen);
	}
}
