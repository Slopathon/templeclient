package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface GameModeAccessor {
	@Accessor("destroyBlockPos")
	BlockPos temple$destroyPos();

	@Accessor("destroyProgress")
	float temple$destroyProgress();
}
