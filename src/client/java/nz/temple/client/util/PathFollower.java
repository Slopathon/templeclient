package nz.temple.client.util;

public final class PathFollower {
   private boolean valid;
   private long lastNs;
   private double outYaw;
   private double outPitch;
   private double startRelYaw;
   private double startRelPitch;
   private double lastTargetYaw;
   private double lastTargetPitch;
   private double progress;
   private double durationMs;

   public double yaw() {
      return this.outYaw;
   }

   public double pitch() {
      return this.outPitch;
   }

   public void reset() {
      this.valid = false;
   }

   public void step(long nowNs, double fromYaw, double fromPitch, double toYaw, double toPitch, PathFollower.Params p, double rand) {
      double tPitch = Math.max(-90.0, Math.min(90.0, toPitch));
      double gapMs = this.valid ? (nowNs - this.lastNs) / 1000000.0 : Double.MAX_VALUE;
      double dtMs = Math.min(Math.max(gapMs, 0.0), 60.0);
      boolean restart = !this.valid || gapMs > p.idleGapMs;
      double tYaw = fromYaw + wrap(toYaw - fromYaw);
      if (!restart) {
         double offYaw = wrap(fromYaw - this.outYaw);
         double offPitch = fromPitch - this.outPitch;
         if (Math.hypot(offYaw, offPitch) > 45.0) {
            restart = true;
         } else if (Math.hypot(wrap(tYaw - this.lastTargetYaw), tPitch - this.lastTargetPitch) > p.retargetDeg) {
            restart = true;
         }
      }

      double sYaw;
      double sPitch;
      if (restart) {
         sYaw = fromYaw;
         sPitch = fromPitch;
         double dist = Math.hypot(tYaw - sYaw, tPitch - sPitch);
         double var = 1.0 + Math.max(-1.0, Math.min(1.0, rand)) * (p.variationPct / 100.0);
         this.durationMs = Math.max(1.0, (p.baseMs + dist / 90.0 * p.per90Ms) * var);
         this.progress = dist < 0.02 ? 1.0 : 0.0;
      } else {
         sYaw = fromYaw + this.startRelYaw;
         sPitch = fromPitch + this.startRelPitch;
         this.progress = Math.min(1.0, this.progress + dtMs / this.durationMs);
      }

      double dYaw = tYaw - sYaw;
      double dPitch = tPitch - sPitch;
      double rYaw;
      double rPitch;
      if (this.progress >= 1.0) {
         rYaw = tYaw;
         rPitch = tPitch;
      } else {
         double e = p.curve == null ? this.progress : p.curve.sample(this.progress);
         rYaw = sYaw + dYaw * e;
         rPitch = sPitch + dPitch * e;
      }

      rPitch = Math.max(-90.0, Math.min(90.0, rPitch));
      this.outYaw = rYaw;
      this.outPitch = rPitch;
      this.startRelYaw = sYaw - rYaw;
      this.startRelPitch = sPitch - rPitch;
      this.lastTargetYaw = tYaw;
      this.lastTargetPitch = tPitch;
      this.lastNs = nowNs;
      this.valid = true;
   }

   private static double wrap(double deg) {
      double d = deg % 360.0;
      if (d >= 180.0) {
         d -= 360.0;
      }

      if (d < -180.0) {
         d += 360.0;
      }

      return d;
   }

   public static final class Params {
      public EasingCurve curve = EasingCurve.LINEAR;
      public double baseMs = 60.0;
      public double per90Ms = 90.0;
      public double variationPct = 10.0;
      public double retargetDeg = 12.0;
      public double idleGapMs = 250.0;
   }
}
