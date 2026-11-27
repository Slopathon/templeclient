package nz.temple.client.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import nz.temple.client.TempleNzClient;
import nz.temple.client.gui.TempleTitleScreen;
import nz.temple.client.modules.client.MenuModule;

/**
 * Every time the game goes to the title screen, it goes to Temple's instead (unless Client > Menu > Menu theme is
 * Default), and Options likewise open as Temple's. Swapping at setScreen catches every route there, whatever built
 * the vanilla screen.
 */
//? if >=26 {
@Mixin(Gui.class)
//?} else {
/*// 1.21.11 opens screens on Minecraft itself.
@Mixin(net.minecraft.client.Minecraft.class)
*///?}
public abstract class TitleScreenSwapMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen temple$swapTitle(Screen screen) {
		if (screen != null && screen.getClass() == TitleScreen.class && MenuModule.templeTitle()) {
			return new TempleTitleScreen();
		}
		// And back: menus opened from Temple's title screen return to that same screen, so switching the theme to
		// Default has to swap it here too, or it would only take effect after leaving a world.
		if (screen instanceof TempleTitleScreen && TempleNzClient.modules() != null && !MenuModule.templeTitle()) {
			return new TitleScreen();
		}
		// Minecraft's Options, from the title screen or the pause menu, open as Temple's (Client > Menu > Themed settings).
		if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.options.OptionsScreen.class && MenuModule.themedSettings()) {
			net.minecraft.client.gui.screens.options.OptionsScreen options = (net.minecraft.client.gui.screens.options.OptionsScreen) screen;
			//? if >=26 {
			Screen parent = options.getLastScreen();
			//?} else {
			/*Screen parent = ((LegacyOptionsScreenAccessor) options).templenz$lastScreen();
			*///?}
			return new nz.temple.client.gui.TempleOptionsScreen(parent,
					net.minecraft.client.Minecraft.getInstance().level != null);
		}
		return screen;
	}
}
