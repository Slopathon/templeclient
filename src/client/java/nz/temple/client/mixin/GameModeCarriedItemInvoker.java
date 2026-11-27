package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface GameModeCarriedItemInvoker {
	@Invoker("ensureHasSentCarriedItem")
	void temple$ensureHasSentCarriedItem();
}
