package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import nz.temple.client.modules.combat.Velocity;

import java.util.Optional;

/** Lets {@link Velocity} edit the knockback the server gives the local player, as it is applied. */
@Mixin(ClientPacketListener.class)
public abstract class KnockbackMixin {
	// The packet's getters lost their get prefix in 26.1.
	//? if >=26 {
	@Redirect(method = "handleSetEntityMotion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/game/ClientboundSetEntityMotionPacket;movement()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 temple$motion(ClientboundSetEntityMotionPacket packet) {
		Vec3 movement = packet.movement();
		int id = packet.id();
	//?} else {
	/*@Redirect(method = "handleSetEntityMotion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/game/ClientboundSetEntityMotionPacket;getMovement()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 temple$motion(ClientboundSetEntityMotionPacket packet) {
		Vec3 movement = packet.getMovement();
		int id = packet.getId();
	*///?}
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && id == player.getId() ? Velocity.knockback(movement, player) : movement;
	}

	@Redirect(method = "handleExplosion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;playerKnockback()Ljava/util/Optional;"))
	private Optional<Vec3> temple$explosion(ClientboundExplodePacket packet) {
		return packet.playerKnockback().map(Velocity::explosion);
	}
}
