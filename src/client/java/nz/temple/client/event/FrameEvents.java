package nz.temple.client.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FrameEvents {
   private static final List<Runnable> CALLBACKS = new CopyOnWriteArrayList<>();

   private FrameEvents() {
   }

   public static void register(Runnable callback) {
      if (!CALLBACKS.contains(callback)) {
         CALLBACKS.add(callback);
      }
   }

   public static void unregister(Runnable callback) {
      CALLBACKS.remove(callback);
   }

   public static void fire() {
      for (Runnable r : CALLBACKS) {
         try {
            r.run();
         } catch (Exception var3) {
         }
      }
   }

   public static void clear() {
      CALLBACKS.clear();
   }
}
