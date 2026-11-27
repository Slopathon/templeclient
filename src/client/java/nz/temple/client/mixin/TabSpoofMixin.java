package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.Spoof;

/** Marks the tab list while it is built, so Spoof swaps the balance in its money slot ("$ 1.4B" on DonutSMP) too. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabSpoofMixin {
	// The GUI methods were render before 26.1.
	//? if >=26 {
	private static final String RENDER = "extractRenderState";
	//?} else {
	/*private static final String RENDER = "render";
	*///?}

	@Inject(method = RENDER, at = @At("HEAD"))
	private void temple$enter(GuiGraphicsExtractor graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
		Spoof.enterSidebar();
	}

	@Inject(method = RENDER, at = @At("RETURN"))
	private void temple$leave(GuiGraphicsExtractor graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
		Spoof.leaveSidebar();
	}
}
