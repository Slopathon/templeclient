package nz.temple.client.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EasingCurve {
   public static final EasingCurve LINEAR = new EasingCurve(new double[]{0.0, 1.0}, new double[]{0.0, 1.0});
   private static final int LUT = 256;
   private static final Pattern PAIR = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
   private final double[] lut = new double[257];

   private EasingCurve(double[] xs, double[] ys) {
      int n = xs.length;
      double[] h = new double[n - 1];
      double[] d = new double[n - 1];

      for (int i = 0; i < n - 1; i++) {
         h[i] = xs[i + 1] - xs[i];
         d[i] = (ys[i + 1] - ys[i]) / h[i];
      }

      double[] m = new double[n];
      if (n == 2) {
         m[0] = m[1] = d[0];
      } else {
         for (int i = 1; i < n - 1; i++) {
            if (d[i - 1] * d[i] <= 0.0) {
               m[i] = 0.0;
            } else {
               double w1 = 2.0 * h[i] + h[i - 1];
               double w2 = h[i] + 2.0 * h[i - 1];
               m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i]);
            }
         }

         m[0] = endSlope(h[0], h[1], d[0], d[1]);
         m[n - 1] = endSlope(h[n - 2], h[n - 3], d[n - 2], d[n - 3]);
      }

      int seg = 0;

      for (int i = 0; i <= 256; i++) {
         double x = i / 256.0;

         while (seg < n - 2 && x > xs[seg + 1]) {
            seg++;
         }

         double x0 = xs[seg];
         double dx = h[seg];
         double t = (x - x0) / dx;
         double p0 = ys[seg];
         double p3 = ys[seg + 1];
         double p1 = p0 + m[seg] * dx / 3.0;
         double p2 = p3 - m[seg + 1] * dx / 3.0;
         double u = 1.0 - t;
         double y = u * u * u * p0 + 3.0 * u * u * t * p1 + 3.0 * u * t * t * p2 + t * t * t * p3;
         this.lut[i] = Math.max(0.0, Math.min(1.0, y));
      }

      this.lut[0] = 0.0;
      this.lut[256] = 1.0;
   }

   private static double endSlope(double h0, double h1, double d0, double d1) {
      double s = ((2.0 * h0 + h1) * d0 - h0 * d1) / (h0 + h1);
      if (s * d0 <= 0.0) {
         return 0.0;
      } else {
         return d0 * d1 <= 0.0 && Math.abs(s) > 3.0 * Math.abs(d0) ? 3.0 * d0 : s;
      }
   }

   public double sample(double x) {
      if (!(x > 0.0)) {
         return 0.0;
      }

      if (x >= 1.0) {
         return 1.0;
      }

      double f = x * 256.0;
      int i = (int)f;
      double k = f - i;
      return this.lut[i] + (this.lut[i + 1] - this.lut[i]) * k;
   }

   public static EasingCurve parse(String text) {
      if (text == null) {
         return LINEAR;
      }

      List<double[]> pts = new ArrayList<>();
      Matcher m = PAIR.matcher(text);

      while (m.find()) {
         double x;
         double y;
         try {
            x = Double.parseDouble(m.group(1));
            y = Double.parseDouble(m.group(2));
         } catch (NumberFormatException e) {
            continue;
         }

         if (Double.isFinite(x) && Double.isFinite(y)) {
            pts.add(new double[]{clamp(x, 0.0, 100.0) / 100.0, clamp(y, 0.0, 100.0) / 100.0});
         }
      }

      if (pts.isEmpty()) {
         return LINEAR;
      }

      pts.sort((a, b) -> Double.compare(a[0], b[0]));
      int n = pts.size() + 2;
      double[] xs = new double[n];
      double[] ys = new double[n];
      xs[0] = 0.0;
      ys[0] = 0.0;

      for (int i = 0; i < pts.size(); i++) {
         xs[i + 1] = pts.get(i)[0];
         ys[i + 1] = pts.get(i)[1];
      }

      xs[n - 1] = 1.0;
      ys[n - 1] = 1.0;

      for (int i = 1; i < n; i++) {
         if (xs[i] < xs[i - 1] + 0.001) {
            xs[i] = Math.min(1.0, xs[i - 1] + 0.001);
         }
      }

      for (int i = n - 2; i >= 1; i--) {
         if (xs[i] > xs[i + 1] - 0.001) {
            xs[i] = Math.max(0.0, xs[i + 1] - 0.001);
         }
      }

      return new EasingCurve(xs, ys);
   }

   private static double clamp(double v, double lo, double hi) {
      return Math.max(lo, Math.min(hi, v));
   }
}
