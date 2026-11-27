package nz.temple.client.modules.client;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import nz.temple.client.modules.SchemaModule;
import nz.temple.client.util.EasingCurve;
import nz.temple.client.util.PathFollower;
import net.minecraft.client.Minecraft;

public class CustomRotations extends SchemaModule {
   private static volatile CustomRotations INSTANCE;
   public static final String DEFAULT_PRESET = "Ease In Out";
   private static final Map<String, String> PRESETS = Map.of(
      "Linear",
      "",
      "Ease In",
      "25:6, 50:25, 75:56",
      "Ease Out",
      "25:44, 50:75, 75:94",
      "Ease In Out",
      "25:16, 50:50, 75:84",
      "Snap",
      "10:45, 25:80, 50:95",
      "Hesitate",
      "20:42, 40:48, 60:55, 80:75"
   );
   private static final String FALLBACK_POINTS = PRESETS.get("Ease In Out");
   private final Map<Object, PathFollower> followers = new IdentityHashMap<>();
   private final PathFollower.Params params = new PathFollower.Params();
   private final Map<Object, double[]> driftPhases = new IdentityHashMap<>();
   private String curveSource;
   private EasingCurve curve;

   public static String presetPoints(String preset) {
      return PRESETS.getOrDefault(preset, FALLBACK_POINTS);
   }

   public CustomRotations() {
      super("Custom Rotations", "Client");
      INSTANCE = this;
   }

   @Override
   public void onDisable() {
      synchronized (this.followers) {
         this.followers.clear();
      }

      synchronized (this.driftPhases) {
         this.driftPhases.clear();
      }
   }

   public static boolean active() {
      CustomRotations m = INSTANCE;
      return m != null && m.isEnabled();
   }

   public static float[] shape(Object owner, float fromYaw, float fromPitch, float toYaw, float toPitch, float normalYaw, float normalPitch) {
      CustomRotations m = INSTANCE;
      if (m != null
         && m.isEnabled()
        
         && Float.isFinite(fromYaw)
         && Float.isFinite(fromPitch)
         && Float.isFinite(toYaw)
         && Float.isFinite(toPitch)) {
         try {
            return m.follow(owner, fromYaw, fromPitch, toYaw, toPitch);
         } catch (Throwable t) {
            return new float[]{normalYaw, normalPitch};
         }
      } else {
         return new float[]{normalYaw, normalPitch};
      }
   }

   public static float[] shapeAim(Object owner, float fromYaw, float fromPitch, float toYaw, float toPitch, float normalYaw, float normalPitch) {
      CustomRotations m = INSTANCE;
      if (m != null && m.isEnabled() && Float.isFinite(toYaw) && Float.isFinite(toPitch)) {
         try {
            if (m.getSetting("Variate Aim", false)) {
               float[] off = m.drift(owner);
               toYaw += off[0];
               toPitch = Math.max(-90.0F, Math.min(90.0F, toPitch + off[1]));
            }
         } catch (Throwable var9) {
         }

         return shape(owner, fromYaw, fromPitch, toYaw, toPitch, normalYaw, normalPitch);
      } else {
         return shape(owner, fromYaw, fromPitch, toYaw, toPitch, normalYaw, normalPitch);
      }
   }

   private float[] drift(Object owner) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return new float[]{0.0F, 0.0F};
      }

      double strength = this.getSetting("Variate Strength", 4.0);
      double lock = Math.max(0.05, Math.min(0.8, this.getSetting("Variate Lock At", 50.0) / 100.0));
      double cooldown = mc.player.getAttackStrengthScale(0.0F);
      double t = (cooldown - lock * 0.4) / (lock * 0.6);
      t = Math.max(0.0, Math.min(1.0, t));
      double amount = 1.0 - t * t * (3.0 - 2.0 * t);
      if (!(amount <= 0.0) && !(strength <= 0.0)) {
         double[] ph;
         synchronized (this.driftPhases) {
            ph = this.driftPhases.computeIfAbsent(owner, k -> {
               double[] a = new double[6];

               for (int i = 0; i < a.length; i++) {
                  a[i] = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
               }

               return a;
            });
         }

         double sec = System.nanoTime() / 1.0E9;
         double yaw = 0.5 * Math.sin(sec * 2.9 + ph[0]) + 0.3 * Math.sin(sec * 4.7 + ph[1]) + 0.2 * Math.sin(sec * 7.1 + ph[2]);
         double pitch = 0.5 * Math.sin(sec * 2.3 + ph[3]) + 0.3 * Math.sin(sec * 5.3 + ph[4]) + 0.2 * Math.sin(sec * 6.7 + ph[5]);
         return new float[]{(float)(yaw * strength * amount), (float)(pitch * strength * 0.6 * amount)};
      } else {
         return new float[]{0.0F, 0.0F};
      }
   }

   private synchronized float[] follow(Object owner, float fromYaw, float fromPitch, float toYaw, float toPitch) {
      PathFollower f;
      synchronized (this.followers) {
         f = this.followers.computeIfAbsent(owner, k -> new PathFollower());
      }

      this.readSettings();
      f.step(System.nanoTime(), fromYaw, fromPitch, toYaw, toPitch, this.params, ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0);
      return new float[]{(float)f.yaw(), (float)f.pitch()};
   }

   private void readSettings() {
      String preset = String.valueOf(this.getSetting("Preset", "Ease In Out"));
      boolean custom = "Custom".equals(preset);
      String source = custom ? String.valueOf(this.getSetting("Custom Curve", FALLBACK_POINTS)) : presetPoints(preset);
      if (this.curveSource == null || !source.equals(this.curveSource)) {
         this.curve = EasingCurve.parse(source);
         this.curveSource = source;
      }

      this.params.curve = this.curve;
      if (custom) {
         this.params.baseMs = 60.0;
         this.params.per90Ms = 90.0;
         this.params.variationPct = 10.0;
         this.params.retargetDeg = 12.0;
      } else {
         this.params.baseMs = this.getSetting("Base Duration", 60.0);
         this.params.per90Ms = this.getSetting("Duration Per 90", 90.0);
         this.params.variationPct = this.getSetting("Duration Variation", 10.0);
         this.params.retargetDeg = this.getSetting("Retarget Threshold", 12.0);
      }
   }
}
