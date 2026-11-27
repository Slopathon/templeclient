package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import nz.temple.TempleNz;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Minecraft offsets a text shadow by one GUI unit, which is several screen pixels at a high GUI scale (6 px at 1440p) and
 * reads as a blocky double outline. For the client's own fonts the shadow is offset by a few screen pixels instead,
 * whatever the GUI scale.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class FontShadowOffsetMixin {
	@ModifyExpressionValue(method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/font/GlyphInfo;getShadowOffset()F"))
	private float temple$fineShadow(float offset, @Local(argsOnly = true) Style style) {
		if (!(style.getFont() instanceof FontDescription.Resource r) || !TempleNz.MOD_ID.equals(r.id().getNamespace())) {
			return offset;
		}
		float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
		if (gui <= 1) {
			return offset;
		}
		return offset * Math.max(1, Math.round(gui / 3f)) / gui;
	}
}
