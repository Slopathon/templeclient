package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Click cooldown fields the ported combat modules reset. Remapped on every version (reflection by name is not). */
@Mixin(Minecraft.class)
public interface MinecraftClickDelayAccessor {
	@Accessor("rightClickDelay")
	void temple$setRightClickDelay(int ticks);

	@Accessor("missTime")
	void temple$setMissTime(int ticks);
}
