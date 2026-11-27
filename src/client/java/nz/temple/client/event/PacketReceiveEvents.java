package nz.temple.client.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import nz.temple.client.event.PacketReceiveEvent;

public final class PacketReceiveEvents {
   private static final List<Consumer<PacketReceiveEvent>> LISTENERS = new CopyOnWriteArrayList<>();

   private PacketReceiveEvents() {
   }

   public static void register(Consumer<PacketReceiveEvent> listener) {
      LISTENERS.add(listener);
   }

   public static void unregister(Consumer<PacketReceiveEvent> listener) {
      LISTENERS.remove(listener);
   }

   public static PacketReceiveEvent fire(PacketReceiveEvent event) {

      for (Consumer<PacketReceiveEvent> l : LISTENERS) {
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
