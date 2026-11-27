package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.TempleNzClient;
import nz.temple.client.render.GlassButtons;

@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	@Inject(method = "extractBackground", at = @At("RETURN"))
	private void temple$placeGlass(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (GlassButtons.enabled() && TempleNzClient.glass() != null) {
			TempleNzClient.glass().placeLayer(g);
		}
	}
}
