package nz.temple.client.mixin;

import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import nz.temple.client.render.Fonts;

@Mixin(StringWidget.class)
public abstract class StringWidgetFontMixin {
	@Redirect(method = "visitLines", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/StringWidget;getMessage()Lnet/minecraft/network/chat/Component;"))
	private Component temple$interTitle(StringWidget widget) {
		return Fonts.widget(widget.getMessage(), true);
	}

	/**
	 * The widget was sized for Minecraft's font; the client font can measure a pixel or two wider (it is rendered at
	 * the GUI scale), which would cut a title such as "Game Menu" to "Game Me…". Give it the room it needs.
	 */
	@ModifyExpressionValue(method = "visitLines", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/StringWidget;getWidth()I"))
	private int temple$fitTitle(int width) {
		StringWidget self = (StringWidget) (Object) this;
		return Math.max(width, net.minecraft.client.Minecraft.getInstance().font.width(Fonts.widget(self.getMessage(), true)));
	}
}
