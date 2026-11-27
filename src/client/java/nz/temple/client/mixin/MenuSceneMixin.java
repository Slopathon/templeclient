package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.client.render.MenuScene;

@Mixin(Screen.class)
public abstract class MenuSceneMixin {
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void temple$openMenu(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof TitleScreen && TempleNzClient.isMenuKey(event)
				&& TempleNzClient.openMenu((Screen) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
	private void temple$backdrop(GuiGraphicsExtractor g, float alpha, CallbackInfo ci) {
		String theme = MenuModule.theme();
		boolean song = nz.temple.client.render.title.SongBackdrop.active();
		if (((Object) this).getClass() == TitleScreen.class || !song && (theme == null || "Default".equals(theme))) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		double scale = mc.getWindow().getGuiScale();
		MenuScene.pointer((float) (mc.mouseHandler.xpos() / scale), (float) (mc.mouseHandler.ypos() / scale));
		MenuScene.draw(g, theme, g.guiWidth(), g.guiHeight());
		ci.cancel();
	}
}
