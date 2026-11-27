package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.render.GlassButtons;

@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
	private void temple$pointer(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		GlassButtons.mouse(mouseX, mouseY);
	}

	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void temple$glassSprite(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (GlassButtons.draw((AbstractButton) (Object) this, g)) {
			ci.cancel();
		}
	}

	@Inject(method = "onClick", at = @At("HEAD"))
	private void temple$splash(MouseButtonEvent event, boolean doubleClick, CallbackInfo ci) {
		GlassButtons.clicked((AbstractButton) (Object) this);
	}
}
