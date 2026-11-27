package nz.temple.client.compat;

import com.mojang.blaze3d.platform.Window;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/** Operating-system plumbing whose home moved between Minecraft versions (26.3 swapped GLFW for SDL). */
public final class Platform {
	private Platform() {
	}

	/** The game window's Win32 handle (HWND), or 0 where there is none. */
	public static long hwnd(Window window) {
		//? if >=26.3 {
		/*return org.lwjgl.sdl.SDLProperties.SDL_GetPointerProperty(org.lwjgl.sdl.SDLVideo.SDL_GetWindowProperties(window.handle()),
				org.lwjgl.sdl.SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0L);
		*///?} else {
		return org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(window.handle());
		//?}
	}

	/** Opens a web address in the browser. */
	public static void openUri(String uri) {
		//? if >=26.3 {
		/*com.mojang.blaze3d.Blaze3D.openUri(URI.create(uri));
		*///?} else {
		net.minecraft.util.Util.getPlatform().openUri(uri);
		//?}
	}

	/** Opens a folder or file in the system's file manager. */
	public static void openPath(Path path) {
		//? if >=26.3 {
		/*com.mojang.blaze3d.Blaze3D.openPath(path);
		*///?} else {
		net.minecraft.util.Util.getPlatform().openPath(path);
		//?}
	}

	/**
	 * The system "Open file" dialog, blocking until it closes: the chosen path, or null. Call it off the render thread.
	 * {@code extensions} (without dots) limits the files shown; {@code label} names them.
	 */
	public static String openFileDialog(String title, List<String> extensions, String label) {
		//? if >=26.3 {
		/*return Win32FileDialog.open(title, extensions, label);
		*///?} else {
		try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
			org.lwjgl.PointerBuffer filters = null;
			if (!extensions.isEmpty()) {
				filters = stack.mallocPointer(extensions.size());
				for (String ext : extensions) {
					filters.put(stack.UTF8("*." + ext));
				}
				filters.flip();
			}
			return org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(title, null, filters, label, false);
		}
		//?}
	}
}
