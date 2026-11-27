package nz.temple.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2f;

/**
 * Picks a pre-scaled copy of a GUI image. Minecraft draws GUI images without mipmaps, so an image shown at less than
 * half its size skips texels and looks jagged ("pixelated"). Each image ships in several halved sizes; the smallest one
 * that is still at least as big as it appears on screen is used, so the GPU never shrinks by more than 2×.
 */
public final class MipLevels {
	private MipLevels() {
	}

	/** How many real screen pixels {@code size} GUI units cover under the current transform. */
	public static float screenPixels(GuiGraphicsExtractor g, float size) {
		Matrix3x2f m = g.pose();
		float scale = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
		return size * scale * (float) Minecraft.getInstance().getWindow().getGuiScale();
	}

	/** Index into {@code sizes} (largest first, each half the previous) of the level to draw {@code pixels} wide. */
	public static int pick(int[] sizes, float pixels) {
		int best = 0;
		for (int i = 1; i < sizes.length; i++) {
			if (sizes[i] >= pixels) {
				best = i;
			}
		}
		return best;
	}
}
