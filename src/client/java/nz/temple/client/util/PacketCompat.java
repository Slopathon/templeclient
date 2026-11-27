package nz.temple.client.util;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.phys.Vec3;

/** Packet fields the ported modules read, across Minecraft versions (26.3 sends movement as position paths). */
public final class PacketCompat {
	private PacketCompat() {
	}

	/** The position a relative move packet moves to, from {@code codec}'s base. */
	public static Vec3 decodeMove(VecDeltaCodec codec, ClientboundMoveEntityPacket p) {
		//? if >=26.3 {
		/*return p.getPositionDelta().decode(codec).endPosition();
		*///?} else {
		return codec.decode(p.getXa(), p.getYa(), p.getZa());
		//?}
	}

	/** How far a relative move packet moves the entity, in blocks. */
	public static Vec3 moveDelta(ClientboundMoveEntityPacket p) {
		return decodeMove(new VecDeltaCodec(), p);
	}

	public static Vec3 syncPosition(ClientboundEntityPositionSyncPacket p) {
		//? if >=26.3 {
		/*return p.position().endPosition();
		*///?} else {
		return p.values().position();
		//?}
	}

	public static float syncYRot(ClientboundEntityPositionSyncPacket p) {
		//? if >=26.3 {
		/*return p.yRot();
		*///?} else {
		return p.values().yRot();
		//?}
	}

	public static float syncXRot(ClientboundEntityPositionSyncPacket p) {
		//? if >=26.3 {
		/*return p.xRot();
		*///?} else {
		return p.values().xRot();
		//?}
	}

	public static IntList removedIds(ClientboundRemoveEntitiesPacket p) {
		//? if >=26.3 {
		/*return p.entityIds();
		*///?} else {
		return p.getEntityIds();
		//?}
	}

	public static int motionEntityId(ClientboundSetEntityMotionPacket p) {
		//? if >=26 {
		return p.id();
		//?} else {
		/*return p.getId();
		*///?}
	}
}
