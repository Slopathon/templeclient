package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Hud.class)
public interface HudOverlayInvoker {
	@Invoker("extractTextureOverlay")
	void templenz$extractTextureOverlay(GuiGraphicsExtractor graphics, Identifier texture, float alpha);
}
