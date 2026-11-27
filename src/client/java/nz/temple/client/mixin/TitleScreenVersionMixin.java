package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.TempleNzClient;

/** The client's version, small and faint in the bottom right of Minecraft's own title screen, just above the credits. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenVersionMixin {
	// The GUI methods were render before 26.1.
	//? if >=26 {
	private static final String RENDER = "extractRenderState";
	//?} else {
	/*private static final String RENDER = "render";
	*///?}

	@Inject(method = RENDER, at = @At("TAIL"))
	private void temple$version(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		var font = Minecraft.getInstance().font;
		String label = TempleNzClient.versionLabel();
		float scale = 0.5f;
		g.pose().pushMatrix();
		g.pose().translate(g.guiWidth() - 2 - font.width(label) * scale, g.guiHeight() - 10 - font.lineHeight * scale - 1);
		g.pose().scale(scale, scale);
		g.text(font, label, 0, 0, 0x80FFFFFF, false);
		g.pose().popMatrix();
	}
}
