package nz.temple.client.mixin;

import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.client.MenuModule;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public abstract class ToastEaseMixin {
	@Shadow
	private float visiblePortion;
	@Shadow
	private Toast.Visibility visibility;
	@Inject(method = "calculateVisiblePortion", at = @At("TAIL"))
	private void temple$ease(long now, CallbackInfo ci) {
		if (!MenuModule.transitions()) {
			return;
		}
		float p = Math.max(0, Math.min(1, visiblePortion));
		if (visibility == Toast.Visibility.SHOW) {
			float u = p - 1;
			visiblePortion = Math.min(1.06f, 1 + 2.2f * u * u * u + 1.4f * u * u);
		} else {
			visiblePortion = p * p;
		}
	}
}
