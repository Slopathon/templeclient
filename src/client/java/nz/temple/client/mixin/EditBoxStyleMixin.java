package nz.temple.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.client.render.GlassWidgets;

@Mixin(EditBox.class)
public abstract class EditBoxStyleMixin {
	@Redirect(method = "extractWidgetRenderState",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void temple$glassField(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
		EditBox box = (EditBox) (Object) this;
		if (!MenuModule.glassWidgets()
				|| !GlassWidgets.textField(g, x, y, w, h, box.isFocused(), box.isActive(), Math.max(0, Math.min(1, box.getAlpha())))) {
			g.blitSprite(pipeline, sprite, x, y, w, h);
		}
	}
}
