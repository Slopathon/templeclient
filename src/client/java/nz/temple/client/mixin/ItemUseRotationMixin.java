package nz.temple.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import nz.temple.client.modules.combat.AimAssist;
import nz.temple.client.modules.combat.CrystalAura;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item uses (pearls, crossbows, ...) are sent with the silent rotation; Crystal Aura learns about manual hits. */
@Mixin(MultiPlayerGameMode.class)
public class ItemUseRotationMixin {
	@Unique
	private boolean temple$swapped;
	@Unique
	private float temple$yaw;
	@Unique
	private float temple$pitch;

	@Inject(method = "attack", at = @At("HEAD"))
	private void temple$attack(Player player, Entity target, CallbackInfo ci) {
		CrystalAura.onLocalAttack(target);
	}

	@Inject(method = "useItem", at = @At("HEAD"))
	private void temple$useItemHead(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		temple$swapped = false;
		if (!AimAssist.hasSilentRotation()) {
			return;
		}
		float yaw = AimAssist.getSilentServerYaw();
		float pitch = AimAssist.getSilentServerPitch();
		if (Math.abs(Mth.wrapDegrees(yaw - player.getYRot())) < 0.05F && Math.abs(Mth.wrapDegrees(pitch - player.getXRot())) < 0.05F) {
			return;
		}
		temple$yaw = player.getYRot();
		temple$pitch = player.getXRot();
		temple$swapped = true;
		AimAssist.commitUseItemRotation(yaw, pitch);
		player.setYRot(yaw);
		player.setXRot(pitch);
	}

	@Inject(method = "useItem", at = @At("RETURN"))
	private void temple$useItemReturn(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (temple$swapped) {
			temple$swapped = false;
			player.setYRot(temple$yaw);
			player.setXRot(temple$pitch);
		}
	}
}
