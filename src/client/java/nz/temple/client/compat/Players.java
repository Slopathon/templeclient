package nz.temple.client.compat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * Arm swings across versions. Before 26.3 a client-side swing also told the server (ServerboundSwingPacket, per hand).
 * From 26.3 a swing is local and the server learns of it from the attack itself, or from a punch packet when the swing
 * hits nothing, which is what vanilla sends.
 */
public final class Players {
	private Players() {
	}

	/** Swings {@code hand} the way a click does. */
	public static void swing(LivingEntity entity, InteractionHand hand) {
		//? if >=26.3 {
		/*entity.swing(hand, entity.getItemInHand(hand).getAttackAnimation(), false);
		*///?} else {
		entity.swing(hand);
		//?}
	}

	/** Tells the server about a swing without animating it here. */
	public static void swingPacket(LocalPlayer player, InteractionHand hand) {
		//? if >=26.3 {
		/*player.connection.send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
		*///?} else {
		player.connection.send(new net.minecraft.network.protocol.game.ServerboundSwingPacket(hand));
		//?}
	}

	/** A line in your own chat, from the client (not sent to the server). */
	public static void message(net.minecraft.world.entity.player.Player player, net.minecraft.network.chat.Component text) {
		//? if >=26 {
		player.sendSystemMessage(text);
		//?} else {
		/*player.displayClientMessage(text, false);
		*///?}
	}

	public static boolean isSwinging(LivingEntity entity) {
		//? if >=26.3 {
		/*return entity.isSwinging();
		*///?} else {
		return entity.swinging;
		//?}
	}
}
