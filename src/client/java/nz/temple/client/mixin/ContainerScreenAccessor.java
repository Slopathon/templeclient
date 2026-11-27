package nz.temple.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int temple$leftPos();

	@Accessor("topPos")
	int temple$topPos();

	@Accessor("imageWidth")
	int temple$imageWidth();
}
