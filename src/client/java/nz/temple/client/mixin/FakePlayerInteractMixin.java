package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nz.temple.client.modules.player.FakePlayer;

/**
 * Fake Player's dummy exists only on this client: hitting it hurts it here, and the attack packet that would name an
 * entity the server has never heard of is held back. Vanilla's own attack still runs, so the swing, sounds and
 * particles play as for any hit. Using an item on it does nothing.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class FakePlayerInteractMixin {
	/** Whether the attack running now is on the dummy. */
	@Unique private boolean temple$onDummy;

	@Inject(method = "attack", at = @At("HEAD"))
	private void temple$hitDummy(Player player, Entity target, CallbackInfo ci) {
		temple$onDummy = FakePlayer.is(target);
		FakePlayer fake = temple$onDummy ? FakePlayer.active() : null;
		if (fake != null && player == Minecraft.getInstance().player && player instanceof LocalPlayer local) {
			fake.hit(local);
		}
	}

	@WrapOperation(method = "attack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
	private void temple$keepDummyHitLocal(ClientPacketListener connection, Packet<?> packet, Operation<Void> original) {
		if (!temple$onDummy) {
			original.call(connection, packet);
		}
	}

	@Inject(method = "attack", at = @At("TAIL"))
	private void temple$hitDone(Player player, Entity target, CallbackInfo ci) {
		temple$onDummy = false;
	}

	//? if >=26 {
	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void temple$useOnDummy(Player player, Entity target, EntityHitResult hit, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (FakePlayer.is(target)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
	//?} else {
	/*@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void temple$useOnDummy(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (FakePlayer.is(target)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}

	@Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
	private void temple$useOnDummyAt(Player player, Entity target, EntityHitResult hit, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (FakePlayer.is(target)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
	*///?}
}
