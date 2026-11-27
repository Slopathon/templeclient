package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.streamproof.StreamproofRenderer;

/** Buttons Temple adds to vanilla screens are left out of the recording. */
@Mixin(AbstractWidget.class)
public abstract class StreamproofWidgetMixin {
	// The GUI methods were render before 26.1.
	//? if >=26 {
	private static final String RENDER = "extractRenderState";
	//?} else {
	/*private static final String RENDER = "render";
	*///?}

	@Inject(method = RENDER, at = @At("HEAD"), cancellable = true)
	private void temple$hide(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (StreamproofRenderer.hiddenWidget(this)) {
			ci.cancel();
		}
	}
}
