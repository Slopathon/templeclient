package nz.temple.client.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class AttackEvents {
   private static final List<Consumer<AttackStartEvent>> LISTENERS = new CopyOnWriteArrayList<>();

   private AttackEvents() {
   }

   public static void register(Consumer<AttackStartEvent> listener) {
      LISTENERS.add(listener);
   }

   public static void fire(AttackStartEvent event) {
      for (Consumer<AttackStartEvent> l : LISTENERS) {
         try {
            l.accept(event);
         } catch (Exception var4) {
         }
      }
   }

   public static void clear() {
      LISTENERS.clear();
   }
}
