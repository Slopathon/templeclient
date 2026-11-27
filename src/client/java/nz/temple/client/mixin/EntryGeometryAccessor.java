package nz.temple.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.components.AbstractSelectionList$Entry")
public interface EntryGeometryAccessor {
	@Accessor("x")
	int temple$x();

	@Accessor("y")
	int temple$y();

	@Accessor("width")
	int temple$width();

	@Accessor("height")
	int temple$height();
}
