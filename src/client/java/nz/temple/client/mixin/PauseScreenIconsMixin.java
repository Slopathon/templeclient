package nz.temple.client.mixin;

import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import nz.temple.client.modules.client.MenuModule;

@Mixin(PauseScreen.class)
public abstract class PauseScreenIconsMixin {
	@Redirect(method = "createPauseMenu", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;ILnet/minecraft/client/gui/layouts/LayoutSettings;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
	private LayoutElement temple$skipIconRow(GridLayout.RowHelper rows, LayoutElement child, int span, LayoutSettings settings) {
		if (child instanceof LinearLayout && MenuModule.hidePauseIcons()) {
			return child;
		}
		return rows.addChild(child, span, settings);
	}
}
