package nz.temple.smoke;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class TrailerDummy {
	private TrailerDummy() {
	}
	static final class Dummy {
		final ServerPlayer player;
		volatile Consumer<ServerPlayer> brain = p -> { };
		volatile boolean totems;
		int deadTicks;
		String logged = "";
		Dummy(ServerPlayer player) {
			this.player = player;
		}
	}
	static final List<Dummy> ALL = new CopyOnWriteArrayList<>();
	private static boolean installed;
	private static final class NoConnection extends Connection {
		NoConnection() {
			super(PacketFlow.SERVERBOUND);
			try {
				var field = Connection.class.getDeclaredField("channel");
				field.setAccessible(true);
				field.set(this, new EmbeddedChannel());
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
		@Override
		public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
		}
		@Override
		public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> info, T listener) {
		}
		@Override
		public void setupOutboundProtocol(ProtocolInfo<?> info) {
		}
		@Override
		public void setReadOnly() {
		}
		@Override
		public void handleDisconnection() {
		}
	}
	static void install() {
		if (installed) {
			return;
		}
		installed = true;
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (Dummy d : ALL) {
				ServerPlayer p = d.player;
				if (p.isRemoved()) {
					ALL.remove(d);
					continue;
				}
				if (!p.isAlive()) {
					if (++d.deadTicks >= 20) {
						server.getPlayerList().remove(p);
						ALL.remove(d);
					}
					continue;
				}
				if (d.totems && !p.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.TOTEM_OF_UNDYING)) {
					p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
				}
				d.brain.accept(p);
				p.doTick();
				ServerPlayer you = null;
				for (ServerPlayer q : server.getPlayerList().getPlayers()) {
					if (ALL.stream().noneMatch(x -> x.player == q)) {
						you = q;
					}
				}
				String state = String.format(java.util.Locale.ROOT, "hp=%.1f abs=%.1f blocking=%b shieldCd=%b pos=%.1f,%.1f,%.1f you.y=%.2f you.fall=%.2f",
						p.getHealth(), p.getAbsorptionAmount(), p.isBlocking(),
						p.getCooldowns().isOnCooldown(new ItemStack(Items.SHIELD)), p.getX(), p.getY(), p.getZ(),
						you == null ? 0 : you.getY(), you == null ? 0 : you.fallDistance);
				String key = state.substring(0, state.indexOf(" pos="));
				if (!key.equals(d.logged)) {
					d.logged = key;
					System.out.println("TEMPLE_DUMMY " + server.getTickCount() + " " + p.getGameProfile().name() + " " + state);
				}
			}
		});
	}
	static Dummy spawn(MinecraftServer server, String name, Vec3 at, float yaw) {
		install();
		var level = server.overworld();
		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("temple-dummy:" + name).getBytes()), name);
		ServerPlayer p = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
		p.snapTo(at.x, at.y, at.z, yaw, 0);
		server.getPlayerList().placeNewPlayer(new NoConnection(), p, new CommonListenerCookie(profile, 0, p.clientInformation(), false));
		p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		p.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), yaw, 0, true);
		p.setYHeadRot(yaw);
		p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		Dummy d = new Dummy(p);
		ALL.add(d);
		return d;
	}
	static void kit(ServerPlayer p, boolean netherite) {
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(netherite ? Items.NETHERITE_HELMET : Items.DIAMOND_HELMET));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(netherite ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(netherite ? Items.NETHERITE_LEGGINGS : Items.DIAMOND_LEGGINGS));
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(netherite ? Items.NETHERITE_BOOTS : Items.DIAMOND_BOOTS));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(netherite ? Items.NETHERITE_SWORD : Items.DIAMOND_SWORD));
	}
	static void face(ServerPlayer p, Vec3 target) {
		Vec3 d = target.subtract(p.getEyePosition());
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
		p.setYRot(yaw);
		p.setXRot(pitch);
		p.setYHeadRot(yaw);
		p.setYBodyRot(yaw);
	}
	static void clear(MinecraftServer server) {
		for (Dummy d : ALL) {
			server.getPlayerList().remove(d.player);
			d.player.discard();
		}
		ALL.clear();
	}
}
