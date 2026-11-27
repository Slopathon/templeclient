package nz.temple.client.modules.render;

import nz.temple.module.Category;
import nz.temple.module.Module;

public final class Fullbright extends Module {
	private static volatile boolean refresh;

	public Fullbright() {
		super("Fullbright", Category.RENDER, "Makes everything fully lit. Only affects what you see.");
	}

	@Override
	protected void onEnable() {
		refresh = true;
	}

	@Override
	protected void onDisable() {
		refresh = true;
	}

	public static void requestRefresh() {
		refresh = true;
	}

	public static boolean consumeRefresh() {
		boolean r = refresh;
		refresh = false;
		return r;
	}
}
