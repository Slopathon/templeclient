package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.world.NoBlockInteract;
import nz.temple.client.util.DetachedCamera;

@Mixin(MultiPlayerGameMode.class)
public class BlockInteractionMixin {
	@org.spongepowered.asm.mixin.Unique private net.minecraft.world.item.Item temple$usedItem;

	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void temple$blockUse(LocalPlayer p, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		temple$usedItem = p.getItemInHand(hand).getItem();
		if (DetachedCamera.freecam()) { cir.setReturnValue(InteractionResult.FAIL); return; }
		if (NoBlockInteract.blocks(hit)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void temple$blockUsed(LocalPlayer p, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		nz.temple.client.modules.combat.Macro macro = nz.temple.client.modules.combat.Macro.instance();
		if (macro != null && temple$usedItem != null && cir.getReturnValue().consumesAction()) {
			macro.placed(p, temple$usedItem, hit);
		}
	}
	@Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
	private void temple$itemUse(Player p, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (DetachedCamera.freecam()) cir.setReturnValue(InteractionResult.FAIL);
	}
	@Inject(method = {"startDestroyBlock", "continueDestroyBlock"}, at = @At("HEAD"), cancellable = true)
	private void temple$mining(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
		if (DetachedCamera.blocksMining()) cir.setReturnValue(false);
	}
}
