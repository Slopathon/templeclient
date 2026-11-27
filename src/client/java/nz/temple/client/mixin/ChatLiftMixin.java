package nz.temple.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.hud.HudOverlay;

/** Lifts the chat above Temple's widgets in the bottom-left corner (coordinates, speed graph) so they never overlap. */
@Mixin(ChatComponent.class)
public class ChatLiftMixin {
	//? if >=26 {
	private static final String RENDER = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V";
	//?} else {
	/*private static final String RENDER = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V";
	*///?}

	// The target's own arguments are taken in full rather than with MixinExtras @Local: the parameter-annotation
	// attribute @Local leaves crashes ZKM's saver (see FoliageVertexMixin).
	//? if >=26 {
	@Inject(method = RENDER, at = @At("HEAD"))
	private void temple$lift(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int a, int b, int c,
			ChatComponent.DisplayMode mode, boolean d, CallbackInfo ci) {
		temple$lift(g);
	}

	@Inject(method = RENDER, at = @At("RETURN"))
	private void temple$drop(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int a, int b, int c,
			ChatComponent.DisplayMode mode, boolean d, CallbackInfo ci) {
		g.pose().popMatrix();
	}
	//?} else {
	/*@Inject(method = RENDER, at = @At("HEAD"))
	private void temple$lift(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int a, int b, int c, boolean d,
			boolean e, CallbackInfo ci) {
		temple$lift(g);
	}

	@Inject(method = RENDER, at = @At("RETURN"))
	private void temple$drop(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int a, int b, int c, boolean d,
			boolean e, CallbackInfo ci) {
		g.pose().popMatrix();
	}
	*///?}

	private static void temple$lift(GuiGraphicsExtractor g) {
		g.pose().pushMatrix();
		g.pose().translate(0, -HudOverlay.chatLift(g.guiHeight()));
	}
}
