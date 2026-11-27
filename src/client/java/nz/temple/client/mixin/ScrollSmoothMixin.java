package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.client.MenuModule;

@Mixin(AbstractScrollArea.class)
public abstract class ScrollSmoothMixin {
	@Shadow
	private boolean scrolling;
	@Shadow
	protected abstract double scrollRate();
	@Unique
	private double temple$target;
	@Unique
	private boolean temple$hasTarget;
	@Unique
	private long temple$last;
	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void temple$scroll(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		if (!MenuModule.smoothScrolling()) {
			return;
		}
		AbstractScrollArea area = (AbstractScrollArea) (Object) this;
		int max = area.maxScrollAmount();
		if (max <= 0) {
			return;
		}
		if (!temple$hasTarget) {
			temple$target = area.scrollAmount();
			temple$hasTarget = true;
		}
		temple$target = Math.max(0, Math.min(max, temple$target - scrollY * scrollRate()));
		temple$last = System.nanoTime();
		cir.setReturnValue(true);
	}
	@Inject(method = "extractScrollbar", at = @At("HEAD"))
	private void temple$glide(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		AbstractScrollArea area = (AbstractScrollArea) (Object) this;
		if (!MenuModule.smoothScrolling() || !temple$hasTarget) {
			return;
		}
		double current = area.scrollAmount();
		if (scrolling) {
			temple$target = current;
			return;
		}
		temple$target = Math.max(0, Math.min(area.maxScrollAmount(), temple$target));
		long now = System.nanoTime();
		float dt = temple$last == 0 ? 0 : Math.min(0.1f, (now - temple$last) / 1e9f);
		temple$last = now;
		double remaining = temple$target - current;
		if (Math.abs(remaining) < 0.25) {
			area.setScrollAmount(temple$target);
			return;
		}
		area.setScrollAmount(current + remaining * (1 - Math.exp(-18 * dt)));
	}
}
