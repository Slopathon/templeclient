package nz.temple.client.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionsScreen.class)
public interface LegacyOptionsScreenAccessor {
	@Accessor("lastScreen")
	Screen templenz$lastScreen();
}
