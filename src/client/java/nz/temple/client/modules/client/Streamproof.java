package nz.temple.client.modules.client;

import nz.temple.client.TempleNzClient;
import nz.temple.module.Category;
import nz.temple.module.Module;

public final class Streamproof extends Module {
	public static final String HELP = "Hides Temple from OBS, Discord and other recording software. They capture plain Minecraft "
			+ "while you still see your visuals, HUD and menus, which are drawn in an overlay that screen capture skips. Works "
			+ "windowed and fullscreen, but not with Exclusive Fullscreen (Video Settings). May reduce performance on some computers.";
	public Streamproof() {
		super("Streamproof", Category.CLIENT, HELP);
	}

	@Override
	public boolean listedInCategory() {
		return false;
	}
	public static boolean on() {
		var modules = TempleNzClient.modules();
		Streamproof m = modules == null ? null : modules.get(Streamproof.class);
		return m != null && m.isEnabled();
	}
	public static void turnOff() {
		var modules = TempleNzClient.modules();
		Streamproof m = modules == null ? null : modules.get(Streamproof.class);
		if (m != null) {
			m.setEnabled(false);
		}
	}
}
