package nz.temple.client.mixin;

import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.client.StreamerMode;

/** Streamer Mode: drops the F3 lines that give away where you are (position, chunk, the block or entity looked at). */
//? if >=26 {
@Mixin({net.minecraft.client.gui.components.debug.DebugEntryPosition.class, net.minecraft.client.gui.components.debug.DebugEntrySectionPosition.class,
		net.minecraft.client.gui.components.debug.DebugEntryLookingAt.class, net.minecraft.client.gui.components.debug.DebugEntryLookingAtEntity.class})
//?} else {
/*// 1.21.11 has separate block and fluid entries where 26.1 has one.
@Mixin({net.minecraft.client.gui.components.debug.DebugEntryPosition.class, net.minecraft.client.gui.components.debug.DebugEntrySectionPosition.class,
		net.minecraft.client.gui.components.debug.DebugEntryLookingAtBlock.class, net.minecraft.client.gui.components.debug.DebugEntryLookingAtFluid.class,
		net.minecraft.client.gui.components.debug.DebugEntryLookingAtEntity.class})
*///?}
public class DebugPositionMixin {
	@Inject(method = "display", at = @At("HEAD"), cancellable = true)
	private void temple$hidePosition(DebugScreenDisplayer displayer, Level level, LevelChunk clientChunk, LevelChunk serverChunk, CallbackInfo ci) {
		if (StreamerMode.hidesF3()) {
			ci.cancel();
		}
	}
}
