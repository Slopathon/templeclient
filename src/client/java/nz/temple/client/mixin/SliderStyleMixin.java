package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.client.render.GlassWidgets;

@Mixin(AbstractSliderButton.class)
public abstract class SliderStyleMixin {
	@Shadow
	protected double value;

	@Shadow
	protected boolean canChangeValue;

	@org.spongepowered.asm.mixin.Unique
	private float temple$grab;
	@org.spongepowered.asm.mixin.Unique
	private float temple$warm;
	@org.spongepowered.asm.mixin.Unique
	private double temple$shown = -1;
	@org.spongepowered.asm.mixin.Unique
	private long temple$last;

	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
	private void temple$glassSlider(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!MenuModule.glassWidgets()) {
			return;
		}
		AbstractSliderButton slider = (AbstractSliderButton) (Object) this;
		long now = System.nanoTime();
		float dt = temple$last == 0 ? 0 : Math.min(0.1f, (now - temple$last) / 1e9f);
		temple$last = now;
		temple$grab += ((canChangeValue ? 1 : 0) - temple$grab) * (1 - (float) Math.exp(-11 * dt));
		boolean over = mouseX >= slider.getX() && mouseX < slider.getX() + slider.getWidth()
				&& mouseY >= slider.getY() && mouseY < slider.getY() + slider.getHeight();
		temple$warm += ((over ? 1 : 0) - temple$warm) * (1 - (float) Math.exp(-14 * dt));
		if (temple$shown < 0) {
			temple$shown = value;
		}
		temple$shown += (value - temple$shown) * (1 - Math.exp(-26 * dt));
		if (Math.abs(value - temple$shown) < 0.0005) {
			temple$shown = value;
		}
		if (GlassWidgets.slider(g, slider.getX(), slider.getY(), slider.getWidth(), slider.getHeight(), temple$shown,
				temple$warm, temple$grab, slider.isActive(),
				Math.max(0, Math.min(1, slider.getAlpha())), slider.getMessage())) {
			ci.cancel();
		}
	}
}
