package nz.temple.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerSprintAccessor {
	@Accessor("wasSprinting")
	void temple$setWasSprinting(boolean sprinting);
}
