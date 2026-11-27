package nz.temple.smoke.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface TrailerMouseAccessor {
	@Accessor("xpos")
	void temple$setX(double x);

	@Accessor("ypos")
	void temple$setY(double y);
}
