package nz.temple.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.ChatAnimations;
import nz.temple.client.modules.render.CleanView;
import nz.temple.client.modules.render.Crosshair;
import nz.temple.client.modules.render.SmoothHotbar;

/** Smooth Hotbar, Chat Animations, Crosshair and Clean View: small changes to how the vanilla HUD draws. */
@Mixin(Hud.class)
public abstract class HudPolishMixin {
	//? if >=26 {
	private static final String CHAT = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V";
	//?} else {
	/*private static final String CHAT = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V";
	*///?}
	private static final String BLIT_SPRITE =
			"Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V";

	/**
	 * The hotbar's sprites. With the glass hotbar on, vanilla's bar, selection box and offhand slot are not drawn (the glass
	 * hotbar draws its own). Otherwise the selection box glides between slots, at sub-pixel positions, with Smooth Hotbar.
	 */
	@Redirect(method = "extractItemHotbar", at = @At(value = "INVOKE", target = BLIT_SPRITE))
	private void temple$glideSelection(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
		String path = sprite.getPath();
		SmoothHotbar hotbar = SmoothHotbar.active();
		if (hotbar == null || !path.equals("hud/hotbar_selection")) {
			g.blitSprite(pipeline, sprite, x, y, w, h);
			return;
		}
		float shown = hotbar.x(x);
		int whole = (int) Math.floor(shown);
		g.pose().pushMatrix();
		g.pose().translate(shown - whole, 0);
		g.blitSprite(pipeline, sprite, whole, y, w, h);
		g.pose().popMatrix();
	}

	/**
	 * No crosshair, vanilla or custom, while any screen is open: the HUD is still drawn under menus (the click GUI does not
	 * blur the world), and a crosshair floating over a menu is just in the way.
	 */
	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
	private void temple$noCrosshairInScreens(CallbackInfo ci) {
		if (net.minecraft.client.Minecraft.getInstance().gui.screen() != null) {
			ci.cancel();
		}
	}

	/** Crosshair: vanilla's sprite or a picture, recoloured and reacting to hits (the attack indicator below it stays). */
	@Redirect(method = "extractCrosshair", at = @At(value = "INVOKE", target = BLIT_SPRITE, ordinal = 0))
	private void temple$crosshair(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
		Crosshair crosshair = Crosshair.active();
		if (crosshair == null) {
			g.blitSprite(pipeline, sprite, x, y, w, h);
			return;
		}
		crosshair.draw(g, pipeline, sprite, x, y, w, h);
	}

	@Inject(method = "extractChat", at = @At(value = "INVOKE",
			target = CHAT))
	private void temple$chatSlideIn(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
		ChatAnimations chat = ChatAnimations.active();
		g.pose().pushMatrix();
		if (chat != null) {
			g.pose().translate(0, chat.offset());
		}
	}

	@Inject(method = "extractChat", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = CHAT))
	private void temple$chatSlideInDone(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
		g.pose().popMatrix();
	}

	@Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
	private void temple$noVignette(CallbackInfo ci) {
		if (CleanView.noVignette()) {
			ci.cancel();
		}
	}

	/** The first texture overlay in the camera overlays is the one a worn item (a carved pumpkin) puts over the screen. */
	@Redirect(method = "extractCameraOverlays", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V"))
	private void temple$noPumpkin(Hud hud, GuiGraphicsExtractor g, Identifier texture, float alpha) {
		if (!CleanView.noPumpkin()) {
			((HudOverlayInvoker) hud).templenz$extractTextureOverlay(g, texture, alpha);
		}
	}

	@Inject(method = {"extractPortalOverlay", "extractConfusionOverlay"}, at = @At("HEAD"), cancellable = true)
	private void temple$noPortal(CallbackInfo ci) {
		if (CleanView.noPortal()) {
			ci.cancel();
		}
	}
}
