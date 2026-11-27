package nz.temple.client.input;

import nz.temple.client.input.KeyNames;

/**
 * Decodes the bind strings ported modules read from {@code SchemaModule.getSetting}: "TK:&lt;code&gt;" wraps a Temple keybind code
 * (keyboard key, or {@link KeyNames#MOUSE} + button for mouse buttons).
 */
public final class BindStrings {
	private BindStrings() {
	}

	private static int code(String name) {
		if (name == null || !name.startsWith("TK:")) {
			return -1;
		}
		try {
			return Integer.parseInt(name.substring(3));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	public static int mouseButtonIndex(String name) {
		int c = code(name);
		return c >= KeyNames.MOUSE ? c - KeyNames.MOUSE : -1;
	}

	/** Whether the bound key or mouse button is held, on any supported Minecraft version. */
	public static boolean isDown(String name) {
		int c = code(name);
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		return c >= 0 && mc.getWindow() != null && KeyNames.isDown(mc.getWindow(), c);
	}

	/** The bound key's number, or -1 for none (or a mouse button). */
	public static int keyCode(String name) {
		return glfwKeyCode(name);
	}

	public static int glfwKeyCode(String name) {
		int c = code(name);
		return c >= 0 && c < KeyNames.MOUSE ? c : -1;
	}
}
