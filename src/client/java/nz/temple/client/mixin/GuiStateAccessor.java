package nz.temple.client.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiStateAccessor {
	@Accessor("guiRenderState")
	GuiRenderState templenz$state();

	@Mutable
	@Accessor("guiRenderState")
	void templenz$setState(GuiRenderState state);
}
