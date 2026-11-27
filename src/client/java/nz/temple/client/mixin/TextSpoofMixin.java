package nz.temple.client.mixin;

import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import nz.temple.client.modules.render.Spoof;

@Mixin(StringDecomposer.class)
public abstract class TextSpoofMixin {
	@ModifyVariable(method = {
			"iterate(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
			"iterateBackwards(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
			"iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z"
	}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static String temple$spoof(String text) {
		return Spoof.apply(text);
	}
}
