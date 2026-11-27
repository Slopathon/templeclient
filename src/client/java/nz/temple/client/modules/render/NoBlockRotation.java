package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import nz.temple.module.Category;
import nz.temple.module.Module;

public final class NoBlockRotation extends Module {
	private static volatile boolean active;

	public NoBlockRotation() {
		super("No Block Rotation", Category.RENDER, "Uses the same model rotation for identical blocks instead of random rotations. "
				+ "Directional blocks are unchanged.");
	}

	public static boolean active() {
		return active;
	}

	@Override
	protected void onEnable() {
		active = true;
		rebuildTerrain();
	}

	@Override
	protected void onDisable() {
		active = false;
		rebuildTerrain();
	}

	private static void rebuildTerrain() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.level != null) {
				mc.levelExtractor.allChanged();
			}
		});
	}
}
