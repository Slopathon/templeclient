package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.util.Rotations;
import nz.temple.client.util.SilentShot;

@Mixin(MultiPlayerGameMode.class)
public abstract class ShotAimMixin {
	@Unique private float temple$realYaw;
	@Unique private float temple$realPitch;
	@Unique private boolean temple$turned;
	@Inject(method = "releaseUsingItem", at = @At("HEAD"))
	private void temple$aimRelease(Player player, CallbackInfo ci) {
		if (player instanceof LocalPlayer local) {
			float[] aim = SilentShot.aim(local);
			if (aim != null) {
				local.connection.send(new ServerboundMovePlayerPacket.Rot(aim[0], aim[1], local.onGround(), local.horizontalCollision));
				Rotations.sent(aim[0], aim[1]);
			}
		}
	}
	@Inject(method = "useItem", at = @At("HEAD"))
	private void temple$aimUse(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		temple$turned = false;
		if (player instanceof LocalPlayer local && local.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.CrossbowItem
				&& net.minecraft.world.item.CrossbowItem.isCharged(local.getItemInHand(hand))) {
			float[] aim = SilentShot.aim(local);
			if (aim != null) {
				temple$realYaw = local.getYRot();
				temple$realPitch = local.getXRot();
				local.setYRot(aim[0]);
				local.setXRot(aim[1]);
				temple$turned = true;
			}
		}
	}
	@Inject(method = "useItem", at = @At("RETURN"))
	private void temple$restoreUse(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (temple$turned) {
			player.setYRot(temple$realYaw);
			player.setXRot(temple$realPitch);
			temple$turned = false;
		}
	}
}
