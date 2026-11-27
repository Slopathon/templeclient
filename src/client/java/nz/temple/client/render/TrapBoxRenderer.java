package nz.temple.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.world.phys.AABB;
import nz.temple.client.render.WorldDraw;

/** Auto Trap's box preview, drawn with Temple's ESP boxes. */
public final class TrapBoxRenderer {
	public static final String[] STYLES = new String[]{"Glow Edges", "Gradient", "Shimmer", "Pulse", "Neon"};

	private TrapBoxRenderer() {
	}

	public static int style(String name) {
		for (int i = 0; i < STYLES.length; i++) {
			if (STYLES[i].equals(name)) {
				return i;
			}
		}
		return -1;
	}

	public static void render(PoseStack ps, List<Box> boxes, int style, int rgb1, int rgb2, float fill, float outline, float time,
			boolean throughWalls) {
		for (Box b : boxes) {
			int fa = Math.round(Math.max(0, Math.min(1, fill * b.fade())) * 255);
			int la = Math.round(Math.max(0, Math.min(1, outline * b.fade())) * 255);
			WorldDraw.drawBoxes(ps, List.of(b.box()), fa << 24 | rgb1 & 0xFFFFFF, la << 24 | rgb1 & 0xFFFFFF, fa > 0, la > 0);
		}
	}

	public record Box(AABB box, float fade, float seed) {
	}
}
