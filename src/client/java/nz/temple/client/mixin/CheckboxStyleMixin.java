package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Checkbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.client.render.GlassWidgets;

@Mixin(Checkbox.class)
public abstract class CheckboxStyleMixin {
	@Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
	private void temple$glassCheckbox(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!MenuModule.glassWidgets()) {
			return;
		}
		Checkbox box = (Checkbox) (Object) this;
		int size = box.getHeight();
		if (GlassWidgets.checkbox(g, box.getX(), box.getY(), size, box.selected(), box.isHoveredOrFocused(),
				Math.max(0, Math.min(1, box.getAlpha())), box.getMessage())) {
			ci.cancel();
		}
	}
}
