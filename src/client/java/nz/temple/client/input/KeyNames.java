package nz.temple.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import nz.temple.client.compat.Input;
import nz.temple.setting.KeybindSetting;

public final class KeyNames {
	public static final int MOUSE = 1000;
	private KeyNames() {
	}

	public static int mouse(int button) {
		return MOUSE + button;
	}
	public static boolean bindable(int button) {
		return button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT
				&& button >= InputConstants.MOUSE_BUTTON_MIDDLE && button <= Input.LAST_MOUSE_BUTTON;
	}
	public static boolean isDown(Window window, int key) {
		if (key >= MOUSE) {
			int button = key - MOUSE;
			return Input.isMouseDown(window, button);
		}
		return key >= 0 && Input.isKeyDown(window, key);
	}
	public static String name(int key) {
		if (key == KeybindSetting.NONE) {
			return "None";
		}
		if (key >= MOUSE) {
			int button = key - MOUSE;
			return button == InputConstants.MOUSE_BUTTON_MIDDLE ? "Middle Mouse" : "Mouse " + (button - InputConstants.MOUSE_BUTTON_LEFT + 1);
		}
		return Input.KEYBOARD.getOrCreate(key).getDisplayName().getString();
	}
}
