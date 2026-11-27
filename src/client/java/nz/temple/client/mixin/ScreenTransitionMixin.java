package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.client.render.ScreenTransition;

@Mixin(Screen.class)
public abstract class ScreenTransitionMixin {
	@Unique
	private float temple$progress = 1;

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
	private void temple$begin(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		Screen screen = (Screen) (Object) this;
		boolean menu = net.minecraft.client.Minecraft.getInstance().level == null;
		temple$progress = menu && MenuModule.transitions() ? ScreenTransition.progress(screen, MenuModule.transitionMillis()) : 1;
		if (temple$progress >= 1) {
			return;
		}
		float cx = g.guiWidth() / 2f;
		float cy = g.guiHeight() / 2f;
		float scale = 0.975f + 0.025f * temple$progress;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(scale, scale);
		pose.translate(-cx, -cy);
		pose.translate(0, (1 - temple$progress) * 12);
	}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
	private void temple$end(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (temple$progress >= 1) {
			return;
		}
		g.pose().popMatrix();
		int wash = Math.round((1 - temple$progress) * 46) << 24 | HudModule.GOLD & 0xFFFFFF;
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), wash);
	}
}
