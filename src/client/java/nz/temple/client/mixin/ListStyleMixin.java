package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.client.render.GlassWidgets;

@Mixin(AbstractSelectionList.class)
public abstract class ListStyleMixin {
	@Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
	private void temple$listBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (!MenuModule.glassWidgets()) {
			return;
		}
		AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
		GlassWidgets.listBackground(g, list.getX(), list.getY(), list.getRight(), list.getBottom());
		ci.cancel();
	}
}
