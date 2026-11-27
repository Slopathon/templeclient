package nz.temple.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

/**
 * Keyboard and mouse state across versions. Key and button numbers are the running version's own (GLFW key codes
 * before 26.3, SDL scancodes from it); Temple stores them as they are, and each version keeps its own config folder.
 */
public final class Input {
	/** The keyboard key type, for building a key from its number. */
	//? if >=26.3 {
	/*public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYBOARD;
	*///?} else {
	public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYSYM;
	//?}

	/** The numpad minus key, which the game has no constant for. */
	//? if >=26.3 {
	/*public static final int KEY_NUMPAD_SUBTRACT = org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_KP_MINUS;
	*///?} else {
	public static final int KEY_NUMPAD_SUBTRACT = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT;
	//?}

	/** The highest mouse button number the game reports. */
	//? if >=26.3 {
	/*public static final int LAST_MOUSE_BUTTON = InputConstants.MOUSE_BUTTON_8;
	*///?} else {
	public static final int LAST_MOUSE_BUTTON = org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LAST;
	//?}

	private Input() {
	}

	public static boolean isKeyDown(Window window, int key) {
		//? if >=26.3 {
		/*return InputConstants.isKeyDown(key);
		*///?} else {
		return InputConstants.isKeyDown(window, key);
		//?}
	}

	/**
	 * Lets {@code owner} receive typed characters. From 26.3 SDL sends them only while text input is on, which vanilla
	 * turns on just for a focused text box; Temple's screens draw their own fields, so they ask for it when shown.
	 * Changing screens turns it off again. Before 26.3 characters always arrive and this does nothing.
	 */
	public static void startTyping(Object owner) {
		//? if >=26.3 {
		/*net.minecraft.client.Minecraft.getInstance().textInputManager().startTextInput(owner);
		*///?}
	}

	public static boolean isMouseDown(Window window, int button) {
		if (button < 0 || button > LAST_MOUSE_BUTTON) {
			return false;
		}
		//? if >=26.3 {
		/*// SDL numbers buttons from 1; its state is a bit mask with button n at bit n - 1.
		return button > 0 && (org.lwjgl.sdl.SDLMouse.SDL_GetMouseState((java.nio.FloatBuffer) null, null) & (1 << (button - 1))) != 0;
		*///?} else {
		return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window.handle(), button) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		//?}
	}
}
