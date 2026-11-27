package nz.temple.client.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import nz.temple.client.event.PacketSendEvent;

public final class PacketSendEvents {
   private static final List<Consumer<PacketSendEvent>> LISTENERS = new CopyOnWriteArrayList<>();

   private PacketSendEvents() {
   }

   public static void register(Consumer<PacketSendEvent> listener) {
      LISTENERS.add(listener);
   }

   public static void unregister(Consumer<PacketSendEvent> listener) {
      LISTENERS.remove(listener);
   }

   public static PacketSendEvent fire(PacketSendEvent event) {

      for (Consumer<PacketSendEvent> l : LISTENERS) {
         try {
            l.accept(event);
         } catch (Exception var4) {
         }
      }

      return event;
   }

   public static void clear() {
      LISTENERS.clear();
   }
}
