package nz.temple.client.event;

import net.minecraft.network.protocol.Packet;

public class PacketSendEvent {
   private final Packet<?> packet;
   private boolean cancelled;

   public PacketSendEvent(Packet<?> packet) {
      this.packet = packet;
      this.cancelled = false;
   }

   public Packet<?> getPacket() {
      return this.packet;
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void cancel() {
      this.cancelled = true;
   }
}
