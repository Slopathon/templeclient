package nz.temple.client.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether the window was made with exclusive fullscreen (read once at startup; off means vanilla's borderless fullscreen). */
@Mixin(Window.class)
public interface WindowFullscreenAccessor {
	// 1.21.11 has only exclusive fullscreen: being fullscreen there is being exclusive.
	//? if >=26 {
	@Accessor("exclusiveFullscreen")
	//?} else {
	/*@Accessor("fullscreen")
	*///?}
	boolean templenz$exclusiveFullscreen();

	/** Streamproof switches it off: its overlay cannot show over exclusive fullscreen, only over borderless. */
	@Mutable
	//? if >=26 {
	@Accessor("exclusiveFullscreen")
	//?} else {
	/*@Accessor("fullscreen")
	*///?}
	void templenz$setExclusiveFullscreen(boolean exclusive);
}
