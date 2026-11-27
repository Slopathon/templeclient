package nz.temple.client.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import nz.temple.client.render.Fonts;

@Mixin(AbstractWidget.class)
public abstract class WidgetFontMixin {
	@ModifyVariable(method = "extractScrollingStringOverContents", at = @At("HEAD"), argsOnly = true)
	private Component temple$interLabel(Component message) {
		return Fonts.widget(message, false);
	}
}
