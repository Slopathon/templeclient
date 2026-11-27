package nz.temple.client.util;

public final class SilentRotationState {
   public static float tickServerYaw;
   public static float tickServerPitch;
   public static float realYaw;
   public static boolean silentActive;
   /** Server pitch of the previous and current tick, for drawing your player's head at the silent aim. */
   public static float lastModelPitch;
   public static float modelPitch;

   public static float modelPitch(float partialTick) {
      return net.minecraft.util.Mth.lerp(net.minecraft.util.Mth.clamp(partialTick, 0.0F, 1.0F), lastModelPitch, modelPitch);
   }

   private SilentRotationState() {
   }
}
