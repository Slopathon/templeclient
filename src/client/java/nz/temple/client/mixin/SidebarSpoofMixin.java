package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.Spoof;

@Mixin(Hud.class)
public abstract class SidebarSpoofMixin {
	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
	private void temple$enter(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
		Spoof.enterSidebar();
	}

	@Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
	private void temple$leave(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
		Spoof.leaveSidebar();
	}
}
